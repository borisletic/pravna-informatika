import os
import json
import time
import httpx
import xml.etree.ElementTree as ET

# Uvozimo naš trenutni (hibridni) ekstraktor za poređenje
from extractors.fact_extractor import extract_facts as extract_facts_regex

# =====================================================================
# 1. LLM KONFIGURACIJA I PROMPT (lokalni Ollama — bez API ključa)
# =====================================================================

OLLAMA_URL = os.environ.get("OLLAMA_URL", "http://localhost:11434")
OLLAMA_MODEL = os.environ.get("OLLAMA_MODEL", "mistral")

SYSTEM_PROMPT = """
Ti si ekspertni pravni asistent (NLP sistem) specijalizovan za krivično pravo Republike Srbije, posebno za ekološka krivična dela.
Tvoj zadatak je da iz priloženog teksta presude izvučeš tačno određene činjenice i vratiš ih ISKLJUČIVO u JSON formatu.

Pravila za vrednosti (prema predicate_dictionary.yaml):
- articleViolated: (String) Broj prekršenog člana Krivičnog zakonika (samo broj, npr. "260", "269", "274"). Fokusiraj se na materijalno pravo, ignoriši ZKP (članove poput 488, 438).
- substanceQuantityM3: (Float) Količina zagađujuće materije u kubicima.
- forestAreaHa: (Float) Površina pustošenja šume u hektarima.
- pollutionTarget: (String) VODA, VAZDUH, TLO, ili SUMA.
- substanceType: (String) OPASNE_MATERIJE, NAFTNI_DERIVATI, DRVO, ZIVOTINJE_RIBE, ili KOMUNALNI_OTPAD.
- intent: (String) UMISLJAJ ili NEHAT.
- priorConviction: (String) "True" ako je osuđivan ranije, "False" ako je neosuđivan.
- remediedDamage: (String) "True" ako je naknadno sanirao štetu.
- damageExtent: (String) VELIKA ili MALA.
- sentenceType: (String) ZATVOR, NOVCANA, USLOVNA, ili RAD_U_JAVNOM_INTERESU.
- sentenceMonths: (String) Ukupna dužina kazne pretvorena isključivo u mesece (npr. "6", "18").

Ako se neka činjenica NE SPOMINJE u tekstu, nemoj je uključiti u JSON. 
Vrati isključivo validan JSON objekat gde su ključevi nazivi činjenica, a vrednosti izvučeni podaci.
"""

def extract_facts_llm(text: str):
    """Šalje tekst lokalnom Ollama modelu i vraća parsiran JSON."""
    try:
        resp = httpx.post(
            f"{OLLAMA_URL}/api/chat",
            json={
                "model": OLLAMA_MODEL,
                "format": "json",
                "stream": False,
                "options": {"temperature": 0.0},
                "messages": [
                    {"role": "system", "content": SYSTEM_PROMPT},
                    {"role": "user", "content": f"Tekst presude:\n{text[:10000]}"},
                ],
            },
            timeout=httpx.Timeout(180.0, connect=2.0),
        )
        if resp.status_code != 200:
            return {"error": f"Ollama HTTP {resp.status_code}"}
        return json.loads(resp.json().get("message", {}).get("content", "{}"))
    except Exception as e:
        return {"error": str(e), "hint": "Pokreni Ollama: 'ollama pull mistral' pa 'ollama serve'"}

# =====================================================================
# 2. POMOĆNE FUNKCIJE I IZVRŠENJE EKSPERIMENTA
# =====================================================================

def get_text_from_xml(xml_path):
    try:
        tree = ET.parse(xml_path)
        namespaces = {'akn': 'http://docs.oasis-open.org/legaldocml/ns/akn/3.0'}
        paragraphs = tree.getroot().findall('.//akn:p', namespaces)
        if not paragraphs: paragraphs = tree.getroot().findall('.//p')
        
        text_content = [p.text.strip() for p in paragraphs if p.text]
        return " ".join(text_content)
    except Exception as e: 
        print(f"Greška pri parsiranju XML-a: {e}")
        return ""

def run_experiment():
    print("=== POKREĆEM EKSPERIMENT: HIBRIDNI METOD VS LLM ===\n")
    
    # Sigurno nalaženje putanje do XML fajla bez obzira odakle se pokreće
    BASE_DIR = os.path.dirname(os.path.abspath(__file__))
    test_file = os.path.normpath(os.path.join(BASE_DIR, "..", "data", "judgments", "kzz-151-2024.xml"))
    
    if not os.path.exists(test_file):
        print(f"[GREŠKA] Ne mogu da nađem fajl na putanji:\n{test_file}")
        print("Proveri da li je direktorijumska struktura tačna.")
        return

    text = get_text_from_xml(test_file)
    print(f"Presuda uspešno učitana. Dužina teksta: {len(text)} karaktera.\n")

    if len(text) == 0:
        print("[GREŠKA] Tekst je prazan, verovatno XML format nije dobro pročitan.")
        return

    # 1. Hibridni metod (Trenutna implementacija)
    print("--- 1. POKREĆEM REGEX/SPACY METOD ---")
    start_time = time.time()
    regex_facts = extract_facts_regex(text)
    regex_time = time.time() - start_time
    
    regex_dict = {f.predicate: str(f.value) for f in regex_facts}
    print(f"Završeno za {regex_time:.3f} sekundi!")
    print("Rezultati:")
    print(json.dumps(regex_dict, indent=2, ensure_ascii=False))
    print("\n")

    # 2. LLM metod
    print(f"--- 2. POKREĆEM LLM METOD (Ollama: {OLLAMA_MODEL}) ---")
    start_time = time.time()
    llm_dict = extract_facts_llm(text)
    llm_time = time.time() - start_time
    
    print(f"Završeno za {llm_time:.3f} sekundi!")
    print("Rezultati:")
    print(json.dumps(llm_dict, indent=2, ensure_ascii=False))
    print("\n")

    # 3. ZAKLJUČAK
    print("=== ANALIZA PERFORMANSI ===")
    print(f"Brzina Regexa: {regex_time:.3f} s")
    print(f"Brzina LLM-a:  {llm_time:.3f} s")

if __name__ == "__main__":
    run_experiment()