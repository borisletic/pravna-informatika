"""
fact_extractor.py
VLASNIK: Član 2.
CELINA: 4.

HIBRIDNI EKSTRAKTOR (spaCy + Regex)
Ova skripta obrađuje korisničke upite uživo. Koristi dependency parsing 
za prepoznavanje konteksta i negacija (npr. "nije osuđivan"), 
a regex za precizno hvatanje numeričkih vrednosti.
"""

import re
import spacy
from typing import List

# Učitavamo NLP model na nivou modula (da se ne bi učitavao pri svakom requestu)
print("Učitavam spaCy model (hr_core_news_sm) za fact_extractor...")
try:
    nlp = spacy.load("hr_core_news_sm")
except OSError:
    print("[UPOZORENJE] SpaCy model nije pronađen. Pokrenite: python -m spacy download hr_core_news_sm")
    nlp = None

def check_negation(token) -> bool:
    """
    Proverava da li je dati spaCy token negiran u rečenici.
    Gleda decu tokena u drvetu zavisnosti, kao i neposredno prethodne reči.
    """
    # 1. Provera kroz dependency parsing (deca tokena)
    for child in token.children:
        if child.text.lower() in ["ne", "nije", "nikad", "nikada", "nema"]:
            return True
            
    # 2. Linearna provera (za svaki slučaj, ako parser omaši granu)
    if token.i > 0:
        prev_token = token.doc[token.i - 1].text.lower()
        if prev_token in ["ne", "nije", "nikad", "nikada"]:
            return True
            
    return False

