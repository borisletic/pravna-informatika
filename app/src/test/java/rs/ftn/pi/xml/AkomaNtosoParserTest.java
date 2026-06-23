package rs.ftn.pi.xml;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import rs.ftn.pi.model.Judgment;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test za parsiranje ANOTIRANIH presuda (Celina 2). Radni dir = app/, podaci u ../data.
 */
class AkomaNtosoParserTest {

    static boolean dataPresent() {
        return Files.exists(Paths.get("../data/judgments/kzz-1032-2024.xml"));
    }

    private final AkomaNtosoParser parser = new AkomaNtosoParser();

    @Test
    @EnabledIf("dataPresent")
    @DisplayName("Čita anotirane metapodatke i SAMO prave KZ reference (bez lažnih)")
    void test_reads_annotations() {
        Judgment j = parser.parseJudgment(Paths.get("../data/judgments/kzz-1032-2024.xml"));

        assertThat(j.getCaseNumber()).contains("1032");
        assertThat(j.getCourt()).containsIgnoringCase("врховни");
        assertThat(j.getDate()).isNotNull();
        assertThat(j.getJudges()).isNotEmpty();

        // reference iskључиво iz anotiranih <ref> ka KZ čl. 260-277 — nema procesnih (npr. 438 ZKP)
        assertThat(j.getReferencedArticles()).isNotEmpty();
        for (String a : j.getReferencedArticles()) {
            int n = Integer.parseInt(a.replace("art_", ""));
            assertThat(n).isBetween(260, 277);
        }
    }

    @Test
    @EnabledIf("dataPresent")
    @DisplayName("Svaka anotirana presuda u korpusu citira bar jedan član KZ 260-277")
    void test_corpus_is_in_domain() throws Exception {
        Path dir = Paths.get("../data/judgments");
        try (var files = Files.list(dir)) {
            files.filter(p -> p.toString().endsWith(".xml")).forEach(p -> {
                Judgment j = parser.parseJudgment(p);
                assertThat(j.getReferencedArticles())
                        .as("in-domain refs for %s", p.getFileName())
                        .isNotEmpty();
            });
        }
    }
}
