#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
annotate_judgments.py — Celina 2 (Akoma Ntoso anotacija sudskih odluka).
VLASNIK: Član 2 (NLP & Data).

Pretvara SIROVE presude (samo <p> tekst + FRBRthis) u POTPUNO anotiran
Akoma Ntoso dokument:
  - FRBRalias caseNumber + court, FRBRdate(name=judgment), FRBRauthor
  - <references>: TLCOrganization (sud), TLCPerson (sudije, zapisničar, stranke),
    TLCReference (Krivični zakonik)
  - struktuiran <judgmentBody>: <introduction> / <decision> / <motivation>
  - <ref href="/laws/kz#art_N"> oko referenci na članove KZ 260-277 (klikabilno, Celina 7)

Kurira i domen: odluke koje NE citiraju KZ čl. 260-277 (parnice, upravni sporovi,
privredni prestupi po Zakonu o zaštiti vazduha/voda) se izmeštaju u
data/judgments/_excluded/ da ne zagađuju bazu znanja.

Pokretanje:  python nlp-service/annotate_judgments.py
"""
import os, re, glob, shutil, datetime
import xml.etree.ElementTree as ET

BASE = os.path.dirname(os.path.abspath(__file__))
JDIR = os.path.normpath(os.path.join(BASE, "..", "data", "judgments"))
EXCLUDED = os.path.join(JDIR, "_excluded")
AKN = "http://docs.oasis-open.org/legaldocml/ns/akn/3.0"

# ćirilica
U = "ЂЈЉЊЋЏА-Я"   # velika slova (+ Ђ Ј Љ Њ Ћ Џ)
L = "а-яђјљњћџ"    # mala slova
NAME = rf"[{U}][{L}]+(?:\s+[{U}][{L}]+){{1,2}}"

ENV_MIN, ENV_MAX = 260, 277


def raw_paragraphs(path):
    root = ET.parse(path).getroot()
    ns = {'akn': AKN}
    ps = root.findall('.//akn:p', ns) or root.findall('.//p')
    return [(p.text or "").strip() for p in ps if (p.text or "").strip()]


def already_annotated(path):
    txt = open(path, encoding="utf-8").read()
    return "FRBRalias" in txt and "caseNumber" in txt


def esc(s):
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


# ---------- ekstrakcija ----------
CASE_NO_RE = re.compile(
    rf"(?:Кзз|Кжм?|Кж\d|Пкж|Пж|Кд|Рев|Гж\d?|Р\d|Бр|У|К)\.?\s*(?:бр\.?\s*)?(\d+)\s*/\s*(\d{{2,4}})")

def find_case_number(paras, jid):
    """Broj predmeta iz pravog zaglavlja (prvih 6 paragrafa). Bira match čiji
    se broj poklapa sa brojem iz imena fajla (npr. br-143-2012 -> .../143/...),
    da ne uhvati referencirani niži sud."""
    head = " ".join(paras[:6])
    matches = list(CASE_NO_RE.finditer(head))
    if not matches:
        return None
    fm = re.match(r"[a-zA-Z]+-(\d+)-(\d{2,4})", jid)
    if fm:
        want_num = fm.group(1)
        for m in matches:
            if m.group(1) == want_num:
                return re.sub(r"\s+", " ", m.group(0)).strip()
    return re.sub(r"\s+", " ", matches[0].group(0)).strip()


def find_court(head):
    pats = [
        rf"Врховн[{L}]+\s+касацион[{L}]+\s+суд[{L}]*",
        rf"Привредн[{L}]+\s+апелацион[{L}]+\s+суд[{L}]*",
        rf"Прекршајн[{L}]+\s+апелацион[{L}]+\s+суд[{L}]*",
        rf"Апелацион[{L}]+\s+суд[{L}]*(?:\s+у\s+[{U}][{L}]+)?",
        rf"Врховн[{L}]+\s+суд[{L}]*(?:\s+Србије)?(?:\s+у\s+[{U}][{L}]+)?",
        rf"Виш[{L}]+\s+суд[{L}]*(?:\s+у\s+[{U}][{L}]+)?",
        rf"Основн[{L}]+\s+суд[{L}]*(?:\s+у\s+[{U}][{L}]+)?",
        rf"Управн[{L}]+\s+суд[{L}]*",
    ]
    # all-caps varijanta "ВРХОВНИ СУД"
    up = head.upper()
    for kw in ["ВРХОВНИ КАСАЦИОНИ СУД", "ПРИВРЕДНИ АПЕЛАЦИОНИ СУД",
               "ПРЕКРШАЈНИ АПЕЛАЦИОНИ СУД", "АПЕЛАЦИОНИ СУД", "ВРХОВНИ СУД",
               "УПРАВНИ СУД", "ВИШИ СУД", "ОСНОВНИ СУД"]:
        if kw in up:
            base = kw.title()
            # dohvati "у Београду" ako sledi
            i = up.find(kw) + len(kw)
            tail = head[i:i+18]
            mt = re.search(rf"у\s+[{U}][{L}]+", tail)
            return base + (" " + mt.group(0) if mt else "")
    for p in pats:
        m = re.search(p, head)
        if m:
            return re.sub(r"\s+", " ", m.group(0)).strip()
    return None


def find_date(head):
    m = re.search(r"(\d{1,2})\.\s*(\d{1,2})\.\s*(\d{4})", head)
    if m:
        return f"{m.group(3)}-{int(m.group(2)):02d}-{int(m.group(1)):02d}"
    return None


def find_judges(text):
    judges = []
    m = re.search(rf"састављен[{L}]+\s+од\s+судиј[{L}]+\s*:?(.{{0,400}})", text)
    span = m.group(1) if m else ""
    # presеci kod 'записничар' / 'чланова већа' / 'донео'
    span = re.split(r"записничар|донео|донела|једногласно", span)[0]
    for nm in re.findall(NAME, span):
        nm = nm.strip()
        if nm not in judges and "суд" not in nm.lower():
            judges.append(nm)
    return judges[:6]


def find_recorder(text):
    m = re.search(rf"(?:саветником|записничар[{L}]*)\s+({NAME})", text)
    if m:
        return m.group(1).strip()
    m = re.search(rf"({NAME})\s*,?\s+као\s+записничар", text)
    return m.group(1).strip() if m else None


def find_parties(text):
    parties = []
    for m in re.finditer(rf"окривљен[{L}]*\s+([{U}]{{2,3}})\b", text):
        ini = m.group(1)
        if ini not in [p[1] for p in parties]:
            parties.append(("OKR", ini))
    for m in re.finditer(rf"оптужен[{L}]*\s+([{U}]{{2,3}})\b", text):
        ini = m.group(1)
        if ini not in [p[1] for p in parties]:
            parties.append(("OPT", ini))
    return parties[:6]


def env_articles(text):
    arts = set()
    for m in re.finditer(r"чл(?:ан|ана|\.)\s*(\d{1,3})", text):
        n = int(m.group(1))
        if ENV_MIN <= n <= ENV_MAX:
            arts.add(n)
    return sorted(arts)


# ---------- ref wrapping ----------
REF_RE = re.compile(r"(чл(?:ан|ана|ану|аном)?\.?\s*)(\d{1,3})")

def wrap_refs(escaped_text):
    """Obavija reference na članove KZ 260-277 u <ref href=/laws/kz#art_N>."""
    def repl(m):
        n = int(m.group(2))
        if ENV_MIN <= n <= ENV_MAX:
            return f'{m.group(1)}<ref href="/laws/kz#art_{n}">{m.group(2)}</ref>'
        return m.group(0)
    return REF_RE.sub(repl, escaped_text)


