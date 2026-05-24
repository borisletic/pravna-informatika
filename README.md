# Pravna informatika 2025/2026 — Krivična dela protiv životne sredine

Sistem za podršku odlučivanju u krivičnim predmetima vezanim za životnu sredinu (KZ čl. 260–268).

## Tim i podela

| Član | Uloga | Celine | Glavna grana |
|------|-------|--------|--------------|
| Član 1 | Legal Modeling | 1, 3, 5 | `feature/legal-*` |
| Član 2 | NLP & Data | 2, 4, 6 | `feature/nlp-*`, `feature/data-*` |
| Član 3 | Application | 7, 8, 9 | `feature/app-*` |

## Brzi start

### Preduslovi
- Java 17+
- Maven 3.8+
- Python 3.10+
- PostgreSQL 15+ (lokalno instaliran)
- Node.js 18+ (samo ako budete radili dodatne JS alate)

### Setup PostgreSQL (jednokratno)
```bash
# Kreirati bazu i korisnika
sudo -u postgres psql
CREATE DATABASE pravna_informatika;
CREATE USER pi_user WITH PASSWORD 'pi_password';
GRANT ALL PRIVILEGES ON DATABASE pravna_informatika TO pi_user;
\q
```

### Pokretanje aplikacije
```bash
# 1. NLP servis (terminal 1)
cd nlp-service
python -m venv venv
source venv/bin/activate    # Windows: venv\Scripts\activate
pip install -r requirements.txt
uvicorn main:app --reload --port 8000

# 2. Spring app (terminal 2)
cd app
mvn spring-boot:run
# Otvori http://localhost:8080
```

## Struktura repozitorijuma

```
.
├── data/                 # Svi podaci (XML, CSV, šeme)
│   ├── laws/             # Akoma Ntoso XML zakona (Celina 1)
│   ├── judgments/        # Akoma Ntoso XML odluka (Celina 2)
│   ├── rules/            # LegalRuleML pravila (Celina 3)
│   ├── cases/            # CBR baza (CSV) (Celina 6)
│   └── schemas/          # XSD šeme i rečnik činjenica
├── nlp-service/          # Python FastAPI NLP mikroservis (Celina 4)
├── app/                  # Spring Boot aplikacija (Celine 5, 7, 8, 9)
└── docs/                 # Dodatna dokumentacija
```

## Ugovori između članova tima

Pre nego što počnete da radite, **PROČITAJTE**:
- `docs/INTEGRATION_CONTRACTS.md` — interfejsi i formati koji se ne menjaju bez dogovora
- `data/schemas/predicate_dictionary.yaml` — rečnik činjenica (Član 1 ↔ Član 2)

## Git pravila

- **Main grana**: uvek mora da se buildi (`mvn clean install` prolazi)
- **Feature grane**: `feature/<oblast>-<kratak-opis>`, npr. `feature/legal-akn-zakon`, `feature/nlp-metadata`
- Pre merge u main: pull request + bar jedan review
- Commit poruke: `[CELINA-X] kratak opis` (npr. `[CELINA-3] dodato pravilo R1 za čl. 260 st. 1`)

## Status implementacije

Svaka celina je trenutno SKELET sa `TODO` markerima. Pretražite repo sa `grep -r "TODO"` da nađete svoje delove.
