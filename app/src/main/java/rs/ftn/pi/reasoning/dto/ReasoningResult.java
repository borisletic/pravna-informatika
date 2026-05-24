package rs.ftn.pi.reasoning.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Rezultat rasuđivanja (i rule-based i CBR).
 *
 * UGOVOR: Struktura se ne menja bez sinhronizacije sa Članom 3 (UI binding).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReasoningResult {

    /**
     * Tip rasuđivanja koji je proizveo ovaj rezultat.
     */
    private ReasonerType reasonerType;

    /**
     * Prekršeni članovi (samo za rule-based reasoner).
     * Za CBR ovo polje može biti prazno - umesto njega popunjava se similarCases.
     */
    @Builder.Default
    private List<ViolatedArticle> violatedArticles = new ArrayList<>();

    /**
     * Predlog kazne (može biti null ako ne može da se izvede).
     */
    private SentenceProposal sentenceProposal;

    /**
     * Slični slučajevi (samo za CBR reasoner).
     */
    @Builder.Default
    private List<SimilarCase> similarCases = new ArrayList<>();

    /**
     * Trag izvođenja - šta je sve izvedeno i kako (za prikaz korisniku).
     */
    @Builder.Default
    private List<DerivationStep> derivations = new ArrayList<>();

    /**
     * Tekst objašnjenja (može biti generisan iz derivacija).
     */
    private String explanation;

    public enum ReasonerType {
        RULE_BASED, CASE_BASED
    }
}
