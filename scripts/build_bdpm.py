#!/usr/bin/env python3
"""Construit une base SQLite compacte à partir de la BDPM (data.gouv.fr).

Usage :
    python3 scripts/build_bdpm.py [--download] [--raw data/raw] [--out data/build/bdpm.sqlite]

Sans --download, les fichiers déjà présents dans --raw sont utilisés.
Bibliothèque standard uniquement.

Source : Base de données publique des médicaments (ANSM / HAS / Assurance Maladie),
Licence Ouverte Etalab 2.0.
"""
import argparse
import re
import sqlite3
import sys
import unicodedata
import urllib.request
from pathlib import Path

BASE_URL = "https://base-donnees-publique.medicaments.gouv.fr/download/file/{}.txt"
FILES = ("CIS_bdpm", "CIS_CIP_bdpm", "CIS_COMPO_bdpm")

# Unités « comptables » : le nombre extrait est un nombre d'unités de prise.
UNITES_DISCRETES = {
    "comprimé": "comprimé", "gélule": "gélule", "capsule": "capsule",
    "pastille": "pastille", "suppositoire": "suppositoire", "ovule": "ovule",
    "gomme": "gomme", "dose": "dose", "lyophilisat": "lyophilisat",
    "sachet": "sachet", "patch": "patch", "dispositif": "dispositif",
    "ampoule": "ampoule", "stylo": "stylo", "seringue": "seringue",
    "implant": "implant", "film": "film", "tablette": "tablette",
    "bâton": "bâton", "bandelette": "bandelette", "carpule": "carpule",
    "cartouche": "cartouche", "inhalation": "inhalation", "pilule": "pilule",
}
UNITES_VOLUME = {"ml": "ml", "l": "l", "g": "g", "mg": "mg", "kg": "kg"}

RE_LEADING = re.compile(r"^(\d+)\s+(?!x\b)")
RE_CONTENANT = re.compile(
    r"^(\d+)\s+(sachet|récipient|stylo|seringue|ampoule|flacon|tube|pot|poche|cartouche|"
    r"applicateur|pilulier|boîte|boite|étui|dispositif)",
    re.IGNORECASE,
)
RE_DE = re.compile(r"\bde\s+(\d+(?:[.,]\d+)?)\s*([a-zA-Zéèêâîôûç]+)", re.IGNORECASE)


def decode(path: Path) -> str:
    """Les fichiers BDPM ne sont pas tous dans le même encodage (UTF-8 ou ISO-8859-1)."""
    raw = path.read_bytes()
    try:
        return raw.decode("utf-8")
    except UnicodeDecodeError:
        return raw.decode("iso-8859-1")


def rows(path: Path):
    for line in decode(path).splitlines():
        if line.strip():
            yield [c.strip() for c in line.split("\t")]


def normalize(s: str) -> str:
    s = unicodedata.normalize("NFD", s.lower())
    return "".join(c for c in s if unicodedata.category(c) != "Mn")


def singular(word: str) -> str:
    w = word.lower()
    return w[:-1] if w.endswith("s") and w[:-1] in UNITES_DISCRETES else w


def parse_unites(libelle: str):
    """Retourne (nb_unites, unite, fiable) ou (None, None, 0).

    Heuristique sur le libellé : « [N conditionnements] ... de M unité(s) ».
    fiable=1 : unités comptables (comprimés, gélules...) ; fiable=0 : volumes/poids,
    ou ambiguïté probable. Toujours modifiable par l'utilisateur dans l'app.
    """
    matches = list(RE_DE.finditer(libelle))
    lead_c = RE_CONTENANT.match(libelle)
    if not matches:
        # « 90 sachet(s) ... », « 10 récipient(s) unidose(s) ... » : on compte les contenants
        if lead_c:
            return int(lead_c.group(1)), lead_c.group(2).lower().replace("boite", "boîte"), 1
        return None, None, 0
    # La dernière occurrence « de N unité » est en général le contenu unitaire.
    for m in reversed(matches):
        qty_s, unit_s = m.group(1), m.group(2)
        qty = float(qty_s.replace(",", "."))
        unit = singular(unit_s)
        if unit in UNITES_DISCRETES or unit in UNITES_VOLUME:
            lead = RE_LEADING.match(libelle)
            mult = int(lead.group(1)) if lead else 1
            discrete = unit in UNITES_DISCRETES
            if not discrete and lead_c and mult > 1:
                # « 20 récipients de 0,3 ml », « 10 flacons de 50 ml » : on compte les contenants
                return mult, lead_c.group(2).lower().replace("boite", "boîte"), 1
            total = qty * mult
            nom_unite = UNITES_DISCRETES.get(unit) or UNITES_VOLUME[unit]
            total_i = int(total) if total == int(total) else total
            # Plusieurs « de N » (ex. « 5 récipients unidoses ») : moins fiable.
            fiable = 1 if discrete and len(matches) == 1 else 0
            return total_i, nom_unite, fiable
    # « de N » portait sur une unité inconnue (cm2, emplâtre...) : on compte les contenants,
    # sauf les emballages génériques (boîte, étui) dont le contenu reste inconnu.
    if lead_c and lead_c.group(2).lower() not in ("boîte", "boite", "étui"):
        return int(lead_c.group(1)), lead_c.group(2).lower(), 1
    return None, None, 0


def download(raw_dir: Path):
    raw_dir.mkdir(parents=True, exist_ok=True)
    for name in FILES:
        url = BASE_URL.format(name)
        print(f"Téléchargement {url}", file=sys.stderr)
        with urllib.request.urlopen(url, timeout=120) as r:
            (raw_dir / f"{name}.txt").write_bytes(r.read())


