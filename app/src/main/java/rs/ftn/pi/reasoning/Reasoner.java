package rs.ftn.pi.reasoning;

import rs.ftn.pi.model.CaseFacts;
import rs.ftn.pi.reasoning.dto.ReasoningResult;

/**
 * Zajednički interfejs za rule-based i CBR rasuđivanje.
 * Omogućava da Član 3 (app) tretira oba reasoner-a uniformno.
 */
public interface Reasoner {

    /**
     * Izvodi zaključke nad činjenicama.
     */
    ReasoningResult reason(CaseFacts facts);
}
