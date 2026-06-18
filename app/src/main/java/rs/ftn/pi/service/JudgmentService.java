package rs.ftn.pi.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import rs.ftn.pi.config.AppConfig;
import rs.ftn.pi.model.Judgment;
import rs.ftn.pi.nlp.NlpClient;
import rs.ftn.pi.xml.AkomaNtosoParser;
import rs.ftn.pi.xml.XsltTransformer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class JudgmentService {

    private final AkomaNtosoParser parser;
    private final XsltTransformer xsltTransformer;
    private final NlpClient nlpClient;
    private final AppConfig appConfig;

    public List<Judgment> listJudgments() {
        Path judgmentsDir = Paths.get(appConfig.getDataDir(), "judgments");
        List<Judgment> result = new ArrayList<>();
        if (!Files.exists(judgmentsDir)) {
            log.warn("Direktorijum sa odlukama ne postoji: {}", judgmentsDir);
            return result;
        }
        try (Stream<Path> files = Files.list(judgmentsDir)) {
            files.filter(p -> p.toString().endsWith(".xml"))
                 .forEach(p -> result.add(parser.parseJudgment(p)));
        } catch (Exception e) {
            log.error("Greška pri listanju odluka", e);
        }
        Path generatedDir = judgmentsDir.resolve("generated");
        if (Files.exists(generatedDir)) {
            try (Stream<Path> files = Files.list(generatedDir)) {
                files.filter(p -> p.toString().endsWith(".xml"))
                        .forEach(p -> result.add(parser.parseJudgment(p)));
            } catch (Exception e) {
                log.error("Greška pri listanju generisanih odluka", e);
            }
        }
        return result;
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
