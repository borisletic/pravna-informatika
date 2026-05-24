package rs.ftn.pi.reasoning.rule;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import rs.ftn.pi.model.CaseFacts;
import rs.ftn.pi.reasoning.dto.ReasoningResult;
import rs.ftn.pi.reasoning.dto.ViolatedArticle;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test za RuleReasoner / Drools.
 *
 * VLASNIK: Član 1
 *
 * Ovi testovi proveravaju da pravila ispravno pucaju na realnim
 * scenarijima koji odgovaraju primerima iz sudske prakse.
 *
 * NAPOMENA: zahteva da Drools dependency-ji budu u pom.xml-u i da
 * environmental_rules.drl postoji u data/rules/.
 */
@SpringBootTest
@ActiveProfiles("test")
class RuleReasonerTest {

    @Autowired
    private RuleReasoner reasoner;

    @Test
    @DisplayName("R1: Umiljaj + voda + veca mera -> krsi cl. 260 st. 1")
    void test_R1_intentional_pollution() {
        CaseFacts facts = facts(Map.of(
                "violatedEnvironmentalRegs", true,
                "pollutionTarget", "VODA",
                "pollutionExtent", "VECA_MERA",
                "intent", "UMISLJAJ"
        ));

        ReasoningResult result = reasoner.reason(facts);

        assertThat(result.getViolatedArticles())
                .extracting(ViolatedArticle::getArticleEId)
                .contains("art_260__para_1");
        assertThat(result.getSentenceProposal().getMinMonths()).isEqualTo(6);
        assertThat(result.getSentenceProposal().getMaxMonths()).isEqualTo(60);
    }

    @Test
    @DisplayName("R2: Nehat + zemljiste + siri prostor -> krsi cl. 260 st. 2")
    void test_R2_negligent_pollution() {
        CaseFacts facts = facts(Map.of(
                "violatedEnvironmentalRegs", true,
                "pollutionTarget", "ZEMLJISTE",
                "pollutionScope", "SIRI_PROSTOR",
                "intent", "NEHAT"
        ));

        ReasoningResult result = reasoner.reason(facts);

        assertThat(result.getViolatedArticles())
                .extracting(ViolatedArticle::getArticleEId)
                .contains("art_260__para_2");
    }

    @Test
    @DisplayName("R1+R3: Umiljaj + velike razmere stete -> krsi i cl. 260 st. 1 i st. 3")
    void test_R1_R3_chained_qualified() {
        CaseFacts facts = facts(Map.of(
                "violatedEnvironmentalRegs", true,
                "pollutionTarget", "VODA",
                "pollutionExtent", "VECA_MERA",
                "intent", "UMISLJAJ",
                "ecologicalDamage", "VELIKIH_RAZMERA"
        ));

        ReasoningResult result = reasoner.reason(facts);

        // Treba da imamo OBA - R1 (osnovni oblik) i R3 (kvalifikovani)
        assertThat(result.getViolatedArticles())
                .extracting(ViolatedArticle::getArticleEId)
                .contains("art_260__para_1", "art_260__para_3");

        // Najtezi je 260/3 sa rasponom 12-96
        // Sentence proposal treba da odrazi najtezi oblik
        assertThat(result.getSentenceProposal().getMaxMonths()).isEqualTo(96);
    }

    @Test
    @DisplayName("R5: Sluzbeno lice nije preduzelo mere zastite")
    void test_R5_failed_protective_measures() {
        CaseFacts facts = facts(Map.of(
                "perpetratorType", "SLUZBENO_LICE",
                "failedToTakeProtectiveMeasures", true
        ));

        ReasoningResult result = reasoner.reason(facts);

        assertThat(result.getViolatedArticles())
                .extracting(ViolatedArticle::getArticleEId)
                .contains("art_261__para_1");
    }

    @Test
    @DisplayName("R11 vs R10: Zloupotreba polozaja kvalifikuje delo iz st. 1 u st. 2")
    void test_R11_dangerous_substance_official_abuse() {
        CaseFacts facts = facts(Map.of(
                "dangerousSubstanceAction", true,
                "officialPositionAbuse", true
        ));

        ReasoningResult result = reasoner.reason(facts);

        // R11 puca (kvalifikovani), R10 NE puca (jer ima officialPositionAbuse=true)
        assertThat(result.getViolatedArticles())
                .extracting(ViolatedArticle::getArticleEId)
                .contains("art_266__para_2")
                .doesNotContain("art_266__para_1");
    }

    @Test
    @DisplayName("R12: Organizovanje vrsenja - najtezi oblik")
    void test_R12_organizer() {
        CaseFacts facts = facts(Map.of(
                "dangerousSubstanceAction", true,
                "organizesCrime", true
        ));

        ReasoningResult result = reasoner.reason(facts);

        assertThat(result.getViolatedArticles())
                .extracting(ViolatedArticle::getArticleEId)
                .contains("art_266__para_5");
        assertThat(result.getSentenceProposal().getMinMonths()).isEqualTo(36);
        assertThat(result.getSentenceProposal().getMaxMonths()).isEqualTo(120);
    }

    @Test
    @DisplayName("Prazne cinjenice -> nijedno pravilo ne puca")
    void test_empty_facts() {
        ReasoningResult result = reasoner.reason(new CaseFacts());

        assertThat(result.getViolatedArticles()).isEmpty();
        assertThat(result.getExplanation())
                .contains("Nijedno pravilo nije puklo");
    }

    @Test
    @DisplayName("Trace izvodjenja - prati koje cinjenice su pokrenule pravila")
    void test_derivation_trace() {
        CaseFacts facts = facts(Map.of(
                "violatedEnvironmentalRegs", true,
                "pollutionTarget", "VODA",
                "pollutionExtent", "VECA_MERA",
                "intent", "UMISLJAJ"
        ));

        ReasoningResult result = reasoner.reason(facts);

        assertThat(result.getDerivations()).isNotEmpty();
        assertThat(result.getDerivations().get(0).getRuleId()).isEqualTo("R1");
        assertThat(result.getDerivations().get(0).getMatchedFacts())
                .anyMatch(f -> f.contains("intent = UMISLJAJ"));
    }

    // ============================================================
    // helper
    // ============================================================

    private CaseFacts facts(Map<String, Object> map) {
        CaseFacts cf = new CaseFacts();
        cf.setFacts(new HashMap<>(map));
        return cf;
    }
}