def para_block(paras):
    out = []
    for p in paras:
        out.append("        <p>" + wrap_refs(esc(p)) + "</p>")
    return "\n".join(out)


def split_body(paras):
    """Deli na introduction / decision / motivation po markerima."""
    def norm(s): return re.sub(r"\s+", "", s).upper()
    dec_i = mot_i = None
    for i, p in enumerate(paras):
        n = norm(p)
        if dec_i is None and (n in ("ПРЕСУДУ", "РЕШЕЊЕ", "РЕШЕЊЕ:") or n.startswith("ПРЕСУДУ")):
            dec_i = i
        if mot_i is None and n.startswith("ОБРАЗЛОЖЕЊЕ"):
            mot_i = i
            break
    if dec_i is None and mot_i is None:
        return paras, [], []
    if dec_i is None:
        return paras[:mot_i], [], paras[mot_i:]
    if mot_i is None:
        return paras[:dec_i], paras[dec_i:], []
    return paras[:dec_i], paras[dec_i:mot_i], paras[mot_i:]


def slug(s):
    return re.sub(r"[^a-z0-9]+", "-", (s or "court").lower()).strip("-") or "court"


def build_akn(jid, paras):
    head = " ".join(paras[:18])
    full = " ".join(paras)
    case_no = find_case_number(paras, jid) or jid.replace("-", " ").title()
    court = find_court(head) or "Непознат суд"
    date = find_date(head)
    year = (date or "").split("-")[0] or (re.search(r"(\d{4})", jid).group(1) if re.search(r"\d{4}", jid) else "2000")
    judges = find_judges(full)
    recorder = find_recorder(full)
    parties = find_parties(full)
    arts = env_articles(full)

    intro, decision, motivation = split_body(paras)

    refs = []
    refs.append(f'        <TLCOrganization eId="court" href="/akn/ontology/organization/rs/{slug(court)}" showAs="{esc(court)}"/>')
    refs.append('        <TLCPerson eId="nlpAnnotator" href="/akn/ontology/person/rs/nlpAnnotator" showAs="NLP anotator"/>')
    for i, j in enumerate(judges, 1):
        refs.append(f'        <TLCPerson eId="judge_{i}" href="/akn/ontology/person/rs/{slug(j)}" showAs="{esc(j)}"/>')
    if recorder:
        refs.append(f'        <TLCPerson eId="recorder" href="/akn/ontology/person/rs/{slug(recorder)}" showAs="{esc(recorder)}"/>')
    for i, (role, ini) in enumerate(parties, 1):
        refs.append(f'        <TLCPerson eId="party_{role.lower()}_{i}" href="/akn/ontology/person/rs/anon-{ini}" showAs="{ini}"/>')
    refs.append('        <TLCReference eId="kz" href="/akn/rs/act/2005/kz" showAs="Кривични законик"/>')

    judges_attr = ""
    body = []
    if intro:
        body.append("      <introduction>\n" + para_block(intro) + "\n      </introduction>")
    if decision:
        body.append("      <decision>\n" + para_block(decision) + "\n      </decision>")
    if motivation:
        body.append("      <motivation>\n" + para_block(motivation) + "\n      </motivation>")
    body_xml = "\n".join(body)

    date_attr = f' date="{date}"' if date else ' date="2000-01-01"'

    xml = f"""<?xml version="1.0" encoding="UTF-8"?>
<akomaNtoso xmlns="{AKN}">
  <judgment name="judgment">
    <meta>
      <identification source="#nlpAnnotator">
        <FRBRWork>
          <FRBRthis value="/akn/rs/judgment/{year}/{jid}/main"/>
          <FRBRuri value="/akn/rs/judgment/{year}/{jid}"/>
          <FRBRalias name="caseNumber" value="{esc(case_no)}"/>
          <FRBRalias name="court" value="{esc(court)}"/>
          <FRBRdate{date_attr} name="judgment"/>
          <FRBRauthor href="#court"/>
          <FRBRcountry value="rs"/>
        </FRBRWork>
        <FRBRExpression>
          <FRBRthis value="/akn/rs/judgment/{year}/{jid}/srp@/main"/>
          <FRBRuri value="/akn/rs/judgment/{year}/{jid}/srp@"/>
          <FRBRdate{date_attr} name="judgment"/>
          <FRBRauthor href="#court"/>
          <FRBRlanguage language="srp"/>
        </FRBRExpression>
        <FRBRManifestation>
          <FRBRthis value="/akn/rs/judgment/{year}/{jid}/srp@/main.xml"/>
          <FRBRuri value="/akn/rs/judgment/{year}/{jid}/srp@.akn"/>
          <FRBRdate date="{datetime.date.today().isoformat()}" name="generation"/>
          <FRBRauthor href="#nlpAnnotator"/>
        </FRBRManifestation>
      </identification>
      <references source="#nlpAnnotator">
{chr(10).join(refs)}
      </references>
    </meta>
    <judgmentBody>
{body_xml}
    </judgmentBody>
  </judgment>
</akomaNtoso>
"""
    meta = dict(case_no=case_no, court=court, date=date, judges=judges,
                recorder=recorder, parties=parties, arts=arts)
    return xml, meta


