package rs.ftn.pi.reasoning.cbr;

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

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Rasuđivanje po slučajevima (CBR) preko jColibri.
 *
 * VLASNIK: Član 2 (NLP & Data).
 * CELINA: 6.
 *
 * Trenutno MOCK retrieval, ali retain RADI - dopisuje slučajeve u CSV.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CaseReasoner implements Reasoner {

    private static final String[] CSV_HEADER = {
            "id", "source", "sourceJudgmentId",
            "substanceQuantityM3", "substanceType", "pollutionTarget",
            "damageExtent", "intent", "priorConviction", "remediedDamage",
            "forestAreaHa", "articleViolated", "sentenceType", "sentenceMonths"
    };

    private final AppConfig appConfig;
    private final AtomicLong nextId = new AtomicLong(1);

    @PostConstruct
    public void initCsvAndId() {
        Path csvPath = Paths.get(appConfig.getReasoning().getCasesFile());

        // Inicijalizuj CSV ako ne postoji ili je prazan
        try {
            if (!Files.exists(csvPath)) {
                Files.createDirectories(csvPath.getParent());
                writeHeader(csvPath);
                log.info("Kreiran novi CBR CSV: {}", csvPath);
            } else if (Files.size(csvPath) == 0) {
                writeHeader(csvPath);
            }

            // Nađi najveći postojeći ID iz CSV-a
            long maxId = Files.lines(csvPath)
                    .skip(1)  // header
                    .map(line -> line.split(",", 2)[0])
                    .filter(s -> !s.isBlank())
                    .mapToLong(s -> {
                        try { return Long.parseLong(s.trim()); }
                        catch (NumberFormatException e) { return 0L; }
                    })
                    .max().orElse(0L);
            nextId.set(maxId + 1);
            log.info("CBR baza: sledeći ID = {}", nextId.get());

        } catch (IOException e) {
            log.error("Greška pri inicijalizaciji CBR CSV-a: {}", e.getMessage());
        }
    }

    private void writeHeader(Path csvPath) throws IOException {
        try (CSVWriter writer = new CSVWriter(new FileWriter(csvPath.toFile()))) {
            writer.writeNext(CSV_HEADER);
        }
    }

    @Override
    public ReasoningResult reason(CaseFacts facts) {
        log.info("CaseReasoner: pretraga sličnih slučajeva za {} činjenica", facts.getFacts().size());

        // TODO Član 2: zameniti pravim jColibri NN-Retrieval-om.
        // Trenutno mock - vraća jedan placeholder slučaj.

        return mockResult(facts);
    }

    /**
     * Retain - dopisuje novi (rešen) slučaj u CSV bazu.
     * Poziva CaseController nakon što korisnik potvrdi presudu.
     *
     * VLASNIK: Član 2 (CBR). Inicijalna implementacija je polazna - dovoljna
     * za demonstraciju retain ciklusa. Pravi jColibri može da je prepiše.
     */
    public void retain(CaseFacts facts,
                       String articleViolated,
                       SentenceProposal sentence) {
        Path csvPath = Paths.get(appConfig.getReasoning().getCasesFile());
        long id = nextId.getAndIncrement();

        String[] row = new String[]{
                String.valueOf(id),
                "USER",
                "",  // sourceJudgmentId - prazno za korisničke slučajeve
                stringOf(facts, "substanceQuantityM3"),
                stringOf(facts, "substanceType"),
                stringOf(facts, "pollutionTarget"),
                stringOf(facts, "damageExtent"),
                stringOf(facts, "intent"),
                stringOf(facts, "priorConviction"),
                stringOf(facts, "remediedDamage"),
                stringOf(facts, "forestAreaHa"),
                articleViolated == null ? "" : articleViolated,
                sentence != null && sentence.getType() != null ? sentence.getType().name() : "",
                sentence != null && sentence.getProposedMonths() != null
                        ? String.valueOf(sentence.getProposedMonths()) : ""
        };

        try (CSVWriter writer = new CSVWriter(new FileWriter(csvPath.toFile(), true))) {
            writer.writeNext(row);
            log.info("CBR retain: dodat slučaj #{} u CSV (article={})", id, articleViolated);
        } catch (IOException e) {
            log.error("Greška pri retain-u u CSV: {}", e.getMessage(), e);
        }
    }

    private String stringOf(CaseFacts facts, String key) {
        Object v = facts.get(key);
        return v == null ? "" : v.toString();
    }

    private ReasoningResult mockResult(CaseFacts facts) {
        SimilarCase mock1 = SimilarCase.builder()
                .caseId(1L)
                .sourceJudgmentId("K-145/2019")
                .similarity(0.87)
                .attributeSimilarities(Map.of(
                        "substanceQuantityM3", 0.9,
                        "substanceType", 1.0,
                        "damageExtent", 0.8
                ))
                .facts(Map.of(
                        "substanceQuantityM3", 10.0,
                        "substanceType", "HEMIJSKI_OTPAD"
                ))
                .articleViolated("art_260__para_1")
                .sentence(SentenceProposal.builder()
                        .type(SentenceProposal.SentenceType.ZATVOR)
                        .proposedMonths(15)
                        .build())
                .build();

        return ReasoningResult.builder()
                .reasonerType(ReasoningResult.ReasonerType.CASE_BASED)
                .similarCases(List.of(mock1))
                .explanation("[MOCK] CBR pretraga još nije implementirana (Član 2).")
                .build();
    }
}
