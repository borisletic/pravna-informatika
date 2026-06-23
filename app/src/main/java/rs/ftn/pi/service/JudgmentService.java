package rs.ftn.pi.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import rs.ftn.pi.config.AppConfig;
import rs.ftn.pi.model.CaseFacts;
import rs.ftn.pi.model.Judgment;
import rs.ftn.pi.model.JudgmentOverride;
import rs.ftn.pi.nlp.NlpClient;
import rs.ftn.pi.nlp.NlpDtos;
import rs.ftn.pi.xml.AkomaNtosoParser;
import rs.ftn.pi.xml.XsltTransformer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class JudgmentService {

    /** Predikati koje korisnik može ručno da ažurira (Celina 4). */
    public static final List<String> EDITABLE_PREDICATES = List.of(
            "intent", "pollutionTarget", "pollutionExtent", "pollutionScope",
            "ecologicalDamage", "damageExtent", "damageRemovalDifficulty", "perpetratorType",
            "substanceType", "substanceQuantityM3", "forestAreaHa",
            "priorConviction", "remediedDamage", "protectedSpecies", "usesExplosives",
            "articleViolated", "sentenceType", "sentenceMonths");

    private final AkomaNtosoParser parser;
    private final XsltTransformer xsltTransformer;
    private final NlpClient nlpClient;
    private final AppConfig appConfig;
    private final JudgmentOverrideService overrideService;

    public List<Judgment> listJudgments() {
        Path judgmentsDir = Paths.get(appConfig.getDataDir(), "judgments");
        List<Judgment> result = new ArrayList<>();
        if (!Files.exists(judgmentsDir)) {
            log.warn("Direktorijum sa odlukama ne postoji: {}", judgmentsDir);
            return result;
        }
        try (Stream<Path> files = Files.list(judgmentsDir)) {
            files.filter(p -> p.toString().endsWith(".xml"))
                 .forEach(p -> result.add(applyOverride(parser.parseJudgment(p))));
        } catch (Exception e) {
            log.error("Greška pri listanju odluka", e);
        }
        Path generatedDir = judgmentsDir.resolve("generated");
        if (Files.exists(generatedDir)) {
            try (Stream<Path> files = Files.list(generatedDir)) {
                files.filter(p -> p.toString().endsWith(".xml"))
                        .forEach(p -> result.add(applyOverride(parser.parseJudgment(p))));
            } catch (Exception e) {
                log.error("Greška pri listanju generisanih odluka", e);
            }
        }
        return result;
    }

    /** Parsira presudu, primeni ručne ispravke i popuni NLP-izvučene činjenice (za prikaz/edit). */
    public Optional<Judgment> getJudgmentDetail(String judgmentId) {
        Path xmlPath = resolveXml(judgmentId);
        if (xmlPath == null) return Optional.empty();
        Judgment j = parser.parseJudgment(xmlPath);

        // NLP ekstrakcija činjenica iz teksta (best-effort; ako servis nije dostupan -> prazno)
        Map<String, Object> facts = new LinkedHashMap<>();
        try {
            if (j.getFactualBackground() != null && !j.getFactualBackground().isBlank()) {
                NlpDtos.ExtractResponse resp = nlpClient.extractFromJudgment(j.getFactualBackground());
                if (resp != null && resp.getFacts() != null) {
                    for (NlpDtos.Fact f : resp.getFacts()) {
                        if (f.getPredicate() != null && f.getValue() != null) {
                            facts.put(f.getPredicate(), f.getValue());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("NLP nedostupan za {}: {}", judgmentId, e.getMessage());
        }
        CaseFacts cf = new CaseFacts();
        cf.setFacts(facts);
        j.setExtractedFacts(cf);

        return Optional.of(applyOverride(j));
    }

    public void saveOverride(String judgmentId, JudgmentOverride override) {
        overrideService.save(judgmentId, override);
    }

    public JudgmentOverride loadOverride(String judgmentId) {
        return overrideService.load(judgmentId).orElse(null);
    }

    /** Ručne ispravke (sidecar JSON) imaju prednost nad auto-izvučenim vrednostima. */
    private Judgment applyOverride(Judgment j) {
        JudgmentOverride o = overrideService.load(j.getId()).orElse(null);
        if (o == null) return j;
        if (notBlank(o.getCaseNumber())) j.setCaseNumber(o.getCaseNumber());
        if (notBlank(o.getCourt())) j.setCourt(o.getCourt());
        if (notBlank(o.getDate())) {
            try { j.setDate(LocalDate.parse(o.getDate().trim())); } catch (Exception ignored) {}
        }
        if (o.getJudges() != null && !o.getJudges().isEmpty()) j.setJudges(o.getJudges());
        if (notBlank(o.getRecorder())) j.setRecorder(o.getRecorder());
        if (o.getParties() != null && !o.getParties().isEmpty()) j.setParties(o.getParties());
        if (o.getFacts() != null && !o.getFacts().isEmpty()) {
            CaseFacts cf = j.getExtractedFacts() != null ? j.getExtractedFacts() : new CaseFacts();
            if (cf.getFacts() == null) cf.setFacts(new LinkedHashMap<>());
            cf.getFacts().putAll(o.getFacts());
            j.setExtractedFacts(cf);
        }
        return j;
    }

    private boolean notBlank(String s) { return s != null && !s.isBlank(); }

    private Path resolveXml(String judgmentId) {
        Path p = Paths.get(appConfig.getDataDir(), "judgments", judgmentId + ".xml");
        if (Files.exists(p)) return p;
        p = Paths.get(appConfig.getDataDir(), "judgments", "generated", judgmentId + ".xml");
        return Files.exists(p) ? p : null;
    }

    public Optional<String> getJudgmentHtml(String judgmentId) {
        Path xmlPath = Paths.get(appConfig.getDataDir(), "judgments", judgmentId + ".xml");
        if (!Files.exists(xmlPath)) {
            xmlPath = Paths.get(appConfig.getDataDir(), "judgments", "generated", judgmentId + ".xml");
        }
        if (!Files.exists(xmlPath)) {
            return Optional.empty();
        }
        try {
            String xml = Files.readString(xmlPath);
            return Optional.of(xsltTransformer.judgmentToHtml(xml));
        } catch (Exception e) {
            log.error("Greška pri učitavanju odluke {}", judgmentId, e);
            return Optional.empty();
        }
    }
}
