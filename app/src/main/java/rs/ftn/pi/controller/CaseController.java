package rs.ftn.pi.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import rs.ftn.pi.model.CaseFacts;
import rs.ftn.pi.service.ReasoningService;

import java.util.HashMap;
import java.util.Map;

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
        // Forma šalje "" za nepopunjena polja - filtriramo ih jer pravila
        // proveravaju == null, a prazan string nije null.
        // Takođe normalizujemo "true"/"false" stringove u Boolean za checkbox-e.
        CaseFacts cleaned = cleanFacts(facts);

        log.info("Reasoning request: {}", cleaned.getFacts());
        ReasoningService.CombinedResult result = reasoningService.reasonAll(cleaned);
        model.addAttribute("facts", cleaned);
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

    /**
     * Čisti činjenice koje su došle iz forme:
     *  - prazni stringovi se brišu (jer pravila proveravaju null, ne empty)
     *  - "true" string konvertuje se u Boolean true (checkbox-i)
     *  - numeričke vrednosti ostaju kao stringovi (Drools ih poredi kao stringove)
     */
    private CaseFacts cleanFacts(CaseFacts input) {
        Map<String, Object> cleaned = new HashMap<>();
        for (Map.Entry<String, Object> entry : input.getFacts().entrySet()) {
            Object value = entry.getValue();
            if (value == null) continue;
            String str = value.toString().trim();
            if (str.isEmpty()) continue;

            // Booleani iz checkbox-a: forma šalje "true" string
            if ("true".equalsIgnoreCase(str)) {
                cleaned.put(entry.getKey(), Boolean.TRUE);
            } else if ("false".equalsIgnoreCase(str)) {
                cleaned.put(entry.getKey(), Boolean.FALSE);
            } else {
                // Pokušaj numerik
                try {
                    cleaned.put(entry.getKey(), Double.parseDouble(str));
                } catch (NumberFormatException e) {
                    // Ostavi kao string (enum vrednosti tipa "VECA_MERA")
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
