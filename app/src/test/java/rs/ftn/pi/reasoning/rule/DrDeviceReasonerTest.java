package rs.ftn.pi.reasoning.rule;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import rs.ftn.pi.config.AppConfig;
import rs.ftn.pi.model.CaseFacts;
import rs.ftn.pi.reasoning.dto.ReasoningResult;
import rs.ftn.pi.reasoning.dto.ViolatedArticle;
import rs.ftn.pi.service.LawArticleTextService;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integracioni test za {@link DrDeviceReasoner} (Celina 5).
 *
 * Pokrece STVARNI dr-device (CLIPS) nad generisanim pravilima iz
 * {@code environmental_rules.lrml}. Ne koristi Spring kontekst (ni bazu).
 *
 * Test se PRESKACE ako dr-device distribucija nije dostupna / ne moze da se
 * pokrene (npr. na ne-Windows CI), pa ne lomi build na drugim masinama.
 *
 * Pretpostavka: radni direktorijum je app/ (Maven default), pa su
 * ../data i ../dr-device u korenu repozitorijuma.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DrDeviceReasonerTest {

    private DrDeviceReasoner reasoner;

    @BeforeAll
    void setUp() {
        AppConfig cfg = new AppConfig();
        cfg.setDataDir("../data");
        cfg.getReasoning().setRulesFile("../data/rules/environmental_rules.lrml");
        cfg.getDrDevice().setHome("../dr-device");
        cfg.getDrDevice().setTimeoutSeconds(120);

        LawArticleTextService law = new LawArticleTextService(cfg);
        reasoner = new DrDeviceReasoner(cfg, law);
        reasoner.initRules();

        assumeTrue(reasoner.isReady(),
                "dr-device nije dostupan (CLIPSDOS/Saxon) - preskacem integracioni test.");
    }

    @Test
    @DisplayName("R1+R3: kvalifikovani oblik (st.3) pobija osnovni (st.1) - defeasible")
    void test_qualified_defeats_basic() {
        ReasoningResult r = reasoner.reason(facts(Map.of(
                "violatedEnvironmentalRegs", true,
                "pollutionExtent", "VECA_MERA",
                "intent", "UMISLJAJ",
                "ecologicalDamage", "VELIKIH_RAZMERA"
        )));

        assertThat(r.getViolatedArticles())
                .extracting(ViolatedArticle::getArticleEId)
                .contains("art_260__para_3")        // kvalifikovani - dokazan
                .doesNotContain("art_260__para_1"); // osnovni - pobijen (defeasible)
        assertThat(r.getSentenceProposal().getMaxMonths()).isEqualTo(96);
        assertThat(r.getSentenceProposal().getMinMonths()).isEqualTo(12);
    }

    @Test
    @DisplayName("R2: nehat + siri prostor -> krsi cl. 260 st. 2")
    void test_negligent_pollution() {
        ReasoningResult r = reasoner.reason(facts(Map.of(
                "violatedEnvironmentalRegs", true,
                "pollutionScope", "SIRI_PROSTOR",
                "intent", "NEHAT"
        )));

        assertThat(r.getViolatedArticles())
                .extracting(ViolatedArticle::getArticleEId)
                .contains("art_260__para_2");
    }

    @Test
    @DisplayName("R11 vs R10: zloupotreba polozaja kvalifikuje delo iz st.1 u st.2")
    void test_dangerous_substance_official_abuse() {
        ReasoningResult r = reasoner.reason(facts(Map.of(
                "dangerousSubstanceAction", true,
                "officialPositionAbuse", true
        )));

        assertThat(r.getViolatedArticles())
                .extracting(ViolatedArticle::getArticleEId)
                .contains("art_266__para_2")
                .doesNotContain("art_266__para_1");
    }

    @Test
    @DisplayName("R12: organizovanje vrsenja - najtezi oblik (36-120 meseci)")
    void test_organizer() {
        ReasoningResult r = reasoner.reason(facts(Map.of(
                "dangerousSubstanceAction", true,
                "organizesCrime", true
        )));

        assertThat(r.getViolatedArticles())
                .extracting(ViolatedArticle::getArticleEId)
                .contains("art_266__para_5");
        assertThat(r.getSentenceProposal().getMinMonths()).isEqualTo(36);
        assertThat(r.getSentenceProposal().getMaxMonths()).isEqualTo(120);
    }

    @Test
    @DisplayName("Prazne cinjenice -> nijedno pravilo nije dokazano")
    void test_empty_facts() {
        ReasoningResult r = reasoner.reason(new CaseFacts());
        assertThat(r.getViolatedArticles()).isEmpty();
    }

    private CaseFacts facts(Map<String, Object> map) {
        CaseFacts cf = new CaseFacts();
        cf.setFacts(new HashMap<>(map));
        return cf;
    }
}