def main():
    os.makedirs(EXCLUDED, exist_ok=True)
    files = sorted(glob.glob(os.path.join(JDIR, "*.xml")))
    annotated, excluded, skipped = [], [], []
    for path in files:
        jid = os.path.splitext(os.path.basename(path))[0]
        try:
            paras = raw_paragraphs(path)
        except Exception as e:
            print(f"[ERR] {jid}: {e}"); continue
        if not paras:
            print(f"[PRAZNO] {jid}"); continue
        full = " ".join(paras)
        arts = env_articles(full)
        if not arts:
            shutil.move(path, os.path.join(EXCLUDED, os.path.basename(path)))
            excluded.append(jid)
            continue
        if already_annotated(path):
            skipped.append(jid); continue
        xml, meta = build_akn(jid, paras)
        with open(path, "w", encoding="utf-8") as f:
            f.write(xml)
        annotated.append((jid, meta))

    print(f"\n=== ANOTIRANO: {len(annotated)} odluka (KZ čl. 260-277) ===")
    for jid, m in annotated:
        print(f"  {jid:<18} {m['case_no']:<16} čl.{m['arts']}  sudije={len(m['judges'])} stranke={len(m['parties'])}")
    print(f"\n=== IZMEŠTENO (van domena -> _excluded/): {len(excluded)} ===")
    print("  " + ", ".join(excluded))
    if skipped:
        print(f"\n=== preskočeno (već anotirano): {len(skipped)} ===")
    print(f"\nUKUPNO u bazi posle kuriranja: {len(annotated) + len(skipped)}")


if __name__ == "__main__":
    main()
