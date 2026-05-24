package rs.ftn.pi.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Stav unutar člana.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Paragraph {

    /**
     * Akoma Ntoso eId, npr. "art_260__para_1"
     */
    private String eId;

    private String number;  // "(1)"
    private String text;
}
