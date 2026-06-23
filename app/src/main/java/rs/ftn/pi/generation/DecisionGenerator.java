package rs.ftn.pi.generation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import rs.ftn.pi.model.CaseFacts;
import rs.ftn.pi.nlp.NlpClient;
import rs.ftn.pi.reasoning.dto.ReasoningResult;
import rs.ftn.pi.reasoning.dto.SentenceProposal;
import rs.ftn.pi.reasoning.dto.SimilarCase;
import rs.ftn.pi.reasoning.dto.ViolatedArticle;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Generisanje nove sudske odluke (Celina 9).
 *
 * Obrazloženje se piše „po ugledu na postojeće odluke":
 *  1. ako je dostupan lokalni LLM (NLP servis + Ollama) — generiše ga jezički model
 *     na osnovu činjenica, primenjenih članova i NAJSLIČNIJIH ranijih presuda (CBR);
 *  2. u suprotnom — šablonski tekst koji citira primenjeni član, rezultat rasuđivanja
 *     po pravilima i slične ranije slučajeve.
 *
 * Rezultat je anotiran Akoma Ntoso XML (kao i ostatak korpusa).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DecisionGenerator {

    private final NlpClient nlpClient;

    public String generate(CaseFacts facts,
                           ReasoningResult ruleResult,
                           ReasoningResult cbrResult,
                           SentenceProposal selectedSentence) {
        log.info("Generisanje nove odluke (Celina 9)");

        String id = UUID.randomUUID().toString().substring(0, 8);
        LocalDate now = LocalDate.now();
        String date = now.toString();
        String displayDate = String.format("%02d.%02d.%04d.", now.getDayOfMonth(), now.getMonthValue(), now.getYear());
        String caseNumber = "PI-" + id.toUpperCase();

        String appliedArticle = "";
        String articleText = "";
        String citation = "";
        if (ruleResult != null && !ruleResult.getViolatedArticles().isEmpty()) {
            ViolatedArticle art = ruleResult.getViolatedArticles().get(0);
            appliedArticle = art.getArticleEId() != null ? art.getArticleEId() : "";
            articleText = art.getText() != null ? art.getText() : "";
            citation = art.getCitation() != null ? art.getCitation() : "";
        }

        String sentenceText = "";
        if (selectedSentence != null && selectedSentence.getType() != null) {
            sentenceText = humanSentence(selectedSentence);
        }

        String description = facts.getDescription() != null && !facts.getDescription().isBlank()
                ? facts.getDescription()
                : "Postupak je pokrenut na osnovu utvrđenog činjeničnog stanja.";
        List<SimilarCase> similar = cbrResult != null && cbrResult.getSimilarCases() != null
                ? cbrResult.getSimilarCases() : List.of();

        // obrazloženje: LLM ako je dostupan, inače šablon (oba "po ugledu na postojeće odluke")
        String obrazlozenje = buildMotivation(facts, ruleResult, similar, selectedSentence, citation);

        String factsSummary = humanizeFacts(facts);
        String similarHtml = similarCasesText(similar);

        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <akomaNtoso xmlns="http://docs.oasis-open.org/legaldocml/ns/akn/3.0">
                  <judgment name="presuda">
                    <meta>
                      <identification source="#sistem">
                        <FRBRWork>
                          <FRBRthis value="/akn/rs/judgment/pi/%s/main"/>
                          <FRBRuri value="/akn/rs/judgment/pi/%s"/>
                          <FRBRalias name="caseNumber" value="%s"/>
                          <FRBRalias name="court" value="Sistem za podrsku odlucivanju"/>
                          <FRBRdate date="%s" name="judgment"/>
                          <FRBRauthor href="#sistem"/>
                          <FRBRcountry value="rs"/>
                        </FRBRWork>
                        <FRBRExpression>
                          <FRBRthis value="/akn/rs/judgment/pi/%s/srp@/main"/>
                          <FRBRuri value="/akn/rs/judgment/pi/%s/srp@"/>
                          <FRBRdate date="%s" name="judgment"/>
                          <FRBRauthor href="#sistem"/>
                          <FRBRlanguage language="srp"/>
                        </FRBRExpression>
                        <FRBRManifestation>
                          <FRBRthis value="/akn/rs/judgment/pi/%s/srp@/main.xml"/>
                          <FRBRuri value="/akn/rs/judgment/pi/%s/srp@.xml"/>
                          <FRBRdate date="%s" name="generation"/>
                          <FRBRauthor href="#sistem"/>
                          <FRBRformat value="application/xml"/>
                        </FRBRManifestation>
                      </identification>
                      <references source="#sistem">
                        <TLCOrganization eId="sistem" href="/akn/ontology/organization/rs/pi-sistem" showAs="Sistem za podrsku odlucivanju"/>
                        <TLCReference eId="kz" href="/akn/rs/act/2005/kz" showAs="Krivicni zakonik"/>
                      </references>
                    </meta>
                    <judgmentBody>
                      <introduction>
                        <p>Predmet broj %s od dana %s.</p>
                        <p>%s</p>
                      </introduction>
                      <background>
                        <p>%s</p>
                      </background>
                      <decision>
                        <p>Okrivljeni se oglasava krivim jer je prekrsio <ref href="/laws/kz#%s">%s</ref>.</p>
                        <p>Izrice se: <b>%s</b></p>
                      </decision>
                      <motivation>
                        %s
                        %s
                      </motivation>
                    </judgmentBody>
                  </judgment>
                </akomaNtoso>
                """.formatted(
                        id, id, caseNumber, date,
                        id, id, date,
                        id, id, date,
                        caseNumber, displayDate, esc(description),
                        esc(factsSummary),
                        articleEIdToAnchor(appliedArticle), esc(citation.isEmpty() ? "navedeni clan KZ" : citation),
                        esc(sentenceText.isEmpty() ? "kazna po oceni suda" : sentenceText),
                        obrazlozenje,
                        similarHtml
        );
    }

    /** Obrazloženje: LLM (ako dostupan) inače šablon — oba se oslanjaju na slične ranije presude. */
    private String buildMotivation(CaseFacts facts, ReasoningResult ruleResult,
                                   List<SimilarCase> similar, SentenceProposal sentence, String citation) {
        String prompt = buildPrompt(facts, ruleResult, similar, sentence, citation);
        String llm = nlpClient.generateDecisionText(prompt);
        if (llm != null) {
            log.info("Obrazloženje generisano LLM-om.");
            StringBuilder sb = new StringBuilder();
            for (String para : llm.split("\\n\\s*\\n")) {
                if (!para.isBlank()) sb.append("<p>").append(esc(para.trim())).append("</p>\n        ");
            }
            return sb.toString();
        }
        // šablon
        String expl = ruleResult != null && ruleResult.getExplanation() != null
                ? ruleResult.getExplanation() : "Na osnovu utvrdjenih cinjenica i primenjenih pravnih normi.";
        return "<p>" + esc(expl) + "</p>";
    }

    private String buildPrompt(CaseFacts facts, ReasoningResult ruleResult,
                               List<SimilarCase> similar, SentenceProposal sentence, String citation) {
        StringBuilder sb = new StringBuilder();
        sb.append("Napiši obrazloženje presude za sledeći slučaj.\n");
        if (facts.getDescription() != null && !facts.getDescription().isBlank()) {
            sb.append("Opis: ").append(facts.getDescription()).append("\n");
        }
        sb.append("Utvrđene činjenice: ").append(humanizeFacts(facts)).append("\n");
        if (ruleResult != null && !ruleResult.getViolatedArticles().isEmpty()) {
            sb.append("Prekršeni članovi: ");
            List<String> cs = new ArrayList<>();
            for (ViolatedArticle a : ruleResult.getViolatedArticles()) cs.add(a.getCitation());
            sb.append(String.join(", ", cs)).append("\n");
        }
        if (sentence != null) sb.append("Izrečena kazna: ").append(humanSentence(sentence)).append("\n");
        if (!similar.isEmpty()) {
            sb.append("Slične ranije presude (radi ujednačenosti kaznene politike): ");
            List<String> sc = new ArrayList<>();
            for (SimilarCase s : similar) {
                String out = s.getSourceJudgmentId();
                if (s.getSentence() != null && s.getSentence().getProposedMonths() != null) {
                    out += " (" + s.getSentence().getProposedMonths() + " meseci)";
                }
                sc.add(out);
            }
            sb.append(String.join("; ", sc)).append("\n");
        }
        return sb.toString();
    }

    // Boolean predikat -> pravna formulacija (uključuje se samo kada je tačno).
    private static final Map<String, String> BOOL_PHRASES = Map.ofEntries(
            Map.entry("violatedEnvironmentalRegs", "kršio propise o zaštiti životne sredine"),
            Map.entry("failedToTakeProtectiveMeasures", "nije preduzeo propisane mere zaštite"),
            Map.entry("unauthorizedConstruction", "dozvolio izgradnju odnosno stavljanje u pogon objekata koji zagađuju životnu sredinu"),
            Map.entry("damagedProtectionEquipment", "oštetio objekte i uređaje za zaštitu životne sredine"),
            Map.entry("destroyedProtectedNaturalAsset", "uništio odnosno oštetio posebno zaštićeno prirodno dobro"),
            Map.entry("illegalSpeciesTraffic", "protivpravno iznosio zaštićenu biljnu odnosno životinjsku vrstu"),
            Map.entry("dangerousSubstanceAction", "postupao sa opasnim materijama protivno propisima"),
            Map.entry("officialPositionAbuse", "uz zloupotrebu službenog položaja"),
            Map.entry("organizesCrime", "organizovao vršenje dela sa opasnim materijama"),
            Map.entry("unauthorizedNuclearFacility", "nedozvoljeno gradio nuklearno postrojenje"),
            Map.entry("deniedEnvironmentalInfo", "uskratio podatke o stanju životne sredine"),
            Map.entry("killsOrAbusesAnimal", "ubio odnosno zlostavljao životinju"),
            Map.entry("largerNumberOrProtectedAnimals", "u odnosu na veći broj životinja odnosno posebno zaštićenu životinjsku vrstu"),
            Map.entry("protectedSpecies", "radi se o posebno zaštićenoj vrsti"),
            Map.entry("organizesAnimalFights", "iz koristoljublja organizovao borbe između životinja"),
            Map.entry("spreadsContagiousDisease", "nije postupao po merama za suzbijanje zaraznih bolesti životinja odnosno biljaka"),
            Map.entry("causedAnimalDeathOrDamage", "usled čega je nastupilo uginuće životinja odnosno druga znatna šteta"),
            Map.entry("negligentVetHelp", "nesavesno pružio veterinarsku pomoć"),
            Map.entry("producesHarmfulVetProduct", "proizvodio odnosno stavljao u promet štetna sredstva za lečenje životinja"),
            Map.entry("pollutesAnimalFoodWater", "zagadio hranu odnosno vodu za ishranu i napajanje životinja"),
            Map.entry("devastatesForest", "protivno propisima vršio seču odnosno pustošenje šume"),
            Map.entry("protectedForest", "u zaštićenoj šumi odnosno nacionalnom parku"),
            Map.entry("forestTheftOverOneM3", "radi krađe oborio drvo u količini većoj od jednog kubnog metra"),
            Map.entry("intentToSellWood", "u nameri da oboreno drvo proda"),
            Map.entry("overFiveCubicMeters", "u količini većoj od pet kubnih metara"),
            Map.entry("illegalHuntingClosedSeason", "lovio divljač za vreme lovostaja odnosno na području gde je lov zabranjen"),
            Map.entry("huntingForeignGround", "neovlašćeno lovio na tuđem lovištu"),
            Map.entry("largeGame", "u odnosu na krupnu divljač"),
            Map.entry("prohibitedGameOrMassDestruction", "lovio divljač čiji je lov zabranjen odnosno sredstvima kojima se divljač masovno uništava"),
            Map.entry("illegalFishingClosedSeason", "lovio ribu za vreme lovostaja odnosno u vodama u kojima je ribolov zabranjen"),
            Map.entry("fishingHarmfulMeans", "ribu lovio sredstvima štetnim za razmnožavanje vodenih životinja"),
            Map.entry("usesExplosives", "upotrebom eksploziva odnosno električne struje"),
            Map.entry("fishingHighValueOrQuantity", "u odnosu na ribu veće biološke vrednosti odnosno u većoj količini"),
            Map.entry("priorConviction", "ranije osuđivan"),
            Map.entry("remediedDamage", "naknadno otklonio prouzrokovanu štetu")
    );

    private static String enumPhrase(String pred, String val) {
        return switch (pred) {
            case "intent" -> switch (val) {
                case "UMISLJAJ" -> "delo je izvršeno sa umišljajem";
                case "NEHAT" -> "delo je izvršeno iz nehata";
                default -> null;
            };
            case "pollutionExtent" -> "VECA_MERA".equals(val) ? "zagađenje je izvršeno u većoj meri" : null;
            case "pollutionScope" -> "SIRI_PROSTOR".equals(val) ? "zagađenje je nastalo na širem prostoru" : null;
            case "ecologicalDamage" -> "VELIKIH_RAZMERA".equals(val)
                    ? "prouzrokovana je ekološka šteta velikih razmera" : null;
            case "damageExtent" -> "VELIKA".equals(val) ? "prouzrokovana šteta je velika" : null;
            case "damageRemovalDifficulty" -> "ZAHTEVNO".equals(val)
                    ? "otklanjanje štete zahteva duže vreme i velike troškove" : null;
            case "perpetratorType" -> switch (val) {
                case "SLUZBENO_LICE" -> "učinilac je postupao u svojstvu službenog lica";
                case "ODGOVORNO_LICE" -> "učinilac je postupao u svojstvu odgovornog lica";
                default -> null;
            };
            case "pollutionTarget" -> switch (val) {
                case "VAZDUH" -> "predmet zagađenja je vazduh";
                case "VODA" -> "predmet zagađenja je voda";
                case "ZEMLJISTE", "TLO" -> "predmet zagađenja je zemljište";
                case "SUMA" -> "delo je izvršeno u odnosu na šumu";
                case "VISESTRUKO" -> "zagađeno je više činilaca životne sredine";
                default -> null;
            };
            case "substanceType" -> switch (val) {
                case "HEMIJSKI_OTPAD" -> "radi se o hemijskom otpadu";
                case "RADIOAKTIVNI_OTPAD" -> "radi se o radioaktivnom otpadu";
                case "KOMUNALNI_OTPAD" -> "radi se o komunalnom otpadu";
                case "NAFTNI_DERIVATI" -> "radi se o naftnim derivatima";
                case "OPASNE_MATERIJE" -> "radi se o opasnim materijama";
                case "DRVO" -> "predmet dela je drvna masa";
                case "ZIVOTINJE_RIBE" -> "predmet dela su životinje odnosno ribe";
                default -> null;
            };
            default -> null;
        };
    }

    /** Pretvara strukturirane činjenice u čitljivu pravnu formulaciju (Celina 9). */
    private String humanizeFacts(CaseFacts facts) {
        if (facts.getFacts() == null || facts.getFacts().isEmpty()) {
            return "U postupku nije utvrđeno strukturirano činjenično stanje.";
        }
        List<String> num = new ArrayList<>();   // numeričke
        List<String> phrases = new ArrayList<>();
        for (Map.Entry<String, Object> e : facts.getFacts().entrySet()) {
            String pred = e.getKey();
            Object v = e.getValue();
            if (v == null) continue;
            String s = v.toString().trim();
            if (s.isEmpty()) continue;

            if (isTrue(v)) {
                String ph = BOOL_PHRASES.get(pred);
                if (ph != null) phrases.add(ph);
            } else if ("substanceQuantityM3".equals(pred)) {
                num.add("količina materije iznosi " + stripDot(s) + " m³");
            } else if ("forestAreaHa".equals(pred)) {
                num.add("zahvaćena površina iznosi " + stripDot(s) + " ha");
            } else if (!isFalse(v)) {
                String ph = enumPhrase(pred, s.toUpperCase());
                if (ph != null) phrases.add(ph);
            }
        }
        phrases.addAll(num);
        if (phrases.isEmpty()) {
            return "U postupku nije utvrđeno relevantno činjenično stanje za primenu inkriminacija iz Glave XXIV KZ.";
        }
        String joined = String.join(", ", phrases);
        String sentence = "Utvrđeno je da je okrivljeni " + joined + ".";
        return sentence.substring(0, 1).toUpperCase() + sentence.substring(1);
    }

    private static boolean isTrue(Object v) {
        return (v instanceof Boolean b && b) || "true".equalsIgnoreCase(String.valueOf(v))
                || "DA".equalsIgnoreCase(String.valueOf(v));
    }

    private static boolean isFalse(Object v) {
        return (v instanceof Boolean b && !b) || "false".equalsIgnoreCase(String.valueOf(v))
                || "NE".equalsIgnoreCase(String.valueOf(v));
    }

    private static String stripDot(String s) {
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }

    private String similarCasesText(List<SimilarCase> similar) {
        if (similar.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("<p>Pri odmeravanju kazne sud je imao u vidu sličnu sudsku praksu: ");
        List<String> cs = new ArrayList<>();
        for (SimilarCase s : similar) {
            String out = s.getSourceJudgmentId();
            if (s.getSentence() != null && s.getSentence().getProposedMonths() != null) {
                out += " — " + s.getSentence().getProposedMonths() + " meseci";
            }
            cs.add(out);
        }
        sb.append(esc(String.join("; ", cs))).append(".</p>");
        return sb.toString();
    }

    private String humanSentence(SentenceProposal s) {
        String t = s.getType() != null ? s.getType().name() : "";
        if (s.getProposedMonths() != null && s.getType() == SentenceProposal.SentenceType.ZATVOR) {
            return "kazna zatvora u trajanju od " + s.getProposedMonths() + " meseci";
        }
        if (s.getType() == SentenceProposal.SentenceType.NOVCANA) return "novčana kazna";
        if (s.getType() == SentenceProposal.SentenceType.USLOVNA) {
            return "uslovna osuda" + (s.getProposedMonths() != null ? " (" + s.getProposedMonths() + " meseci)" : "");
        }
        return t.isEmpty() ? "" : t;
    }

    private String articleEIdToAnchor(String eId) {
        if (eId == null || eId.isBlank()) return "";
        // za navigaciju ka zakonu koristimo nivo člana (art_NNN)
        int idx = eId.indexOf("__");
        return idx > 0 ? eId.substring(0, idx) : eId;
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
