"""
Osnovni testovi za NLP servis.

Pokretanje:
    cd nlp-service
    pytest
"""
from fastapi.testclient import TestClient
from main import app

client = TestClient(app)


def test_health():
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json() == {"status": "ok"}


def test_extract_substance_quantity():
    payload = {
        "text": "Osumnjičeni je ispustio 12.5 m³ hemijskog otpada u reku Tamiš.",
        "documentType": "CASE_DESCRIPTION",
    }
    response = client.post("/extract", json=payload)
    assert response.status_code == 200
    data = response.json()

    facts = data["facts"]
    predicates = {f["predicate"] for f in facts}

    assert "substanceQuantityM3" in predicates
    quantity = next(f for f in facts if f["predicate"] == "substanceQuantityM3")
    assert quantity["value"] == 12.5

    # Tip supstance ekstrahovan iz "hemijskog otpada"
    assert "substanceType" in predicates

    # Cilj iz "reku"
    assert "pollutionTarget" in predicates
