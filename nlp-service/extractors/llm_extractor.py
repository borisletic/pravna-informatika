"""
llm_extractor.py — ekstrakcija pravnih činjenica LOKALNIM LLM-om preko Ollama.
VLASNIK: Član 2. CELINA: 4.

Besplatno i bez API ključa: koristi lokalni Ollama server (npr. `ollama run mistral`
ili neki manji model 1–3B kao llama3.2:3b / qwen2.5:3b). Ako Ollama nije pokrenut,
funkcija vraća [] pa /extract pada nazad na regex ekstrakciju.

Konfiguracija (env):
  OLLAMA_URL    (default http://localhost:11434)
  OLLAMA_MODEL  (default mistral)
"""
import os
import json
import httpx

OLLAMA_URL = os.environ.get("OLLAMA_URL", "http://localhost:11434")
OLLAMA_MODEL = os.environ.get("OLLAMA_MODEL", "mistral")
TIMEOUT = float(os.environ.get("OLLAMA_TIMEOUT", "120"))

# Whitelist — sve van ovoga LLM ne sme da vrati (sprečava halucinacije).
ENUM_VALUES = {
    "pollutionTarget": ["VAZDUH", "VODA", "ZEMLJISTE", "VISESTRUKO", "SUMA"],
    "pollutionExtent": ["MANJA_MERA", "VECA_MERA"],
    "pollutionScope": ["UZI_PROSTOR", "SIRI_PROSTOR"],
    "intent": ["UMISLJAJ", "NEHAT"],
    "ecologicalDamage": ["NEMA", "OBICNA", "VELIKIH_RAZMERA"],
    "damageRemovalDifficulty": ["JEDNOSTAVNO", "ZAHTEVNO"],
    "perpetratorType": ["OBICAN", "SLUZBENO_LICE", "ODGOVORNO_LICE"],
    "substanceType": ["HEMIJSKI_OTPAD", "RADIOAKTIVNI_OTPAD", "KOMUNALNI_OTPAD",
                      "NAFTNI_DERIVATI", "OPASNE_MATERIJE", "DRVO", "ZIVOTINJE_RIBE", "OSTALO"],
}
NUM_PREDS = ["substanceQuantityM3", "forestAreaHa"]
BOOL_PREDS = [
    "violatedEnvironmentalRegs", "failedToTakeProtectiveMeasures", "unauthorizedConstruction",
    "damagedProtectionEquipment", "destroyedProtectedNaturalAsset", "illegalSpeciesTraffic",
    "dangerousSubstanceAction", "officialPositionAbuse", "organizesCrime",
    "unauthorizedNuclearFacility", "deniedEnvironmentalInfo",
    "killsOrAbusesAnimal", "largerNumberOrProtectedAnimals", "protectedSpecies",
    "organizesAnimalFights", "spreadsContagiousDisease", "causedAnimalDeathOrDamage",
    "negligentVetHelp", "producesHarmfulVetProduct", "pollutesAnimalFoodWater",
    "devastatesForest", "protectedForest", "forestTheftOverOneM3", "intentToSellWood",
    "overFiveCubicMeters", "illegalHuntingClosedSeason", "huntingForeignGround", "largeGame",
    "prohibitedGameOrMassDestruction", "illegalFishingClosedSeason", "fishingHarmfulMeans",
    "usesExplosives", "fishingHighValueOrQuantity", "priorConviction", "remediedDamage",
]

