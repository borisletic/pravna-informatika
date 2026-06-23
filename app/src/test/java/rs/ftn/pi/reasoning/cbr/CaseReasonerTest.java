package rs.ftn.pi.reasoning.cbr;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import rs.ftn.pi.config.AppConfig;
import rs.ftn.pi.model.CaseFacts;
import rs.ftn.pi.reasoning.dto.ReasoningResult;
import rs.ftn.pi.reasoning.dto.SimilarCase;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Test za nativni CBR ({@link CaseReasoner}) — Celina 6.
 * Ne koristi Spring/bazu; učitava pravi predicate_dictionary.yaml i cbr_cases.csv
 * iz ../data (radni dir je app/).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CaseReasonerTest {

    private CaseReasoner reasoner;

    @BeforeAll
    void setUp() {
        AppConfig cfg = new AppConfig();
        cfg.setDataDir("../data");
        cfg.getReasoning().setCasesFile("../data/cases/cases.csv");
        cfg.getReasoning().setPredicateDict("../data/schemas/predicate_dictionary.yaml");
        reasoner = new CaseReasoner(cfg);
        reasoner.init();
        assumeTrue(java.nio.file.Files.exists(java.nio.file.Paths.get("../data/cases/cbr_cases.csv")),
                "cbr_cases.csv nije dostupan — preskačem CBR test.");
    }

    @Test
    @DisplayName("kNN vraća sortirane slične slučajeve sa per-atribut sličnostima")
    void test_retrieval_ranks_similar_cases() {
        ReasoningResult r = reasoner.reason(facts(Map.of(
                "pollutionTarget", "SUMA",
                "substanceType", "DRVO",
                "intent", "UMISLJAJ",
                "damageExtent", "VELIKA",
                "priorConviction", true,
                "remediedDamage", false
        )));

        assertThat(r.getReasonerType()).isEqualTo(ReasoningResult.ReasonerType.CASE_BASED);
        assertThat(r.getSimilarCases()).isNotEmpty();

        // sortirano opadajuće
        for (int i = 1; i < r.getSimilarCases().size(); i++) {
            assertThat(r.getSimilarCases().get(i - 1).getSimilarity())
                    .isGreaterThanOrEqualTo(r.getSimilarCases().get(i).getSimilarity());
        }

        SimilarCase best = r.getSimilarCases().get(0);
        assertThat(best.getSimilarity()).isGreaterThan(0.8);     // skoro identičan slučaj postoji
        assertThat(best.getAttributeSimilarities()).isNotEmpty();
        assertThat(best.getArticleViolated()).isNotBlank();
    }

    @Test
    @DisplayName("Taksonomijska sličnost: srodne supstance daju delimičnu sličnost")
    void test_taxonomy_partial_similarity() {
        // HEMIJSKI_OTPAD i NAFTNI_DERIVATI su u istoj grupi (OPASAN) -> 0.5
        double sim = new SimilarityModelProbe().local("substanceType", "HEMIJSKI_OTPAD", "NAFTNI_DERIVATI");
        assertThat(sim).isEqualTo(0.5);
        double same = new SimilarityModelProbe().local("substanceType", "DRVO", "DRVO");
        assertThat(same).isEqualTo(1.0);
        double diff = new SimilarityModelProbe().local("substanceType", "DRVO", "HEMIJSKI_OTPAD");
        assertThat(diff).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Ordinalna sličnost: susedne vrednosti bliže od udaljenih")
    void test_ordinal_similarity() {
        SimilarityModelProbe p = new SimilarityModelProbe();
        // ecologicalDamage order [NEMA, OBICNA, VELIKIH_RAZMERA]
        double adjacent = p.local("ecologicalDamage", "OBICNA", "VELIKIH_RAZMERA"); // 0.5
        double far = p.local("ecologicalDamage", "NEMA", "VELIKIH_RAZMERA");        // 0.0
        assertThat(adjacent).isGreaterThan(far);
    }

    private CaseFacts facts(Map<String, Object> map) {
        CaseFacts cf = new CaseFacts();
        cf.setFacts(new HashMap<>(map));
        return cf;
    }

    /** Pomoćni omotač za testiranje SimilarityModel-a direktno. */
    static class SimilarityModelProbe {
        private final SimilarityModel m = new SimilarityModel(
                java.nio.file.Paths.get("../data/schemas/predicate_dictionary.yaml"));
        double local(String pred, String a, String b) {
            return m.localSimilarity(pred, m.normalize(pred, a), m.normalize(pred, b));
        }
    }
}
