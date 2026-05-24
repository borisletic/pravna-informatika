package rs.ftn.pi.nlp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * DTO-ovi za komunikaciju sa Python NLP servisom.
 * UGOVOR: videti docs/INTEGRATION_CONTRACTS.md sekcija 3.
 */
public class NlpDtos {

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ExtractRequest {
        private String text;
        private String documentType;   // "JUDGMENT" ili "CASE_DESCRIPTION"
        private Map<String, Object> options;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ExtractResponse {
        private Metadata metadata;
        private List<Fact> facts;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Metadata {
        private String caseNumber;
        private String court;
        private String date;
        private List<Party> parties;
        private List<String> judges;
        private String recorder;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Party {
        private String role;
        private String initials;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Fact {
        private String predicate;
        private Object value;
        private Double confidence;
        private SourceSpan sourceSpan;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class SourceSpan {
        private Integer start;
        private Integer end;
    }
}
