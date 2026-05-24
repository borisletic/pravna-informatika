package rs.ftn.pi.reasoning.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Prekršeni član zakona.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ViolatedArticle {

    /**
     * Akoma Ntoso eId, npr. "art_260__para_1"
     */
    private String articleEId;

    /**
     * Citirano ime, npr. "Kazneni zakonik, čl. 260 st. 1"
     */
    private String citation;

    /**
     * Tekst odredbe (radi prikaza u UI).
     */
    private String text;

    /**
     * Sigurnost zaključka (za defeasible reasoning iz dr-device-a).
     * Vrednosti: STRICT, DEFEASIBLE, AMBIGUOUS
     */
    private String certainty;
}
