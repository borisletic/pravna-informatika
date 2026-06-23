package rs.ftn.pi.reasoning.cbr;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * Jedan slučaj u CBR bazi (red iz cbr_cases.csv ili cases.csv).
 *
 * VLASNIK: Član 2 (CBR), Celina 6.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CbrCase {

    private Long id;
    /** Oznaka izvora — broj predmeta (cbr_cases.csv) ili "USER" (retain). */
    private String label;
    private String source;

    /** Činjenice (predikat -> vrednost), samo CBR-relevantni atributi. */
    @Builder.Default
    private Map<String, Object> facts = new HashMap<>();

    /** Ishod: prekršeni član (sirovo iz baze, npr. "274" ili "art_260__para_3"). */
    private String articleViolated;
    private String sentenceType;
    private Integer sentenceMonths;
}
