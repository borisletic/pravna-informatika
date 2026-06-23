package rs.ftn.pi.generation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import rs.ftn.pi.model.CaseFacts;
import rs.ftn.pi.nlp.NlpClient;
import rs.ftn.pi.reasoning.dto.ReasoningResult;
import rs.ftn.pi.reasoning.dto.SentenceProposal;
import rs.ftn.pi.reasoning.dto.SimilarCase;
import rs.ftn.pi.reasoning.dto.ViolatedArticle;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Generisanje nove sudske odluke (Celina 9).
 *
 * Obrazloženje se piše „po ugledu na postojeće odluke":
 *  1. ako je dostupan LLM (NLP servis, OPENAI_API_KEY) — generiše ga jezički model
 *     na osnovu činjenica, primenjenih članova i NAJSLIČNIJIH ranijih presuda (CBR);
 *  2. u suprotnom — šablonski tekst koji citira primenjeni član, rezultat rasuđivanja
 *     po pravilima i slične ranije slučajeve.
 *
 * Rezultat je anotiran Akoma Ntoso XML (kao i ostatak korpusa).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DecisionGenerator {

    private final NlpClient nlpClient;

    public String generate(CaseFacts facts,
                           ReasoningResult ruleResult,
                           ReasoningResult cbrResult,
                           SentenceProposal selectedSentence) {
        log.info("Generisanje nove odluke (Celina 9)");

        String id = UUID.randomUUID().toString().substring(0, 8);
        LocalDate now = LocalDate.now();
        String date = now.toString();
        String displayDate = String.format("%02d.%02d.%04d.", now.getDayOfMonth(), now.getMonthValue(), now.getYear());
        String caseNumber = "PI-" + id.toUpperCase();

        String appliedArticle = "";
        String articleText = "";
        String citation = "";
        if (ruleResult != null && !ruleResult.getViolatedArticles().isEmpty()) {
            ViolatedArticle art = ruleResult.getViolatedArticles().get(0);
            appliedArticle = art.getArticleEId() != null ? art.getArticleEId() : "";
            articleText = art.getText() != null ? art.getText() : "";
            citation = art.getCitation() != null ? art.getCitation() : "";
        }

        String sentenceText = "";
        if (selectedSentence != null && selectedSentence.getType() != null) {
            sentenceText = humanSentence(selectedSentence);
        }

        String description = facts.getDescription() != null ? facts.getDescription() : "";
        List<SimilarCase> similar = cbrResult != null && cbrResult.getSimilarCases() != null
                ? cbrResult.getSimilarCases() : List.of();

        // obrazloženje: LLM ako je dostupan, inače šablon (oba "po ugledu na postojeće odluke")
        String obrazlozenje = buildMotivation(facts, ruleResult, similar, selectedSentence, citation);

        String factsSummary = summarizeFacts(facts);
        String similarHtml = similarCasesText(similar);

        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <akomaNtoso xmlns="http://docs.oasis-open.org/legaldocml/ns/akn/3.0">
                  <judgment name="presuda">
                    <meta>
                      <identification source="#sistem">
                        <FRBRWork>
                          <FRBRthis value="/akn/rs/judgment/pi/%s/main"/>
                          <FRBRuri value="/akn/rs/judgment/pi/%s"/>
                          <FRBRalias name="caseNumber" value="%s"/>
                          <FRBRalias name="court" value="Sistem za podrsku odlucivanju"/>
                          <FRBRdate date="%s" name="judgment"/>
                          <FRBRauthor href="#sistem"/>
                          <FRBRcountry value="rs"/>
                        </FRBRWork>
                        <FRBRExpression>
                          <FRBRthis value="/akn/rs/judgment/pi/%s/srp@/main"/>
                          <FRBRuri value="/akn/rs/judgment/pi/%s/srp@"/>
                          <FRBRdate date="%s" name="judgment"/>
                          <FRBRauthor href="#sistem"/>
                          <FRBRlanguage language="srp"/>
                        </FRBRExpression>
                        <FRBRManifestation>
                          <FRBRthis value="/akn/rs/judgment/pi/%s/srp@/main.xml"/>
                          <FRBRuri value="/akn/rs/judgment/pi/%s/srp@.xml"/>
                          <FRBRdate date="%s" name="generation"/>
                          <FRBRauthor href="#sistem"/>
                          <FRBRformat value="application/xml"/>
                        </FRBRManifestation>
                      </identification>
                      <references source="#sistem">
                        <TLCOrganization eId="sistem" href="/akn/ontology/organization/rs/pi-sistem" showAs="Sistem za podrsku odlucivanju"/>
                        <TLCReference eId="kz" href="/akn/rs/act/2005/kz" showAs="Krivicni zakonik"/>
                      </references>
                    </meta>
                    <judgmentBody>
                      <introduction>
                        <p>Predmet broj %s od dana %s.</p>
                        <p>%s</p>
                      </introduction>
                      <background>
                        <p>Na osnovu utvrdjenog cinjenicnog stanja:</p>
                        <p>%s</p>
                      </background>
                      <decision>
                        <p>Okrivljeni se oglasava krivim jer je prekrsio <ref href="/laws/kz#%s">%s</ref>.</p>
                        <p>Izrice se: <b>%s</b></p>
                      </decision>
                      <motivation>
                        %s
                        %s
                      </motivation>
                    </judgmentBody>
                  </judgment>
                </akomaNtoso>
                """.formatted(
                        id, id, caseNumber, date,
                        id, id, date,
                        id, id, date,
                        caseNumber, displayDate, esc(description),
                        esc(factsSummary),
                        articleEIdToAnchor(appliedArticle), esc(citation.isEmpty() ? "navedeni clan KZ" : citation),
                        esc(sentenceText.isEmpty() ? "kazna po oceni suda" : sentenceText),
                        obrazlozenje,
                        similarHtml
        );
    }

    /** Obrazloženje: LLM (ako dostupan) inače šablon — oba se oslanjaju na slične ranije presude. */
    private String buildMotivation(CaseFacts facts, ReasoningResult ruleResult,
                                   List<SimilarCase> similar, SentenceProposal sentence, String citation) {
        String prompt = buildPrompt(facts, ruleResult, similar, sentence, citation);
        String llm = nlpClient.generateDecisionText(prompt);
        if (llm != null) {
            log.info("Obrazloženje generisano LLM-om.");
            StringBuilder sb = new StringBuilder();
            for (String para : llm.split("\\n\\s*\\n")) {
                if (!para.isBlank()) sb.append("<p>").append(esc(para.trim())).append("</p>\n        ");
            }
            return sb.toString();
        }
        // šablon
        String expl = ruleResult != null && ruleResult.getExplanation() != null
                ? ruleResult.getExplanation() : "Na osnovu utvrdjenih cinjenica i primenjenih pravnih normi.";
        return "<p>" + esc(expl) + "</p>";
    }

    private String buildPrompt(CaseFacts facts, ReasoningResult ruleResult,
                               List<SimilarCase> similar, SentenceProposal sentence, String citation) {
        StringBuilder sb = new StringBuilder();
        sb.append("Napiši obrazloženje presude za sledeći slučaj.\n");
        if (facts.getDescription() != null && !facts.getDescription().isBlank()) {
            sb.append("Opis: ").append(facts.getDescription()).append("\n");
        }
        sb.append("Utvrđene činjenice: ").append(summarizeFacts(facts)).append("\n");
        if (ruleResult != null && !ruleResult.getViolatedArticles().isEmpty()) {
            sb.append("Prekršeni članovi: ");
            List<String> cs = new ArrayList<>();
            for (ViolatedArticle a : ruleResult.getViolatedArticles()) cs.add(a.getCitation());
            sb.append(String.join(", ", cs)).append("\n");
        }
        if (sentence != null) sb.append("Izrečena kazna: ").append(humanSentence(sentence)).append("\n");
        if (!similar.isEmpty()) {
            sb.append("Slične ranije presude (radi ujednačenosti kaznene politike): ");
            List<String> sc = new ArrayList<>();
            for (SimilarCase s : similar) {
                String out = s.getSourceJudgmentId();
                if (s.getSentence() != null && s.getSentence().getProposedMonths() != null) {
                    out += " (" + s.getSentence().getProposedMonths() + " meseci)";
                }
                sc.add(out);
            }
            sb.append(String.join("; ", sc)).append("\n");
        }
        return sb.toString();
    }

    private String summarizeFacts(CaseFacts facts) {
        if (facts.getFacts() == null || facts.getFacts().isEmpty()) return "nema strukturiranih činjenica";
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Object> e : facts.getFacts().entrySet()) {
            parts.add(e.getKey() + " = " + e.getValue());
        }
        return String.join(", ", parts);
    }

    private String similarCasesText(List<SimilarCase> similar) {
        if (similar.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("<p>Pri odmeravanju kazne sud je imao u vidu sličnu sudsku praksu: ");
        List<String> cs = new ArrayList<>();
        for (SimilarCase s : similar) {
            String out = s.getSourceJudgmentId();
            if (s.getSentence() != null && s.getSentence().getProposedMonths() != null) {
                out += " — " + s.getSentence().getProposedMonths() + " meseci";
            }
            cs.add(out);
        }
        sb.append(esc(String.join("; ", cs))).append(".</p>");
        return sb.toString();
    }

    private String humanSentence(SentenceProposal s) {
        String t = s.getType() != null ? s.getType().name() : "";
        if (s.getProposedMonths() != null && s.getType() == SentenceProposal.SentenceType.ZATVOR) {
            return "kazna zatvora u trajanju od " + s.getProposedMonths() + " meseci";
        }
        if (s.getType() == SentenceProposal.SentenceType.NOVCANA) return "novčana kazna";
        if (s.getType() == SentenceProposal.SentenceType.USLOVNA) {
            return "uslovna osuda" + (s.getProposedMonths() != null ? " (" + s.getProposedMonths() + " meseci)" : "");
        }
        return t.isEmpty() ? "" : t;
    }

    private String articleEIdToAnchor(String eId) {
        if (eId == null || eId.isBlank()) return "";
        // za navigaciju ka zakonu koristimo nivo člana (art_NNN)
        int idx = eId.indexOf("__");
        return idx > 0 ? eId.substring(0, idx) : eId;
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
