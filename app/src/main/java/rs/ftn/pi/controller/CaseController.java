package rs.ftn.pi.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import rs.ftn.pi.model.CaseEntity;
import rs.ftn.pi.model.CaseFacts;
import rs.ftn.pi.reasoning.cbr.CaseReasoner;
import rs.ftn.pi.reasoning.dto.SentenceProposal;
import rs.ftn.pi.repository.CaseRepository;
import rs.ftn.pi.service.CaseEntityMapper;
import rs.ftn.pi.service.ReasoningService;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Controller
@RequestMapping("/cases")
@RequiredArgsConstructor
public class CaseController {

    private final ReasoningService reasoningService;
    private final CaseRepository caseRepository;
    private final CaseEntityMapper caseEntityMapper;
    private final CaseReasoner caseReasoner;

    /**
     * NLP alias -> kanonske vrednosti.
     * NLP (Član 2) vraća svoje vrednosti, pravila (Član 1) koriste kanonske.
     * Mapiranje se radi ovde, pre nego što činjenice udju u Drools.
     *
     * Sinkronizovano sa predicate_dictionary.yaml v0.3.0, sekcija nlp_aliases.
     */
    private static final Map<String, Map<String, String>> NLP_ALIASES = Map.of(
            "pollutionTarget", Map.of(
                    "TLO", "ZEMLJISTE"
            ),
            "ecologicalDamage", Map.of(
                    "VELIKA", "VELIKIH_RAZMERA",
                    "MALA", "OBICNA"
            )
    );

    @GetMapping("/new")
    public String newCaseForm(Model model) {
        model.addAttribute("facts", new CaseFacts());
        return "cases/new";
    }

    @PostMapping("/reason")
    public String reason(@ModelAttribute CaseFacts facts, Model model) {
        CaseFacts cleaned = cleanFacts(facts);
        log.info("Reasoning request: {}", cleaned.getFacts());
        ReasoningService.CombinedResult result = reasoningService.reasonAll(cleaned);
        model.addAttribute("facts", cleaned);
        model.addAttribute("result", result);
        return "cases/result";
    }

    @PostMapping("/save")
    public String save(@ModelAttribute CaseFacts facts,
                       @RequestParam String articleViolated,
                       @RequestParam String sentenceType,
                       @RequestParam(required = false) Integer sentenceMonths,
                       RedirectAttributes redirectAttrs) {

        CaseFacts cleaned = cleanFacts(facts);
        log.info("Save case: article={}, sentence={} {}m",
                articleViolated, sentenceType, sentenceMonths);

        try {
            CaseEntity entity = caseEntityMapper.toEntity(
                    cleaned, articleViolated, sentenceType, sentenceMonths);
            CaseEntity saved = caseRepository.save(entity);
            log.info("Slučaj sačuvan u PostgreSQL: id={}", saved.getId());

            SentenceProposal sentenceProposal = buildSentenceProposal(sentenceType, sentenceMonths);
            caseReasoner.retain(cleaned, articleViolated, sentenceProposal);

            redirectAttrs.addFlashAttribute("message",
                    "Slučaj #" + saved.getId() + " uspešno sačuvan.");
            return "redirect:/cases";

        } catch (Exception e) {
            log.error("Greška pri čuvanju slučaja: {}", e.getMessage(), e);
            redirectAttrs.addFlashAttribute("error",
                    "Greška pri čuvanju: " + e.getMessage());
            return "redirect:/cases/new";
        }
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("cases", caseRepository.findAll());
        return "cases/list";
    }

    private SentenceProposal buildSentenceProposal(String sentenceType, Integer months) {
        if (sentenceType == null || sentenceType.isBlank()) return null;
        try {
            return SentenceProposal.builder()
                    .type(SentenceProposal.SentenceType.valueOf(sentenceType))
                    .proposedMonths(months)
                    .build();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Čisti i normalizuje činjenice iz forme/NLP-a:
     *  1. uklanja prazne stringove
     *  2. konvertuje "true"/"false" stringove u Boolean (checkbox-i)
     *  3. brojeve u Double
     *  4. PREVODI NLP ALIAS-E u kanonske vrednosti
     */
    private CaseFacts cleanFacts(CaseFacts input) {
        Map<String, Object> cleaned = new HashMap<>();
        if (input.getFacts() == null) {
            CaseFacts result = new CaseFacts();
            result.setFacts(cleaned);
            return result;
        }

        for (Map.Entry<String, Object> entry : input.getFacts().entrySet()) {
            String predicate = entry.getKey();
            Object value = entry.getValue();
            if (value == null) continue;
            String str = value.toString().trim();
            if (str.isEmpty()) continue;

            // 1. Boolean
            if ("true".equalsIgnoreCase(str)) {
                cleaned.put(predicate, Boolean.TRUE);
                continue;
            }
            if ("false".equalsIgnoreCase(str)) {
                cleaned.put(predicate, Boolean.FALSE);
                continue;
            }

            // 2. NLP alias mapiranje
            Map<String, String> aliases = NLP_ALIASES.get(predicate);
            if (aliases != null && aliases.containsKey(str)) {
                String canonical = aliases.get(str);
                log.debug("NLP alias: {}.{} -> {}", predicate, str, canonical);
                cleaned.put(predicate, canonical);
                continue;
            }

            // 3. Broj?
            try {
                cleaned.put(predicate, Double.parseDouble(str));
            } catch (NumberFormatException e) {
                cleaned.put(predicate, str);
            }
        }
        CaseFacts result = new CaseFacts();
        result.setFacts(cleaned);
        result.setDescription(input.getDescription());
        return result;
    }
}
