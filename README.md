# Pravna informatika 2025/2026 — Krivična dela protiv životne sredine

Sistem za podršku odlučivanju u krivičnim predmetima vezanim za životnu sredinu (KZ čl. 260–277).

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

### Pokretanje — JEDAN KLIK (IntelliJ)

Aplikacija pri startu sama podiže **Ollama** (lokalni LLM) i **Python NLP servis**,
pa je dovoljno pokrenuti Spring app:

1. **Jednokratni setup** (samo prvi put):
   ```bash
   # PostgreSQL (vidi gore) + Ollama + Python zavisnosti
   #  - instaliraj Ollama: https://ollama.com   (model se povlači automatski pri prvom startu)
   pip install -r nlp-service/requirements.txt   # fastapi, uvicorn, httpx (spaCy je opcioni)
   ```
2. U IntelliJ-u izaberi run konfiguraciju **„Pravna informatika (full stack)"** i klikni ▶ Run.
   - Pokreće `PiApplication`, a ona automatski startuje `ollama serve` (+ `ollama pull mistral`
     u pozadini) i NLP servis (`uvicorn`) ako već ne rade. Otvori <http://localhost:8080>.
   - Pri zaustavljanju (Stop), prateći procesi koje je app pokrenuo se gase.

> Autostart se isključuje sa `app.autostart.enabled=false` (ili env `AUTOSTART=false`) ako
> želiš ručno pokretanje. Model se bira sa `OLLAMA_MODEL` (npr. `llama3.2:3b` za slabiji hardver).

### Pokretanje — ručno (alternativa)
```bash
# 1. (opciono, ako je autostart isključen) NLP servis
cd nlp-service && pip install -r requirements.txt && uvicorn main:app --port 8000
# 2. Spring app
cd app && mvn spring-boot:run    # http://localhost:8080
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

Sve celine (1–9) su implementirane:
- **Celina 1/3** — Akoma Ntoso zakon + LegalRuleML pravila (čl. 260–277).
- **Celina 5** — rasuđivanje po pravilima alatom **dr-device** (CLIPS), 39 normi / 55 pravila.
- **Celina 2** — anotirane sudske odluke (37 u domenu, van-domena izmešteno u `data/judgments/_excluded/`).
- **Celina 4** — NLP ekstrakcija (regex + lokalni LLM/Ollama) + ručno ažuriranje izvučenih podataka.
- **Celina 6** — CBR (funkcije sličnosti iz rečnika + kNN retrieval).
- **Celina 7/8/9** — pregled + navigacija, kombinovano rasuđivanje, generisanje odluka (lokalni LLM, fallback šablon).
