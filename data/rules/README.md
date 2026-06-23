# Pravila i rasuđivanje (Celina 3 + Celina 5)

## Jedini izvor pravila: `environmental_rules.lrml`

Pravila za krivična dela protiv životne sredine (KZ RS, **čl. 260–277**, cela
Glava XXIV) modelovana su **isključivo** u LegalRuleML formatu u
[environmental_rules.lrml](environmental_rules.lrml). Nema više `.drl` fajla —
Drools je uklonjen. Rasuđivanje radi alat **dr-device** (CLIPS defeasible logic
reasoner), kako traži specifikacija Celine 5.

`environmental_rules.lrml` se generiše skriptom [build_environmental_lrml.py](build_environmental_lrml.py),
koja drži **39 pravnih normi** (čl. 260–277) na jednom mestu i obezbeđuje
konzistentnost (pravila, kazne, pobijanja). Posle razbijanja „ILI" uslova
dobija se 55 dr-device pravila + 14 negacija/override-a. Ručno editovanje `.lrml`-a je moguće, ali je preporučeno menjati
skriptu pa regenerisati:

```
python data/rules/build_environmental_lrml.py
```

## Tok rasuđivanja (radi ga `DrDeviceReasoner`)

```
environmental_rules.lrml                     (LegalRuleML — izvor)
        │  XSLT: app/.../dr-device/lrml2ruleml_environmental.xsl  (Saxon)
        ▼
dr-device/rulebase.ruleml                    (DR-DEVICE RuleML 0.91)
        │  XSLT: dr-device/XSL/dr-device.xsl                      (Saxon)
        ▼
dr-device/rulebase.clp                       (CLIPS native)

CaseFacts ──► dr-device/facts.rdf + facts.n3 (RDF činjenice; N-Triples se daju
                                              direktno da se zaobiđe ARP parser)
        │
        ▼
CLIPSDOS + dr-device  ──►  export.rdf (dokazani zaključci) + proof.ruleml
        │
        ▼
ReasoningResult (prekršeni članovi + raspon kazne + objašnjenje)
```

Koraci LRML→RuleML→CLIPS se rade jednom, pri startu aplikacije. Generisanje
činjenica i pokretanje CLIPS-a se rade pri svakom rasuđivanju.

## Modelske napomene (dijalekt dr-device-a)

- **Činjenice slučaja**: `<ruleml:Rel iri="lc:PREDIKAT"/>` + `Var(type=lc:defendant)` + `Data`.
  Svi predikati moraju postojati kao slotovi klase `lc:case` u `facts.rdf` (zato
  `DrDeviceReasoner` uvek upisuje pun skup predikata sa podrazumevanim vrednostima).
- **Izvedene relacije** (zaključci): `<ruleml:Rel>violates_260_3</ruleml:Rel>`.
- **Disjunkcije (ILI)**: `dr-device.xsl` ne prevodi `<Or>` u telu pravila, pa se svaki
  „ILI" uslov **razbija na više pravila** sa istim zaključkom (kao u referentnom
  primeru `ps220_3_a` / `ps220_3_b`). 39 normi → 55 pravila.
- **Kazne**: `lrml:PenaltyStatement` → `min_imprisonment` / `max_imprisonment` (meseci),
  povezane sa pravilima preko `lrml:ReparationStatement`.
- **Pobijanje (defeasible)**: `lrml:OverrideStatement` čini teži oblik nadređenim
  blažem — npr. kvalifikovani oblik čl. 260 st. 3 pobija osnovni st. 1
  (`violates_260_1` postaje `defeasibly-proven-negative`). Kvalifikovani oblici se
  izvode iz **činjenica**, ne iz osnovnog zaključka, da bi se izbegao defeasible ciklus.

## Putanje / konfiguracija

`app/src/main/resources/application.yml`:

```yaml
app:
  reasoning:
    rules-file: ${app.data-dir}/rules/environmental_rules.lrml
  dr-device:
    enabled: ${DR_DEVICE_ENABLED:true}
    home: ${DR_DEVICE_HOME:./dr-device}   # resolver probava i ../dr-device
    timeout-seconds: 90
```

Ako dr-device nije dostupan (npr. ne-Windows mašina), `DrDeviceReasoner` vraća
mock rezultat i loguje upozorenje umesto da obori aplikaciju.
