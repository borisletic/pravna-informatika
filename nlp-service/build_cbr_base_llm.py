"""
build_cbr_base_llm.py
VLASNIK: Član 2

Ova skripta koristi OpenAI isključivo OFFLINE za kreiranje najkvalitetnije moguće
CBR baze (cbr_cases.csv). 
Implementirana je STROGA 'WHITELIST' NORMALIZACIJA koja blokira svaku LLM halucinaciju,
svodi izlaz na dogovorene enum vrednosti, i strogo filtrira prekršene članove
(samo KZ 260-289).
"""
import os
import csv
import json
import re
import xml.etree.ElementTree as ET
from openai import OpenAI

# =====================================================================
# KONFIGURACIJA
# =====================================================================
client = OpenAI(api_key=API_KEY)

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
# Putanje usklađene sa arhitekturom
XML_DIR = os.path.normpath(os.path.join(BASE_DIR, "..", "data", "judgments"))
CSV_OUTPUT = os.path.normpath(os.path.join(BASE_DIR, "..", "data", "cases", "cbr_cases.csv"))

# OVO SU JEDINA DOZVOLJENA POLJA (iz predicate_dictionary.yaml ugovora)
EXPECTED_FIELDS = [
    "id", "caseNumber", "court", "date", "articleViolated",
    "substanceQuantityM3", "forestAreaHa", "pollutionTarget", 
    "substanceType", "intent", "priorConviction", "remediedDamage", 
    "damageExtent", "sentenceType", "sentenceMonths"
]

# STROGA WHITELIST PRAVILA - Sve van ovoga se pretvara u "NEPOZNATO"
ALLOWED_VALUES = {
    "pollutionTarget": ["VODA", "VAZDUH", "TLO", "SUMA"],
    "substanceType": ["OPASNE_MATERIJE", "NAFTNI_DERIVATI", "DRVO", "ZIVOTINJE_RIBE", "KOMUNALNI_OTPAD"],
    "intent": ["UMISLJAJ", "NEHAT"],
    "priorConviction": ["DA", "NE"],
    "remediedDamage": ["DA", "NE"],
    "damageExtent": ["VELIKA", "MALA"],
    "sentenceType": ["ZATVOR", "NOVCANA", "USLOVNA", "RAD_U_JAVNOM_INTERESU"]
}

SYSTEM_PROMPT = """
Ti si ekspertni pravni asistent za krivično pravo Republike Srbije.
Tvoj zadatak je ekstrakcija činjenica iz presuda u striktni JSON format.
Zabranjeno je dodavati bilo kakve ključeve koji nisu na donjoj listi.

Lista dozvoljenih ključeva i njihovih vrednosti:
- court: (String) Naziv suda (npr. "Viši sud u Beogradu").
- date: (String) Datum donošenja presude u formatu YYYY-MM-DD.
- caseNumber: (String) Broj predmeta (npr. "K 151/2024").
- articleViolated: (String) Broj prekršenog člana Krivičnog zakonika (samo broj: 260 do 289). Ignoriši ZKP i druge zakone.
- substanceQuantityM3: (Float) Količina zagađujuće materije u kubicima.
- forestAreaHa: (Float) Površina pustošenja šume u hektarima.
- pollutionTarget: (String) Samo jedno od: VODA, VAZDUH, TLO, SUMA.
- substanceType: (String) Samo jedno od: OPASNE_MATERIJE, NAFTNI_DERIVATI, DRVO, ZIVOTINJE_RIBE, KOMUNALNI_OTPAD.
- intent: (String) Samo jedno od: UMISLJAJ, NEHAT.
- priorConviction: (String) "DA" ako je osuđivan, "NE" ako nije.
- remediedDamage: (String) "DA" ako je sanirao štetu, "NE" ako nije.
- damageExtent: (String) Samo jedno od: VELIKA, MALA.
- sentenceType: (String) Samo jedno od: ZATVOR, NOVCANA, USLOVNA, RAD_U_JAVNOM_INTERESU.
- sentenceMonths: (String) Ukupna dužina kazne pretvorena u mesece (npr. "6").

Ako podatak ne postoji u tekstu, ključ jednostavno izostavi (nemoj pisati null).
Vrati ISKLJUČIVO validan JSON.
"""

def extract_with_llm(text):
    """Šalje tekst OpenAI-u i vraća sirovi parsirani rečnik."""
    try:
        response = client.chat.completions.create(
            model="gpt-3.5-turbo",
            messages=[
                {"role": "system", "content": SYSTEM_PROMPT},
                {"role": "user", "content": f"Tekst presude:\n{text[:12000]}"}
            ],
            response_format={"type": "json_object"},
            temperature=0.0
        )
        return json.loads(response.choices[0].message.content)
    except Exception as e:
        print(f"  [GREŠKA LLM] {str(e)}")
        return {}

def get_text_from_xml(xml_path):
    """Vadi sirovi tekst iz Akoma Ntoso XML-a."""
    try:
        tree = ET.parse(xml_path)
        root = tree.getroot()
        namespaces = {'akn': 'http://docs.oasis-open.org/legaldocml/ns/akn/3.0'}
        paragraphs = root.findall('.//akn:p', namespaces)
        if not paragraphs:
            paragraphs = root.findall('.//p')
        return " ".join([p.text.strip() for p in paragraphs if p.text])
    except Exception as e:
        print(f"  [GREŠKA XML] Ne mogu parsirati fajl: {str(e)}")
        return ""

