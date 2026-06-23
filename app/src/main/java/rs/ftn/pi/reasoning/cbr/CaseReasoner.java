package rs.ftn.pi.reasoning.cbr;

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import rs.ftn.pi.config.AppConfig;
import rs.ftn.pi.model.CaseFacts;
import rs.ftn.pi.reasoning.Reasoner;
import rs.ftn.pi.reasoning.dto.ReasoningResult;
import rs.ftn.pi.reasoning.dto.SentenceProposal;
import rs.ftn.pi.reasoning.dto.SimilarCase;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Rasuđivanje po slučajevima (CBR) — Celina 6.
 *
 * VLASNIK: Član 2 (NLP & Data).
 *
 * Pravi kNN retrieval nad bazom slučajeva ({@code cbr_cases.csv} izgrađen iz
 * presuda + {@code cases.csv} korisnički zadržani slučajevi). Funkcije sličnosti
 * dolaze iz {@link SimilarityModel} (učitanog iz predicate_dictionary.yaml):
 * globalna sličnost = težinski prosek lokalnih sličnosti po ključnim činjenicama.
 *
 * Ciklus: RETRIEVE (ovde) → REUSE/REVISE (UI, korisnik bira) → RETAIN ({@link #retain}).
 *
 * Spec dozvoljava „alate kao što su jColibri"; ovde je nativna implementacija
 * istog modela (NN-Retrieval + lokalne/globalne funkcije sličnosti).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CaseReasoner implements Reasoner {

    private static final int TOP_K = 5;
    private static final double MIN_SIMILARITY = 0.01;

    private static final String[] CSV_HEADER = {
            "id", "source", "sourceJudgmentId",
            "substanceQuantityM3", "substanceType", "pollutionTarget",
            "damageExtent", "intent", "priorConviction", "remediedDamage",
            "forestAreaHa", "articleViolated", "sentenceType", "sentenceMonths"
    };

    /** Kolone u CSV bazama koje su činjenice (ostalo su metapodaci/ishod). */
    private static final List<String> FACT_COLUMNS = List.of(
            "substanceQuantityM3", "substanceType", "pollutionTarget", "damageExtent",
            "ecologicalDamage", "intent", "priorConviction", "remediedDamage", "forestAreaHa",
            "pollutionExtent", "pollutionScope", "damageRemovalDifficulty", "perpetratorType",
            "protectedSpecies", "usesExplosives");

    private final AppConfig appConfig;
    private final AtomicLong nextId = new AtomicLong(1);

    private SimilarityModel model;
    private List<CbrCase> caseBase = new ArrayList<>();

    @PostConstruct
    public void init() {
        initCsvAndId();
        try {
            Path dict = Paths.get(appConfig.getReasoning().getPredicateDict());
            model = new SimilarityModel(dict);
        } catch (Exception e) {
            log.error("Ne mogu da učitam model sličnosti: {}", e.getMessage(), e);
        }
        loadCaseBase();
    }

    // ====================== učitavanje baze ======================

    /** Učitava CBR bazu iz cbr_cases.csv (iz presuda) i cases.csv (retain). */
    private void loadCaseBase() {
        List<CbrCase> all = new ArrayList<>();
        Path casesDir = Paths.get(appConfig.getReasoning().getCasesFile()).getParent();
        if (casesDir != null) {
            all.addAll(readCsv(casesDir.resolve("cbr_cases.csv")));
        }
        all.addAll(readCsv(Paths.get(appConfig.getReasoning().getCasesFile())));
        this.caseBase = all;

        // skale za numeričke predikate = max apsolutna vrednost iz baze
        if (model != null) {
            for (String pred : model.cbrPredicates().keySet()) {
                double max = 0;
                for (CbrCase c : all) {
                    Object v = model.normalize(pred, c.getFacts().get(pred));
                    if (v instanceof Number n) max = Math.max(max, Math.abs(n.doubleValue()));
                }
                if (max > 0) model.setNumericScale(pred, max);
            }
        }
        log.info("CBR baza učitana: {} slučajeva", caseBase.size());
    }

    private List<CbrCase> readCsv(Path path) {
        List<CbrCase> out = new ArrayList<>();
        if (path == null || !Files.exists(path)) return out;
        try (CSVReader reader = new CSVReader(new FileReader(path.toFile(), java.nio.charset.StandardCharsets.UTF_8))) {
            List<String[]> rows = reader.readAll();
            if (rows.isEmpty()) return out;
            String[] header = rows.get(0);
            Map<String, Integer> col = new LinkedHashMap<>();
            for (int i = 0; i < header.length; i++) col.put(header[i].trim(), i);

            for (int r = 1; r < rows.size(); r++) {
                String[] row = rows.get(r);
                CbrCase c = new CbrCase();
                c.setId(parseLong(get(row, col, "id")));
                String label = firstNonBlank(get(row, col, "caseNumber"), get(row, col, "sourceJudgmentId"));
                c.setLabel(label);
                c.setSource(firstNonBlank(get(row, col, "source"), "JUDGMENT"));
                c.setArticleViolated(blankToNull(get(row, col, "articleViolated")));
                c.setSentenceType(blankToNull(get(row, col, "sentenceType")));
                c.setSentenceMonths(parseIntOrNull(get(row, col, "sentenceMonths")));
                Map<String, Object> facts = new LinkedHashMap<>();
                for (String fc : FACT_COLUMNS) {
                    if (col.containsKey(fc)) {
                        String v = get(row, col, fc);
                        if (v != null && !v.isBlank()) facts.put(fc, v);
                    }
                }
                c.setFacts(facts);
                out.add(c);
            }
            log.info("Učitano {} slučajeva iz {}", out.size() - 0, path.getFileName());
        } catch (Exception e) {
            log.error("Greška pri čitanju CBR CSV {}: {}", path, e.getMessage());
        }
        return out;
    }

    // ====================== RETRIEVE ======================

    @Override
    public ReasoningResult reason(CaseFacts facts) {
        log.info("CaseReasoner: kNN pretraga za {} činjenica nad bazom od {} slučajeva",
                facts.getFacts().size(), caseBase.size());

        if (model == null || caseBase.isEmpty()) {
            return ReasoningResult.builder()
                    .reasonerType(ReasoningResult.ReasonerType.CASE_BASED)
                    .explanation("CBR baza je prazna ili model sličnosti nije učitan.")
                    .build();
        }

        List<SimilarCase> scored = new ArrayList<>();
        for (CbrCase c : caseBase) {
            SimilarCase sc = score(facts, c);
            if (sc != null && sc.getSimilarity() >= MIN_SIMILARITY) {
                scored.add(sc);
            }
        }
        scored.sort(Comparator.comparingDouble(SimilarCase::getSimilarity).reversed());
        List<SimilarCase> top = scored.stream().limit(TOP_K).toList();

        if (top.isEmpty()) {
            return ReasoningResult.builder()
                    .reasonerType(ReasoningResult.ReasonerType.CASE_BASED)
                    .explanation("Nije pronađen nijedan sličan slučaj — premalo zajedničkih činjenica.")
                    .build();
        }

        return ReasoningResult.builder()
                .reasonerType(ReasoningResult.ReasonerType.CASE_BASED)
                .similarCases(top)
                .explanation(buildExplanation(top))
                .build();
    }

    /** Globalna sličnost = težinski prosek lokalnih po zajedničkim ključnim činjenicama. */
    private SimilarCase score(CaseFacts query, CbrCase c) {
        double num = 0, den = 0;
        Map<String, Double> attrSim = new LinkedHashMap<>();

        for (String pred : model.cbrPredicates().keySet()) {
            Object qv = model.normalize(pred, query.get(pred));
            Object cv = model.normalize(pred, c.getFacts().get(pred));
            if (qv == null || cv == null) continue;   // ne kažnjavamo nedostajuće
            double w = model.weight(pred);
            double s = model.localSimilarity(pred, qv, cv);
            num += w * s;
            den += w;
            attrSim.put(pred, round(s));
        }
        if (den == 0) return null;
        double global = num / den;

        return SimilarCase.builder()
                .caseId(c.getId())
                .sourceJudgmentId(c.getLabel())
                .similarity(round(global))
                .attributeSimilarities(attrSim)
                .facts(new LinkedHashMap<>(c.getFacts()))
                .articleViolated(toArticleEId(c.getArticleViolated()))
                .sentence(buildSentence(c))
                .build();
    }

    private SentenceProposal buildSentence(CbrCase c) {
        if (c.getSentenceType() == null && c.getSentenceMonths() == null) return null;
        SentenceProposal.SentenceType type = null;
        if (c.getSentenceType() != null) {
            try { type = SentenceProposal.SentenceType.valueOf(c.getSentenceType()); }
            catch (IllegalArgumentException ignored) {}
        }
        return SentenceProposal.builder()
                .type(type)
                .proposedMonths(c.getSentenceMonths())
                .build();
    }

    private String buildExplanation(List<SimilarCase> top) {
        SimilarCase best = top.get(0);
        StringBuilder sb = new StringBuilder();
        sb.append("Pronađeno ").append(top.size()).append(" sličnih slučajeva. ");
        sb.append("Najsličniji je ").append(best.getSourceJudgmentId())
          .append(" (sličnost ").append(Math.round(best.getSimilarity() * 100)).append("%)");
        if (best.getArticleViolated() != null) {
            sb.append(", ishod: ").append(best.getArticleViolated());
            if (best.getSentence() != null && best.getSentence().getProposedMonths() != null) {
                sb.append(", ").append(best.getSentence().getProposedMonths()).append(" meseci");
            }
        }
        sb.append(".");
        return sb.toString();
    }

    /** "274" -> "art_274"; "art_260__para_3" ostaje; prazno -> null. */
    private String toArticleEId(String raw) {
        if (raw == null || raw.isBlank() || raw.equalsIgnoreCase("NEPOZNATO")) return null;
        if (raw.startsWith("art_")) return raw;
        if (raw.matches("\\d{1,3}")) return "art_" + raw;
        return raw;
    }

    // ====================== RETAIN ======================

    /** Dopisuje novi (rešen) slučaj u cases.csv i u in-memory bazu. */
    public void retain(CaseFacts facts, String articleViolated, SentenceProposal sentence) {
        Path csvPath = Paths.get(appConfig.getReasoning().getCasesFile());
        long id = nextId.getAndIncrement();

        String[] row = new String[]{
                String.valueOf(id), "USER", "",
                stringOf(facts, "substanceQuantityM3"), stringOf(facts, "substanceType"),
                stringOf(facts, "pollutionTarget"), stringOf(facts, "damageExtent"),
                stringOf(facts, "intent"), stringOf(facts, "priorConviction"),
                stringOf(facts, "remediedDamage"), stringOf(facts, "forestAreaHa"),
                articleViolated == null ? "" : articleViolated,
                sentence != null && sentence.getType() != null ? sentence.getType().name() : "",
                sentence != null && sentence.getProposedMonths() != null
                        ? String.valueOf(sentence.getProposedMonths()) : ""
        };

        try (CSVWriter writer = new CSVWriter(new FileWriter(csvPath.toFile(), true))) {
            writer.writeNext(row);
            log.info("CBR retain: dodat slučaj #{} (article={})", id, articleViolated);
        } catch (IOException e) {
            log.error("Greška pri retain-u u CSV: {}", e.getMessage(), e);
            return;
        }

        // odmah dostupan za naredna rasuđivanja (bez restarta)
        Map<String, Object> f = new LinkedHashMap<>();
        for (String fc : FACT_COLUMNS) {
            Object v = facts.get(fc);
            if (v != null) f.put(fc, v.toString());
        }
        CbrCase c = CbrCase.builder()
                .id(id).label("USER-" + id).source("USER")
                .facts(f).articleViolated(articleViolated)
                .sentenceType(row[12].isEmpty() ? null : row[12])
                .sentenceMonths(sentence != null ? sentence.getProposedMonths() : null)
                .build();
        caseBase.add(c);
    }

    // ====================== CSV init + helperi ======================

    private void initCsvAndId() {
        Path csvPath = Paths.get(appConfig.getReasoning().getCasesFile());
        try {
            if (!Files.exists(csvPath)) {
                if (csvPath.getParent() != null) Files.createDirectories(csvPath.getParent());
                writeHeader(csvPath);
                log.info("Kreiran novi CBR CSV: {}", csvPath);
            } else if (Files.size(csvPath) == 0) {
                writeHeader(csvPath);
            }
            long maxId = Files.lines(csvPath).skip(1)
                    .map(line -> line.split(",", 2)[0].replace("\"", ""))
                    .filter(s -> !s.isBlank())
                    .mapToLong(s -> { try { return Long.parseLong(s.trim()); } catch (NumberFormatException e) { return 0L; } })
                    .max().orElse(0L);
            nextId.set(maxId + 1);
        } catch (IOException e) {
            log.error("Greška pri inicijalizaciji CBR CSV-a: {}", e.getMessage());
        }
    }

    private void writeHeader(Path csvPath) throws IOException {
        try (CSVWriter writer = new CSVWriter(new FileWriter(csvPath.toFile()))) {
            writer.writeNext(CSV_HEADER);
        }
    }

    private String stringOf(CaseFacts facts, String key) {
        Object v = facts.get(key);
        return v == null ? "" : v.toString();
    }

    private static String get(String[] row, Map<String, Integer> col, String name) {
        Integer i = col.get(name);
        if (i == null || i >= row.length) return null;
        return row[i];
    }

    private static String firstNonBlank(String... vals) {
        for (String v : vals) if (v != null && !v.isBlank()) return v;
        return null;
    }

    private static String blankToNull(String v) {
        return (v == null || v.isBlank() || v.equalsIgnoreCase("NEPOZNATO")) ? null : v;
    }

    private static Long parseLong(String s) {
        try { return s == null ? null : Long.parseLong(s.trim()); } catch (NumberFormatException e) { return null; }
    }

    private static Integer parseIntOrNull(String s) {
        if (s == null || s.isBlank() || s.equalsIgnoreCase("NEPOZNATO")) return null;
        try { return (int) Math.round(Double.parseDouble(s.trim().replace(',', '.'))); }
        catch (NumberFormatException e) { return null; }
    }

    private static double round(double d) {
        return Math.round(d * 1000.0) / 1000.0;
    }
}
