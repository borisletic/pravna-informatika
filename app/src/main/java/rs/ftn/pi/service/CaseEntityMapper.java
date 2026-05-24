package rs.ftn.pi.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import rs.ftn.pi.model.CaseEntity;
import rs.ftn.pi.model.CaseFacts;
import rs.ftn.pi.reasoning.dto.SentenceProposal;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Mapira CaseFacts (Map<String, Object>) u CaseEntity (tipizirane kolone).
 *
 * VLASNIK: Član 3 (Application) - jer mapiranje zavisi od JPA šeme
 *          koju je Član 3 napravio u CaseEntity.java
 *
 * Ključne činjenice (cbr_key: true u predicate_dictionary.yaml) idu u
 * dedikovane kolone radi brzog SQL query-ja. Ostatak činjenica se serijalizuje
 * u factsJson kolonu kao JSON.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CaseEntityMapper {

    private final ObjectMapper objectMapper;

    /**
     * Predikati koji imaju dedikovane kolone u CaseEntity.
     * Sve ostalo ide u factsJson.
     */
    private static final Set<String> KEY_PREDICATES = Set.of(
            "substanceQuantityM3",
            "substanceType",
            "pollutionTarget",
            "damageExtent",
            "intent",
            "priorConviction",
            "remediedDamage",
            "forestAreaHa"
    );

    public CaseEntity toEntity(CaseFacts facts,
                               String articleViolated,
                               String sentenceType,
                               Integer sentenceMonths) {
        CaseEntity entity = new CaseEntity();

        // Source - svi korisnički slučajevi su USER
        entity.setSource(CaseEntity.Source.USER);

        // Ishod
        entity.setArticleViolated(articleViolated);
        if (sentenceType != null && !sentenceType.isBlank()) {
            try {
                entity.setSentenceType(CaseEntity.SentenceType.valueOf(sentenceType));
            } catch (IllegalArgumentException e) {
                log.warn("Nepoznata vrsta kazne: {}", sentenceType);
            }
        }
        entity.setSentenceMonths(sentenceMonths);

        // Ključne činjenice u dedikovane kolone
        Map<String, Object> all = facts.getFacts() != null ? facts.getFacts() : new HashMap<>();
        Map<String, Object> remainder = new HashMap<>();

        for (Map.Entry<String, Object> entry : all.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value == null) continue;

            if (KEY_PREDICATES.contains(key)) {
                applyKeyPredicate(entity, key, value);
            } else {
                remainder.put(key, value);
            }
        }

        // Ostatak kao JSON
        if (!remainder.isEmpty()) {
            try {
                entity.setFactsJson(objectMapper.writeValueAsString(remainder));
            } catch (JsonProcessingException e) {
                log.error("Ne mogu serijalizovati ostatak činjenica: {}", e.getMessage());
            }
        }

        // Opis (slobodni tekst)
        entity.setDescription(facts.getDescription());

        return entity;
    }

    private void applyKeyPredicate(CaseEntity entity, String key, Object value) {
        try {
            switch (key) {
                case "substanceQuantityM3" -> entity.setSubstanceQuantityM3(toDouble(value));
                case "forestAreaHa" -> entity.setForestAreaHa(toDouble(value));
                case "priorConviction" -> entity.setPriorConviction(toBoolean(value));
                case "remediedDamage" -> entity.setRemediedDamage(toBoolean(value));
                case "substanceType" -> entity.setSubstanceType(
                        CaseEntity.SubstanceType.valueOf(value.toString()));
                case "pollutionTarget" -> entity.setPollutionTarget(
                        CaseEntity.PollutionTarget.valueOf(value.toString()));
                case "damageExtent" -> entity.setDamageExtent(
                        CaseEntity.DamageExtent.valueOf(value.toString()));
                case "intent" -> entity.setIntent(
                        CaseEntity.Intent.valueOf(value.toString()));
            }
        } catch (IllegalArgumentException e) {
            log.warn("Ne mogu mapirati '{}' = '{}': {}", key, value, e.getMessage());
        }
    }

    private Double toDouble(Object value) {
        if (value instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Boolean toBoolean(Object value) {
        if (value instanceof Boolean b) return b;
        return Boolean.parseBoolean(value.toString());
    }
}