def format_case_number_from_filename(filename):
    """Fallback za broj predmeta na osnovu imena fajla."""
    name = filename.replace('.xml', '').upper()
    parts = name.split('-')
    if len(parts) >= 3:
        return f"{parts[0]} {parts[1]}/{parts[2]}"
    return name

def normalize_field(key, value):
    """
    Surova normalizacija koja propušta podatke kroz strogu 'whitelist' blokadu
    i primenjuje regex za validaciju članova zakona.
    """
    if value is None:
        return "NEPOZNATO"
        
    val_str = str(value).strip()
    val_upper = val_str.upper()
    
    # 1. Osnovno čišćenje null vrednosti
    if val_upper in ["NONE", "NULL", "N/A", "", "NAN", "NEPOZNATO", "НЕПОЗНАТО"]:
        return "NEPOZNATO"

    # 2. STROGA VALIDACIJA ZA ČLAN ZAKONA (Samo 260 do 289)
    if key == "articleViolated":
        # Tražimo bilo koji broj od 260 do 289 u stringu koji je LLM vratio
        match = re.search(r'\b(2[6-8][0-9])\b', val_str)
        if match:
            return match.group(1) # Vraća samo čist broj (npr. "260")
        else:
            return "NEPOZNATO" # Ako je vratio 333, 111, ZKP itd.

    # 3. Whitelist provera za kategorijska (enum) polja
    if key in ALLOWED_VALUES:
        # Rečnik za normalizaciju poznatih odstupanja (ćirilica, greške u kucanju LLM-a)
        mapping = {
            "ДА": "DA", "НЕ": "NE", "TRUE": "DA", "FALSE": "NE",
            "УМИШЉАЈ": "UMISLJAJ", "УМИСЛЈАЈ": "UMISLJAJ", "УМИСЉАЈ": "UMISLJAJ", "НЕХАТ": "NEHAT",
            "ЗАТВОР": "ZATVOR", "УСЛОВНА": "USLOVNA", "НОВЧАНА": "NOVCANA", "РАД_У_ЈАВНОМ_ИНТЕРЕСУ": "RAD_U_JAVNOM_INTERESU",
            "РАД У ЈАВНОМ ИНТЕРЕСУ": "RAD_U_JAVNOM_INTERESU", "RAD U JAVNOM INTERESU": "RAD_U_JAVNOM_INTERESU",
            "ZEMJISTE": "TLO", "ЗЕМЉИШТЕ": "TLO", "ZIVA_SREDOINA": "TLO",
            "ORUZJE_I_EKSPLOZIVNE_MATERIJE": "OPASNE_MATERIJE"
        }
        
        # Pokušavamo da ispravimo string
        mapped_val = mapping.get(val_upper, val_upper)
        
        # AKO NI NAKON ISPRAVKE NIJE NA ZVANIČNOJ LISTI -> SEČEMO GA!
        if mapped_val in ALLOWED_VALUES[key]:
            return mapped_val
        else:
            return "NEPOZNATO"
    
    # Za ostala polja (sudovi, datumi, caseNumber) ostavljamo string
    return val_str

def build_database():
    print("=== POKRETANJE GENERISANJA CBR BAZE (LLM OFFLINE) ===")
    
    if not os.path.exists(XML_DIR):
        print(f"[GREŠKA] Direktorijum sa presudama ne postoji: {XML_DIR}")
        return

    xml_files = [f for f in os.listdir(XML_DIR) if f.endswith('.xml')]
    if not xml_files:
        print("[UPOZORENJE] Nema XML fajlova u folderu.")
        return

    rows = []
    current_id = 1

    for filename in xml_files:
        print(f"\nObrađujem: {filename}")
        xml_path = os.path.join(XML_DIR, filename)
        
        # 1. Čitanje teksta
        raw_text = get_text_from_xml(xml_path)
        if not raw_text:
            continue
            
        # 2. LLM Ekstrakcija
        llm_facts = extract_with_llm(raw_text)
        
        # 3. FILTRACIJA I NORMALIZACIJA
        safe_row = {"id": str(current_id)}
        
        for field in EXPECTED_FIELDS:
            if field == "id":
                continue
                
            raw_val = llm_facts.get(field, None)
            safe_row[field] = normalize_field(field, raw_val)
                
        # 4. Fallback za broj predmeta ako LLM omaši
        if safe_row["caseNumber"] == "NEPOZNATO":
            safe_row["caseNumber"] = format_case_number_from_filename(filename)

        rows.append(safe_row)
        current_id += 1
        print(f"  [USPEH] Identifikovan predmet: {safe_row['caseNumber']}")

    # 5. Upisivanje u CSV
    os.makedirs(os.path.dirname(os.path.abspath(CSV_OUTPUT)), exist_ok=True)
    try:
        with open(CSV_OUTPUT, mode='w', newline='', encoding='utf-8') as f:
            writer = csv.DictWriter(f, fieldnames=EXPECTED_FIELDS, extrasaction='ignore', quoting=csv.QUOTE_ALL)
            writer.writeheader()
            writer.writerows(rows)
        print(f"\n=== BINGO! CBR BAZA GENERISANA BEZ GREŠKE! Ukupno zapisa: {len(rows)} ===")
    except Exception as e:
        print(f"\n[KRITIČNA GREŠKA PRI UPISU CSV]: {str(e)}")

if __name__ == "__main__":
    build_database()