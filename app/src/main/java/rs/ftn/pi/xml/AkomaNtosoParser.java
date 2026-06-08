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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Učitava i parsira Akoma Ntoso XML dokumente.
 *
 * VLASNIK: Član 1 (za zakone), Član 2 (za odluke).
 * CELINE: 1, 2.
 *
 * NAPOMENA: parseJudgment je realizovan na osnovu strukture presuda
 * koje je Član 2 commitovao - imaju samo FRBRthis URI plus slobodan tekst
 * u <judgmentBody>/<p>. Metapodaci (sud, datum, broj predmeta) izvlače se
 * heuristički iz prvih nekoliko paragrafa.
 *
 * Kasnije kad Član 2 dopuni FRBR (FRBRalias caseNumber, FRBRauthor,
 * FRBRdate, judges, parties), parser će prvo koristiti njih pre fallback-a
 * na heuristiku.
 */
@Slf4j
@Component
public class AkomaNtosoParser {

    private static final String AKN_NS = "http://docs.oasis-open.org/legaldocml/ns/akn/3.0";

    // ============================================================
    // ZAKONI (Član 1)
    // ============================================================

    public Law parseLaw(Path xmlPath) {
        log.info("Učitavanje zakona iz {}", xmlPath);
        try {
            Document doc = loadXml(xmlPath);
            XPath xpath = newXpath();

            String fileName = xmlPath.getFileName().toString();
            String id = fileName.endsWith(".xml")
                    ? fileName.substring(0, fileName.length() - 4)
                    : fileName;

            String title = xpathString(xpath, doc,
                    "//akn:FRBRWork/akn:FRBRalias[@name='caseName']/@value");
            if (title == null || title.isBlank()) {
                title = xpathString(xpath, doc, "//akn:act/@name");
            }
            if (title == null || title.isBlank()) {
                title = id;
            }

            String akomaNtosoUri = xpathString(xpath, doc, "//akn:FRBRWork/akn:FRBRuri/@value");

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

    // ============================================================
    // PRESUDE (Član 2 - ovde realizovano)
    // ============================================================

    /**
     * Učitava sudsku odluku iz Akoma Ntoso XML fajla.
     *
     * Strategija:
     *   1. caseNumber: prvo FRBRalias, pa FRBRthis URI, pa ime fajla
     *   2. court, date: heuristika regex-om nad prvih 15 <p> elemenata u judgmentBody
     *   3. referencedArticles: regex za "član XXX" / "члан XXX" po celom telu
     *   4. factualBackground: ceo tekst telesnih paragrafa (bez strukture jer
     *      Član 2 nije podelio na <background>/<motivation>/<decision>)
     */
    public Judgment parseJudgment(Path xmlPath) {
        log.info("Učitavanje odluke iz {}", xmlPath);
        String fileName = xmlPath.getFileName().toString();
        String id = fileName.endsWith(".xml")
                ? fileName.substring(0, fileName.length() - 4)
                : fileName;

        try {
            Document doc = loadXml(xmlPath);
            XPath xpath = newXpath();

            // FRBRthis URI (npr. "/akn/rs/judgment/2024/br-1056/main")
            String frbrThis = xpathString(xpath, doc,
                    "//akn:judgment//akn:FRBRWork/akn:FRBRthis/@value");

            // 1. Broj predmeta
            String caseNumber = extractCaseNumber(xpath, doc, frbrThis, id);

            // 2. Telesni paragrafi
            List<String> bodyParagraphs = extractBodyParagraphs(xpath, doc);
            String headBlock = bodyParagraphs.stream()
                    .limit(15)
                    .reduce((a, b) -> a + "\n" + b)
                    .orElse("");
            String fullBodyText = String.join("\n", bodyParagraphs);

            // 3. Sud i datum - heuristika
            String court = extractCourt(headBlock);
            LocalDate date = extractDate(headBlock, frbrThis);

            // 4. Reference ka članovima
            List<String> referencedArticles = extractArticleReferences(fullBodyText);

            return Judgment.builder()
                    .id(id)
                    .caseNumber(caseNumber)
                    .court(court)
                    .date(date)
                    .factualBackground(fullBodyText)
                    .referencedArticles(referencedArticles)
                    .xmlPath(xmlPath.toString())
                    .build();

        } catch (Exception e) {
            log.error("Greška pri parsiranju odluke {}: {}", xmlPath, e.getMessage(), e);
            return Judgment.builder()
                    .id(id)
                    .caseNumber(formatFileIdAsCaseNumber(id))
                    .xmlPath(xmlPath.toString())
                    .build();
        }
    }

    /**
     * Izvlači broj predmeta. Prioritet:
     *   1. FRBRalias[@name='caseNumber']
     *   2. Iz FRBRthis URI-ja (npr. "/akn/rs/judgment/2024/br-1056/main" -> "Br. 1056/2024")
     *   3. Iz imena fajla
     */
    private String extractCaseNumber(XPath xpath, Document doc, String frbrThis, String fileId)
            throws Exception {
        String alias = xpathString(xpath, doc,
                "//akn:judgment//akn:FRBRalias[@name='caseNumber']/@value");
        if (alias != null && !alias.isBlank()) {
            return alias;
        }
        if (frbrThis != null && !frbrThis.isBlank()) {
            String fromUri = formatCaseNumberFromUri(frbrThis);
            if (fromUri != null) return fromUri;
        }
        return formatFileIdAsCaseNumber(fileId);
    }

    /**
     * "/akn/rs/judgment/2024/br-1056/main" -> "Br. 1056/2024"
     * "/akn/rs/judgment/2013/pkz-408/main" -> "Pkz. 408/2013"
     */
    private String formatCaseNumberFromUri(String uri) {
        String[] parts = uri.split("/");
        String year = null;
        String caseId = null;
        for (String part : parts) {
            if (part.matches("\\d{4}")) {
                year = part;
            } else if (part.matches("[a-zA-Z]+-?\\d+(-\\d+)?")) {
                if (!part.equals("main")) {
                    caseId = part;
                }
            }
        }
        if (caseId == null) return null;
        return prettifyCaseId(caseId, year);
    }

    /**
     * "br-1056" + "2024" -> "Br. 1056/2024"
     * "u-1116-2004" -> "U. 1116/2004"
     * "kzz-94-2021" -> "Kzz. 94/2021"
     */
    private String prettifyCaseId(String raw, String fallbackYear) {
        Matcher m = Pattern.compile("([a-zA-Z]+)-?(\\d+)(?:-(\\d{4}))?").matcher(raw);
        if (m.matches()) {
            String prefix = capitalize(m.group(1));
            String num = m.group(2);
            String y = m.group(3) != null ? m.group(3) : fallbackYear;
            if (y != null) {
                return prefix + ". " + num + "/" + y;
            }
            return prefix + ". " + num;
        }
        return raw;
    }

    private String formatFileIdAsCaseNumber(String fileId) {
        return prettifyCaseId(fileId, null);
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
    }

    /**
     * Sakupi sve <p> tekstove iz judgmentBody.
     */
    private List<String> extractBodyParagraphs(XPath xpath, Document doc) throws Exception {
        List<String> result = new ArrayList<>();
        NodeList ps = (NodeList) xpath.evaluate(
                "//akn:judgment//akn:judgmentBody//akn:p", doc, XPathConstants.NODESET);
        for (int i = 0; i < ps.getLength(); i++) {
            String text = ps.item(i).getTextContent();
            if (text == null) continue;
            text = text.trim();
            if (!text.isEmpty()) {
                result.add(text);
            }
        }
        return result;
    }

    /**
     * Regex za prepoznavanje tipa suda na ćirilici i latinici.
     * Hvata sve padeže ("ВРХОВНОМ СУДУ", "ВРХОВНОГ КАСАЦИОНОГ СУДА",
     * "Привредном суду", "АПЕЛАЦИОНОМ СУДУ"...) i toleriše OCR
     * artefakte gde je tekst zalepljen bez razmaka.
     *
     * Bez \b prefiksa - Unicode word boundary nije pouzdan između
     * ćiriličnih karaktera, plus Član 2 ima primere gde je "СРБИЈА"
     * zalepljen sa "АПЕЛАЦИОНИ" bez razmaka.
     */
    private static final Pattern COURT_PATTERN = Pattern.compile(
            "(?i)(?:" +
            "врховн[аеиогмух]{1,3}\\s+касацион[аеиогмух]{1,3}|" +
            "vrhovn[aeiogmuh]{1,3}\\s+kasacion[aeiogmuh]{1,3}|" +
            "привредн[аеиогмух]{1,3}\\s+апелацион[аеиогмух]{1,3}|" +
            "privredn[aeiogmuh]{1,3}\\s+apelacion[aeiogmuh]{1,3}|" +
            "прекршајн[аеиогмух]{1,3}\\s+апелацион[аеиогмух]{1,3}|" +
            "prekršajn[aeiogmuh]{1,3}\\s+apelacion[aeiogmuh]{1,3}|" +
            "врховн[аеиогмух]{1,3}|vrhovn[aeiogmuh]{1,3}|" +
            "апелацион[аеиогмух]{1,3}|apelacion[aeiogmuh]{1,3}|" +
            "управн[аеиогмух]{1,3}|upravn[aeiogmuh]{1,3}|" +
            "привредн[аеиогмух]{1,3}|privredn[aeiogmuh]{1,3}|" +
            "прекршајн[аеиогмух]{1,3}|prekršajn[aeiogmuh]{1,3}|" +
            "основн[аеиогмух]{1,3}|osnovn[aeiogmuh]{1,3}|" +
            "виш[еиаогму]{1,3}|viš[eiaogmu]{1,3}|vis[eiaogmu]{1,3}" +
            ")\\s+(?:суд[аеуом]{0,2}|sud[aeuom]{0,2})",
            Pattern.UNICODE_CASE);

    private String extractCourt(String text) {
        Matcher m = COURT_PATTERN.matcher(text);
        if (m.find()) {
            String matched = m.group(0).toLowerCase();
            // Pretvori prvo slovo svake reči u veliko ("Прекршајни Апелациони Суд")
            StringBuilder sb = new StringBuilder();
            boolean capitalizeNext = true;
            for (char c : matched.toCharArray()) {
                if (Character.isWhitespace(c)) {
                    sb.append(c);
                    capitalizeNext = true;
                } else if (capitalizeNext) {
                    sb.append(Character.toUpperCase(c));
                    capitalizeNext = false;
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }
        return null;
    }

    /**
     * Datumi u srpskom formatu: 14.08.2024. ili 22.05.2024
     */
    private static final Pattern DATE_PATTERN = Pattern.compile(
            "(\\d{1,2})[.\\-/](\\d{1,2})[.\\-/](\\d{4})");

    private LocalDate extractDate(String text, String frbrUri) {
        // Prvo proba iz teksta
        Matcher m = DATE_PATTERN.matcher(text);
        while (m.find()) {
            try {
                int day = Integer.parseInt(m.group(1));
                int month = Integer.parseInt(m.group(2));
                int year = Integer.parseInt(m.group(3));
                if (year >= 1990 && year <= 2030 && month >= 1 && month <= 12
                        && day >= 1 && day <= 31) {
                    return LocalDate.of(year, month, day);
                }
            } catch (Exception ignored) {}
        }

        // Fallback: bar godina iz FRBRthis (npr. ".../2024/...")
        if (frbrUri != null) {
            Matcher ym = Pattern.compile("/(\\d{4})/").matcher(frbrUri);
            if (ym.find()) {
                try {
                    int year = Integer.parseInt(ym.group(1));
                    return LocalDate.of(year, 1, 1);
                } catch (Exception ignored) {}
            }
        }
        return null;
    }

    /**
     * Hvata reference ka članovima zakona: "члана 260", "чл. 261", "član 274".
     * Vraća listu u formi ["art_260", "art_261", "art_274"], bez duplikata.
     */
    private static final Pattern ARTICLE_REF_PATTERN = Pattern.compile(
            "(?i)(?:члан[аеу]?|чл\\.?|član[au]?|čl\\.?)\\s*(\\d{1,3})",
            Pattern.UNICODE_CASE);

    private List<String> extractArticleReferences(String text) {
        Set<String> articles = new LinkedHashSet<>();
        Matcher m = ARTICLE_REF_PATTERN.matcher(text);
        while (m.find()) {
            try {
                int num = Integer.parseInt(m.group(1));
                // Sačuvaj samo "razumne" brojeve - članova zakona ima do ~600
                if (num >= 1 && num <= 700) {
                    articles.add("art_" + num);
                }
            } catch (Exception ignored) {}
        }
        return new ArrayList<>(articles);
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
