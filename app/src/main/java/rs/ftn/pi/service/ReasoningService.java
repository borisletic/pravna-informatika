package rs.ftn.pi.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import rs.ftn.pi.model.CaseFacts;
import rs.ftn.pi.reasoning.cbr.CaseReasoner;
import rs.ftn.pi.reasoning.dto.ReasoningResult;
import rs.ftn.pi.reasoning.rule.RuleReasoner;

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

    /**
     * Pokreće oba reasoner-a i vraća kombinovan rezultat.
     *
     * TODO Član 3: razmotriti paralelno pokretanje preko CompletableFuture.
     */
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
