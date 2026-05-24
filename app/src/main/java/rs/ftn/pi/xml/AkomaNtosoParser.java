package rs.ftn.pi.xml;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import rs.ftn.pi.model.Article;
import rs.ftn.pi.model.Judgment;
import rs.ftn.pi.model.Law;
import rs.ftn.pi.model.Paragraph;

import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Učitava i parsira Akoma Ntoso XML dokumente.
 *
 * VLASNIK: Član 1 (za zakone), Član 2 (za odluke).
 * CELINE: 1, 2.
 */
@Slf4j
@Component
public class AkomaNtosoParser {

    private static final String AKN_NS = "http://docs.oasis-open.org/legaldocml/ns/akn/3.0";

    /**
     * Učitava zakon iz Akoma Ntoso XML fajla.
     * Implementirano: čita FRBR metapodatke + strukturu chapter/article/paragraph.
     */
    public Law parseLaw(Path xmlPath) {
        log.info("Učitavanje zakona iz {}", xmlPath);
        try {
            Document doc = loadXml(xmlPath);
            XPath xpath = newXpath();

            // ID = ime fajla bez .xml (npr. "kz.xml" -> "kz")
            String fileName = xmlPath.getFileName().toString();
            String id = fileName.endsWith(".xml")
                    ? fileName.substring(0, fileName.length() - 4)
                    : fileName;

            // Title: prvo proba FRBRalias[@name='caseName'], pa fallback na act/@name
            String title = xpathString(xpath, doc,
                    "//akn:FRBRWork/akn:FRBRalias[@name='caseName']/@value");
            if (title == null || title.isBlank()) {
                title = xpathString(xpath, doc, "//akn:act/@name");
            }
            if (title == null || title.isBlank()) {
                title = id;
            }

            // FRBRWork URI
            String akomaNtosoUri = xpathString(xpath, doc, "//akn:FRBRWork/akn:FRBRuri/@value");

            // Datum donošenja
            String dateStr = xpathString(xpath, doc,
                    "//akn:FRBRWork/akn:FRBRdate[@name='adoption']/@date");
            LocalDate enactedDate = null;
            if (dateStr != null && !dateStr.isBlank()) {
                try {
                    enactedDate = LocalDate.parse(dateStr);
                } catch (Exception e) {
                    log.warn("Ne mogu da parsiram datum '{}' iz {}", dateStr, xmlPath);
                }
            }

            // Sakupi sve članove
            List<Article> articles = new ArrayList<>();
            NodeList articleNodes = (NodeList) xpath.evaluate(
                    "//akn:article", doc, XPathConstants.NODESET);
            for (int i = 0; i < articleNodes.getLength(); i++) {
                Element artEl = (Element) articleNodes.item(i);
                articles.add(parseArticle(artEl, xpath));
            }

            return Law.builder()
                    .id(id)
                    .title(title)
                    .enactedDate(enactedDate)
                    .akomaNtosoUri(akomaNtosoUri)
                    .articles(articles)
                    .xmlPath(xmlPath.toString())
                    .build();

        } catch (Exception e) {
            log.error("Greška pri parsiranju zakona iz {}: {}", xmlPath, e.getMessage(), e);
            String fileName = xmlPath.getFileName().toString();
            String id = fileName.endsWith(".xml")
                    ? fileName.substring(0, fileName.length() - 4)
                    : fileName;
            return Law.builder()
                    .id(id)
                    .title("[Greška pri učitavanju] " + id)
                    .xmlPath(xmlPath.toString())
                    .build();
        }
    }

    private Article parseArticle(Element artEl, XPath xpath) throws Exception {
        String eId = artEl.getAttribute("eId");
        String number = xpathString(xpath, artEl, "akn:num");
        String heading = xpathString(xpath, artEl, "akn:heading");

        List<Paragraph> paragraphs = new ArrayList<>();
        NodeList paraNodes = (NodeList) xpath.evaluate(
                "akn:paragraph", artEl, XPathConstants.NODESET);
        for (int i = 0; i < paraNodes.getLength(); i++) {
            Element pEl = (Element) paraNodes.item(i);
            paragraphs.add(Paragraph.builder()
                    .eId(pEl.getAttribute("eId"))
                    .number(xpathString(xpath, pEl, "akn:num"))
                    .text(xpathString(xpath, pEl, "akn:content"))
                    .build());
        }

        // Sve interne reference unutar člana
        List<String> references = new ArrayList<>();
        NodeList refNodes = (NodeList) xpath.evaluate(
                ".//akn:ref/@href", artEl, XPathConstants.NODESET);
        for (int i = 0; i < refNodes.getLength(); i++) {
            String href = refNodes.item(i).getNodeValue();
            if (!references.contains(href)) {
                references.add(href);
            }
        }

        return Article.builder()
                .eId(eId)
                .number(number)
                .heading(heading)
                .paragraphs(paragraphs)
                .references(references)
                .build();
    }

    /**
     * Učitava sudsku odluku iz Akoma Ntoso XML fajla.
     *
     * TODO Član 2: implementirati (slično kao parseLaw).
     */
    public Judgment parseJudgment(Path xmlPath) {
        log.info("Učitavanje odluke iz {} - TODO Član 2", xmlPath);
        String fileName = xmlPath.getFileName().toString();
        String id = fileName.endsWith(".xml")
                ? fileName.substring(0, fileName.length() - 4)
                : fileName;
        return Judgment.builder()
                .id(id)
                .caseNumber("[TODO Član 2]")
                .build();
    }

    // ============================================================
    // XML/XPath helperi
    // ============================================================

    private Document loadXml(Path xmlPath) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        DocumentBuilder db = dbf.newDocumentBuilder();
        try (var is = Files.newInputStream(xmlPath)) {
            return db.parse(is);
        }
    }

    private XPath newXpath() {
        XPath xpath = XPathFactory.newInstance().newXPath();
        xpath.setNamespaceContext(new NamespaceContext() {
            @Override
            public String getNamespaceURI(String prefix) {
                return "akn".equals(prefix) ? AKN_NS : null;
            }
            @Override
            public String getPrefix(String namespaceURI) { return null; }
            @Override
            public Iterator<String> getPrefixes(String namespaceURI) { return null; }
        });
        return xpath;
    }

    private String xpathString(XPath xpath, Object source, String expr) throws Exception {
        Object result = xpath.evaluate(expr, source, XPathConstants.NODE);
        if (result == null) return null;
        Node node = (Node) result;
        String text = node.getTextContent();
        return text == null ? null : text.trim();
    }
}
