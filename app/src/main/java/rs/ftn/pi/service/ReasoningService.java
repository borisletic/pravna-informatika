package rs.ftn.pi.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import rs.ftn.pi.model.CaseFacts;
import rs.ftn.pi.nlp.NlpClient;
import rs.ftn.pi.nlp.NlpDtos;
import rs.ftn.pi.reasoning.cbr.CaseReasoner;
import rs.ftn.pi.reasoning.dto.ReasoningResult;
import rs.ftn.pi.reasoning.rule.RuleReasoner;

import java.util.Map;

/**
 * Centralni servis koji pokreće oba reasoner-a paralelno (Celina 8)
 * i vraća kombinovan rezultat za prikaz.
 *
 * VLASNIK: Član 3.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReasoningService {

    private final RuleReasoner ruleReasoner;
    private final CaseReasoner caseReasoner;
    private final NlpClient nlpClient;
    /**
     * Pokreće oba reasoner-a i vraća kombinovan rezultat.
     *
     * TODO Član 3: razmotriti paralelno pokretanje preko CompletableFuture.
     */

    public CaseFacts extractFromText(String description) {
        NlpDtos.ExtractResponse response = nlpClient.extractFromCaseDescription(description);
        Map<String, Object> facts = new java.util.HashMap<>();
        if (response.getFacts() != null) {
            for (NlpDtos.Fact fact : response.getFacts()) {
                if (fact.getPredicate() != null && fact.getValue() != null) {
                    facts.put(fact.getPredicate(), fact.getValue());
                }
            }
        }
        CaseFacts result = new CaseFacts();
        result.setFacts(facts);
        return result;
    }

    public CombinedResult reasonAll(CaseFacts facts) {
        log.info("Pokrenuto kombinovano rasuđivanje");

        ReasoningResult ruleResult = ruleReasoner.reason(facts);
        ReasoningResult caseResult = caseReasoner.reason(facts);

        return CombinedResult.builder()
                .ruleBasedResult(ruleResult)
                .caseBasedResult(caseResult)
                .build();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @lombok.Builder
    public static class CombinedResult {
        private ReasoningResult ruleBasedResult;
        private ReasoningResult caseBasedResult;
    }
}