SYSTEM_PROMPT = """Ti si pravni NLP sistem za krivična dela protiv životne sredine (KZ Srbije, čl. 260-277).
Iz teksta izvuci činjenice i vrati ISKLJUČIVO JSON objekat (bez objašnjenja).
Koristi samo dole navedene ključeve. Ako se činjenica ne pominje, izostavi ključ.

BOOLEAN ključevi (vrednost true samo ako tekst to potvrđuje):
violatedEnvironmentalRegs, failedToTakeProtectiveMeasures, unauthorizedConstruction,
damagedProtectionEquipment, destroyedProtectedNaturalAsset, illegalSpeciesTraffic,
dangerousSubstanceAction, officialPositionAbuse, organizesCrime, unauthorizedNuclearFacility,
deniedEnvironmentalInfo, killsOrAbusesAnimal, largerNumberOrProtectedAnimals, protectedSpecies,
organizesAnimalFights, spreadsContagiousDisease, causedAnimalDeathOrDamage, negligentVetHelp,
producesHarmfulVetProduct, pollutesAnimalFoodWater, devastatesForest, protectedForest,
forestTheftOverOneM3, intentToSellWood, overFiveCubicMeters, illegalHuntingClosedSeason,
huntingForeignGround, largeGame, prohibitedGameOrMassDestruction, illegalFishingClosedSeason,
fishingHarmfulMeans, usesExplosives, fishingHighValueOrQuantity, priorConviction, remediedDamage

ENUM ključevi (tačno jedna dozvoljena vrednost):
pollutionTarget: VAZDUH|VODA|ZEMLJISTE|VISESTRUKO|SUMA
pollutionExtent: MANJA_MERA|VECA_MERA
pollutionScope: UZI_PROSTOR|SIRI_PROSTOR
intent: UMISLJAJ|NEHAT
ecologicalDamage: NEMA|OBICNA|VELIKIH_RAZMERA
damageRemovalDifficulty: JEDNOSTAVNO|ZAHTEVNO
perpetratorType: OBICAN|SLUZBENO_LICE|ODGOVORNO_LICE
substanceType: HEMIJSKI_OTPAD|RADIOAKTIVNI_OTPAD|KOMUNALNI_OTPAD|NAFTNI_DERIVATI|OPASNE_MATERIJE|DRVO|ZIVOTINJE_RIBE|OSTALO

BROJ ključevi (float):
substanceQuantityM3, forestAreaHa
"""


def ollama_available() -> bool:
    try:
        r = httpx.get(f"{OLLAMA_URL}/api/tags", timeout=3)
        return r.status_code == 200
    except Exception:
        return False


def _make_fact(predicate, value, confidence=0.7):
    try:
        from main import Fact
        return Fact(predicate=predicate, value=value, confidence=confidence, sourceSpan=None)
    except Exception:
        class _F:
            def __init__(self, predicate, value, confidence, sourceSpan=None):
                self.predicate = predicate; self.value = value
                self.confidence = confidence; self.sourceSpan = sourceSpan
        return _F(predicate, value, confidence)


def _normalize(raw: dict):
    facts = []
    for key, val in raw.items():
        if key in BOOL_PREDS:
            truthy = val is True or str(val).strip().lower() in ("true", "da", "yes", "1")
            if truthy:
                facts.append(_make_fact(key, True))
        elif key in ENUM_VALUES:
            v = str(val).strip().upper()
            if v in ENUM_VALUES[key]:
                facts.append(_make_fact(key, v))
        elif key in NUM_PREDS:
            try:
                facts.append(_make_fact(key, float(str(val).replace(",", "."))))
            except (ValueError, TypeError):
                pass
    return facts


def extract_facts_llm(text: str):
    """Vraća listu Fact objekata izvučenih lokalnim LLM-om; [] ako Ollama nije dostupan."""
    if not text or not text.strip():
        return []
    try:
        resp = httpx.post(
            f"{OLLAMA_URL}/api/chat",
            json={
                "model": OLLAMA_MODEL,
                "format": "json",
                "stream": False,
                "keep_alive": "30m",   # drži model učitan da sledeći pozivi budu brzi
                "options": {"temperature": 0},
                "messages": [
                    {"role": "system", "content": SYSTEM_PROMPT},
                    {"role": "user", "content": text},
                ],
            },
            timeout=httpx.Timeout(TIMEOUT, connect=2.0),
        )
        if resp.status_code != 200:
            return []
        content = resp.json().get("message", {}).get("content", "")
        data = json.loads(content)
        if not isinstance(data, dict):
            return []
        return _normalize(data)
    except Exception as e:
        print(f"[LLM] Ollama ekstrakcija nije uspela: {e}")
        return []
