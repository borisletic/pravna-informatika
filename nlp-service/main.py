"""
Glavna FastAPI aplikacija za NLP mikroservis (Celina 4).
VLASNIK: Član 2
UGOVOR: REST interfejs prema Članu 3 (Application)
"""

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field
from typing import List, Optional, Any

# Uvozimo naše ekstraktore
from extractors.metadata_extractor import extract_metadata
from extractors.fact_extractor import extract_facts

app = FastAPI(
    title="Pravna Informatika - NLP Service",
    description="Mikroservis za ekstrakciju metapodataka i pravnih činjenica iz presuda.",
    version="1.0.0"
)

# ===========================================================
# PYDANTIC MODELI (Prema INTEGRATION_CONTRACTS.md)
# ===========================================================

class Party(BaseModel):
    role: str = Field(..., description="Uloga stranke, npr. OKR (Okrivljeni)")
    initials: str = Field(..., description="Inicijali stranke, npr. M.P.")

class Metadata(BaseModel):
    caseNumber: Optional[str] = Field(None, description="Broj predmeta, npr. K-145/2019")
    court: Optional[str] = Field(None, description="Naziv suda")
    date: Optional[str] = Field(None, description="Datum presude u YYYY-MM-DD formatu")
    parties: List[Party] = Field(default_factory=list, description="Lista stranaka u postupku")
    judges: List[str] = Field(default_factory=list, description="Lista sudija")
    recorder: Optional[str] = Field(None, description="Zapisničar")

class SourceSpan(BaseModel):
    start: int = Field(..., description="Početni karakter u tekstu")
    end: int = Field(..., description="Krajnji karakter u tekstu")

class Fact(BaseModel):
    predicate: str = Field(..., description="Naziv činjenice iz rečnika")
    value: Any = Field(..., description="Izvučena vrednost (broj, string ili boolean)")
    confidence: float = Field(..., description="Nivo pouzdanosti ekstrakcije (0.0 - 1.0)")
    sourceSpan: Optional[SourceSpan] = Field(None, description="Pozicija u tekstu gde je činjenica pronađena")

# Modeli za Request / Response
class ExtractionOptions(BaseModel):
    includeMetadata: bool = True
    includeFacts: bool = True
    language: str = "sr"

class ExtractionRequest(BaseModel):
    text: str = Field(..., description="Sirovi tekst sudske presude")
    options: Optional[ExtractionOptions] = Field(default_factory=ExtractionOptions)

class ExtractionResponse(BaseModel):
    metadata: Optional[Metadata] = None
    facts: List[Fact] = Field(default_factory=list)

# ===========================================================
# ENDPOINTS
# ===========================================================

# OVDJE JE BILA GREŠKA - dodato je "app."
@app.post("/extract", response_model=ExtractionResponse)
def extract_all(request: ExtractionRequest):
    """
    Prima tekst presude i vraća strukturirane metapodatke i pravne činjenice.
    Ovo je glavni endpoint koji poziva Java Spring aplikacija Člana 3.
    """
    if not request.text.strip():
        raise HTTPException(status_code=400, detail="Tekst presude ne sme biti prazan.")
    
    response_data = ExtractionResponse()
    
    try:
        # 1. Ekstrakcija metapodataka (ako je zahtevano opcijama)
        if request.options.includeMetadata:
            response_data.metadata = extract_metadata(request.text)
            
        # 2. Ekstrakcija pravnih činjenica (ako je zahtevano opcijama)
        if request.options.includeFacts:
            response_data.facts = extract_facts(request.text)
            
        return response_data
        
    except Exception as e:
        # Prijavljivanje greške na serveru i vraćanje 500 status koda
        print(f"[ERROR] Greška tokom NLP ekstrakcije: {str(e)}")
        raise HTTPException(status_code=500, detail=f"Interna greška servera: {str(e)}")

class GenerateRequest(BaseModel):
    prompt: str = Field(..., description="Pun prompt za generisanje obrazloženja odluke")


class GenerateResponse(BaseModel):
    available: bool
    text: Optional[str] = None


@app.post("/generate-decision", response_model=GenerateResponse)
def generate_decision(request: GenerateRequest):
    """
    Celina 9 — generisanje obrazloženja odluke jezičkim modelom (LLM), po ugledu
    na postojeće presude. Aktivira se ako je postavljen OPENAI_API_KEY; u suprotnom
    vraća available=false pa Java aplikacija koristi šablonski generator.
    """
    import os
    key = os.environ.get("OPENAI_API_KEY")
    if not key:
        return GenerateResponse(available=False)
    try:
        from openai import OpenAI
        client = OpenAI(api_key=key)
        resp = client.chat.completions.create(
            model=os.environ.get("OPENAI_MODEL", "gpt-4o-mini"),
            messages=[
                {"role": "system", "content":
                    "Ti si sudija Republike Srbije. Pišeš obrazloženje presude za krivična dela "
                    "protiv životne sredine (KZ čl. 260-277), formalnim pravnim stilom, po ugledu "
                    "na postojeće presude. Vrati isključivo tekst obrazloženja (2-4 pasusa), bez naslova."},
                {"role": "user", "content": request.prompt},
            ],
            temperature=0.4,
        )
        return GenerateResponse(available=True, text=resp.choices[0].message.content.strip())
    except Exception as e:
        print(f"[ERROR] LLM generisanje nije uspelo: {e}")
        return GenerateResponse(available=False)


# Dodato je "app."
@app.get("/health")
def health_check():
    """Pomoćni endpoint za proveru zdravlja servisa."""
    return {"status": "healthy"}