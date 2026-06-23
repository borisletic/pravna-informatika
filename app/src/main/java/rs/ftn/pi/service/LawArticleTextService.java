package rs.ftn.pi.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import rs.ftn.pi.config.AppConfig;

import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Servis koji pri startu aplikacije učitava kz.xml (i sve ostale zakone iz data/laws/)
 * i indeksira sve paragraphs i articles po njihovom eId atributu.
 *
 * Koristi se u DrDeviceReasoner-u da bi rezultat rasuđivanja sadržao stvarni tekst
 * prekršenog člana, ne placeholder.
 *
 * VLASNIK: Član 1 (Legal Modeling)
 *
 * Napomena: ovaj servis je nezavisan od LawService-a (ako postoji u skeletu).
 * Njegova jedina svrha je da popuni ViolatedArticle.text. Ako Član 3 želi
 * da koristi ovaj servis i u drugim delovima aplikacije, slobodno.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LawArticleTextService {

    private static final String AKN_NS = "http://docs.oasis-open.org/legaldocml/ns/akn/3.0";

    private final AppConfig appConfig;
    private final ConcurrentHashMap<String, String> eIdToText = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        Path lawsDir = Paths.get(appConfig.getDataDir(), "laws");
        if (!Files.exists(lawsDir)) {
            log.warn("LawArticleTextService: data/laws/ ne postoji ({}) - reasoner će prikazati '(tekst nedostupan)'", lawsDir);
            return;
        }

        try (Stream<Path> files = Files.list(lawsDir)) {
            files.filter(p -> p.toString().toLowerCase().endsWith(".xml"))
                 .forEach(this::indexFile);
        } catch (Exception e) {
            log.error("LawArticleTextService: greška pri listanju {}: {}", lawsDir, e.getMessage());
        }

        log.info("LawArticleTextService: indeksirano {} eId -> tekst mapiranja", eIdToText.size());
    }

    private void indexFile(Path path) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            Document doc = dbf.newDocumentBuilder().parse(path.toFile());

            XPath xpath = XPathFactory.newInstance().newXPath();
            xpath.setNamespaceContext(aknNamespaceContext());

            // Indeksiraj sve <paragraph eId="...">
            NodeList paragraphs = (NodeList) xpath.evaluate(
                    "//akn:paragraph[@eId]", doc, XPathConstants.NODESET);
            for (int i = 0; i < paragraphs.getLength(); i++) {
                Element p = (Element) paragraphs.item(i);
                String eId = p.getAttribute("eId");
                eIdToText.put(eId, extractCleanText(p));
            }

            // Indeksiraj i sve <article eId="..."> (za slučaj da rule vrati art_267 bez st.)
            NodeList articles = (NodeList) xpath.evaluate(
                    "//akn:article[@eId]", doc, XPathConstants.NODESET);
            for (int i = 0; i < articles.getLength(); i++) {
                Element a = (Element) articles.item(i);
                String eId = a.getAttribute("eId");
                if (!eIdToText.containsKey(eId)) {
                    eIdToText.put(eId, extractCleanText(a));
                }
            }

            log.debug("LawArticleTextService: učitan {}", path.getFileName());

        } catch (Exception e) {
            log.error("LawArticleTextService: ne mogu da parsiram {}: {}", path, e.getMessage());
        }
    }

    /**
     * Izvlači čist tekst iz elementa, eliminišući unutrašnje XML tagove
     * (npr. <ref>, <num>) i normalizuje whitespace.
     */
    private String extractCleanText(Element el) {
        String raw = el.getTextContent();
        return raw.replaceAll("\\s+", " ").trim();
    }

    /**
     * Vraća tekst paragrafa ili člana po eId, ili null ako ne postoji.
     *
     * @param eId Akoma Ntoso eId (npr. "art_260__para_3" ili "art_267")
     * @return tekst stava/člana, ili null
     */
    public String findText(String eId) {
        if (eId == null) return null;
        return eIdToText.get(eId);
    }

    /**
     * Vraća broj indeksiranih eId-jeva (za health check).
     */
    public int size() {
        return eIdToText.size();
    }

    private NamespaceContext aknNamespaceContext() {
        return new NamespaceContext() {
            @Override
            public String getNamespaceURI(String prefix) {
                return "akn".equals(prefix) ? AKN_NS : null;
            }
            @Override
            public String getPrefix(String namespaceURI) { return null; }
            @Override
            public Iterator<String> getPrefixes(String namespaceURI) { return null; }
        };
    }
}
