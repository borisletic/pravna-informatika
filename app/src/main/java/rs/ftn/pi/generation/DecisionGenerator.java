package rs.ftn.pi.generation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import rs.ftn.pi.model.CaseFacts;
import rs.ftn.pi.reasoning.dto.ReasoningResult;
import rs.ftn.pi.reasoning.dto.SentenceProposal;
import rs.ftn.pi.reasoning.dto.ViolatedArticle;

import java.time.LocalDate;
import java.util.UUID;

@Slf4j
@Service
public class DecisionGenerator {

    public String generate(CaseFacts facts,
                           ReasoningResult ruleResult,
                           SentenceProposal selectedSentence) {
        log.info("Generisanje nove odluke");

        String id = UUID.randomUUID().toString().substring(0, 8);
        LocalDate now = LocalDate.now();
        String date = now.toString();
        String displayDate = String.format("%02d.%02d.%04d.", now.getDayOfMonth(), now.getMonthValue(), now.getYear());
        String caseNumber = "PI-" + id.toUpperCase();

        String appliedArticle = "";
        String articleText = "";
        if (ruleResult != null && !ruleResult.getViolatedArticles().isEmpty()) {
            ViolatedArticle art = ruleResult.getViolatedArticles().get(0);
            appliedArticle = art.getArticleEId() != null ? art.getArticleEId() : "";
            articleText = art.getText() != null ? art.getText() : "";
        }

        String sentenceText = "";
        if (selectedSentence != null && selectedSentence.getType() != null) {
            sentenceText = selectedSentence.getType().name();
            if (selectedSentence.getProposedMonths() != null) {
                sentenceText += " u trajanju od " + selectedSentence.getProposedMonths() + " meseci";
            }
        }

        String description = facts.getDescription() != null ? facts.getDescription() : "";
        String explanation = ruleResult != null && ruleResult.getExplanation() != null
                ? ruleResult.getExplanation() : "Na osnovu utvrđenih činjenica.";

        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <akomaNtoso xmlns="http://docs.oasis-open.org/legaldocml/ns/akn/3.0">
                  <judgment name="presuda">
                    <meta>
                      <identification source="#sud">
                        <FRBRWork>
                          <FRBRthis value="/akn/rs/judgment/pi/%s/!main"/>
                          <FRBRuri value="/akn/rs/judgment/pi/%s"/>
                          <FRBRdate date="%s" name="judgment"/>
                          <FRBRauthor href="#sud"/>
                          <FRBRcountry value="rs"/>
                        </FRBRWork>
                        <FRBRExpression>
                          <FRBRthis value="/akn/rs/judgment/pi/%s/srp@%s/!main"/>
                          <FRBRuri value="/akn/rs/judgment/pi/%s/srp@%s"/>
                          <FRBRdate date="%s" name="judgment"/>
                          <FRBRauthor href="#sud"/>
                          <FRBRlanguage language="srp"/>
                        </FRBRExpression>
                        <FRBRManifestation>
                          <FRBRthis value="/akn/rs/judgment/pi/%s/srp@%s/!main.xml"/>
                          <FRBRuri value="/akn/rs/judgment/pi/%s/srp@%s.xml"/>
                          <FRBRdate date="%s" name="generation"/>
                          <FRBRauthor href="#sistem"/>
                          <FRBRformat value="application/xml"/>
                        </FRBRManifestation>
                        <FRBRalias name="caseNumber" value="%s"/>
                             <FRBRalias name="court" value="Sistem za automatsko rasuđivanje"/>
                      </identification>
                    </meta>
                    <judgmentBody>
                      <introduction>
                        <p>Osnovni sud u Beogradu je razmatrao slučaj broj <ref href="#case">%s</ref> od dana %s.</p>
                            <p>%s</p>
                      </introduction>
                      <background>
                        <p>Utvrđeno je da je učinilac kršio propise o zaštiti životne sredine.</p>
                        <p>Primenjeni član: <ref href="#%s">%s</ref></p>
                        <p>%s</p>
                      </background>
                      <motivation>
                        <p>%s</p>
                      </motivation>
                      <decision>
                        <p>Sud izriče sledeću odluku: <b>%s</b></p>
                      </decision>
                    </judgmentBody>
                  </judgment>
                </akomaNtoso>
                """.formatted(
                        id, id,
                        date,
                        id, date, id, date, date,
                        id, date, id, date, date,
                        caseNumber,
                        caseNumber, displayDate, description,
                        appliedArticle, appliedArticle,
                        articleText,
                        explanation,
                        sentenceText.isEmpty() ? "Kazna nije određena." : sentenceText
        );
    }
}