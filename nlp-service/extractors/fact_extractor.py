"""
Ekstrakcija pravnih činjenica iz teksta.

VLASNIK: Član 2.
CELINA: 4.

Strategija (preporučena):
  1. Regex/pravila za jasno strukturirane činjenice (količine, jedinice).
  2. ML/transformers (BERTić, XLM-R) za kategorijalne klasifikacije.
  3. Konfidencija = 1.0 za regex, ML model vraća pravu konfidenciju.

UGOVOR: predikati moraju da odgovaraju onima u
data/schemas/predicate_dictionary.yaml.
"""
import re
from typing import List


def extract_facts(text: str) -> List:
    """
    Glavni ulaz - vraća listu Fact objekata.
    """
    from main import Fact, SourceSpan

    facts = []
    text_lower = text.lower()

    # === substanceQuantityM3 ===
    for m in re.finditer(r'(\d+(?:[.,]\d+)?)\s*(?:m³|m3|m\^3|кубних метара|kubnih metara)', text):
        try:
            value = float(m.group(1).replace(',', '.'))
            facts.append(Fact(
                predicate="substanceQuantityM3",
                value=value,
                confidence=0.95,
                sourceSpan=SourceSpan(start=m.start(), end=m.end()),
            ))
        except ValueError:
            pass

    # === forestAreaHa ===
    for m in re.finditer(r'(\d+(?:[.,]\d+)?)\s*(?:ha|хектара|hektara)', text):
        try:
            value = float(m.group(1).replace(',', '.'))
            facts.append(Fact(
                predicate="forestAreaHa",
                value=value,
                confidence=0.9,
                sourceSpan=SourceSpan(start=m.start(), end=m.end()),
            ))
        except ValueError:
            pass

    # === substanceType - jednostavna leksikalna pravila ===
    type_keywords = {
        "HEMIJSKI_OTPAD": ["хемијски отпад", "hemijski otpad", "хемикалија", "hemikalija"],
        "RADIOAKTIVNI_OTPAD": ["радиоактивн", "radioaktivn"],
        "KOMUNALNI_OTPAD": ["комунални отпад", "komunalni otpad", "смеће", "smeće"],
        "NAFTNI_DERIVATI": ["нафт", "naft", "уље", "ulje", "мазут", "mazut"],
    }
    for stype, kws in type_keywords.items():
        for kw in kws:
            idx = text_lower.find(kw)
            if idx >= 0:
                facts.append(Fact(
                    predicate="substanceType",
                    value=stype,
                    confidence=0.7,
                    sourceSpan=__import__("main").SourceSpan(start=idx, end=idx + len(kw)),
                ))
                break

    # === pollutionTarget ===
    if re.search(r'\b(река|reka|језер|jezer|воду|vodu)', text_lower):
        facts.append(Fact(predicate="pollutionTarget", value="VODA", confidence=0.85))
    elif re.search(r'\b(ваздух|vazduh|атмосфер|atmosfer)', text_lower):
        facts.append(Fact(predicate="pollutionTarget", value="VAZDUH", confidence=0.85))
    elif re.search(r'\b(тло|tlo|земљишт|zemljišt)', text_lower):
        facts.append(Fact(predicate="pollutionTarget", value="TLO", confidence=0.85))

    # === intent ===
    if re.search(r'\b(намерно|namerно|умишљај|umišljaj|свесно|svesno)', text_lower):
        facts.append(Fact(predicate="intent", value="UMISLJAJ", confidence=0.7))
    elif re.search(r'\b(нехат|nehat|непаж|nepaž)', text_lower):
        facts.append(Fact(predicate="intent", value="NEHAT", confidence=0.7))

    # === priorConviction ===
    if re.search(r'\b(претходно осуђив|prethodno osuđiv|раније осуђив|ranije osuđiv)', text_lower):
        facts.append(Fact(predicate="priorConviction", value=True, confidence=0.8))

    # TODO Član 2:
    #  - damageExtent (lakša, teža, naročito teška) - regex + možda ML
    #  - protectedSpecies, usesExplosives - za delove ribolov/lov
    #  - remediedDamage - značajno teže, treba semantičko razumevanje
    #  - Generalno: zameniti regexe sa BERT-baziranim klasifikatorima

    return facts
