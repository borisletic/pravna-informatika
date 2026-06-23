package rs.ftn.pi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@ConfigurationProperties(prefix = "app")
@Data
public class AppConfig {

    private String dataDir;
    private Nlp nlp = new Nlp();
    private Reasoning reasoning = new Reasoning();
    private DrDevice drDevice = new DrDevice();
    private Autostart autostart = new Autostart();

    @Data
    public static class Nlp {
        private String baseUrl;
        private int timeoutSeconds;
    }

    @Data
    public static class Reasoning {
        private String rulesFile;
        private String casesFile;
        private String predicateDict;
    }

    /**
     * Konfiguracija za dr-device (CLIPS) rule reasoner — Celina 5.
     */
    @Data
    public static class DrDevice {
        /** Da li je dr-device rasudjivanje ukljuceno (ako ne, koristi se mock). */
        private boolean enabled = true;
        /** Putanja do dr-device distribucije (sadrzi CLIPSDOS\, DR-DEVICE-source\, ...). */
        private String home = "./dr-device";
        /** Maksimalno trajanje jednog pokretanja reasonera. */
        private int timeoutSeconds = 90;
    }

    /**
     * Automatsko pokretanje pratećih lokalnih servisa pri startu aplikacije
     * (jedan klik „Run" u IntelliJ-u pokreće Ollama + NLP servis + app).
     */
    @Data
    public static class Autostart {
        /** Master prekidač. Ugasi (false) za produkciju / ručno pokretanje. */
        private boolean enabled = true;
        /** Pokreni `ollama serve` ako već ne radi. */
        private boolean ollama = true;
        /** Model koji se obezbeđuje (`ollama pull`) u pozadini. */
        private String ollamaModel = "mistral";
        /** Putanja do ollama izvršne datoteke (na PATH-u: "ollama"). */
        private String ollamaBin = "ollama";
        /** Pokreni Python NLP servis (uvicorn) ako već ne radi. */
        private boolean nlp = true;
        /** Direktorijum NLP servisa; resolver probava i ./nlp-service i ../nlp-service. */
        private String nlpDir = "./nlp-service";
        /** Python izvršna datoteka (na PATH-u: "python"). */
        private String python = "python";
        /** Port NLP servisa. */
        private int nlpPort = 8000;
    }

    @Bean
    public WebClient nlpWebClient() {
        return WebClient.builder()
                .baseUrl(nlp.getBaseUrl())
                .build();
    }
}
