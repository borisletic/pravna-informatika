package rs.ftn.pi.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import rs.ftn.pi.service.LawService;

@Controller
@RequestMapping("/laws")
@RequiredArgsConstructor
public class LawController {

    private final LawService lawService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("laws", lawService.listLaws());
        return "laws/list";
    }

    @GetMapping("/{id}")
    public String view(@PathVariable String id, Model model) {
        String html = lawService.getLawHtml(id)
                .orElse("<p>Zakon nije pronađen.</p>");
        model.addAttribute("lawId", id);
        model.addAttribute("lawHtml", html);
        return "laws/view";
    }
}
