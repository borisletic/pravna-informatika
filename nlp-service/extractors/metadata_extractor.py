"""
Ekstrakcija metapodataka iz odluka.

TODO Član 2: zameniti regex-e pravom NER implementacijom (classla/spaCy).
Za sada - dovoljno regex-a da Spring strana može da testira komunikaciju.
"""
import re
from typing import List


def extract_metadata(text: str):
    """
    Vraća Metadata Pydantic objekat (definisan u main.py).
    Importujemo ga lokalno da izbegnemo cirkulu.
    """
    from main import Metadata, Party

    return Metadata(
        caseNumber=_extract_case_number(text),
        court=_extract_court(text),
        date=_extract_date(text),
        parties=_extract_parties(text),
        judges=_extract_judges(text),
        recorder=_extract_recorder(text),
    )


# ============================================================
# Regex bazirana ekstrakcija - dovoljno za prototip i mock
# ============================================================

def _extract_case_number(text: str):
    # Primer obrazaca: K-145/2019, К.123/20, Kž 45/18
    patterns = [
        r'\b[КKkК][жZzЖ]?\s*[-.]?\s*\d+\s*/\s*\d{2,4}\b',
        r'\b[КK]\.?\s*\d+\s*/\s*\d{2,4}\b',
    ]
    for p in patterns:
        m = re.search(p, text)
        if m:
            return m.group(0).strip()
    return None


def _extract_court(text: str):
    patterns = [
        r'(Виши|Основни|Апелациони|Врховни касациони)\s+суд\s+у\s+\w+',
        r'(Viši|Osnovni|Apelacioni|Vrhovni kasacioni)\s+sud\s+u\s+\w+',
    ]
    for p in patterns:
        m = re.search(p, text)
        if m:
            return m.group(0)
    return None


def _extract_date(text: str):
    # 12.04.2019., 12. 4. 2019, 2019-04-12
    patterns = [
        r'\b(\d{1,2})\.\s*(\d{1,2})\.\s*(\d{4})\.?\b',
        r'\b(\d{4})-(\d{1,2})-(\d{1,2})\b',
    ]
    for p in patterns:
        m = re.search(p, text)
        if m:
            g = m.groups()
            if len(g[0]) == 4:  # ISO
                return f"{g[0]}-{int(g[1]):02d}-{int(g[2]):02d}"
            else:
                return f"{g[2]}-{int(g[1]):02d}-{int(g[0]):02d}"
    return None


def _extract_parties(text: str) -> List:
    from main import Party
    parties = []
    # Inicijali u formatu "M.P." ili "М.П."
    for m in re.finditer(r'\b([А-ШA-Z])\.\s*([А-ШA-Z])\.\b', text):
        parties.append(Party(role="UNKNOWN", initials=f"{m.group(1)}.{m.group(2)}."))
        if len(parties) >= 5:
            break
    return parties


def _extract_judges(text: str) -> List[str]:
    # TODO Član 2: pravi NER. Za sada - prazno.
    return []


def _extract_recorder(text: str):
    m = re.search(r'(записничар|zapisničar)[:\s]+([А-ШA-Zа-шa-z\.\s]+)', text, re.IGNORECASE)
    if m:
        return m.group(2).strip()
    return None
