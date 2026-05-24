package rs.ftn.pi.xml;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import rs.ftn.pi.model.Law;
import rs.ftn.pi.model.Judgment;

import java.nio.file.Path;

/**
 * Učitava i parsira Akoma Ntoso XML dokumente.
 *
 * VLASNIK: Član 1 (za zakone), Član 2 (za odluke).
 * CELINE: 1, 2.
 *
 * Saxon-HE je već u dependencies-ima za XSLT i XPath.
 */
@Slf4j
@Component
public class AkomaNtosoParser {

    /**
     * Učitava zakon iz Akoma Ntoso XML fajla.
     *
     * TODO Član 1: implementirati.
     */
    public Law parseLaw(Path xmlPath) {
        log.info("Učitavanje zakona iz {}", xmlPath);
        // TODO Član 1:
        //  - Učitati XML pomoću Saxon-a ili DocumentBuilder-a
        //  - Iz <meta><identification><FRBRWork>...</FRBRWork> izvući metapodatke
        //  - Prošetati strukturu chapter -> article -> paragraph
        //  - Sakupiti reference (<ref href="...">)
        //  - Vratiti Law objekat sa listom Article-a
        return Law.builder()
                .id("MOCK")
                .title("[MOCK] Zakon")
                .build();
    }

    /**
     * Učitava sudsku odluku iz Akoma Ntoso XML fajla.
     *
     * TODO Član 2: implementirati.
     */
    public Judgment parseJudgment(Path xmlPath) {
        log.info("Učitavanje odluke iz {}", xmlPath);
        // TODO Član 2: slično kao parseLaw, ali za <judgment> elemente
        return Judgment.builder()
                .id("MOCK")
                .caseNumber("[MOCK]")
                .build();
    }
}