SCHEMA = """
CREATE TABLE specialite (
    cis INTEGER PRIMARY KEY,
    nom TEXT NOT NULL,
    nom_recherche TEXT NOT NULL,
    forme TEXT,
    voies TEXT,
    substances TEXT,
    substances_recherche TEXT
);
CREATE TABLE substance (
    cis INTEGER NOT NULL REFERENCES specialite(cis),
    ordre INTEGER NOT NULL,
    nom TEXT NOT NULL,
    dosage TEXT,
    PRIMARY KEY (cis, ordre)
);
CREATE TABLE presentation (
    cip13 TEXT PRIMARY KEY,
    cis INTEGER NOT NULL REFERENCES specialite(cis),
    cip7 TEXT,
    libelle TEXT NOT NULL,
    nb_unites REAL,
    unite TEXT,
    nb_unites_fiable INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX idx_presentation_cis ON presentation(cis);
CREATE INDEX idx_specialite_recherche ON specialite(nom_recherche);
CREATE TABLE meta (cle TEXT PRIMARY KEY, valeur TEXT);
"""


def build(raw_dir: Path, out: Path):
    for name in FILES:
        if not (raw_dir / f"{name}.txt").exists():
            sys.exit(f"Fichier manquant : {raw_dir / (name + '.txt')} (utiliser --download)")

    out.parent.mkdir(parents=True, exist_ok=True)
    if out.exists():
        out.unlink()
    db = sqlite3.connect(out)
    db.executescript(SCHEMA)

    # Spécialités commercialisées (colonne 7 : « Commercialisée »)
    cis_ok = set()
    for r in rows(raw_dir / "CIS_bdpm.txt"):
        if len(r) < 7 or r[6] != "Commercialisée":
            continue
        cis = int(r[0])
        cis_ok.add(cis)
        db.execute(
            "INSERT INTO specialite (cis, nom, nom_recherche, forme, voies) VALUES (?,?,?,?,?)",
            (cis, r[1], normalize(r[1]), r[2], r[3]),
        )

    # Substances : on garde la fraction thérapeutique (FT) si elle existe,
    # sinon la substance active (SA), par numéro de liaison.
    comp = {}  # (cis, liaison) -> (nature, nom, dosage)
    for r in rows(raw_dir / "CIS_COMPO_bdpm.txt"):
        if len(r) < 8 or not r[0].isdigit() or int(r[0]) not in cis_ok:
            continue
        cis, nom, dosage, nature, liaison = int(r[0]), r[3], r[4], r[6], r[7]
        key = (cis, liaison)
        if nature == "FT" or key not in comp:
            comp[key] = (nature, nom, dosage)
    ordre = {}
    textes = {}
    for (cis, liaison), (_, nom, dosage) in sorted(comp.items(), key=lambda kv: (kv[0][0], int(kv[0][1] or 0))):
        ordre[cis] = ordre.get(cis, 0) + 1
        db.execute("INSERT INTO substance VALUES (?,?,?,?)", (cis, ordre[cis], nom, dosage or None))
        textes.setdefault(cis, []).append((nom, dosage))
    # Texte d'affichage et texte de recherche (sans accents) des molécules, par spécialité
    for cis, subs in textes.items():
        affichage = " + ".join(f"{n} {d}".strip() for n, d in subs)
        recherche = " ".join(normalize(n) for n, _ in subs)
        db.execute("UPDATE specialite SET substances = ?, substances_recherche = ? WHERE cis = ?",
                   (affichage, recherche, cis))

    # Présentations actives et commercialisées
    total = parsed = fiables = 0
    for r in rows(raw_dir / "CIS_CIP_bdpm.txt"):
        if len(r) < 7 or not r[0].isdigit() or int(r[0]) not in cis_ok:
            continue
        if r[3] != "Présentation active" or r[4] != "Déclaration de commercialisation":
            continue
        cip13 = r[6]
        if not re.fullmatch(r"\d{13}", cip13):
            continue
        nb, unite, fiable = parse_unites(r[2])
        total += 1
        parsed += nb is not None
        fiables += fiable
        db.execute(
            "INSERT OR IGNORE INTO presentation VALUES (?,?,?,?,?,?,?)",
            (cip13, int(r[0]), r[1], r[2], nb, unite, fiable),
        )

    # Spécialités sans aucune présentation commercialisée : inutiles pour l'app
    db.execute("DELETE FROM substance WHERE cis NOT IN (SELECT cis FROM presentation)")
    db.execute("DELETE FROM specialite WHERE cis NOT IN (SELECT cis FROM presentation)")
    db.execute("INSERT INTO meta VALUES ('source', 'BDPM - Licence Ouverte Etalab 2.0')")
    db.commit()
    db.execute("VACUUM")
    n_spec = db.execute("SELECT COUNT(*) FROM specialite").fetchone()[0]
    db.close()

    print(f"{out} : {out.stat().st_size / 1e6:.1f} Mo")
    print(f"  spécialités : {n_spec}")
    print(f"  présentations : {total}")
    print(f"  nombre d'unités extrait : {parsed} ({100 * parsed / max(total, 1):.1f} %), "
          f"dont fiables : {fiables} ({100 * fiables / max(total, 1):.1f} %)")


def main():
    root = Path(__file__).resolve().parent.parent
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--download", action="store_true", help="télécharge les fichiers BDPM")
    ap.add_argument("--raw", type=Path, default=root / "data" / "raw")
    ap.add_argument("--out", type=Path, default=root / "data" / "build" / "bdpm.sqlite")
    a = ap.parse_args()
    if a.download:
        download(a.raw)
    build(a.raw, a.out)


if __name__ == "__main__":
    main()
