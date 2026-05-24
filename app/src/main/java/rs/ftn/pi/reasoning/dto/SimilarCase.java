package rs.ftn.pi.reasoning.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Sličan slučaj koji vraća CBR reasoner.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SimilarCase {

    private Long caseId;

    /**
     * ID izvorne odluke (ako postoji), npr. "K-145/2019"
     */
    private String sourceJudgmentId;

    /**
     * Sličnost u rasponu [0.0, 1.0]
     */
    private Double similarity;

    /**
     * Sličnosti po pojedinačnim atributima (za prikaz "zašto je sličan").
     */
    private Map<String, Double> attributeSimilarities;

    /**
     * Činjenice tog slučaja.
     */
    private Map<String, Object> facts;

    /**
     * Konačni ishod tog slučaja - prekršeni član.
     */
    private String articleViolated;

    /**
     * Konačni ishod - kazna.
     */
    private SentenceProposal sentence;
}
