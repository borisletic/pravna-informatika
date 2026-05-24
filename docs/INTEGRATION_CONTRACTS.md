# Ugovori između članova tima

Ovo su **interfejsi koji se ne menjaju** bez dogovora cele ekipe. Svaka izmena = poruka u grupi + ažuriranje ovog fajla.

## 1. Rečnik činjenica (Član 1 ↔ Član 2)

**Fajl:** `data/schemas/predicate_dictionary.yaml`

Sve činjenice koje se pojavljuju u:
- LegalRuleML pravilima (Celina 3)
- NLP ekstrakciji (Celina 4)
- CBR atributima (Celina 6)

...MORAJU biti definisane u tom fajlu sa identičnim nazivom, tipom i jedinicom.

**Pravilo:** Ako Član 1 hoće novo polje u pravilu → prvo dodaj u predicate_dictionary.yaml, pa onda piši pravilo.
Ako Član 2 hoće da NLP izvuče novu činjenicu → prvo dodaj u predicate_dictionary.yaml, pa onda implementiraj.

## 2. Akoma Ntoso šema referenci

Svi `eId` i `href` koriste isti format:

```
/akn/rs/act/kz/main/chp_XXIV/art_260/para_1
/akn/rs/judgment/2019/k-145/main
```

Interno u istom dokumentu: `#art_260__para_1`

## 3. NLP REST ugovor (Član 2 ↔ Član 3)

**Endpoint:** `POST http://localhost:8000/extract`

**Request:**
```json
{
  "text": "Pun tekst odluke ili opis slučaja...",
  "documentType": "JUDGMENT | CASE_DESCRIPTION",
  "options": {
    "includeMetadata": true,
    "includeFacts": true,
    "language": "sr"
  }
}
```

**Response:**
```json
{
  "metadata": {
    "caseNumber": "К-145/2019",
    "court": "Виши суд у Београду",
    "date": "2019-04-12",
    "parties": [{"role": "OKR", "initials": "М.П."}],
    "judges": ["Ј.Ј.", "М.М."],
    "recorder": "А.А."
  },
  "facts": [
    {
      "predicate": "substanceQuantityM3",
      "value": 12.5,
      "confidence": 0.91,
      "sourceSpan": {"start": 1240, "end": 1252}
    }
  ]
}
```

**Pravilo:** Član 2 prvo pravi mock koji vraća fiksni JSON, da Član 3 može da razvija paralelno.

## 4. Reasoning Result (Član 1 ↔ Član 3)

Java klasa `ReasoningResult` u `rs.ftn.pi.reasoning.dto`. Vraćaju je i `RuleReasoner.reason()` i `CaseReasoner.reason()`.

Polja su definisana u kodu — pogledaj `app/src/main/java/rs/ftn/pi/reasoning/dto/ReasoningResult.java`.

**Pravilo:** Menjanje strukture = sinhronizacija sa Članom 3 (UI binding).

## 5. CBR query format (Član 2 ↔ Član 3)

Član 3 šalje `CaseFacts` objekat. Član 2 (CaseReasoner) interno konvertuje u jColibri `Description`.

`CaseFacts` je samo `Map<String, Object>` gde su ključevi predikati iz `predicate_dictionary.yaml`.

## 6. Verzionisanje šeme baze

PostgreSQL šema se menja preko Flyway migracija u `app/src/main/resources/db/migration/`.

Naming: `V<broj>__<opis>.sql`, npr. `V1__init_cases.sql`, `V2__add_decision_column.sql`.

**Pravilo:** Nikad ne menjati postojeću migraciju nakon merge-a u main. Uvek nova.
