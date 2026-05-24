package rs.ftn.pi.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import rs.ftn.pi.model.CaseFacts;
import rs.ftn.pi.service.ReasoningService;

@Slf4j
@Controller
@RequestMapping("/cases")
@RequiredArgsConstructor
public class CaseController {

    private final ReasoningService reasoningService;

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
        log.info("Reasoning request: {}", facts.getFacts());
        ReasoningService.CombinedResult result = reasoningService.reasonAll(facts);
        model.addAttribute("facts", facts);
        model.addAttribute("result", result);
        return "cases/result";
    }

    /**
     * Konačna potvrda korisnika - retain u CBR bazu.
     * TODO Član 3: implementirati persistovanje slučaja preko CaseRepository.
     */
    @PostMapping("/save")
    public String save(@RequestParam String articleViolated,
                       @RequestParam String sentenceType,
                       @RequestParam Integer sentenceMonths,
                       @ModelAttribute CaseFacts facts) {
        log.info("Save case: article={}, sentence={} {}m",
                articleViolated, sentenceType, sentenceMonths);
        // TODO Član 3:
        //  1. Mapirati CaseFacts u CaseEntity
        //  2. Snimiti u CaseRepository
        //  3. Pozvati caseReasoner.retain(...)
        //  4. Redirect na detalje sačuvanog slučaja
        return "redirect:/cases";
    }
}
