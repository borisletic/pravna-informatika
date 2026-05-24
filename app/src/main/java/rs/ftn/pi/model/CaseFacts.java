package rs.ftn.pi.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * Centralna struktura sa činjenicama o slučaju.
 * Koristi se kao ulaz u oba reasoner-a (rule i CBR) i kao izlaz iz NLP servisa.
 *
 * Ključevi mape moraju da odgovaraju predikatima iz
 * data/schemas/predicate_dictionary.yaml
 *
 * UGOVOR: Ne menjati strukturu bez ažuriranja Integration Contracts.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CaseFacts {

    /**
     * Mapa činjenica: predikat -> vrednost.
     * Tip vrednosti zavisi od tipa predikata u rečniku
     * (Number, String/Enum naziv, Boolean).
     */
    @Builder.Default
    private Map<String, Object> facts = new HashMap<>();

    /**
     * Opcionalno: tekstualni opis slučaja od korisnika (Celina 8).
     */
    private String description;

    public Object get(String predicate) {
        return facts.get(predicate);
    }

    public void put(String predicate, Object value) {
        facts.put(predicate, value);
    }

    public boolean has(String predicate) {
        return facts.containsKey(predicate);
    }
}
