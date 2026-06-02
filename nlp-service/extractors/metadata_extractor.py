"""
Ekstrakcija metapodataka korišćenjem HIBRIDNOG pristupa (spaCy NER + Regex).
VLASNIK: Član 2.


1. spaCy (Deep Learning) - za fleksibilno prepoznavanje imena sudija i stranaka, jer se ona stalno mijenjaju. (classla mi nije radila za Python 3.12, pa sam prešao na spaCy uz hr_core_news_sm za srpski jezik).
2. Regex (Pattern Matching) - za strogo formatirane podatke (broj predmeta, datum) i kao sigurnosnu mrežu za institucije (sudove).
"""
import re
import spacy
from typing import List

# ===========================================================
# INICIJALIZACIJA NLP MODELA
# ===========================================================
print("Učitavam spaCy NLP model (hr_core_news_sm)...")
try:
    # Model hr_core_news_sm odlično prepoznaje južnoslovenska imena i morfologiju
    nlp = spacy.load("hr_core_news_sm")
    print("Model uspješno učitan!")
except OSError:
    print("Greška: Model nije pronađen. Pokrenite u terminalu: python -m spacy download hr_core_news_sm")
    nlp = None

def extract_metadata(text: str):
    """
    Glavna funkcija koja orkestrira ekstrakciju. 
    Vraća Metadata Pydantic objekat definisan u glavnoj aplikaciji.
    """
    from main import Metadata, Party

    # 1. ČISTI REGEX (Najpouzdanije za brojeve i datume)
    case_number = _extract_case_number(text)
    date = _extract_date(text)

    # 2. NLP (NER - Named Entity Recognition) za imena i organizacije
    court, judges, parties = _extract_entities_with_nlp(text)

    # 3. HIBRIDNI FALLBACK (Korekcija)
    # Ako AI nije uspeo da prepozna sud (jer je npr. "Beograd" shvatio samo kao lokaciju),
    # palimo Regex pravila da spasimo situaciju.
    if not court:
        court = _extract_court_regex(text)

    return Metadata(
        caseNumber=case_number,
        court=court,
        date=date,
        parties=parties,
        judges=judges,
        recorder=None
    )

# ===========================================================
# 1. NLP LOGIKA (Vještačka inteligencija)
# ===========================================================
def _extract_entities_with_nlp(text: str):
    if nlp is None:
        return None, [], []

    # Obrađujemo samo prvih 2500 karaktera. 
    # Zašto? U sudskim presudama, uvod (imena sudija, stranaka i sud) se uvek nalazi na početku.
    # Time drastično štedimo procesorsko vrijeme (CPU) i ubrzavamo API.
    doc = nlp(text[:2500])
    
    court = None
    judges = []
    parties = []
    from main import Party

    for ent in doc.ents:
        # Prepoznavanje organizacija (ORG) - AI pokušava da nađe sud
        if ent.label_ == 'ORG' and 'sud' in ent.text.lower():
            if not court:
                court = ent.text

        # Prepoznavanje osoba (PER) - razvrstavanje na sudije i stranke
        elif ent.label_ == 'PER':
            ime = ent.text.strip()
            
            # Filtriramo lažna očitavanja (imena kraća od 3 slova su obično greška modela)
            if not ime or len(ime) < 3:
                continue
                
            # Heuristika konteksta: Gledamo 40 karaktera pre prepoznatog imena.
            # Ako se tu spominje "sudija" ili "predsednik", klasifikujemo osobu kao sudiju.
            context_start = max(0, text.find(ime) - 40)
            context = text[context_start : text.find(ime)].lower()
            
            if len(judges) < 3 and ("sudij" in context or "predsednik" in context):
                if ime not in judges:
                    judges.append(ime)
            else:
                # Svi ostali identifikovani ljudi idu u stranke (okrivljeni, svedoci).
                # Generisanje inicijala (npr. "Petar Petrović" -> "P.P.")
                initials = "".join([part[0].upper() + "." for part in ime.split() if part.isalpha()])
                # Dodajemo stranku samo ako smo uspeli generisati validne inicijale
                if len(initials) >= 4: 
                    parties.append(Party(role="OKRIVLJENI/SVEDOK", initials=initials))

    # Brisanje duplikata (da ne bismo istog okrivljenog imali 5 puta u JSON-u)
    unique_parties = list({p.initials: p for p in parties}.values())
    return court, judges, unique_parties

# ===========================================================
# 2. REGEX PRAVILA (Pravila zasnovana na šablonima)
# ===========================================================
def _extract_court_regex(text: str):
    """
    Sigurnosna mreža za sudove. Hvata specifične formate sudova u Srbiji 
    i na ćirilici i na latinici, nezavisno od veličine slova (re.IGNORECASE).
    """
    patterns = [
        # Hvata "Osnovni sud u Novom Sadu", "Viši sud u Beogradu", "Apelacioni...", itd.
        r'\b(Основни|Виши|Апелациони|Врховни касациони|Управни|Привредни)\s+суд\s+у\s+([А-ШA-Zа-шa-z]+(\s[А-ШA-Zа-шa-z]+)?)\b',
        r'\b(Osnovni|Viši|Apelacioni|Vrhovni kasacioni|Upravni|Privredni)\s+sud\s+u\s+([A-Za-zČčĆćŠšĐđŽž]+(\s[A-Za-zČčĆćŠšĐđŽž]+)?)\b',
        # Specijalni slučajevi
        r'\bВрховни суд Србије\b',
        r'\bVrhovni sud Srbije\b'
    ]
    for p in patterns:
        m = re.search(p, text, re.IGNORECASE)
        if m:
            # Vraća pronađeni tekst sa sređenim razmacima
            return " ".join(m.group(0).split())
    return None

def _extract_case_number(text: str):
    """
    Ekstrakcija identifikatora predmeta (npr. K-145/2019, Kž 12/20).
    """
    patterns = [r'\b[КKkК][жZzЖ]?\s*[-.]?\s*\d+\s*/\s*\d{2,4}\b', r'\b[КK]\.?\s*\d+\s*/\s*\d{2,4}\b']
    for p in patterns:
        m = re.search(p, text)
        if m: return m.group(0).strip()
    return None

def _extract_date(text: str):
    """
    Pretvara različite formate datuma iz presuda u standardni ISO format (YYYY-MM-DD).
    """
    patterns = [r'\b(\d{1,2})\.\s*(\d{1,2})\.\s*(\d{4})\.?\b', r'\b(\d{4})-(\d{1,2})-(\d{1,2})\b']
    for p in patterns:
        m = re.search(p, text)
        if m:
            g = m.groups()
            if len(g[0]) == 4: # Ako počinje godinom
                return f"{g[0]}-{int(g[1]):02d}-{int(g[2]):02d}"
            else: # Ako počinje danom
                return f"{g[2]}-{int(g[1]):02d}-{int(g[0]):02d}"
    return None