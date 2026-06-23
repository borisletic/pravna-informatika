"""
Ekstrakcija metapodataka korišćenjem HIBRIDNOG pristupa (spaCy NER + Regex).
VLASNIK: Član 2.

1. spaCy (Deep Learning) - za fleksibilno prepoznavanje imena sudija i stranaka.
2. Regex (Pattern Matching) - za strogo formatirane podatke.
"""

import re
import spacy
from typing import List

print("Učitavam spaCy NLP model (hr_core_news_sm)...")
try:
    nlp = spacy.load("hr_core_news_sm")
    print("Model uspešno učitan!")
except OSError:
    print("Greška: Model nije pronađen. Pokrenite u terminalu: python -m spacy download hr_core_news_sm")
    nlp = None

def extract_metadata(text: str):
    """
    Glavna funkcija koja orkestrira ekstrakciju metapodataka. 
    """
    from main import Metadata, Party

    # 1. ČISTI REGEX (Najpouzdanije za brojeve, datume i sudove)
    case_number = _extract_case_number(text)
    date = _extract_date(text)
    court = _extract_court_regex(text)

    # 2. NLP za ljude i preostale institucije
    nlp_court, judges, parties, recorder = _extract_entities_with_nlp(text)

    # Fallback za sud
    if not court:
        court = nlp_court

    return Metadata(
        caseNumber=case_number,
        court=court,
        date=date,
        parties=parties,
        judges=judges,
        recorder=recorder
    )

def _extract_entities_with_nlp(text: str):
    from main import Party
    court = None
    judges = []
    parties = []
    recorder = None

    if nlp is None:
        return court, judges, parties, recorder

    # Obrađujemo samo prvih 3000 karaktera jer su metapodaci uvek na početku
    doc = nlp(text[:3000])
    
    # Brza heuristika za zapisničara putem regexa
    m_recorder = re.search(r'(?:zapisničarem|zapisničarom|zapisničara)\s+([A-ZŽĐŠČĆ][a-zžđšćčć]+\s+[A-ZŽĐŠČĆ][a-zžđšćčć]+)', text[:3000], re.IGNORECASE)
    if m_recorder:
        recorder = m_recorder.group(1)
        
    for ent in doc.ents:
        if ent.label_ == 'ORG' and 'sud' in ent.text.lower():
            if not court:
                court = ent.text.strip().capitalize()
                
        elif ent.label_ == 'PER':
            ime = ent.text.strip()
            # Ignorišemo greške kraće od 3 slova
            if not ime or len(ime) < 3:
                continue
            
            context_start = max(0, text.find(ime) - 60)
            context = text[context_start : text.find(ime)].lower()
            
            if "zapisničar" in context and not recorder:
                recorder = ime
            elif len(judges) < 5 and ("sudij" in context or "predsednik" in context or "već" in context):
                if ime not in judges and ime != recorder:
                    judges.append(ime)
            else:
                if "okrivljen" in context or "optužen" in context:
                    initials = "".join([part[0].upper() + "." for part in ime.split() if part.isalpha()])
                    if len(initials) >= 4 and not any(p.initials == initials for p in parties):
                        parties.append(Party(role="OKR", initials=initials))

    unique_parties = list({p.initials: p for p in parties}.values())
    return court, judges, unique_parties, recorder

def _extract_case_number(text: str):
    patterns = [
        r'\b([КK][жZzЖ]?\d*|[ПPpП][кKkК]?[зZzЖ]?|[УUuУ]|[РR][еe][вv]|[ПP][рr][жzЖ]?)\.?\s*(?:бр\.?|broj)?\s*[-]?\s*\d+\s*[/.-]\s*\d{2,4}\b',
        r'\b(?:бр\.?|broj)\s*\d+\s*[/.-]\s*\d{2,4}\b'
    ]
    for p in patterns:
        m = re.search(p, text, re.IGNORECASE)
        if m:
            return re.sub(r'\s+', ' ', m.group(0)).upper().replace("БР.", "").replace("BROJ", "").strip()
    return None

def _extract_date(text: str):
    patterns = [
        r'\b(\d{1,2})\.\s*(\d{1,2})\.\s*(\d{4})\.',
        r'\b(\d{1,2})\s*[-/]\s*(\d{1,2})\s*[-/]\s*(\d{4})\b'
    ]
    for p in patterns:
        m = re.search(p, text)
        if m:
            dan = m.group(1).zfill(2)
            mesec = m.group(2).zfill(2)
            godina = m.group(3)
            return f"{godina}-{mesec}-{dan}"
    return None

def _extract_court_regex(text: str):
    m = re.search(r'\b((?:Osnovni|Viši|Apelacioni|Vrhovni|Upravni|Privredni)\s+(?:kasacioni\s+)?sud(?:\s+u\s+[A-ZŽĐŠČĆ][a-zžđšćčć]+)?)\b', text, re.IGNORECASE)
    if m: return m.group(1).title()
    
    m_cir = re.search(r'\b((?:Основни|Виши|Апелациони|Врховни|Управни|Привредни)\s+(?:касациони\s+)?суд(?:\s+у\s+[А-ШЂЖЧЋ][а-шђжчћ]+)?)\b', text, re.IGNORECASE)
    if m_cir: return m_cir.group(1).title()
    
    return None