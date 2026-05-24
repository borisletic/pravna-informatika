package rs.ftn.pi.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Član zakona.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Article {

    /**
     * Akoma Ntoso eId, npr. "art_260"
     */
    private String eId;

    private String number;     // "260"
    private String heading;    // "Zagađenje životne sredine"

    @Builder.Default
    private List<Paragraph> paragraphs = new ArrayList<>();

    /**
     * Reference ka drugim članovima/zakonima u ovom članu.
     */
    @Builder.Default
    private List<String> references = new ArrayList<>();
}
