"""
NLP mikroservis za pravnu informatiku.
VLASNIK: Član 2 (NLP & Data).
CELINA: 4.
"""
from fastapi import FastAPI
from pydantic import BaseModel
from typing import Any, Dict, List, Optional

# Importi iz podfoldera 'extractors'
from extractors.metadata_extractor import extract_metadata
from extractors.fact_extractor import extract_facts

app = FastAPI(
    title="Pravna informatika NLP servis",
    version="0.1.0",
)

# ==========================================
# Pydantic šeme (Ugovor sa Spring aplikacijom)
# ==========================================
class ExtractRequest(BaseModel):
    text: str
    documentType: str = "JUDGMENT"
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

# ==========================================
# API Endpoints
# ==========================================
@app.get("/health")
def health():
    return {"status": "ok", "message": "NLP Mikroservis je aktivan!"}

@app.post("/extract", response_model=ExtractResponse)
def extract(req: ExtractRequest):
    options = req.options or {}
    include_meta = options.get("includeMetadata", True)
    include_facts = options.get("includeFacts", True)

    meta = Metadata()
    facts = []

    if include_meta:
        meta = extract_metadata(req.text)
        
    if include_facts:
        facts = extract_facts(req.text)

    return ExtractResponse(metadata=meta, facts=facts)