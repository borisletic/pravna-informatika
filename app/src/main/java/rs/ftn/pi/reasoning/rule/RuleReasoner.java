package rs.ftn.pi.reasoning.rule;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.Message;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.springframework.stereotype.Component;
import rs.ftn.pi.config.AppConfig;
import rs.ftn.pi.model.CaseFacts;
import rs.ftn.pi.reasoning.Reasoner;
import rs.ftn.pi.reasoning.dto.DerivationStep;
import rs.ftn.pi.reasoning.dto.ReasoningResult;
import rs.ftn.pi.reasoning.dto.SentenceProposal;
import rs.ftn.pi.reasoning.dto.ViolatedArticle;
import rs.ftn.pi.service.LawArticleTextService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Rule-based rasuđivanje preko Drools engine-a.
 *
 * VLASNIK: Član 1 (Legal Modeling).
 * CELINA: 5.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RuleReasoner implements Reasoner {

    private final AppConfig appConfig;
    private final LawArticleTextService lawArticleTextService;

    private KieContainer kieContainer;

    @PostConstruct
    public void initRules() {
        log.info("Inicijalizacija Drools rule engine-a...");

        String rulesPath = appConfig.getReasoning().getRulesFile();
        String drlPath = rulesPath.replace(".lrml", ".drl");

        try {
            Path path = Paths.get(drlPath);
            if (!Files.exists(path)) {
                log.error("DRL fajl ne postoji: {}", drlPath);
                log.error("Ostavi pravila na mock-u dok ne pripremis DRL.");
                return;
            }

            String drlContent = Files.readString(path);

            KieServices ks = KieServices.Factory.get();
            KieFileSystem kfs = ks.newKieFileSystem();
            kfs.write("src/main/resources/rules/environmental_rules.drl",
                    ks.getResources()
                      .newReaderResource(new java.io.StringReader(drlContent))
                      .setResourceType(ResourceType.DRL)
                      .setSourcePath("rules/environmental_rules.drl"));

            KieBuilder kb = ks.newKieBuilder(kfs).buildAll();

            if (kb.getResults().hasMessages(Message.Level.ERROR)) {
                log.error("Drools build greske:");
                kb.getResults().getMessages(Message.Level.ERROR)
                  .forEach(m -> log.error("  {}", m.getText()));
                return;
            }

            this.kieContainer = ks.newKieContainer(ks.getRepository().getDefaultReleaseId());
            log.info("Drools engine inicijalizovan: ucitana pravila iz {}", drlPath);

        } catch (Exception e) {
            log.error("Greska pri inicijalizaciji Drools-a: {}", e.getMessage(), e);
        }
    }

    @Override
    public ReasoningResult reason(CaseFacts facts) {
        log.info("RuleReasoner: rasudjivanje nad {} cinjenica", facts.getFacts().size());

        if (kieContainer == null) {
            log.warn("Drools nije dostupan - vracam mock rezultat");
            return mockResult(facts);
        }

        KieSession session = kieContainer.newKieSession();
        try {
            List<DroolsConclusion> conclusions = new ArrayList<>();
            List<String> trace = new ArrayList<>();
            session.setGlobal("conclusions", conclusions);
            session.setGlobal("trace", trace);

            session.insert(facts);

            int firedRules = session.fireAllRules();
            log.info("Drools: ispaljeno {} pravila, {} zakljucaka",
                    firedRules, conclusions.size());

            return toReasoningResult(facts, conclusions, trace);

        } finally {
            session.dispose();
        }
    }

    private ReasoningResult toReasoningResult(CaseFacts facts,
                                              List<DroolsConclusion> conclusions,
                                              List<String> trace) {
        if (conclusions.isEmpty()) {
            return ReasoningResult.builder()
                    .reasonerType(ReasoningResult.ReasonerType.RULE_BASED)
                    .explanation("Nijedno pravilo nije puklo nad zadatim cinjenicama. " +
                                 "Mozda nedostaju kljucne cinjenice.")
                    .build();
        }

        List<DroolsConclusion> sorted = new ArrayList<>(conclusions);
        sorted.sort(Comparator.comparingInt(DroolsConclusion::getMaxMonths).reversed());

        // Lista prekršenih clanova - tekst se vuče iz kz.xml preko LawArticleTextService
        List<ViolatedArticle> violations = sorted.stream()
                .map(c -> ViolatedArticle.builder()
                        .articleEId(c.getArticleEId())
                        .citation(articleEIdToCitation(c.getArticleEId()))
                        .text(resolveText(c.getArticleEId()))
                        .certainty("STRICT")
                        .build())
                .toList();

        DroolsConclusion primary = sorted.get(0);
        SentenceProposal sentence = SentenceProposal.builder()
                .type(SentenceProposal.SentenceType.ZATVOR)
                .minMonths(primary.getMinMonths())
                .maxMonths(primary.getMaxMonths())
                .proposedMonths((primary.getMinMonths() + primary.getMaxMonths()) / 2)
                .adjustments(buildAdjustmentsNote(facts))
                .build();

        List<DerivationStep> derivations = new ArrayList<>();
        for (DroolsConclusion c : sorted) {
            derivations.add(DerivationStep.builder()
                    .ruleId(c.getRuleId())
                    .ruleDescription(ruleDescription(c.getRuleId()))
                    .matchedFacts(matchedFactsForRule(c.getRuleId(), facts))
                    .conclusion("violates(person, " + c.getArticleEId() + "), " +
                               "sentenceRange(" + c.getMinMonths() + "m, " + c.getMaxMonths() + "m)")
                    .build());
        }

        return ReasoningResult.builder()
                .reasonerType(ReasoningResult.ReasonerType.RULE_BASED)
                .violatedArticles(violations)
                .sentenceProposal(sentence)
                .derivations(derivations)
                .explanation(buildExplanation(sorted, facts))
                .build();
    }

    /**
     * Pretvara art_260__para_1 -> "KZ čl. 260 st. 1".
     */
    private String articleEIdToCitation(String eId) {
        if (eId == null) return "?";
        String[] parts = eId.split("__");
        String artPart = parts[0].replace("art_", "");
        if (parts.length > 1) {
            String parPart = parts[1].replace("para_", "");
            return "KZ čl. " + artPart + " st. " + parPart;
        }
        return "KZ čl. " + artPart;
    }

    /**
     * Vuče stvarni tekst paragrafa iz kz.xml preko LawArticleTextService.
     * Ako ne postoji u indeksu, vraća umestan placeholder.
     */
    private String resolveText(String eId) {
        String text = lawArticleTextService.findText(eId);
        if (text != null && !text.isEmpty()) {
            return text;
        }
        return "(tekst nedostupan za eId " + eId + ")";
    }

    private String buildAdjustmentsNote(CaseFacts facts) {
        List<String> notes = new ArrayList<>();
        if (Boolean.TRUE.equals(facts.get("priorConviction"))) {
            notes.add("otezavajuca: prethodno osudjivan");
        }
        if (Boolean.TRUE.equals(facts.get("remediedDamage"))) {
            notes.add("olaksavajuca: otklonio stetu");
        }
        return notes.isEmpty() ? null : String.join("; ", notes);
    }

    private static final Map<String, String> RULE_DESCRIPTIONS = Map.ofEntries(
            Map.entry("R1", "Zagadjenje zivotne sredine sa umisljajem"),
            Map.entry("R2", "Zagadjenje zivotne sredine iz nehata"),
            Map.entry("R3", "Kvalifikovani oblik (umisljaj + velike razmere stete)"),
            Map.entry("R4", "Kvalifikovani oblik (nehat + velike razmere stete)"),
            Map.entry("R5", "Nepreduzimanje propisanih mera zastite"),
            Map.entry("R6", "Protivpravna izgradnja zagadjujucih objekata"),
            Map.entry("R7", "Ostecenje objekata za zastitu zivotne sredine"),
            Map.entry("R8", "Unistenje posebno zasticenog prirodnog dobra"),
            Map.entry("R9", "Protivpravno iznosenje zasticene vrste"),
            Map.entry("R10", "Opasne materije - osnovni oblik"),
            Map.entry("R11", "Opasne materije sa zloupotrebom polozaja"),
            Map.entry("R12", "Organizovanje vrsenja sa opasnim materijama"),
            Map.entry("R13", "Nedozvoljena izgradnja nuklearnog postrojenja"),
            Map.entry("R14", "Uskracivanje podataka o stanju zivotne sredine")
    );

    private String ruleDescription(String ruleId) {
        return RULE_DESCRIPTIONS.getOrDefault(ruleId, "(nepoznato pravilo)");
    }

    private List<String> matchedFactsForRule(String ruleId, CaseFacts facts) {
        Map<String, List<String>> matchers = new HashMap<>();
        matchers.put("R1", List.of("violatedEnvironmentalRegs", "pollutionTarget",
                "pollutionExtent", "pollutionScope", "intent"));
        matchers.put("R2", List.of("violatedEnvironmentalRegs", "pollutionTarget",
                "pollutionExtent", "pollutionScope", "intent"));
        matchers.put("R3", List.of("ecologicalDamage", "damageRemovalDifficulty"));
        matchers.put("R4", List.of("ecologicalDamage", "damageRemovalDifficulty"));
        matchers.put("R5", List.of("perpetratorType", "failedToTakeProtectiveMeasures"));
        matchers.put("R6", List.of("perpetratorType", "unauthorizedConstruction",
                "pollutionExtent", "pollutionScope"));
        matchers.put("R7", List.of("damagedProtectionEquipment"));
        matchers.put("R8", List.of("destroyedProtectedNaturalAsset"));
        matchers.put("R9", List.of("illegalSpeciesTraffic"));
        matchers.put("R10", List.of("dangerousSubstanceAction"));
        matchers.put("R11", List.of("dangerousSubstanceAction", "officialPositionAbuse"));
        matchers.put("R12", List.of("dangerousSubstanceAction", "organizesCrime"));
        matchers.put("R13", List.of("unauthorizedNuclearFacility"));
        matchers.put("R14", List.of("deniedEnvironmentalInfo"));

        List<String> relevant = matchers.getOrDefault(ruleId, List.of());
        List<String> result = new ArrayList<>();
        for (String predicate : relevant) {
            Object value = facts.get(predicate);
            if (value != null) {
                result.add(predicate + " = " + value);
            }
        }
        return result;
    }

    private String buildExplanation(List<DroolsConclusion> conclusions, CaseFacts facts) {
        StringBuilder sb = new StringBuilder();
        sb.append("Na osnovu navedenih cinjenica, sistem je zakljucio da je ucinilac ");
        sb.append("prekrsio ").append(conclusions.size()).append(" odredb")
          .append(conclusions.size() == 1 ? "u" : "i").append(" KZ-a. ");
        sb.append("Najtezi oblik je ").append(articleEIdToCitation(conclusions.get(0).getArticleEId()))
          .append(" sa predvidjenom kaznom u rasponu ")
          .append(conclusions.get(0).getMinMonths()).append("-")
          .append(conclusions.get(0).getMaxMonths()).append(" meseci.");

        String adj = buildAdjustmentsNote(facts);
        if (adj != null) {
            sb.append(" Okolnosti: ").append(adj).append(".");
        }
        return sb.toString();
    }

    private ReasoningResult mockResult(CaseFacts facts) {
        ViolatedArticle article = ViolatedArticle.builder()
                .articleEId("art_260__para_1")
                .citation("KZ čl. 260 st. 1")
                .text("[MOCK] Drools nije inicijalizovan.")
                .certainty("STRICT")
                .build();

        return ReasoningResult.builder()
                .reasonerType(ReasoningResult.ReasonerType.RULE_BASED)
                .violatedArticles(List.of(article))
                .explanation("[MOCK] Pravi rule reasoner nije inicijalizovan - " +
                             "proveri da li environmental_rules.drl postoji.")
                .build();
    }
}
