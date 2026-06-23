"""
Testovi za NLP servis (Celina 4).

Pokretanje:
    cd nlp-service
    pytest

Napomena: testovi rade i bez spaCy modela (regex-only predikati). Predikati koji
zavise od spaCy (priorConviction, remediedDamage, imena sudija/stranaka) testiraju
se samo ako je model dostupan.
"""
import os
# Testovi proveravaju regex ekstrakciju — LLM (Ollama) putanja se isključuje radi
# brzine i determinizma.
os.environ["NLP_DISABLE_LLM"] = "1"

from fastapi.testclient import TestClient
from main import app
from extractors.fact_extractor import extract_facts, nlp as fact_nlp

client = TestClient(app)


def predicates(facts):
    return {f["predicate"]: f["value"] for f in facts}


def extract(text):
    r = client.post("/extract", json={"text": text, "documentType": "CASE_DESCRIPTION"})
    assert r.status_code == 200
    return predicates(r.json()["facts"])


# ---------------- health ----------------
def test_health():
    r = client.get("/health")
    assert r.status_code == 200
    assert r.json() == {"status": "healthy"}


# ---------------- substanceQuantityM3 ----------------
def test_quantity_m3_decimal():
    assert float(extract("ispustio je 12.5 m³ otpada")["substanceQuantityM3"]) == 12.5

def test_quantity_m3_comma():
    assert float(extract("ispušteno 7,25 m3 mulja")["substanceQuantityM3"]) == 7.25

def test_quantity_m3_kubnih():
    assert "substanceQuantityM3" in extract("oko 100 kubnih metara")


# ---------------- forestAreaHa ----------------
def test_forest_area_ha():
    assert float(extract("posečena šuma na 3.5 ha")["forestAreaHa"]) == 3.5

def test_forest_area_hektara():
    assert "forestAreaHa" in extract("opožareno 12 hektara")


# ---------------- intent ----------------
def test_intent_umisljaj():
    assert extract("postupao je svesno i namerno").get("intent") == "UMISLJAJ"

def test_intent_nehat():
    assert extract("usled nehata i nepažnje").get("intent") == "NEHAT"


# ---------------- pollutionTarget ----------------
def test_target_voda():
    assert extract("zagadio je vodu reke").get("pollutionTarget") == "VODA"

def test_target_vazduh():
    assert extract("emisija u vazduh").get("pollutionTarget") == "VAZDUH"

def test_target_suma():
    assert extract("oštetio šumu").get("pollutionTarget") == "SUMA"


# ---------------- substanceType ----------------
def test_substance_opasne():
    assert extract("odložio opasne materije").get("substanceType") == "OPASNE_MATERIJE"

def test_substance_naftni():
    assert extract("izlivanje nafte u potok").get("substanceType") == "NAFTNI_DERIVATI"

def test_substance_drvo():
    assert extract("posekao stabla").get("substanceType") == "DRVO"


# ---------------- sentenceType ----------------
def test_sentence_zatvor():
    assert extract("osuđen na kaznu zatvora").get("sentenceType") == "ZATVOR"


# ---------------- NOVI: ecologicalDamage / damageExtent ----------------
def test_ecological_damage_big():
    p = extract("nastupila je šteta velikih razmera")
    assert p.get("ecologicalDamage") == "VELIKIH_RAZMERA"
    assert p.get("damageExtent") == "VELIKA"

def test_ecological_damage_small():
    p = extract("pričinjena je manja šteta")
    assert p.get("damageExtent") == "MALA"


# ---------------- NOVI: protectedSpecies ----------------
def test_protected_species():
    assert extract("ulovio strogo zaštićenu vrstu").get("protectedSpecies") == "DA"

def test_protected_species_cyrillic():
    assert extract("реч је о заштићеној врсти").get("protectedSpecies") == "DA"


# ---------------- NOVI: usesExplosives ----------------
def test_uses_explosives():
    assert extract("ribu je lovio eksplozivom").get("usesExplosives") == "DA"

def test_uses_electricity():
    assert extract("koristio električnu struju za izlov ribe").get("usesExplosives") == "DA"


# ---------------- spaCy-zavisni (preskoči ako model nije dostupan) ----------------
import pytest

@pytest.mark.skipif(fact_nlp is None, reason="spaCy model nije dostupan")
def test_prior_conviction_negation():
    assert extract("ranije nije osuđivan").get("priorConviction") == "NE"

@pytest.mark.skipif(fact_nlp is None, reason="spaCy model nije dostupan")
def test_remedied_damage():
    assert extract("naknadno je sanirao štetu").get("remediedDamage") == "DA"
