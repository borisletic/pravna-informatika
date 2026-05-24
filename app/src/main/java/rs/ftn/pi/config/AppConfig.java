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

    @Bean
    public WebClient nlpWebClient() {
        return WebClient.builder()
                .baseUrl(nlp.getBaseUrl())
                .build();
    }
}
