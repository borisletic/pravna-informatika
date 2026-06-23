package rs.ftn.pi.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import rs.ftn.pi.model.Judgment;
import rs.ftn.pi.model.JudgmentOverride;
import rs.ftn.pi.service.JudgmentService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/judgments")
@RequiredArgsConstructor
public class JudgmentController {

    private final JudgmentService judgmentService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("judgments", judgmentService.listJudgments());
        return "judgments/list";
    }

    @GetMapping("/{id}")
    public String view(@PathVariable String id, Model model) {
        String html = judgmentService.getJudgmentHtml(id)
                .orElse("<p>Odluka nije pronađena.</p>");
        model.addAttribute("judgmentId", id);
        model.addAttribute("judgmentHtml", html);
        model.addAttribute("judgment", judgmentService.getJudgmentDetail(id).orElse(null));
        return "judgments/view";
    }

    /** Celina 4: forma za ručno ažuriranje automatski ekstrahovanih metapodataka i činjenica. */
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable String id, Model model) {
        Judgment j = judgmentService.getJudgmentDetail(id).orElse(null);
        if (j == null) {
            model.addAttribute("error", "Odluka nije pronađena.");
            return "redirect:/judgments";
        }
        Map<String, Object> factValues = new LinkedHashMap<>();
        if (j.getExtractedFacts() != null && j.getExtractedFacts().getFacts() != null) {
            factValues.putAll(j.getExtractedFacts().getFacts());
        }
        model.addAttribute("judgmentId", id);
        model.addAttribute("judgment", j);
        model.addAttribute("predicates", JudgmentService.EDITABLE_PREDICATES);
        model.addAttribute("factValues", factValues);
        model.addAttribute("judgesText", String.join("\n", j.getJudges()));
        model.addAttribute("partiesText", partiesToText(j.getParties()));
        return "judgments/edit";
    }

    @PostMapping("/{id}/edit")
    public String saveEdit(@PathVariable String id,
                           @RequestParam(required = false) String caseNumber,
                           @RequestParam(required = false) String court,
                           @RequestParam(required = false) String date,
                           @RequestParam(required = false) String recorder,
                           @RequestParam(required = false) String judgesText,
                           @RequestParam(required = false) String partiesText,
                           @RequestParam Map<String, String> allParams,
                           RedirectAttributes redirectAttrs) {

        Map<String, String> facts = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : allParams.entrySet()) {
            if (e.getKey().startsWith("fact_") && e.getValue() != null && !e.getValue().isBlank()) {
                facts.put(e.getKey().substring("fact_".length()), e.getValue().trim());
            }
        }

        JudgmentOverride override = JudgmentOverride.builder()
                .caseNumber(trimToNull(caseNumber))
                .court(trimToNull(court))
                .date(trimToNull(date))
                .recorder(trimToNull(recorder))
                .judges(linesToList(judgesText))
                .parties(textToParties(partiesText))
                .facts(facts)
                .build();

        judgmentService.saveOverride(id, override);
        redirectAttrs.addFlashAttribute("message", "Ručne ispravke za odluku " + id + " su sačuvane.");
        return "redirect:/judgments/" + id;
    }

    // ---- helperi za liste u formi ----

    private static String partiesToText(List<Judgment.Party> parties) {
        if (parties == null) return "";
        StringBuilder sb = new StringBuilder();
        for (Judgment.Party p : parties) {
            sb.append(p.getRole() == null ? "OKR" : p.getRole())
              .append(" ").append(p.getInitials() == null ? "" : p.getInitials()).append("\n");
        }
        return sb.toString().trim();
    }

    private static List<Judgment.Party> textToParties(String text) {
        List<Judgment.Party> out = new ArrayList<>();
        if (text == null) return out;
        for (String line : text.split("\\r?\\n")) {
            line = line.trim();
            if (line.isEmpty()) continue;
            String[] parts = line.split("\\s+", 2);
            String role = parts[0].toUpperCase();
            String initials = parts.length > 1 ? parts[1].trim() : "";
            out.add(Judgment.Party.builder().role(role).initials(initials).build());
        }
        return out;
    }

    private static List<String> linesToList(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) return out;
        for (String line : text.split("\\r?\\n")) {
            if (!line.trim().isEmpty()) out.add(line.trim());
        }
        return out;
    }

    private static String trimToNull(String s) {
        return (s == null || s.trim().isEmpty()) ? null : s.trim();
    }
}
