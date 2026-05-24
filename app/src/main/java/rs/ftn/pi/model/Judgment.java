package rs.ftn.pi.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Sudska odluka učitana iz Akoma Ntoso XML-a + ekstrahovanih NLP podataka.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Judgment {

    private String id;             // interni id
    private String caseNumber;     // "K-145/2019"
    private String court;
    private LocalDate date;

    @Builder.Default
    private List<String> judges = new ArrayList<>();

    private String recorder;

    @Builder.Default
    private List<Party> parties = new ArrayList<>();

    /**
     * Pun tekst opisa činjeničnog stanja.
     */
    private String factualBackground;

    /**
     * Obrazloženje.
     */
    private String motivation;

    /**
     * Dispozitiv (presuda).
     */
    private String decision;

    /**
     * Reference ka članovima KZ-a koje su pomenute u odluci.
     */
    @Builder.Default
    private List<String> referencedArticles = new ArrayList<>();

    /**
     * Činjenice ekstrahovane NLP-om (Celina 4).
     */
    @Builder.Default
    private CaseFacts extractedFacts = new CaseFacts();

    /**
     * Putanja do izvornog XML fajla.
     */
    private String xmlPath;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Party {
        private String role;       // "OKR" (okrivljeni), "OST" (ostali)
        private String initials;   // "M.P."
    }
}
