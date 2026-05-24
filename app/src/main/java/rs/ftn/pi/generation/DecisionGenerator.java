package rs.ftn.pi.generation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import rs.ftn.pi.model.CaseFacts;
import rs.ftn.pi.reasoning.dto.ReasoningResult;
import rs.ftn.pi.reasoning.dto.SentenceProposal;

/**
 * Generiše novu sudsku odluku na osnovu opisa slučaja i rezultata rasuđivanja.
 *
 * VLASNIK: Član 3 (uz podršku Člana 2 za ML).
 * CELINA: 9.
 *
 * Pristup (Strategy A iz Structure dokumenta):
 *   - Šablon u Akoma Ntoso XML formatu
 *   - Placeholder-e popunjava sa metadata + činjenicama + rezultatima
 *   - Parafraziranje teksta preko LLM/BART poziva (može se uraditi
 *     preko NLP servisa ili direktnog API poziva)
 */
@Slf4j
@Service
public class DecisionGenerator {

    /**
     * Generiše Akoma Ntoso XML nove odluke.
     */
    public String generate(CaseFacts facts,
                           ReasoningResult ruleResult,
                           SentenceProposal selectedSentence) {
        log.info("Generisanje nove odluke (MOCK)");

        // TODO Član 3:
        //  1. Učitati šablon iz src/main/resources/templates-akn/judgment_template.xml
        //  2. Popuniti placehodere ({{caseNumber}}, {{date}}, {{appliedArticle}}...)
        //  3. Za delove "obrazloženje" - pozvati NLP servis ili LLM da parafrazira
        //     činjenice u pravni stil
        //  4. Anotirati novi XML (reference ka članovima KZ-a)
        //  5. Sačuvati u data/judgments/generated/{id}.xml
        //  6. Vratiti XML string za prikaz

        return "<akomaNtoso><!-- TODO Generisanje --></akomaNtoso>";
    }
}
