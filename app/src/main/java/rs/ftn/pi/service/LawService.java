package rs.ftn.pi.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import rs.ftn.pi.config.AppConfig;
import rs.ftn.pi.model.Law;
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
public class LawService {

    private final AkomaNtosoParser parser;
    private final XsltTransformer xsltTransformer;
    private final AppConfig appConfig;

    /**
     * Vraća listu svih zakona u data/laws.
     */
    public List<Law> listLaws() {
        Path lawsDir = Paths.get(appConfig.getDataDir(), "laws");
        List<Law> result = new ArrayList<>();
        if (!Files.exists(lawsDir)) {
            log.warn("Direktorijum sa zakonima ne postoji: {}", lawsDir);
            return result;
        }
        try (Stream<Path> files = Files.list(lawsDir)) {
            files.filter(p -> p.toString().endsWith(".xml"))
                 .forEach(p -> result.add(parser.parseLaw(p)));
        } catch (Exception e) {
            log.error("Greška pri listanju zakona", e);
        }
        return result;
    }

    /**
     * Vraća HTML prikaz zakona za prikaz korisniku (Celina 7).
     */
    public Optional<String> getLawHtml(String lawId) {
        Path xmlPath = Paths.get(appConfig.getDataDir(), "laws", lawId + ".xml");
        if (!Files.exists(xmlPath)) {
            return Optional.empty();
        }
        try {
            String xml = Files.readString(xmlPath);
            return Optional.of(xsltTransformer.lawToHtml(xml));
        } catch (Exception e) {
            log.error("Greška pri učitavanju zakona {}", lawId, e);
            return Optional.empty();
        }
    }
}
