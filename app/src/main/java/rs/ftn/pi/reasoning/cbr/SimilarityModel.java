package rs.ftn.pi.reasoning.cbr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Model funkcija sličnosti za CBR (Celina 6).
 *
 * VLASNIK: Član 2 (NLP & Data).
 *
 * Funkcije sličnosti se NE hardkoduju — učitavaju se iz
 * {@code data/schemas/predicate_dictionary.yaml} (polja {@code cbr_key},
 * {@code cbr_weight}, {@code similarity}). Time je rečnik jedini izvor i za
 * pravila i za sličnost.
 *
 * Podržani tipovi lokalne sličnosti:
 * <ul>
 *   <li><b>exact</b> — 1.0 ako su jednaki, inače 0.0 (default; enum/boolean)</li>
 *   <li><b>ordinal</b> — 1 - |i-j|/(n-1) po zadatom redosledu vrednosti</li>
 *   <li><b>taxonomy</b> — 1.0 isto, 0.5 ista nadkategorija, inače 0.0</li>
 *   <li><b>number</b> — 1 - min(1, |a-b|/scale) (scale = max iz baze)</li>
 * </ul>
 */
@Slf4j
public class SimilarityModel {

    public enum SimType { EXACT, ORDINAL, TAXONOMY, NUMBER }

    /** Konfiguracija jednog predikata relevantnog za CBR. */
    public static class PredCfg {
        String name;
        double weight;
        SimType simType = SimType.EXACT;
        boolean numeric;
        boolean bool;
        List<String> order = new ArrayList<>();              // ordinal
        Map<String, String> memberToGroup = new HashMap<>(); // taxonomy: clan -> nadkategorija
        double numericScale = 1.0;                            // number
    }

    private final Map<String, PredCfg> cbrPredicates = new LinkedHashMap<>();

    public SimilarityModel(Path predicateDictYaml) {
        try {
            ObjectMapper yaml = new ObjectMapper(new YAMLFactory());
            JsonNode root = yaml.readTree(Files.newInputStream(predicateDictYaml));
            JsonNode preds = root.get("predicates");
            if (preds == null) {
                log.error("predicate_dictionary.yaml nema 'predicates' sekciju: {}", predicateDictYaml);
                return;
            }
            preds.fields().forEachRemaining(e -> {
                String name = e.getKey();
                JsonNode p = e.getValue();
                if (p.path("cbr_key").asBoolean(false)) {
                    cbrPredicates.put(name, parse(name, p));
                }
            });
            log.info("SimilarityModel: učitano {} CBR predikata iz {}", cbrPredicates.size(),
                    predicateDictYaml.getFileName());
        } catch (Exception ex) {
            log.error("Greška pri učitavanju predicate_dictionary.yaml ({}): {}",
                    predicateDictYaml, ex.getMessage(), ex);
        }
    }

    private PredCfg parse(String name, JsonNode p) {
        PredCfg cfg = new PredCfg();
        cfg.name = name;
        cfg.weight = p.path("cbr_weight").asDouble(0.05);
        String type = p.path("type").asText("");
        cfg.numeric = "number".equals(type);
        cfg.bool = "boolean".equals(type);

        JsonNode sim = p.get("similarity");
        String simType = sim != null ? sim.path("type").asText("") : "";
        if (cfg.numeric) {
            cfg.simType = SimType.NUMBER;
        } else if ("ordinal".equals(simType)) {
            cfg.simType = SimType.ORDINAL;
            sim.path("order").forEach(n -> cfg.order.add(n.asText()));
        } else if ("taxonomy".equals(simType)) {
            cfg.simType = SimType.TAXONOMY;
            JsonNode h = sim.get("hierarchy");
            if (h != null) {
                h.fields().forEachRemaining(g ->
                        g.getValue().forEach(member -> cfg.memberToGroup.put(member.asText(), g.getKey())));
            }
        } else {
            cfg.simType = SimType.EXACT;
        }
        return cfg;
    }

    public Map<String, PredCfg> cbrPredicates() {
        return cbrPredicates;
    }

    /** Postavlja skalu za numeričke predikate (max apsolutna vrednost iz baze). */
    public void setNumericScale(String predicate, double scale) {
        PredCfg cfg = cbrPredicates.get(predicate);
        if (cfg != null && scale > 0) cfg.numericScale = scale;
    }

    /**
     * Normalizuje sirovu vrednost u uporedivu (String enum, Double broj, "DA"/"NE").
     * Vraća null ako je vrednost odsutna/nepoznata.
     */
    public Object normalize(String predicate, Object raw) {
        PredCfg cfg = cbrPredicates.get(predicate);
        if (cfg == null || raw == null) return null;
        String s = raw.toString().trim();
        if (s.isEmpty() || s.equalsIgnoreCase("NEPOZNATO") || s.equalsIgnoreCase("NEPOZNAT0")
                || s.equalsIgnoreCase("null")) {
            return null;
        }
        if (cfg.numeric) {
            try { return Double.parseDouble(s.replace(',', '.')); }
            catch (NumberFormatException e) { return null; }
        }
        if (cfg.bool) {
            if (s.equalsIgnoreCase("true") || s.equalsIgnoreCase("DA")) return "DA";
            if (s.equalsIgnoreCase("false") || s.equalsIgnoreCase("NE")) return "NE";
            return s.toUpperCase();
        }
        return s.toUpperCase();
    }

    /** Lokalna sličnost za dati predikat nad već normalizovanim vrednostima [0,1]. */
    public double localSimilarity(String predicate, Object a, Object b) {
        PredCfg cfg = cbrPredicates.get(predicate);
        if (cfg == null || a == null || b == null) return 0.0;
        switch (cfg.simType) {
            case NUMBER -> {
                double da = ((Number) a).doubleValue();
                double db = ((Number) b).doubleValue();
                double scale = cfg.numericScale > 0 ? cfg.numericScale : 1.0;
                return 1.0 - Math.min(1.0, Math.abs(da - db) / scale);
            }
            case ORDINAL -> {
                int ia = cfg.order.indexOf(a.toString());
                int ib = cfg.order.indexOf(b.toString());
                if (ia < 0 || ib < 0) return a.equals(b) ? 1.0 : 0.0;
                int n = cfg.order.size();
                if (n <= 1) return a.equals(b) ? 1.0 : 0.0;
                return 1.0 - (double) Math.abs(ia - ib) / (n - 1);
            }
            case TAXONOMY -> {
                if (a.equals(b)) return 1.0;
                String ga = cfg.memberToGroup.get(a.toString());
                String gb = cfg.memberToGroup.get(b.toString());
                return (ga != null && ga.equals(gb)) ? 0.5 : 0.0;
            }
            default -> {
                return a.equals(b) ? 1.0 : 0.0;
            }
        }
    }

    public double weight(String predicate) {
        PredCfg cfg = cbrPredicates.get(predicate);
        return cfg != null ? cfg.weight : 0.0;
    }
}
