package rs.ftn.pi.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import rs.ftn.pi.service.JudgmentService;

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
        return "judgments/view";
    }
}
