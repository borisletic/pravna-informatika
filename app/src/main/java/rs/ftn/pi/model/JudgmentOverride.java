package rs.ftn.pi.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ručne ispravke automatski ekstrahovanih metapodataka i činjenica jedne presude
 * (Celina 4 — „mogućnost ručnog ažuriranja").
 *
 * Čuva se kao sidecar JSON: data/judgments/overrides/{id}.json. Prilikom prikaza,
 * vrednosti iz override-a imaju prednost nad automatski izvučenima.
 *
 * VLASNIK: Član 2.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class JudgmentOverride {

    private String caseNumber;
    private String court;
    private String date;            // YYYY-MM-DD

    @Builder.Default
    private List<String> judges = new ArrayList<>();

    private String recorder;

    @Builder.Default
    private List<Judgment.Party> parties = new ArrayList<>();

    /** Ručno ispravljene činjenice (predikat -> vrednost kao string). */
    @Builder.Default
    private Map<String, String> facts = new LinkedHashMap<>();

    /** Da li je korisnik ručno potvrdio/izmenio ovu presudu. */
    private boolean edited;
}