def extract_facts(text: str) -> List:
    """
    Glavni ulaz - prima sirov tekst presude i vraća listu Fact objekata.
    """
    try:
        from main import Fact, SourceSpan
    except ImportError:
        # Fallback strukture ukoliko se skripta testira izolovano van FastAPI-ja
        class SourceSpan:
            def __init__(self, start, end): 
                self.start = start
                self.end = end
        class Fact:
            def __init__(self, predicate, value, confidence, sourceSpan=None):
                self.predicate = predicate
                self.value = value
                self.confidence = confidence
                self.sourceSpan = sourceSpan

    facts = []
    
    # Ako spaCy nije učitan iz nekog razloga, oslanjamo se samo na string
    text_lower = text.lower()
    doc = nlp(text) if nlp else None

    # Pomoćna funkcija za bezbedno dodavanje činjenica (sprečava duplikate istog predikata)
    def add_fact(predicate, value, confidence, start_idx, end_idx):
        if not any(f.predicate == predicate for f in facts):
            span = SourceSpan(start=start_idx, end=end_idx)
            facts.append(Fact(predicate=predicate, value=value, confidence=confidence, sourceSpan=span))

    # =====================================================================
    # 1. SPACY DEEP LEARNING EKSTRAKCIJA (Kontekst i Negacije)
    # =====================================================================
    if doc:
        for token in doc:
            word = token.text.lower()
            lemma = token.lemma_.lower()

            # --- PRIOR CONVICTION (Da li je osuđivan) ---
            if word in ["osuđivan", "osuđivana", "kažnjavan", "kažnjavana"] or lemma == "osuđivati":
                is_negated = check_negation(token)
                add_fact(
                    predicate="priorConviction", 
                    value="NE" if is_negated else "DA", 
                    confidence=0.85, 
                    start_idx=token.idx, 
                    end_idx=token.idx + len(token.text)
                )

            # --- REMEDIED DAMAGE (Da li je sanirao štetu) ---
            if lemma in ["sanirati", "otkloniti", "nadoknaditi", "popraviti"] and token.dep_ in ["ROOT", "xcomp", "ccomp", "conj"]:
                # Tražimo objekat glagola (šta je sanirao? -> štetu, posledice)
                has_damage_obj = any(child.lemma_.lower() in ["šteta", "posledica", "kvar"] for child in token.children)
                
                # Čak i ako ne nađe savršen objekat, ako smo u ekološkom domenu, pretpostavljamo
                is_negated = check_negation(token)
                add_fact(
                    predicate="remediedDamage", 
                    value="NE" if is_negated else "DA", 
                    confidence=0.8, 
                    start_idx=token.idx, 
                    end_idx=token.idx + len(token.text)
                )

    # =====================================================================
    # 2. NAPREDNI REGEX (Količine i eksplicitne ključne reči)
    # =====================================================================
    
    # --- INTENT (Umišljaj / Nehat) ---
    m_intent = re.search(r'\b(умишљај|umišljaj|свесно|svesno|намерно|namerno)\b', text_lower)
    if m_intent:
        add_fact("intent", "UMISLJAJ", 0.9, m_intent.start(), m_intent.end())
    else:
        m_nehat = re.search(r'\b(нехат|nehat|непажњ|nepažnj)\b', text_lower)
        if m_nehat:
            add_fact("intent", "NEHAT", 0.9, m_nehat.start(), m_nehat.end())

    # --- POLLUTION TARGET (Meta zagađenja) ---
    m_target = re.search(r'\b(voda|vode|vodu|vodu|vazduh|atmosferu|tlo|zemljište|šuma|šumu|šume)\b', text_lower)
    if m_target:
        val = m_target.group(1)
        if val in ["voda", "vode", "vodu"]: target_val = "VODA"
        elif val in ["vazduh", "atmosferu"]: target_val = "VAZDUH"
        elif val in ["tlo", "zemljište"]: target_val = "TLO"
        elif val in ["šuma", "šumu", "šume"]: target_val = "SUMA"
        else: target_val = "VODA" # fallback
        add_fact("pollutionTarget", target_val, 0.85, m_target.start(), m_target.end())

    # --- SUBSTANCE TYPE (Tip supstance) ---
    if re.search(r'\b(opasn[a-z]+ materij[a-z]+|otrov|kiselina|hemikalija)\b', text_lower):
        m_sub = re.search(r'\b(opasn[a-z]+ materij[a-z]+|otrov|kiselina|hemikalija)\b', text_lower)
        add_fact("substanceType", "OPASNE_MATERIJE", 0.85, m_sub.start(), m_sub.end())
    elif re.search(r'\b(naft[a-z]*|benzin|goriv[a-z]*|ulj[ea])\b', text_lower):
        m_sub = re.search(r'\b(naft[a-z]*|benzin|goriv[a-z]*|ulj[ea])\b', text_lower)
        add_fact("substanceType", "NAFTNI_DERIVATI", 0.85, m_sub.start(), m_sub.end())
    elif re.search(r'\b(drvo|stabl[oa]|drveć[ea])\b', text_lower):
        m_sub = re.search(r'\b(drvo|stabl[oa]|drveć[ea])\b', text_lower)
        add_fact("substanceType", "DRVO", 0.85, m_sub.start(), m_sub.end())
    elif re.search(r'\b(rib[ea]|srn[ea]|divljač|životinj[ea])\b', text_lower):
        m_sub = re.search(r'\b(rib[ea]|srn[ea]|divljač|životinj[ea])\b', text_lower)
        add_fact("substanceType", "ZIVOTINJE_RIBE", 0.85, m_sub.start(), m_sub.end())
    elif re.search(r'\b(otpad|smeć[ea]|deponij[ea])\b', text_lower):
        m_sub = re.search(r'\b(otpad|smeć[ea]|deponij[ea])\b', text_lower)
        add_fact("substanceType", "KOMUNALNI_OTPAD", 0.85, m_sub.start(), m_sub.end())

    # --- QUANTITIES (M3 i Hektari) ---
    m_qty = re.search(r'(\d+(?:[.,]\d+)?)\s*(?:m³|m3|m\^3|kubn|kubik)', text_lower)
    if m_qty:
        try:
            val = float(m_qty.group(1).replace(',', '.'))
            add_fact("substanceQuantityM3", str(val), 0.95, m_qty.start(), m_qty.end())
        except ValueError:
            pass

    m_ha = re.search(r'(\d+(?:[.,]\d+)?)\s*(?:ha|hektar|ar[ia])', text_lower)
    if m_ha:
        try:
            val = float(m_ha.group(1).replace(',', '.'))
            add_fact("forestAreaHa", str(val), 0.95, m_ha.start(), m_ha.end())
        except ValueError:
            pass

    # --- SENTENCE TYPE (Tip presude) ---
    if re.search(r'\b(zatvor[a-z]*)\b', text_lower):
        m_sent = re.search(r'\b(zatvor[a-z]*)\b', text_lower)
        add_fact("sentenceType", "ZATVOR", 0.9, m_sent.start(), m_sent.end())
    elif re.search(r'\b(uslovn[a-z]* osud[a-z]*)\b', text_lower):
        m_sent = re.search(r'\b(uslovn[a-z]* osud[a-z]*)\b', text_lower)
        add_fact("sentenceType", "USLOVNA", 0.9, m_sent.start(), m_sent.end())
    elif re.search(r'\b(novčan[a-z]* kazn[a-z]*)\b', text_lower):
        m_sent = re.search(r'\b(novčan[a-z]* kazn[a-z]*)\b', text_lower)
        add_fact("sentenceType", "NOVCANA", 0.9, m_sent.start(), m_sent.end())

    return facts