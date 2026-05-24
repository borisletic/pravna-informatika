package rs.ftn.pi.reasoning.cbr;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import rs.ftn.pi.config.AppConfig;
import rs.ftn.pi.model.CaseFacts;
import rs.ftn.pi.reasoning.Reasoner;
import rs.ftn.pi.reasoning.dto.ReasoningResult;
import rs.ftn.pi.reasoning.dto.SentenceProposal;
import rs.ftn.pi.reasoning.dto.SimilarCase;

import java.util.List;
import java.util.Map;

/**
 * Rasuđivanje po slučajevima (CBR) preko jColibri.
 *
 * VLASNIK: Član 2 (NLP & Data).
 * CELINA: 6.
 *
 * Trenutno MOCK implementacija.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CaseReasoner implements Reasoner {

    private final AppConfig appConfig;

    @Override
    public ReasoningResult reason(CaseFacts facts) {
        log.info("CaseReasoner: pretraga sličnih slučajeva za {} činjenica", facts.getFacts().size());

        // ========================================================
        // TODO Član 2: Implementirati pravi jColibri tok.
        //
        // Koraci:
        //  1. Inicijalizovati jColibri aplikaciju (CBRApplication interface)
        //  2. Connector: CSV connector ka appConfig.getReasoning().getCasesFile()
        //  3. Definisati Description (atributi = ključni predikati iz rečnika)
        //  4. Konfigurisati funkcije sličnosti:
        //     - Interval / Linear za substanceQuantityM3, forestAreaHa
        //     - Equal za pollutionTarget, intent, priorConviction, remediedDamage
        //     - Ordinal za damageExtent
        //     - Taxonomy za substanceType (videti predicate_dictionary.yaml)
        //  5. Globalna funkcija = Weighted Average sa težinama iz rečnika
        //  6. NN-Retrieval, vrati top 5
        //  7. Iskoristi top-N u SimilarCase[] sa per-attribute similarities
        //  8. (kasnije) Retain - dodati novi slučaj u CSV
        // ========================================================

        return mockResult(facts);
    }

    /**
     * Dodaje novi (rešen) slučaj u bazu.
     * Poziva Član 3 nakon što korisnik potvrdi presudu u Celini 8.
     *
     * TODO Član 2: implementirati upis u CSV (OpenCSV već u dependencies).
     */
    public void retain(CaseFacts facts,
                       String articleViolated,
                       SentenceProposal sentence) {
        log.info("CaseReasoner.retain: TODO - implementirati upis novog slučaja u CSV");
        // appendToCsv(appConfig.getReasoning().getCasesFile(), ...);
    }

    private ReasoningResult mockResult(CaseFacts facts) {
        SimilarCase mock1 = SimilarCase.builder()
                .caseId(1L)
                .sourceJudgmentId("K-145/2019")
                .similarity(0.87)
                .attributeSimilarities(Map.of(
                        "substanceQuantityM3", 0.9,
                        "substanceType", 1.0,
                        "damageExtent", 0.8
                ))
                .facts(Map.of(
                        "substanceQuantityM3", 10.0,
                        "substanceType", "HEMIJSKI_OTPAD"
                ))
                .articleViolated("art_260__para_1")
                .sentence(SentenceProposal.builder()
                        .type(SentenceProposal.SentenceType.ZATVOR)
                        .proposedMonths(15)
                        .build())
                .build();

        return ReasoningResult.builder()
                .reasonerType(ReasoningResult.ReasonerType.CASE_BASED)
                .similarCases(List.of(mock1))
                .explanation("[MOCK] CBR pretraga još nije implementirana.")
                .build();
    }
}
