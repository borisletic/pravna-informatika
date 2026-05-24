package rs.ftn.pi.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Predstavlja zakon učitan iz Akoma Ntoso XML-a.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Law {

    private String id;             // npr. "kz"
    private String title;          // "Krivični zakonik"
    private LocalDate enactedDate;
    private String akomaNtosoUri;  // FRBRWork URI

    @Builder.Default
    private List<Article> articles = new ArrayList<>();

    /**
     * Putanja do izvornog XML fajla (za XSLT prikaz).
     */
    private String xmlPath;
}
