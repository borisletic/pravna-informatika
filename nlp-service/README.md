# NLP servis

Python FastAPI mikroservis za ekstrakciju metapodataka i pravnih činjenica iz sudskih odluka.

**Vlasnik:** Član 2 (NLP & Data)
**Celina:** 4

## Pokretanje

```bash
# 1. Virtual env
python -m venv venv
source venv/bin/activate   # Windows: venv\Scripts\activate

# 2. Instalacija
pip install -r requirements.txt

# 3. Start
uvicorn main:app --reload --port 8000
```

Endpoint dostupan na http://localhost:8000. Swagger UI: http://localhost:8000/docs

## Testovi

```bash
pytest
```

## Struktura

```
nlp-service/
├── main.py                      FastAPI aplikacija, endpoint /extract
├── extractors/
│   ├── metadata_extractor.py    Ekstrakcija broja predmeta, stranaka, datuma
│   └── fact_extractor.py        Ekstrakcija činjenica (količine, tipovi, ...)
├── patterns/                    (rezervisano za regex/leksičke pattern fajlove)
└── tests/
    └── test_extract.py
```

## Šta još treba uraditi

1. Zameniti regex-e u `metadata_extractor.py` pravim NER-om (classla).
2. U `fact_extractor.py` dodati:
   - `damageExtent` klasifikaciju (verovatno fine-tuned BERTić)
   - `protectedSpecies`, `usesExplosives` za delove o lovu/ribolovu
   - `remediedDamage` (semantički zahtevno)
3. Dodati `confidence` za sve pravo (ne samo fiksirane brojeve).
4. Pre integracije, proširiti tests/ - bar 5 testova po predikatu.

## Ugovor sa Spring app

`POST /extract` - videti `docs/INTEGRATION_CONTRACTS.md` (sekcija 3).
