package rs.ftn.pi.nlp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import rs.ftn.pi.config.AppConfig;

import java.time.Duration;
import java.util.Map;

/**
 * REST klijent ka Python NLP mikroservisu.
 *
 * KORISTI: Član 3 (controller -> service -> ovaj klijent)
 * NAPRAVIO: Član 2 (pravi NLP iza endpoint-a)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NlpClient {

    private final WebClient nlpWebClient;
    private final AppConfig appConfig;

    /**
     * Ekstraktuje metapodatke i činjenice iz teksta odluke.
     */
    public NlpDtos.ExtractResponse extractFromJudgment(String text) {
        return extract(text, "JUDGMENT");
    }

    /**
     * Ekstraktuje činjenice iz korisničkog opisa novog slučaja (Celina 8).
     */
    public NlpDtos.ExtractResponse extractFromCaseDescription(String text) {
        return extract(text, "CASE_DESCRIPTION");
    }

    private NlpDtos.ExtractResponse extract(String text, String documentType) {
        log.debug("NLP poziv: documentType={}, dužina teksta={}", documentType, text.length());

        NlpDtos.ExtractRequest req = NlpDtos.ExtractRequest.builder()
                .text(text)
                .documentType(documentType)
                .options(Map.of(
                        "includeMetadata", true,
                        "includeFacts", true,
                        "language", "sr"
                ))
                .build();

        try {
            return nlpWebClient.post()
                    .uri("/extract")
                    .bodyValue(req)
                    .retrieve()
                    .bodyToMono(NlpDtos.ExtractResponse.class)
                    .timeout(Duration.ofSeconds(appConfig.getNlp().getTimeoutSeconds()))
                    .block();
        } catch (Exception e) {
            log.error("NLP poziv nije uspeo: {}", e.getMessage());
            // Fallback - prazan rezultat. Korisnik može ručno da unese činjenice.
            return NlpDtos.ExtractResponse.builder()
                    .metadata(NlpDtos.Metadata.builder().build())
                    .facts(java.util.List.of())
                    .build();
        }
    }
}
