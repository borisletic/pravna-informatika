package rs.ftn.pi.reasoning.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Jedan korak u izvođenju (za prikaz "kako je sistem došao do zaključka").
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DerivationStep {

    /**
     * ID pravila koje je primenjeno (npr. "R1").
     */
    private String ruleId;

    /**
     * Tekstualni opis pravila.
     */
    private String ruleDescription;

    /**
     * Činjenice koje su zadovoljile uslove pravila.
     */
    private List<String> matchedFacts;

    /**
     * Zaključak koji je izveden.
     */
    private String conclusion;
}
