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
     * Forma za unos novog slučaja (Celina 8).
     */
    @GetMapping("/new")
    public String newCaseForm(Model model) {
        model.addAttribute("facts", new CaseFacts());
        return "cases/new";
    }

    /**
     * Submit slučaja - pokreće oba reasoner-a.
     */
    @PostMapping("/reason")
    public String reason(@ModelAttribute CaseFacts facts, Model model) {
        CaseFacts cleaned = cleanFacts(facts);
        log.info("Reasoning request: {}", cleaned.getFacts());
        ReasoningService.CombinedResult result = reasoningService.reasonAll(cleaned);
        model.addAttribute("facts", cleaned);
        model.addAttribute("result", result);
        return "cases/result";
    }

    /**
     * Konačna potvrda korisnika - retain u CBR bazu + PostgreSQL.
     *
     * VLASNIK: Član 3 (Celina 8). Trenutno - inicijalna implementacija
     * koja čuva u PostgreSQL preko JPA i poziva CaseReasoner.retain().
     */
    @PostMapping("/save")
    public String save(@ModelAttribute CaseFacts facts,
                       @RequestParam String articleViolated,
                       @RequestParam String sentenceType,
                       @RequestParam(required = false) Integer sentenceMonths,
                       RedirectAttributes redirectAttrs) {

        CaseFacts cleaned = cleanFacts(facts);
        log.info("Save case: article={}, sentence={} {}m, facts={}",
                articleViolated, sentenceType, sentenceMonths, cleaned.getFacts());

        try {
            // 1. PostgreSQL preko JPA
            CaseEntity entity = caseEntityMapper.toEntity(
                    cleaned, articleViolated, sentenceType, sentenceMonths);
            CaseEntity saved = caseRepository.save(entity);
            log.info("Slučaj sačuvan u PostgreSQL: id={}", saved.getId());

            // 2. CBR retain (CSV) - Član 2 implementira pravu logiku
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

    /**
     * Lista sačuvanih slučajeva.
     * TODO Član 3: napraviti templejt cases/list.html
     */
    @GetMapping
    public String list(Model model) {
        model.addAttribute("cases", caseRepository.findAll());
        return "cases/list";
    }

    // ============================================================
    // helperi
    // ============================================================

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
     * Čisti činjenice iz forme:
     *  - prazni stringovi se brišu
     *  - "true"/"false" se konvertuje u Boolean (checkbox-i)
     *  - brojevi u Double
     *  - enum vrednosti ostaju kao stringovi
     */
    private CaseFacts cleanFacts(CaseFacts input) {
        Map<String, Object> cleaned = new HashMap<>();
        if (input.getFacts() == null) {
            CaseFacts result = new CaseFacts();
            result.setFacts(cleaned);
            return result;
        }

        for (Map.Entry<String, Object> entry : input.getFacts().entrySet()) {
            Object value = entry.getValue();
            if (value == null) continue;
            String str = value.toString().trim();
            if (str.isEmpty()) continue;

            if ("true".equalsIgnoreCase(str)) {
                cleaned.put(entry.getKey(), Boolean.TRUE);
            } else if ("false".equalsIgnoreCase(str)) {
                cleaned.put(entry.getKey(), Boolean.FALSE);
            } else {
                try {
                    cleaned.put(entry.getKey(), Double.parseDouble(str));
                } catch (NumberFormatException e) {
                    cleaned.put(entry.getKey(), str);
                }
            }
        }
        CaseFacts result = new CaseFacts();
        result.setFacts(cleaned);
        result.setDescription(input.getDescription());
        return result;
    }
}
