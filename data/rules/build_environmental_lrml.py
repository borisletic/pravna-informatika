#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Generator za data/rules/environmental_rules.lrml (DR-DEVICE LegalRuleML dijalekt).

VLASNIK: Clan 1 (Legal Modeling) — Celina 3 (LegalRuleML) + Celina 5 (rasudjivanje, dr-device).

Pravila za krivicna dela protiv zivotne sredine, KZ Republike Srbije cl. 260-268.
Ovaj .lrml je JEDINI izvor pravila. Iz njega se XSLT-om (dr-device dijalekt)
generise rulebase.ruleml pa rulebase.clp koji dr-device (CLIPS) izvrsava.
Nema vise rucnog .drl.

VAZNO — disjunkcije: dr-device.xsl NE prevodi <Or> u telu pravila (podrzava samo
And/Atom/Neg). Zato se svako pravilo sa "ILI" uslovom RAZBIJA na vise pravila sa
istim zakljuckom (kao sto i referentni primer koristi ps220_3_a / ps220_3_b).
Tako disjunkcija ostaje vidljiva u modelu (vise PrescriptiveStatement-a istog
zakljucka), a prevod u CLIPS je korektan.

Pokretanje:  python data/rules/build_environmental_lrml.py
"""
import io, os, itertools

NS_HEADER = (
    '<lrml:LegalRuleML\n'
    '  xmlns:lrml="http://docs.oasis-open.org/legalruleml/ns/v1.0/"\n'
    '  xmlns:xs="http://www.w3.org/2001/XMLSchema"\n'
    '  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"\n'
    '  xmlns:ruleml="http://ruleml.org/spec">\n'
)

# ---- definicije normi -------------------------------------------------------
# Svaka norma: (zakljucak, (min,max) meseci, opis, [grupe-uslova]).
# grupa-uslova je ILI ("f",pred,val) ILI ("or",[("f",pred,val), ...]).
# ILI-grupe se razbijaju na vise pravila (kartezijanski proizvod).
NORMS = [
    ("violates_260_1", (6, 60), "cl. 260 st. 1 — Zagadjenje, osnovni oblik (umisljaj)",
     [("f", "violatedEnvironmentalRegs", "true"),
      ("or", [("f", "pollutionExtent", "VECA_MERA"), ("f", "pollutionScope", "SIRI_PROSTOR")]),
      ("f", "intent", "UMISLJAJ")]),

    ("violates_260_2", (0, 24), "cl. 260 st. 2 — Zagadjenje iz nehata",
     [("f", "violatedEnvironmentalRegs", "true"),
      ("or", [("f", "pollutionExtent", "VECA_MERA"), ("f", "pollutionScope", "SIRI_PROSTOR")]),
      ("f", "intent", "NEHAT")]),

    # Kvalifikovani oblici se izvode iz CINJENICA (ne iz violates_260_1/2) da bi se
    # izbegao defeasible ciklus; prvenstvo nad osnovnim oblikom daje OverrideStatement.
    ("violates_260_3", (12, 96), "cl. 260 st. 3 — Kvalifikovani oblik (umisljaj + steta velikih razmera)",
     [("f", "violatedEnvironmentalRegs", "true"),
      ("or", [("f", "pollutionExtent", "VECA_MERA"), ("f", "pollutionScope", "SIRI_PROSTOR")]),
      ("f", "intent", "UMISLJAJ"),
      ("or", [("f", "ecologicalDamage", "VELIKIH_RAZMERA"), ("f", "damageRemovalDifficulty", "ZAHTEVNO")])]),

    ("violates_260_4", (6, 60), "cl. 260 st. 4 — Kvalifikovani oblik (nehat + steta velikih razmera)",
     [("f", "violatedEnvironmentalRegs", "true"),
      ("or", [("f", "pollutionExtent", "VECA_MERA"), ("f", "pollutionScope", "SIRI_PROSTOR")]),
      ("f", "intent", "NEHAT"),
      ("or", [("f", "ecologicalDamage", "VELIKIH_RAZMERA"), ("f", "damageRemovalDifficulty", "ZAHTEVNO")])]),

    ("violates_261_1", (0, 36), "cl. 261 st. 1 — Nepreduzimanje mera zastite",
     [("or", [("f", "perpetratorType", "SLUZBENO_LICE"), ("f", "perpetratorType", "ODGOVORNO_LICE")]),
      ("f", "failedToTakeProtectiveMeasures", "true")]),

    ("violates_262_1", (6, 60), "cl. 262 st. 1 — Protivpravna izgradnja zagadjujucih objekata",
     [("or", [("f", "perpetratorType", "SLUZBENO_LICE"), ("f", "perpetratorType", "ODGOVORNO_LICE")]),
      ("f", "unauthorizedConstruction", "true"),
      ("or", [("f", "pollutionExtent", "VECA_MERA"), ("f", "pollutionScope", "SIRI_PROSTOR")])]),

    ("violates_263_1", (0, 36), "cl. 263 st. 1 — Ostecenje objekata za zastitu zivotne sredine",
     [("f", "damagedProtectionEquipment", "true")]),

    ("violates_265_1", (6, 60), "cl. 265 st. 1 — Unistenje posebno zasticenog prirodnog dobra",
     [("f", "destroyedProtectedNaturalAsset", "true")]),

    ("violates_265_3", (3, 36), "cl. 265 st. 3 — Protivpravno iznosenje zasticene vrste",
     [("f", "illegalSpeciesTraffic", "true")]),

    ("violates_266_1", (6, 60), "cl. 266 st. 1 — Opasne materije, osnovni oblik",
     [("f", "dangerousSubstanceAction", "true")]),

    ("violates_266_2", (12, 96), "cl. 266 st. 2 — Opasne materije + zloupotreba sluzbenog polozaja",
     [("f", "dangerousSubstanceAction", "true"),
      ("f", "officialPositionAbuse", "true")]),

    ("violates_266_5", (36, 120), "cl. 266 st. 5 — Organizovanje vrsenja dela sa opasnim materijama",
     [("f", "dangerousSubstanceAction", "true"),
      ("f", "organizesCrime", "true")]),

    ("violates_267_1", (6, 60), "cl. 267 — Nedozvoljena izgradnja nuklearnog postrojenja",
     [("f", "unauthorizedNuclearFacility", "true")]),

    ("violates_268_1", (0, 12), "cl. 268 — Uskracivanje podataka o stanju zivotne sredine",
     [("f", "deniedEnvironmentalInfo", "true")]),

    # ---- cl. 269-277 (zivotinje, sume, lov, ribolov) ----
    ("violates_269_1", (0, 24), "cl. 269 st. 1 — Ubijanje i zlostavljanje zivotinja",
     [("f", "killsOrAbusesAnimal", "true")]),
    ("violates_269_2", (0, 36), "cl. 269 st. 2 — veci broj / posebno zasticena vrsta",
     [("f", "killsOrAbusesAnimal", "true"),
      ("or", [("f", "largerNumberOrProtectedAnimals", "true"), ("f", "protectedSpecies", "true")])]),
    ("violates_269_3", (6, 36), "cl. 269 st. 3 — organizovanje borbi zivotinja",
     [("f", "organizesAnimalFights", "true")]),

    ("violates_270_1", (0, 24), "cl. 270 st. 1 — Prenosenje zaraznih bolesti (umisljaj)",
     [("f", "spreadsContagiousDisease", "true"), ("f", "intent", "UMISLJAJ")]),
    ("violates_270_3", (0, 36), "cl. 270 st. 3 — nastupilo uginuce / znatna steta",
     [("f", "spreadsContagiousDisease", "true"), ("f", "causedAnimalDeathOrDamage", "true")]),
    ("violates_270_4", (0, 12), "cl. 270 st. 4 — iz nehata",
     [("f", "spreadsContagiousDisease", "true"), ("f", "intent", "NEHAT")]),

    ("violates_271_1", (0, 12), "cl. 271 st. 1 — Nesavesno pruzanje veterinarske pomoci",
     [("f", "negligentVetHelp", "true"), ("f", "intent", "UMISLJAJ")]),
    ("violates_271_2", (0, 6), "cl. 271 st. 2 — iz nehata",
     [("f", "negligentVetHelp", "true"), ("f", "intent", "NEHAT")]),

    ("violates_272_1", (0, 12), "cl. 272 st. 1 — Proizvodnja stetnih sredstava za lecenje zivotinja",
     [("f", "producesHarmfulVetProduct", "true"), ("f", "intent", "UMISLJAJ")]),
    ("violates_272_2", (0, 24), "cl. 272 st. 2 — prouzrokovano uginuce / znatna steta",
     [("f", "producesHarmfulVetProduct", "true"), ("f", "causedAnimalDeathOrDamage", "true")]),
    ("violates_272_3", (0, 6), "cl. 272 st. 3 — iz nehata",
     [("f", "producesHarmfulVetProduct", "true"), ("f", "intent", "NEHAT")]),

    ("violates_273_1", (0, 24), "cl. 273 st. 1 — Zagadjivanje hrane i vode za zivotinje (umisljaj)",
     [("f", "pollutesAnimalFoodWater", "true"), ("f", "intent", "UMISLJAJ")]),
    ("violates_273_3", (0, 36), "cl. 273 st. 3 — prouzrokovano uginuce / znatna steta",
     [("f", "pollutesAnimalFoodWater", "true"), ("f", "causedAnimalDeathOrDamage", "true")]),
    ("violates_273_4", (0, 6), "cl. 273 st. 4 — iz nehata",
     [("f", "pollutesAnimalFoodWater", "true"), ("f", "intent", "NEHAT")]),

    ("violates_274_1", (0, 12), "cl. 274 st. 1 — Pustosenje suma",
     [("f", "devastatesForest", "true")]),
    ("violates_274_2", (3, 36), "cl. 274 st. 2 — u zasticenoj sumi / nacionalnom parku",
     [("f", "devastatesForest", "true"), ("f", "protectedForest", "true")]),

    ("violates_275_1", (0, 12), "cl. 275 st. 1 — Sumska kradja (preko 1 m3)",
     [("f", "forestTheftOverOneM3", "true")]),
    ("violates_275_2", (0, 36), "cl. 275 st. 2 — namera prodaje / preko 5 m3 / zasticena suma",
     [("f", "forestTheftOverOneM3", "true"),
      ("or", [("f", "intentToSellWood", "true"), ("f", "overFiveCubicMeters", "true"),
              ("f", "protectedForest", "true")])]),

    ("violates_276_1", (0, 6), "cl. 276 st. 1 — Nezakonit lov (lovostaj / zabranjeno podrucje)",
     [("f", "illegalHuntingClosedSeason", "true")]),
    ("violates_276_2", (0, 12), "cl. 276 st. 2 — lov na tudjem lovistu",
     [("f", "huntingForeignGround", "true")]),
    ("violates_276_3", (0, 24), "cl. 276 st. 3 — krupna divljac",
     [("f", "huntingForeignGround", "true"), ("f", "largeGame", "true")]),
    ("violates_276_4", (0, 36), "cl. 276 st. 4 — zabranjena divljac / masovno unistavanje",
     [("f", "prohibitedGameOrMassDestruction", "true")]),

    ("violates_277_1", (0, 6), "cl. 277 st. 1 — Nezakonit ribolov (lovostaj / zabranjene vode)",
     [("f", "illegalFishingClosedSeason", "true")]),
    ("violates_277_2", (0, 36), "cl. 277 st. 2 — eksploziv / struja / otrov / masovno",
     [("or", [("f", "fishingHarmfulMeans", "true"), ("f", "usesExplosives", "true")])]),
    ("violates_277_3", (0, 36), "cl. 277 st. 3 — veca bioloska vrednost / veca kolicina",
     [("f", "fishingHighValueOrQuantity", "true")]),
]

# ---- negacije / pobijanja (ne racunaju se u minimum od 10 pravila) ---------
# (negKey, ruleKey, ako_izvedeno, onda_negiraj_zakljucak)
NEGATIONS = [
    ("ps_n_r3",   "rule_n_r3",   "violates_260_3", "violates_260_1"),
    ("ps_n_r4",   "rule_n_r4",   "violates_260_4", "violates_260_2"),
    ("ps_n_r11",  "rule_n_r11",  "violates_266_2", "violates_266_1"),
    ("ps_n_r12a", "rule_n_r12a", "violates_266_5", "violates_266_1"),
    ("ps_n_r12b", "rule_n_r12b", "violates_266_5", "violates_266_2"),
    # kvalifikovani / tezi oblici pobijaju blaze (cl. 269-277)
    ("ps_n_269",  "rule_n_269",  "violates_269_2", "violates_269_1"),
    ("ps_n_270",  "rule_n_270",  "violates_270_3", "violates_270_1"),
    ("ps_n_272",  "rule_n_272",  "violates_272_2", "violates_272_1"),
    ("ps_n_273",  "rule_n_273",  "violates_273_3", "violates_273_1"),
    ("ps_n_274",  "rule_n_274",  "violates_274_2", "violates_274_1"),
    ("ps_n_275",  "rule_n_275",  "violates_275_2", "violates_275_1"),
    ("ps_n_2763", "rule_n_2763", "violates_276_3", "violates_276_2"),
    ("ps_n_2764", "rule_n_2764", "violates_276_4", "violates_276_1"),
    ("ps_n_277",  "rule_n_277",  "violates_277_2", "violates_277_1"),
]


def atom_fact(pred, val):
    return (
        '            <ruleml:Atom>\n'
        f'              <ruleml:Rel iri="lc:{pred}"/>\n'
        '              <ruleml:Var type="lc:defendant">Defendant</ruleml:Var>\n'
        f'              <ruleml:Data xsi:type="xs:string">{val}</ruleml:Data>\n'
        '            </ruleml:Atom>\n'
    )


def atom_inferred(rel):
    return (
        '            <ruleml:Atom>\n'
        f'              <ruleml:Rel>{rel}</ruleml:Rel>\n'
        '              <ruleml:Var type=":defendant">Defendant</ruleml:Var>\n'
        '            </ruleml:Atom>\n'
    )


def expand(groups):
    """Kartezijanski proizvod ILI-grupa -> lista listi pojedinacnih cinjenica."""
    choices = []
    for g in groups:
        if g[0] == "f":
            choices.append([g])
        elif g[0] == "or":
            choices.append(list(g[1]))
        else:
            raise ValueError(g)
    return [list(combo) for combo in itertools.product(*choices)]


def render_rule(ps_key, rule_key, concl, conds, comment):
    body = "".join(atom_fact(c[1], c[2]) for c in conds)
    return (
        f'    <!-- {comment} -->\n'
        f'    <lrml:PrescriptiveStatement key="{ps_key}">\n'
        f'      <ruleml:Rule key=":{rule_key}" closure="universal" strength="defeasible">\n'
        '        <ruleml:if>\n'
        '          <ruleml:And>\n'
        f'{body}'
        '          </ruleml:And>\n'
        '        </ruleml:if>\n'
        '        <ruleml:then>\n'
        '          <ruleml:Atom>\n'
        f'            <ruleml:Rel>{concl}</ruleml:Rel>\n'
        '            <ruleml:Var type=":defendant">Defendant</ruleml:Var>\n'
        '          </ruleml:Atom>\n'
        '        </ruleml:then>\n'
        '      </ruleml:Rule>\n'
        '    </lrml:PrescriptiveStatement>\n\n'
    )


def render_negation(ps, rk, ifrel, negrel):
    return (
        f'    <!-- pobijanje: ako {ifrel} onda NE {negrel} -->\n'
        f'    <lrml:PrescriptiveStatement key="{ps}">\n'
        f'      <ruleml:Rule key=":{rk}" closure="universal" strength="defeasible">\n'
        '        <ruleml:if>\n'
        '          <ruleml:And>\n'
        f'{atom_inferred(ifrel)}'
        '          </ruleml:And>\n'
        '        </ruleml:if>\n'
        '        <ruleml:then>\n'
        '          <ruleml:Negation>\n'
        f'{atom_inferred(negrel)}'
        '          </ruleml:Negation>\n'
        '        </ruleml:then>\n'
        '      </ruleml:Rule>\n'
        '    </lrml:PrescriptiveStatement>\n\n'
    )


def render_penalty(key, rel, unit, val):
    return (
        f'    <lrml:PenaltyStatement key="{key}">\n'
        '      <lrml:SuborderList>\n'
        '        <lrml:Obligation>\n'
        '          <ruleml:Atom>\n'
        f'            <ruleml:Rel iri=":{rel}"/>\n'
        f'            <ruleml:Var>{unit}</ruleml:Var>\n'
        f'            <ruleml:Ind>{val}</ruleml:Ind>\n'
        '          </ruleml:Atom>\n'
        '        </lrml:Obligation>\n'
        '      </lrml:SuborderList>\n'
        '    </lrml:PenaltyStatement>\n'
    )


def main():
    out = io.StringIO()
    out.write('<?xml version="1.0" encoding="UTF-8"?>\n')
    out.write('<!--\n')
    out.write('  Pravila za krivicna dela protiv zivotne sredine (KZ RS, cl. 260-268).\n')
    out.write('  DR-DEVICE LegalRuleML dijalekt. GENERISANO iz build_environmental_lrml.py.\n')
    out.write('  Celina 3 (predstavljanje) + Celina 5 (rasudjivanje alatom dr-device).\n')
    out.write('  14 pravnih normi (cl. 260-268); ILI-uslovi su razbijeni na vise pravila.\n')
    out.write('-->\n')
    out.write(NS_HEADER)
    out.write('\n  <lrml:Statements>\n\n')

    out.write('    <!-- ===================== PRAVILA (cl. 260-268) ===================== -->\n\n')
    concl_first_ps = {}   # zakljucak -> kljuc prvog PS-a (za reparaciju)
    concl_all_ps = {}     # zakljucak -> [svi PS kljucevi] (za override)
    for concl, rng, comment, groups in NORMS:
        short = concl.replace("violates_", "")
        for i, conds in enumerate(expand(groups), start=1):
            ps_key = f"ps_{short}_{i}"
            rule_key = f"rule_{short}_{i}"
            c = comment if i == 1 else f"{comment} (varijanta {i})"
            out.write(render_rule(ps_key, rule_key, concl, conds, c))
            concl_first_ps.setdefault(concl, ps_key)
            concl_all_ps.setdefault(concl, []).append(ps_key)

    out.write('    <!-- ===================== NEGACIJE / POBIJANJA ===================== -->\n\n')
    for ps, rk, ifrel, negrel in NEGATIONS:
        out.write(render_negation(ps, rk, ifrel, negrel))

    out.write('    <!-- ===================== KAZNE (raspon u mesecima) ===================== -->\n\n')
    reparations = []   # (penKey, psKey)
    for concl, (mn, mx), comment, groups in NORMS:
        target_ps = concl_first_ps[concl]
        if mn > 0:
            k = f'pen_{concl}_min'
            out.write(render_penalty(k, 'min_imprisonment', 'Months', mn))
            reparations.append((k, target_ps))
        k = f'pen_{concl}_max'
        out.write(render_penalty(k, 'max_imprisonment', 'Months', mx))
        reparations.append((k, target_ps))
    out.write('\n')

    out.write('    <!-- ============== OVERRIDE (kvalifikovani / tezi oblik pobija blazi) ============== -->\n\n')
    for ps, rk, ifrel, negrel in NEGATIONS:
        for under_ps in concl_all_ps.get(negrel, []):
            out.write(f'    <lrml:OverrideStatement><lrml:Override under="#{under_ps}" over="#{ps}"/></lrml:OverrideStatement>\n')
    out.write('\n')

    out.write('    <!-- ===================== REPARACIJE (pravilo -> kazna) ===================== -->\n\n')
    for penKey, psKey in reparations:
        out.write(f'    <lrml:ReparationStatement><lrml:Reparation><lrml:appliesPenalty keyref="#{penKey}"/><lrml:toPrescriptiveStatement keyref="#{psKey}"/></lrml:Reparation></lrml:ReparationStatement>\n')

    out.write('\n  </lrml:Statements>\n</lrml:LegalRuleML>\n')

    here = os.path.dirname(os.path.abspath(__file__))
    path = os.path.join(here, 'environmental_rules.lrml')
    with open(path, 'w', encoding='utf-8') as f:
        f.write(out.getvalue())

    export_classes = [n[0] for n in NORMS] + ['min_imprisonment', 'max_imprisonment']
    total_ps = sum(len(expand(n[3])) for n in NORMS)
    print('WROTE', path)
    print(f'NORMS={len(NORMS)}  EXPANDED_RULES={total_ps}  NEGATIONS={len(NEGATIONS)}')
    print('EXPORT_CLASSES:', ' '.join(export_classes))


if __name__ == '__main__':
    main()
