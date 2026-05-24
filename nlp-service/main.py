"""
NLP mikroservis za pravnu informatiku.

VLASNIK: Član 2 (NLP & Data).
CELINA: 4.

Pokretanje:
    uvicorn main:app --reload --port 8000

UGOVOR sa Spring app: videti docs/INTEGRATION_CONTRACTS.md sekcija 3.
"""
from fastapi import FastAPI
from pydantic import BaseModel
from typing import Any, Dict, List, Optional

from extractors.metadata_extractor import extract_metadata
from extractors.fact_extractor import extract_facts

app = FastAPI(
    title="Pravna informatika NLP servis",
    version="0.1.0",
)


class ExtractRequest(BaseModel):
    text: str
    documentType: str  # "JUDGMENT" ili "CASE_DESCRIPTION"
    options: Optional[Dict[str, Any]] = None


class SourceSpan(BaseModel):
    start: int
    end: int


class Fact(BaseModel):
    predicate: str
    value: Any
    confidence: float
    sourceSpan: Optional[SourceSpan] = None


class Party(BaseModel):
    role: str
    initials: str


class Metadata(BaseModel):
    caseNumber: Optional[str] = None
    court: Optional[str] = None
    date: Optional[str] = None
    parties: List[Party] = []
    judges: List[str] = []
    recorder: Optional[str] = None


class ExtractResponse(BaseModel):
    metadata: Metadata
    facts: List[Fact]


@app.get("/health")
def health():
    return {"status": "ok"}


@app.post("/extract", response_model=ExtractResponse)
def extract(req: ExtractRequest):
    """
    Glavni endpoint - poziva ga Spring app.

    Trenutno - MOCK. TODO Član 2: implementirati prave ekstraktore.
    """
    include_meta = (req.options or {}).get("includeMetadata", True)
    include_facts = (req.options or {}).get("includeFacts", True)

    metadata = extract_metadata(req.text) if include_meta else Metadata()
    facts = extract_facts(req.text) if include_facts else []

    return ExtractResponse(metadata=metadata, facts=facts)
