"""
Skripta za generisanje CSV baze za Case-Based Reasoning (Celina 6).
VLASNIK: Član 2 (NLP & Data)

Ova verzija je POTPUNO usklađena sa proširenim ekstraktorima i mapira
sve nove atribute iz predicate_dictionary.yaml ugovora.
"""
import os
import csv
import re
import xml.etree.ElementTree as ET

# Uvozimo naše ažurirane ekstraktore
from extractors.metadata_extractor import extract_metadata
from extractors.fact_extractor import extract_facts

# Dinamičko određivanje apsolutne putanje bez obzira odakle se pokreće skripta
BASE_DIR = os.path.dirname(os.path.abspath(__file__))

# Mapiranje direktorijuma prema dogovorenoj strukturi projekta
XML_DIR = os.path.normpath(os.path.join(BASE_DIR, "..", "data", "judgments"))
CSV_OUTPUT = os.path.normpath(os.path.join(BASE_DIR, "..", "data", "cases", "cbr_cases.csv"))

def get_text_from_xml(xml_path):
    """Parsira Akoma Ntoso XML i izvlači sirovi tekst iz svih paragrafa (<p>)."""
    try:
        tree = ET.parse(xml_path)
        root = tree.getroot()
        
        # Akoma Ntoso koristi imenske prostore (namespaces)
        namespaces = {'akn': 'http://docs.oasis-open.org/legaldocml/ns/akn/3.0'}
        
        paragraphs = root.findall('.//akn:p', namespaces)
        if not paragraphs:
            # Fallback ako nema namespace-a u parseru
            paragraphs = root.findall('.//p')
            
        text_content = []
        for p in paragraphs:
            # itertext() hvata i tekst unutar <ref> dece (anotirane presude),
            # za razliku od p.text koji uzima samo tekst pre prvog deteta.
            t = "".join(p.itertext()).strip()
            if t:
                text_content.append(t)

        full_text = " ".join(text_content)
        # Agresivno čišćenje višestrukih razmaka i preloma
        full_text = re.sub(r'\s+', ' ', full_text)
        return full_text
    except Exception as e:
        print(f"[GREŠKA] Neuspešno parsiranje XML fajla {xml_path}: {str(e)}")
        return ""

def format_case_number_from_filename(filename):
    """Fallback mehanizam ako se broj predmeta ne izvuče iz teksta."""
    name, _ = os.path.splitext(filename)
    parts = name.split('-')
    if len(parts) >= 3:
        return f"{parts[0].upper()} {parts[1]}/{parts[2]}"
    return "NEPOZNATO"

def build_database():
    print("=== Pokretanje generisanja CBR baze slučajeva ===")
    print(f"Direktorijum sa XML presudama: {XML_DIR}")
    print(f"Izlazni CSV fajl: {CSV_OUTPUT}")

    # Sva polja definisana u predicate_dictionary.yaml koja moraju biti u CSV-u
    headers = [
        "id", "caseNumber", "court", "date", "articleViolated",
        "substanceQuantityM3", "forestAreaHa", "pollutionTarget", 
        "substanceType", "intent", "priorConviction", "remediedDamage", 
        "damageExtent", "sentenceType", "sentenceMonths"
    ]

    if not os.path.exists(XML_DIR):
        print(f"[UPOZORENJE] Direktorijum {XML_DIR} ne postoji. Pravim prazan direktorijum...")
        os.makedirs(XML_DIR, exist_ok=True)
        return

    xml_files = [f for f in os.listdir(XML_DIR) if f.endswith('.xml')]
    if not xml_files:
        print("[UPOZORENJE] Nema pronađenih XML fajlova u direktorijumu 'data/judgments/'.")
        print("Molimo smestite kzz-151-2024.xml i kzz-259-2014.xml u taj direktorijum.")
        
    rows = []
    current_id = 1

    for filename in xml_files:
        xml_path = os.path.join(XML_DIR, filename)
        print(f"\n[OBRADA] Fajl: {filename}")
        
        # 1. Izvlačenje sirovog teksta iz XML-a
        raw_text = get_text_from_xml(xml_path)
        if not raw_text:
            print(f"[PRESKOČENO] Fajl {filename} je prazan ili neispravan.")
            continue
            
        # 2. Pokretanje našeg NLP pipeline-a
        metadata = extract_metadata(raw_text)
        facts = extract_facts(raw_text)
        
        # 3. Inicijalizacija reda sa podrazumevanim vrednostima
        row_data = {h: "NEPOZNATO" for h in headers}
        row_data["id"] = str(current_id)
        
        # Mapiranje metapodataka
        if metadata:
            row_data["caseNumber"] = metadata.caseNumber if metadata.caseNumber else "NEPOZNATO"
            row_data["court"] = metadata.court if metadata.court else "NEPOZNATO"
            row_data["date"] = metadata.date if metadata.date else "NEPOZNATO"
            
        if row_data["caseNumber"] == "NEPOZNATO":
            row_data["caseNumber"] = format_case_number_from_filename(filename)
            
        # 4. Mapiranje i normalizacija činjenica (True/False -> DA/NE za CBR)
        for fact in facts:
            pred = fact.predicate
            if pred in row_data:
                val_str = str(fact.value).strip()
                if val_str == "True":
                    val_str = "DA"
                elif val_str == "False":
                    val_str = "NE"
                row_data[pred] = val_str

        rows.append(row_data)
        print(f"[USPEH] Izvučen broj predmeta: {row_data['caseNumber']} | Činjenica pronađeno: {len(facts)}")
        current_id += 1

    # 5. Upis u CSV fajl sa navodnicima
    os.makedirs(os.path.dirname(os.path.abspath(CSV_OUTPUT)), exist_ok=True)
    try:
        with open(CSV_OUTPUT, mode='w', newline='', encoding='utf-8') as f:
            writer = csv.DictWriter(f, fieldnames=headers, quoting=csv.QUOTE_ALL)
            writer.writeheader()
            writer.writerows(rows)
        print(f"\n[BINGO] Baza uspešno generisana! Ukupno zapisa: {len(rows)}")
    except Exception as e:
        print(f"[GREŠKA] Neuspešan upis u CSV: {str(e)}")

if __name__ == "__main__":
    build_database()