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

    @Enumerated(EnumType.STRING)
    @Column(name = "source")
    private Source source;

    @Column(name = "source_judgment_id")
    private String sourceJudgmentId;

    // ====== Ključne činjenice ======
    @Column(name = "substance_quantity_m3")
    private Double substanceQuantityM3;

    @Enumerated(EnumType.STRING)
    @Column(name = "substance_type")
    private SubstanceType substanceType;

    @Enumerated(EnumType.STRING)
    @Column(name = "pollution_target")
    private PollutionTarget pollutionTarget;

    @Enumerated(EnumType.STRING)
    @Column(name = "damage_extent")
    private DamageExtent damageExtent;

    @Enumerated(EnumType.STRING)
    @Column(name = "intent")
    private Intent intent;

    @Column(name = "prior_conviction")
    private Boolean priorConviction;

    @Column(name = "remedied_damage")
    private Boolean remediedDamage;

    @Column(name = "forest_area_ha")
    private Double forestAreaHa;

    // ====== Ishod ======
    @Column(name = "article_violated")
    private String articleViolated;

    @Enumerated(EnumType.STRING)
    @Column(name = "sentence_type")
    private SentenceType sentenceType;

    @Column(name = "sentence_months")
    private Integer sentenceMonths;

    // ====== Tehnički metapodaci ======
    @Column(name = "facts_json", columnDefinition = "TEXT")
    private String factsJson;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_at")
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