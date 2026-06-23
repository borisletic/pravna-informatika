package rs.ftn.pi.reasoning.rule;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.Serializer;
import net.sf.saxon.s9api.XsltCompiler;
import net.sf.saxon.s9api.XsltExecutable;
import net.sf.saxon.s9api.XsltTransformer;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import rs.ftn.pi.config.AppConfig;
import rs.ftn.pi.model.CaseFacts;
import rs.ftn.pi.reasoning.Reasoner;
import rs.ftn.pi.reasoning.dto.DerivationStep;
import rs.ftn.pi.reasoning.dto.ReasoningResult;
import rs.ftn.pi.reasoning.dto.SentenceProposal;
import rs.ftn.pi.reasoning.dto.ViolatedArticle;
import rs.ftn.pi.service.LawArticleTextService;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.stream.StreamSource;
import java.io.File;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Rasudjivanje po pravilima alatom <b>dr-device</b> (CLIPS defeasible logic reasoner).
 *
 * VLASNIK: Clan 1 (Legal Modeling).
 * CELINA: 5 — zakljucivanje nad pravnim cinjenicama na osnovu pravila
 *             predstavljenih u LegalRuleML formatu.
 *
 * <p>Tok rasudjivanja (sve pokrece ova klasa):
 * <ol>
 *   <li>{@code environmental_rules.lrml} (LegalRuleML, jedini izvor pravila) --XSLT-->
 *       {@code rulebase.ruleml} (DR-DEVICE RuleML 0.91)</li>
 *   <li>{@code rulebase.ruleml} --XSLT--> {@code rulebase.clp} (CLIPS native)</li>
 *   <li>{@link CaseFacts} --> {@code facts.rdf} + {@code facts.n3} (RDF cinjenice)</li>
 *   <li>CLIPSDOS pokrece dr-device nad pravilima i cinjenicama --> {@code export.rdf}
 *       (dokazani zakljucci: prekrseni clanovi + raspon kazne) + {@code proof.ruleml}</li>
 *   <li>{@code export.rdf} se parsira u {@link ReasoningResult}</li>
 * </ol>
 *
 * Koraci 1-2 se rade pri startu (Saxon u procesu). Korak 3-5 pri svakom
 * {@link #reason(CaseFacts)} pozivu. Ako dr-device nije dostupan, vraca se mock.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DrDeviceReasoner implements Reasoner {

    private static final String LRML2RULEML_XSL = "dr-device/lrml2ruleml_environmental.xsl";
    private static final String CASE_NS = "http://informatika.ftn.uns.ac.rs/legal-case.rdf#";
    private static final String CASE_SUBJECT = "Case";

    /** Sve cinjenice koje se pojavljuju u pravilima moraju postojati kao slotovi
     *  klase lc:case (inace dr-device prijavljuje "no objects can satisfy ... restriction").
     *  Zato facts.rdf uvek sadrzi ceo skup, sa podrazumevanim vrednostima. */
    private static final Map<String, String> PREDICATE_DEFAULTS = new LinkedHashMap<>();
    static {
        // enum predikati -> sentinel "NONE" (ne poklapa se ni sa jednim pravilom)
        for (String p : new String[]{"pollutionExtent", "pollutionScope", "intent",
                "ecologicalDamage", "damageRemovalDifficulty", "perpetratorType"}) {
            PREDICATE_DEFAULTS.put(p, "NONE");
        }
        // boolean predikati -> "false"
        for (String p : new String[]{"violatedEnvironmentalRegs", "failedToTakeProtectiveMeasures",
                "unauthorizedConstruction", "damagedProtectionEquipment", "destroyedProtectedNaturalAsset",
                "illegalSpeciesTraffic", "dangerousSubstanceAction", "officialPositionAbuse",
                "organizesCrime", "unauthorizedNuclearFacility", "deniedEnvironmentalInfo"}) {
            PREDICATE_DEFAULTS.put(p, "false");
        }
    }

    private static final Map<String, String> RULE_DESCRIPTIONS = Map.ofEntries(
            Map.entry("art_260__para_1", "Zagadjenje zivotne sredine sa umisljajem"),
            Map.entry("art_260__para_2", "Zagadjenje zivotne sredine iz nehata"),
            Map.entry("art_260__para_3", "Kvalifikovani oblik (umisljaj + velike razmere stete)"),
            Map.entry("art_260__para_4", "Kvalifikovani oblik (nehat + velike razmere stete)"),
            Map.entry("art_261__para_1", "Nepreduzimanje propisanih mera zastite"),
            Map.entry("art_262__para_1", "Protivpravna izgradnja zagadjujucih objekata"),
            Map.entry("art_263__para_1", "Ostecenje objekata za zastitu zivotne sredine"),
            Map.entry("art_265__para_1", "Unistenje posebno zasticenog prirodnog dobra"),
            Map.entry("art_265__para_3", "Protivpravno iznosenje zasticene vrste"),
            Map.entry("art_266__para_1", "Opasne materije - osnovni oblik"),
            Map.entry("art_266__para_2", "Opasne materije sa zloupotrebom polozaja"),
            Map.entry("art_266__para_5", "Organizovanje vrsenja sa opasnim materijama"),
            Map.entry("art_267__para_1", "Nedozvoljena izgradnja nuklearnog postrojenja"),
            Map.entry("art_268__para_1", "Uskracivanje podataka o stanju zivotne sredine")
    );

    private final AppConfig appConfig;
    private final LawArticleTextService lawArticleTextService;
    private final Processor saxon = new Processor(false);

    private File home;          // dr-device distribucija
    private boolean ready = false;

    /** Da li je dr-device uspesno inicijalizovan (za testove/dijagnostiku). */
    public boolean isReady() {
        return ready;
    }

    @PostConstruct
    public void initRules() {
        AppConfig.DrDevice cfg = appConfig.getDrDevice();
        if (cfg == null || !cfg.isEnabled()) {
            log.warn("dr-device je iskljucen (app.dr-device.enabled=false) - koristim mock rezultat.");
            return;
        }
        this.home = resolveHome(cfg.getHome());
        if (home == null) {
            log.error("dr-device home nije pronadjen (probao: {}). Rasudjivanje po pravilima ce vracati mock.",
                    cfg.getHome());
            return;
        }
        log.info("dr-device home: {}", home.getAbsolutePath());

        try {
            String lrmlPath = appConfig.getReasoning().getRulesFile();
            File lrml = new File(lrmlPath);
            if (!lrml.exists()) {
                log.error("LegalRuleML fajl ne postoji: {} - mock.", lrml.getAbsolutePath());
                return;
            }

            // 1) LegalRuleML -> DR-DEVICE RuleML
            File ruleml = new File(home, "rulebase.ruleml");
            transformFromClasspath(LRML2RULEML_XSL, lrml, ruleml);

            // 2) DR-DEVICE RuleML -> CLIPS native
            File drDeviceXsl = new File(home, "XSL/dr-device.xsl");
            File clp = new File(home, "rulebase.clp");
            transformFromFile(drDeviceXsl, ruleml, clp);

            // 3) CLIPS skripta koja ucitava prevedena pravila (preskace pokvaren bundlovani saxon)
            writeRunScript();
            clearStaleArtifacts();

            ready = clp.length() > 0;
            if (ready) {
                log.info("dr-device inicijalizovan: pravila prevedena iz {} u rulebase.clp", lrmlPath);
            } else {
                log.error("Generisanje rulebase.clp nije uspelo - mock.");
            }
        } catch (Exception e) {
            log.error("Greska pri inicijalizaciji dr-device-a: {}", e.getMessage(), e);
        }
    }

    @Override
    public synchronized ReasoningResult reason(CaseFacts facts) {
        log.info("DrDeviceReasoner: rasudjivanje nad {} cinjenica", facts.getFacts().size());
        if (!ready) {
            log.warn("dr-device nije spreman - vracam mock rezultat");
            return mockResult();
        }
        try {
            writeFacts(facts);
            new File(home, "export.rdf").delete();
            new File(home, "proof.ruleml").delete();

            boolean ok = runEngine();
            File export = new File(home, "export.rdf");
            if (!ok || !export.exists()) {
                log.error("dr-device nije proizveo export.rdf - mock.");
                return mockResult();
            }
            return parseExport(export, facts);
        } catch (Exception e) {
            log.error("Greska tokom dr-device rasudjivanja: {}", e.getMessage(), e);
            return mockResult();
        }
    }

    // ====================== pokretanje CLIPS engine-a ======================

    private boolean runEngine() throws Exception {
        File exe = new File(home, "CLIPSDOS/CLIPSDOS.exe");
        if (!exe.exists()) {
            log.error("CLIPSDOS.exe ne postoji: {}", exe.getAbsolutePath());
            return false;
        }
        ProcessBuilder pb = new ProcessBuilder(
                exe.getAbsolutePath(), "-f2", "run-env.clp");
        pb.directory(home);
        pb.redirectErrorStream(true);
        pb.redirectOutput(new File(home, "run_out.txt"));

        Process p = pb.start();
        int timeout = appConfig.getDrDevice().getTimeoutSeconds();
        if (!p.waitFor(timeout, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            log.error("dr-device nije zavrsio u {}s - prekinuto.", timeout);
            return false;
        }
        return true;
    }

    // ====================== generisanje cinjenica ======================

    /** Konvertuje CaseFacts u facts.rdf (zbog namespace zaglavlja) i facts.n3
     *  (N-Triples koje dr-device direktno cita, cime se zaobilazi ARP parser). */
    private void writeFacts(CaseFacts facts) throws Exception {
        Map<String, String> values = new LinkedHashMap<>(PREDICATE_DEFAULTS);
        for (Map.Entry<String, Object> e : facts.getFacts().entrySet()) {
            if (values.containsKey(e.getKey()) && e.getValue() != null) {
                values.put(e.getKey(), stringify(e.getValue()));
            }
        }
        String defendant = facts.get("defendant") != null
                ? String.valueOf(facts.get("defendant")) : "Okrivljeni";

        // facts.rdf
        StringBuilder rdf = new StringBuilder();
        rdf.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n\n")
           .append("<rdf:RDF\n")
           .append("    xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\"\n")
           .append("    xmlns:rdfs=\"http://www.w3.org/2000/01/rdf-schema#\"\n")
           .append("    xmlns:xsd=\"http://www.w3.org/2001/XMLSchema#\"\n")
           .append("    xmlns:lc=\"").append(CASE_NS).append("\">\n\n")
           .append("    <lc:case rdf:about=\"").append(CASE_NS).append(CASE_SUBJECT).append("\">\n")
           .append("        <lc:name>").append(xml(defendant)).append("</lc:name>\n")
           .append("        <lc:defendant>").append(xml(defendant)).append("</lc:defendant>\n");
        for (Map.Entry<String, String> e : values.entrySet()) {
            rdf.append("        <lc:").append(e.getKey()).append(">")
               .append(xml(e.getValue())).append("</lc:").append(e.getKey()).append(">\n");
        }
        rdf.append("    </lc:case>\n</rdf:RDF>\n");
        Files.write(new File(home, "facts.rdf").toPath(), rdf.toString().getBytes(StandardCharsets.UTF_8));

        // facts.n3 (N-Triples; literali kao stringovi)
        String subj = "<" + CASE_NS + CASE_SUBJECT + ">";
        StringBuilder n3 = new StringBuilder();
        n3.append(subj).append(" <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <")
          .append(CASE_NS).append("case> .\n");
        n3.append(triple(subj, "name", defendant));
        n3.append(triple(subj, "defendant", defendant));
        for (Map.Entry<String, String> e : values.entrySet()) {
            n3.append(triple(subj, e.getKey(), e.getValue()));
        }
        Files.write(new File(home, "facts.n3").toPath(), n3.toString().getBytes(StandardCharsets.UTF_8));
    }

    private String triple(String subj, String pred, String value) {
        return subj + " <" + CASE_NS + pred + "> \"" + n3literal(value) + "\" .\n";
    }

    private static String stringify(Object v) {
        if (v instanceof Boolean b) return b ? "true" : "false";
        return String.valueOf(v);
    }

    private static String n3literal(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String xml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // ====================== parsiranje rezultata ======================

    private ReasoningResult parseExport(File export, CaseFacts facts) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.parse(export);

        List<String> violatedEIds = new ArrayList<>();
        List<Integer> mins = new ArrayList<>();
        List<Integer> maxes = new ArrayList<>();

        NodeList children = doc.getDocumentElement().getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n.getNodeType() != Node.ELEMENT_NODE) continue;
            Element el = (Element) n;
            String local = el.getLocalName();
            if (local == null) continue;
            boolean positive = "defeasibly-proven-positive".equals(childText(el, "truthStatus"))
                    || "definitely-proven-positive".equals(childText(el, "truthStatus"));
            if (!positive) continue;

            if (local.startsWith("violates_")) {
                violatedEIds.add(toEId(local));
            } else if ("min_imprisonment".equals(local)) {
                Integer v = parseInt(childText(el, "value"));
                if (v != null) mins.add(v);
            } else if ("max_imprisonment".equals(local)) {
                Integer v = parseInt(childText(el, "value"));
                if (v != null) maxes.add(v);
            }
        }

        if (violatedEIds.isEmpty()) {
            return ReasoningResult.builder()
                    .reasonerType(ReasoningResult.ReasonerType.RULE_BASED)
                    .explanation("dr-device: nijedno pravilo nije dokazano nad zadatim cinjenicama. " +
                                 "Mozda nedostaju kljucne cinjenice.")
                    .build();
        }

        List<ViolatedArticle> violations = new ArrayList<>();
        List<DerivationStep> derivations = new ArrayList<>();
        for (String eId : violatedEIds) {
            violations.add(ViolatedArticle.builder()
                    .articleEId(eId)
                    .citation(eIdToCitation(eId))
                    .text(resolveText(eId))
                    .certainty("DEFEASIBLE")
                    .build());
            derivations.add(DerivationStep.builder()
                    .ruleId(eId)
                    .ruleDescription(RULE_DESCRIPTIONS.getOrDefault(eId, "(pravilo)"))
                    .matchedFacts(matchedFacts(facts))
                    .conclusion("violates(" + CASE_SUBJECT + ", " + eId + ") [defeasibly-proven-positive]")
                    .build());
        }

        int minMonths = mins.stream().min(Integer::compareTo).orElse(0);
        int maxMonths = maxes.stream().max(Integer::compareTo).orElse(0);
        SentenceProposal sentence = SentenceProposal.builder()
                .type(SentenceProposal.SentenceType.ZATVOR)
                .minMonths(minMonths)
                .maxMonths(maxMonths)
                .proposedMonths((minMonths + maxMonths) / 2)
                .adjustments(buildAdjustmentsNote(facts))
                .build();

        return ReasoningResult.builder()
                .reasonerType(ReasoningResult.ReasonerType.RULE_BASED)
                .violatedArticles(violations)
                .sentenceProposal(sentence)
                .derivations(derivations)
                .explanation(buildExplanation(violations, minMonths, maxMonths, facts))
                .build();
    }

    private static String childText(Element parent, String localName) {
        NodeList kids = parent.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            Node k = kids.item(i);
            if (k.getNodeType() == Node.ELEMENT_NODE && localName.equals(k.getLocalName())) {
                return k.getTextContent().trim();
            }
        }
        return null;
    }

    private static Integer parseInt(String s) {
        try { return s == null ? null : Integer.valueOf(s.trim()); }
        catch (NumberFormatException e) { return null; }
    }

    /** violates_260_3 -> art_260__para_3 */
    private static String toEId(String cls) {
        String rest = cls.substring("violates_".length());
        int us = rest.indexOf('_');
        if (us < 0) return "art_" + rest;
        return "art_" + rest.substring(0, us) + "__para_" + rest.substring(us + 1);
    }

    private static String eIdToCitation(String eId) {
        String[] parts = eId.split("__");
        String art = parts[0].replace("art_", "");
        if (parts.length > 1) {
            return "KZ čl. " + art + " st. " + parts[1].replace("para_", "");
        }
        return "KZ čl. " + art;
    }

    private String resolveText(String eId) {
        String text = lawArticleTextService.findText(eId);
        return (text != null && !text.isEmpty()) ? text : "(tekst nedostupan za eId " + eId + ")";
    }

    private List<String> matchedFacts(CaseFacts facts) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Object> e : facts.getFacts().entrySet()) {
            if (PREDICATE_DEFAULTS.containsKey(e.getKey())) {
                out.add(e.getKey() + " = " + e.getValue());
            }
        }
        return out;
    }

    private String buildAdjustmentsNote(CaseFacts facts) {
        List<String> notes = new ArrayList<>();
        if (Boolean.TRUE.equals(facts.get("priorConviction"))) notes.add("otezavajuca: prethodno osudjivan");
        if (Boolean.TRUE.equals(facts.get("remediedDamage"))) notes.add("olaksavajuca: otklonio stetu");
        return notes.isEmpty() ? null : String.join("; ", notes);
    }

    private String buildExplanation(List<ViolatedArticle> violations, int min, int max, CaseFacts facts) {
        StringBuilder sb = new StringBuilder();
        sb.append("dr-device (defeasible logika) je nad zadatim cinjenicama dokazao prekrsaj ")
          .append(violations.size()).append(" odredb").append(violations.size() == 1 ? "e" : "i")
          .append(" KZ-a: ");
        List<String> cites = new ArrayList<>();
        for (ViolatedArticle v : violations) cites.add(v.getCitation());
        sb.append(String.join(", ", cites)).append(". ");
        sb.append("Predlozeni raspon kazne zatvora: ").append(min).append("-").append(max).append(" meseci.");
        String adj = buildAdjustmentsNote(facts);
        if (adj != null) sb.append(" Okolnosti: ").append(adj).append(".");
        return sb.toString();
    }

    private ReasoningResult mockResult() {
        return ReasoningResult.builder()
                .reasonerType(ReasoningResult.ReasonerType.RULE_BASED)
                .explanation("[MOCK] dr-device reasoner nije dostupan - proveri app.dr-device.home " +
                             "i da li CLIPSDOS moze da se pokrene.")
                .build();
    }

    // ====================== pomocno: XSLT + putanje ======================

    private void transformFromClasspath(String classpathXsl, File input, File output) throws Exception {
        ClassPathResource res = new ClassPathResource(classpathXsl);
        try (var in = res.getInputStream()) {
            transform(new StreamSource(in), input, output);
        }
    }

    private void transformFromFile(File xsl, File input, File output) throws Exception {
        transform(new StreamSource(xsl), input, output);
    }

    private void transform(StreamSource xsl, File input, File output) throws Exception {
        XsltCompiler compiler = saxon.newXsltCompiler();
        XsltExecutable exec = compiler.compile(xsl);
        XsltTransformer t = exec.load();
        t.setSource(new StreamSource(input));
        StringWriter sw = new StringWriter();
        Serializer ser = saxon.newSerializer(sw);
        t.setDestination(ser);
        t.transform();
        Files.write(output.toPath(), sw.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** Skripta koja ucitava vec-prevedena pravila (rulebase.clp) i pokrece reasoner.
     *  Zaobilazi bundlovani saxon iz dr-device-a (kome nedostaje xmlresolver). */
    private void writeRunScript() throws Exception {
        String script = """
                (dribble-on "rulebase.log")
                (batch* "bin\\\\dr-device.bat")
                (set-verbose on)
                (set-debug off)
                (set-time-report off)
                (set-compact-proofs on)
                (set-export-non-proved off)
                (load-dr-device "rulebase.clp")
                (dribble-off)
                (exit)
                (exit)
                """;
        Files.write(new File(home, "run-env.clp").toPath(), script.getBytes(StandardCharsets.UTF_8));
    }

    /** Brise zastarele kompajlirane artefakte da bi dr-device koristio sveze rulebase.clp. */
    private void clearStaleArtifacts() {
        String[] gen = {
                "defeasible-r-device-rule-class-instances-rulebase.clp",
                "defeasible-r-device-rule-instances-rulebase.clp",
                "r-device-compiled-derived-classes-defeasible-r-device-rules-rulebase.clp",
                "r-device-compiled-rule-class-instances-defeasible-r-device-rules-rulebase.clp",
                "r-device-compiled-rule-instances-defeasible-r-device-rules-rulebase.clp",
                "r-device-compiled-rules-defeasible-r-device-rules-rulebase.clp",
                "defeasible-r-device-rules-rulebase.clp",
                "defeasible-r-device-rules-rulebase-comp.bat",
                "rulebase-comp.bat"
        };
        for (String f : gen) new File(home, f).delete();
    }

    private File resolveHome(String configured) {
        List<String> candidates = new ArrayList<>();
        if (configured != null) candidates.add(configured);
        candidates.add("./dr-device");
        candidates.add("../dr-device");
        if (appConfig.getDataDir() != null) {
            candidates.add(new File(appConfig.getDataDir()).getParent() + "/dr-device");
        }
        for (String c : candidates) {
            if (c == null) continue;
            File f = new File(c);
            if (new File(f, "CLIPSDOS/CLIPSDOS.exe").exists()) {
                return f.getAbsoluteFile();
            }
        }
        return null;
    }
}
