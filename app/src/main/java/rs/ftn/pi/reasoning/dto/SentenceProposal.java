package rs.ftn.pi.reasoning.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Predlog kazne koji izvodi rule engine.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SentenceProposal {

    /**
     * Vrsta kazne: ZATVOR, NOVCANA, USLOVNA, ...
     */
    private SentenceType type;

    /**
     * Minimum (npr. 6 meseci).
     */
    private Integer minMonths;

    /**
     * Maximum (npr. 5 godina = 60 meseci).
     */
    private Integer maxMonths;

    /**
     * Predlog konkretne dužine (npr. sredina raspona).
     */
    private Integer proposedMonths;

    /**
     * Otežavajuće ili olakšavajuće okolnosti primenjene.
     */
    private String adjustments;

    public enum SentenceType {
        ZATVOR, NOVCANA, USLOVNA, RAD_U_JAVNOM_INTERESU
    }
}
