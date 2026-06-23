package rs.ftn.pi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import rs.ftn.pi.config.AppConfig;
import rs.ftn.pi.model.JudgmentOverride;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * Čuvanje/učitavanje ručnih ispravki ekstrahovanih podataka presuda (Celina 4).
 * Sidecar JSON po presudi: data/judgments/overrides/{id}.json.
 *
 * VLASNIK: Član 2.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JudgmentOverrideService {

    private final AppConfig appConfig;
    private final ObjectMapper objectMapper;

    private Path overridesDir() {
        return Paths.get(appConfig.getDataDir(), "judgments", "overrides");
    }

    public Optional<JudgmentOverride> load(String judgmentId) {
        Path file = overridesDir().resolve(judgmentId + ".json");
        if (!Files.exists(file)) return Optional.empty();
        try {
            return Optional.of(objectMapper.readValue(file.toFile(), JudgmentOverride.class));
        } catch (Exception e) {
            log.error("Ne mogu da učitam override za {}: {}", judgmentId, e.getMessage());
            return Optional.empty();
        }
    }

    public void save(String judgmentId, JudgmentOverride override) {
        try {
            Path dir = overridesDir();
            Files.createDirectories(dir);
            override.setEdited(true);
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(dir.resolve(judgmentId + ".json").toFile(), override);
            log.info("Sačuvane ručne ispravke za presudu {}", judgmentId);
        } catch (Exception e) {
            log.error("Greška pri čuvanju override-a za {}: {}", judgmentId, e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }
}
