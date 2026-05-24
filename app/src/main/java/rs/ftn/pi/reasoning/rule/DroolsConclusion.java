package rs.ftn.pi.reasoning.rule;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Interni DTO koji Drools pravila ispaljuju.
 * Mapira se u ViolatedArticle + SentenceProposal pre vraćanja iz RuleReasoner-a.
 *
 * VLASNIK: Član 1 (Legal Modeling)
 * CELINA: 5
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DroolsConclusion {

    /**
     * ID pravila koje je puknulo (R1-R14).
     */
    private String ruleId;

    /**
     * Akoma Ntoso eId prekršenog člana (npr. "art_260__para_1").
     */
    private String articleEId;

    /**
     * Minimum kazne u mesecima.
     */
    private int minMonths;

    /**
     * Maximum kazne u mesecima.
     */
    private int maxMonths;
}
