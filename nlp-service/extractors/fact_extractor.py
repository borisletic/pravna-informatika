"""
Ekstrakcija pravnih činjenica iz teksta.
VLASNIK: Član 2.
CELINA: 4.

Proširena verzija sa sveobuhvatnim Regex pravilima za obuhvatanje
srpske pravne terminologije (ćirilica i latinica, različiti padeži).
Obezbeđuje maksimalnu ekstrakciju bez halucinacija.
"""
import re
from typing import List

def extract_facts(text: str) -> List:
    from main import Fact, SourceSpan
    facts = []
    text_lower = text.lower()

    # Pomoćna funkcija za dodavanje činjenica bez dupliranja
    def add_fact(predicate, value, confidence, match=None):
        span = SourceSpan(start=match.start(), end=match.end()) if match else None
        if not any(f.predicate == predicate for f in facts):
            facts.append(Fact(predicate=predicate, value=value, confidence=confidence, sourceSpan=span))

    # === 1. substanceQuantityM3 ===
    m_qty = re.search(r'(\d+(?:[.,]\d+)?)\s*(?:m³|m3|m\^3|кубних|kubnih|кубика|kubika)', text_lower)
    if m_qty:
        try:
            val = float(m_qty.group(1).replace(',', '.'))
            add_fact("substanceQuantityM3", val, 0.95, m_qty)
        except ValueError: pass

    # === 2. forestAreaHa ===
    m_ha = re.search(r'(\d+(?:[.,]\d+)?)\s*(?:ha|хектара|hektara|ари|ari|ar|ар\b)', text_lower)
    if m_ha:
        try:
            val = float(m_ha.group(1).replace(',', '.'))
            if 'ar' in m_ha.group(0) or 'ар' in m_ha.group(0):
                val /= 100.0
            add_fact("forestAreaHa", val, 0.95, m_ha)
        except ValueError: pass

    # === 3. pollutionTarget ===
    if re.search(r'\b(река|reka|реку|reku|реци|reci|језер|jezer|воду|vodu|води|vodi|море|more|поток|potok|канал|kanal)\b', text_lower):
        add_fact("pollutionTarget", "VODA", 0.85)
    elif re.search(r'\b(ваздух|vazduh|атмосфер|atmosfer|дим|dim)\b', text_lower):
        add_fact("pollutionTarget", "VAZDUH", 0.85)
    elif re.search(r'\b(тло|tlo|земљишт|zemljišt|земљ|zemlj|њив|njiv)\b', text_lower):
        add_fact("pollutionTarget", "TLO", 0.85)
    elif re.search(r'\b(шум|šum|парк|park|дрвећ|drveć)\b', text_lower):
        add_fact("pollutionTarget", "SUMA", 0.85)

    # === 4. intent (Umišljaj / Nehat) ===
    if re.search(r'\b(умишљај|umišljaj|намерно|namerno|свесн[оа]|svesn[oa]|хтео|hteo|хтела|htela|са\sумишљајем|sa\sumišljajem)\b', text_lower):
        add_fact("intent", "UMISLJAJ", 0.85)
    elif re.search(r'\b(нехат|nehat|непажњ|nepažnj|из\sнехата|iz\snehata|олако|olako)\b', text_lower):
        add_fact("intent", "NEHAT", 0.85)

    # === 5. priorConviction (Prethodna osuđivanost) ===
    # Neosuđivanost ima prioritet proveravanja
    if re.search(r'\b(неосуђиван|neosuđivan|раније\sнеосуђиван|ranije\sneosuđivan|без\sпретходних|bez\sprethodnih|није\sосуђиван|nije\sosuđivan)\b', text_lower):
        add_fact("priorConviction", "False", 0.9)
    elif re.search(r'\b(раније\sосуђиван|ranije\sosuđivan|претходно\sосуђиван|prethodno\sosuđivan|осуђиван|osuđivan|повратник|povratnik)\b', text_lower):
        add_fact("priorConviction", "True", 0.9)

    # === 6. remediedDamage (Sanirana šteta) ===
    if re.search(r'\b(отклонио|otklonio|надокнадио|nadoknadio|санирао|sanirao|поправио|popravio|вратио|vratio|исплатио|isplatio)\b', text_lower):
        add_fact("remediedDamage", "True", 0.8)

    # === 7. damageExtent (Obim oštećenja) ===
    if re.search(r'\b(велика|velika|знатна|znatna|огромна|ogromna|већих\sразмера|većih\srazmera)\b', text_lower):
        add_fact("damageExtent", "VELIKA", 0.8)
    elif re.search(r'\b(мала|mala|незнатна|neznatna|мањег\sобима|manjeg\sobima)\b', text_lower):
        add_fact("damageExtent", "MALA", 0.8)

    # === 8. substanceType (Tip materije / predmeta dela) ===
    if re.search(r'\b(нафт|naft|горив|goriv|бензин|benzin|мазут|mazut|уљ[еа]|ulj[ea])\b', text_lower):
        add_fact("substanceType", "NAFTNI_DERIVATI", 0.85)
    elif re.search(r'\b(отпад|otpad|смећ|smeć|депониј|deponij|шут|šut)\b', text_lower):
        add_fact("substanceType", "KOMUNALNI_OTPAD", 0.85)
    elif re.search(r'\b(хемикалиј|hemikalij|отров|otrov|киселин|kiselin|опасн|opasn)\b', text_lower):
        add_fact("substanceType", "OPASNE_MATERIJE", 0.85)
    elif re.search(r'\b(дрв[оа]|drv[oa]|стабл[оа]|stabl[oa]|шумск|šumsk|балван|balvan)\b', text_lower):
        add_fact("substanceType", "DRVO", 0.85)
    elif re.search(r'\b(риб[аеу]|rib[aeu]|мреж[ае]|mrež[ae]|дивљач|divljač|срн[ае]|srn[ae]|фазан|fazan|бабушк[ае]|babušk[ae]|штук[ае]|štuk[ae])\b', text_lower):
        add_fact("substanceType", "ZIVOTINJE_RIBE", 0.85)

    # === 9. articleViolated (Prekršeni član zakona) ===
    m_art = re.search(r'\bчл(?:ан|ана|\.)?\s*(26[0-9]|27[0-7])\b', text_lower)
    if m_art:
        add_fact("articleViolated", f"art_{m_art.group(1)}", 0.9, m_art)

    # === 10. sentenceType (Vrsta kazne) ===
    if re.search(r'\b(условн[ау]\sосуд[ау]|uslovn[au]\sosud[au]|условно|uslovno)\b', text_lower):
        add_fact("sentenceType", "USLOVNA", 0.9)
    elif re.search(r'\b(затвор|zatvor)\b', text_lower):
        add_fact("sentenceType", "ZATVOR", 0.9)
    elif re.search(r'\b(новчан[ау]\sказн[ау]|novčan[au]\skazn[au])\b', text_lower):
        add_fact("sentenceType", "NOVCANA", 0.9)

    # === 11. sentenceMonths (Dužina kazne) ===
    m_months = re.search(r'(\d+)\s*(?:месеца|meseca|месеци|meseci)', text_lower)
    m_years = re.search(r'(\d+)\s*(?:годин|godin)', text_lower)
    
    # Konverzija tekstualnih brojeva u numeričke vrednosti
    text_nums = {
        'један': '1', 'два': '2', 'три': '3', 'четири': '4', 'пет': '5', 'шест': '6',
        'седам': '7', 'осам': '8', 'девет': '9', 'десет': '10', 'једну': '12'
    }
    
    if m_months:
        add_fact("sentenceMonths", m_months.group(1), 0.85, m_months)
    else:
        for text_num, num in text_nums.items():
            if re.search(rf'\b{text_num}\s*(?:месеца|meseca|месеци|meseci|годину|godinu)\b', text_lower):
                add_fact("sentenceMonths", num, 0.85)
                break

    if not any(f.predicate == "sentenceMonths" for f in facts) and m_years:
        try:
            months = int(m_years.group(1)) * 12
            add_fact("sentenceMonths", str(months), 0.85, m_years)
        except ValueError: pass

    return facts