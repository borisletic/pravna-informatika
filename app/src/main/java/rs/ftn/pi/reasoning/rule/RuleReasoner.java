package rs.ftn.pi.reasoning.rule;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import rs.ftn.pi.config.AppConfig;
import rs.ftn.pi.model.CaseFacts;
import rs.ftn.pi.reasoning.Reasoner;
import rs.ftn.pi.reasoning.dto.DerivationStep;
import rs.ftn.pi.reasoning.dto.ReasoningResult;
import rs.ftn.pi.reasoning.dto.SentenceProposal;
import rs.ftn.pi.reasoning.dto.ViolatedArticle;

import java.util.List;

/**
 * Rule-based rasuđivanje preko dr-device alata.
 *
 * VLASNIK: Član 1 (Legal Modeling).
 * CELINA: 5.
 *
 * Trenutno MOCK implementacija - vraća deterministične dummy rezultate
 * da Član 3 može da razvija UI paralelno.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RuleReasoner implements Reasoner {

    private final AppConfig appConfig;

    @Override
    public ReasoningResult reason(CaseFacts facts) {
        log.info("RuleReasoner: rasuđivanje nad {} činjenicama", facts.getFacts().size());

        // ========================================================
        // TODO Član 1: Implementirati pravi dr-device poziv.
        //
        // Koraci:
        //  1. Učitati LegalRuleML pravila iz appConfig.getReasoning().getRulesFile()
        //     (npr. preko JAXB ili XML parsera; cache-ovati nakon prvog poziva)
        //  2. Konvertovati CaseFacts u format koji dr-device razume
        //     (najverovatnije RDF triples ili RuleML facts)
        //  3. Pozvati dr-device engine
        //  4. Iskoristi rezultate u ReasoningResult
        //     - violatedArticles iz violates(X, art_*) atoma
        //     - sentenceProposal iz sentenceRange(X, min, max) atoma
        //     - derivations iz trace-a engine-a
        //
        // Fallback ako dr-device pravi problem: Drools sa DRL pravilima
        // (transpilirati LegalRuleML -> DRL pomocnim skriptom)
        // ========================================================

        return mockResult(facts);
    }

    /**
     * Mock - ostaje dok se ne implementira dr-device integracija.
     * Vraća detereministične rezultate na osnovu prisutnih činjenica
     * tako da Član 3 može da testira UI.
     */
    private ReasoningResult mockResult(CaseFacts facts) {
        ViolatedArticle article = ViolatedArticle.builder()
                .articleEId("art_260__para_1")
                .citation("KZ čl. 260 st. 1 (zagađenje životne sredine)")
                .text("[MOCK] Ko kršenjem propisa o zaštiti, očuvanju i unapređenju životne sredine zagadi vodu, vazduh ili zemljište...")
                .certainty("DEFEASIBLE")
                .build();

        SentenceProposal sentence = SentenceProposal.builder()
                .type(SentenceProposal.SentenceType.ZATVOR)
                .minMonths(6)
                .maxMonths(60)
                .proposedMonths(18)
                .adjustments("MOCK - bez stvarnog izvođenja")
                .build();

        DerivationStep step = DerivationStep.builder()
                .ruleId("R1-MOCK")
                .ruleDescription("MOCK pravilo (zameniti pravom dr-device integracijom)")
                .matchedFacts(List.of(facts.getFacts().keySet().toString()))
                .conclusion("violates(X, art_260_para_1)")
                .build();

        return ReasoningResult.builder()
                .reasonerType(ReasoningResult.ReasonerType.RULE_BASED)
                .violatedArticles(List.of(article))
                .sentenceProposal(sentence)
                .derivations(List.of(step))
                .explanation("[MOCK] Pravi rule reasoner još nije implementiran.")
                .build();
    }
}
