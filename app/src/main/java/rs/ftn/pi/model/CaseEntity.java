package rs.ftn.pi.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Slučaj u bazi - i izvorni (iz odluke) i novi (korisnički).
 *
 * Skladišti se u PostgreSQL preko JPA.
 * Atributi za CBR sličnost čuvaju se kao kolone.
 * Veće strukture (facts kao JSON, derivations) idu u JSON kolone.
 */
@Entity
@Table(name = "cases")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Da li je slučaj nastao iz izvorne odluke ili od korisnika.
     */
    @Enumerated(EnumType.STRING)
    private Source source;

    /**
     * Ako source = JUDGMENT, ovo je referenca na originalnu odluku.
     */
    private String sourceJudgmentId;

    // ====== Ključne činjenice (cbr_key: true u predicate_dictionary) ======
    private Double substanceQuantityM3;

    @Enumerated(EnumType.STRING)
    private SubstanceType substanceType;

    @Enumerated(EnumType.STRING)
    private PollutionTarget pollutionTarget;

    @Enumerated(EnumType.STRING)
    private DamageExtent damageExtent;

    @Enumerated(EnumType.STRING)
    private Intent intent;

    private Boolean priorConviction;
    private Boolean remediedDamage;

    private Double forestAreaHa;

    // ====== Ishod ======
    private String articleViolated;   // npr. "art_260__para_1"

    @Enumerated(EnumType.STRING)
    private SentenceType sentenceType;

    private Integer sentenceMonths;

    // ====== Tehnički metapodaci ======
    @Column(columnDefinition = "TEXT")
    private String factsJson;   // ostatak činjenica koje nisu u kolonama

    @Column(columnDefinition = "TEXT")
    private String description;

    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public enum Source { JUDGMENT, USER }
    public enum SubstanceType { HEMIJSKI_OTPAD, RADIOAKTIVNI_OTPAD, KOMUNALNI_OTPAD, NAFTNI_DERIVATI, OSTALO }
    public enum PollutionTarget { VODA, VAZDUH, TLO, VISESTRUKO }
    public enum DamageExtent { LAKA, TESKA, NAROCITO_TESKA }
    public enum Intent { UMISLJAJ, NEHAT }
    public enum SentenceType { ZATVOR, NOVCANA, USLOVNA, RAD_U_JAVNOM_INTERESU }
}
