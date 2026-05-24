package rs.ftn.pi;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Sanity test - aplikacija mora da se podigne.
 *
 * NAPOMENA: ovaj test zahteva PostgreSQL na localhost:5432
 * sa kreiranom bazom 'pravna_informatika'. Za CI bismo koristili
 * Testcontainers, ali za sad - pokrenuti lokalno.
 */
@SpringBootTest
@ActiveProfiles("test")
class PiApplicationTests {

    @Test
    void contextLoads() {
        // Ako ovo prođe - aplikacija je validno konfigurisana.
    }
}
