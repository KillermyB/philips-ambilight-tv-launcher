#!/usr/bin/env python3
"""Prepare the curated TV collection from existing local originals, without editing them."""
import argparse
import csv
import json
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]
SELECTION = {
    33: "Hyde Park", 40: "Les Jardins de Kensington", 91: "Le Grand Jour de sa colère",
    96: "Pandémonium", 111: "Le Déluge", 307: "La Destruction de Pompéi et Herculanum",
    140: "Josué arrêtant le soleil", 163: "Paysage d’Arcadie", 169: "La Destruction de Tyr",
    243: "Paysage avec un château", 312: "Le Festin de Balthazar",
    276: "La Destruction de Sodome et Gomorrhe", 279: "Le Jugement dernier",
    358: "Clair de lune au château de Chepstow",
    2: "Rome moderne · Campo Vaccino", 9: "Vue de Boppard", 12: "Les Îles Borromées",
    13: "Charbonniers déchargeant leur cargaison la nuit", 15: "La Mer de Glace à Chamonix",
    18: "Lucerne vue du lac", 22: "Pêcheurs en mer", 28: "L’Incendie du Parlement",
    34: "La Dogana et San Giorgio Maggiore", 47: "Pluie, vapeur et vitesse",
    70: "Le Soleil de Venise allant en mer", 86: "Le Rigi rouge", 93: "Dordrecht",
    105: "Le Dernier Voyage du Téméraire",
    19: "Le Méandre du Connecticut", 31: "L’État sauvage", 37: "Le Passé",
    65: "Le Voyage de la vie · La Jeunesse", 84: "Le Gué dans la montagne",
    87: "Le Rêve de l’architecte", 89: "Le Rêve d’Arcadie", 102: "Le Jardin d’Éden",
    159: "Le Voyage de la vie · La Vieillesse", 162: "La Destruction",
    176: "L’État pastoral", 200: "La Désolation",
    74: "Le Lac des Quatre-Cantons", 83: "Un lac dans la Sierra Nevada",
    88: "Coucher de soleil dans la vallée de Yosemite", 136: "Le Bassin d’émeraude",
    141: "Un orage dans les Rocheuses · Mont Rosalie", 174: "Les Rocheuses · Pic Lander",
    177: "Vue sur la vallée de Yosemite", 231: "Au cœur de la Sierra Nevada",
    153: "Prométhée enchaîné", 297: "Alcyone",
    120: "La Destruction de l’armée de Pharaon", 125: "Ruines d’une cité antique",
    249: "Le Pays de l’Iguanodon", 254: "Le Calvaire", 226: "Paysage · Île de Wight ou Richmond Hill", 300: "Macbeth",
    318: "Tempête de neige · Un vapeur au large du port", 50: "Val d’Aoste · Tempête de neige et avalanche",
    95: "Le Négrier", 113: "Le Phare de Bell Rock", 137: "Le Léman et le Mont-Blanc",
    247: "Ulysse raillant Polyphème", 252: "Le Vésuve en éruption", 333: "Le Lac de Zoug",
    60: "Le Présent", 72: "Les Deux Lacs des Catskills · Matin", 101: "Le Retrait des eaux du Déluge",
    245: "Le Voyage de la vie · L’Enfance", 248: "Le Col de Crawford", 331: "Lever de soleil dans les Catskills",
    213: "Vue dans la vallée de Yosemite", 241: "Le Lac Tahoe", 322: "Les Dômes de Yosemite",
    150: "Paysage avec figures · Le Dernier des Mohicans", 107: "Les Cascatelles de Tivoli, vers Rome",
    175: "La Découverte de l’Hudson", 202: "Les Catskills au début de l’automne",
    205: "L’Allegro", 227: "Le Voyage de la vie · L’Âge adulte",
    326: "Le Départ", 43: "Le Retour", 324: "L’Expulsion du jardin d’Éden",
    296: "L’Exil · Clair de lune et feu", 355: "Le Retour du chasseur",
    299: "Florence vue de San Miniato", 357: "Venise · Canaletto à son chevalet",
    229: "Richmond Hill · L’Anniversaire du prince régent", 316: "La Cinquième Plaie d’Égypte",
    214: "La Bataille de Trafalgar", 334: "Le Palais et le Pont de Caligula",
    341: "Staffa · La Grotte de Fingal", 352: "Rome vue du mont Aventin",
    342: "La Baie de Baïes · Apollon et la Sibylle",
    351: "Les Derniers Bisons", 339: "Dans le Simmental", 345: "Matin dans la Sierra Nevada",
    315: "Le Pique-nique", 82: "La Vallée de Yosemite",
    294: "Le Lac des Quatre-Cantons · Suisse", 217: "Venise vue du canal de la Giudecca",

}

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("index", type=Path)
    parser.add_argument("originals", type=Path)
    args = parser.parse_args()
    rows = {int(row["Jour"]): row for row in csv.DictReader(args.index.open(encoding="utf-8-sig"))}
    destination = ROOT / "app/src/main/assets/art"
    catalog = destination / "catalog.json"
    previous = json.loads(catalog.read_text())
    preserved = {value["id"]: value for value in previous}
    cache_path = ROOT / ".local/collection-renditions.json"
    cache = json.loads(cache_path.read_text()) if cache_path.exists() else {}
    existing = {231: "sierra", 105: "temeraire", 176: "arcadia"}
    output = []
    assert len(SELECTION) == 100
    for day, title in SELECTION.items():
        row = rows[day]
        original = args.originals / row["Fichier"]
        if not original.is_file():
            raise FileNotFoundError(original)
        ident = existing.get(day, f"collection-{day:03d}")
        name = preserved.get(ident, {}).get("asset", f"art/{ident}.jpg")
        target = destination / Path(name).name
        dimensions = subprocess.run(["sips", "-g", "pixelWidth", "-g", "pixelHeight", str(original)],
            check=True, capture_output=True, text=True).stdout
        width = int(re.search(r"pixelWidth: (\d+)", dimensions)[1])
        height = int(re.search(r"pixelHeight: (\d+)", dimensions)[1])
        if width / height < 1.24 or width < 1920 or height < 1080:
            raise ValueError(f"Unsuitable TV artwork: {day} ({width}×{height})")
        signature = f"{original.stat().st_mtime_ns}:{original.stat().st_size}:2560:86"
        if cache.get(ident) != signature or not target.is_file():
            subprocess.run(["sips", "-s", "format", "jpeg", "-s", "formatOptions", "86",
                "-Z", str(min(2560, max(width, height))), str(original), "--out", str(target)],
                check=True, capture_output=True)
            cache[ident] = signature
        years = re.findall(r"\b\d{4}\b", row["Année"])
        year = "–".join(dict.fromkeys(years)) if years else ""
        if year and re.search(r"circa|\bca\.", row["Année"], re.I):
            year = "vers " + year
        if year and "after " in row["Année"].lower(): year = "après " + year
        value = preserved.get(ident, {"id": ident, "asset": name, "title": title,
            "originalTitle": row["Titre"], "artist": row["Artiste"], "year": year,
            "source": row["Fiche source"], "license": "Public domain artwork"})
        # Bias the 16:9 crop upward where a face, mast, tower or summit needs space.
        value["cropY"] = {65: .08, 113: .10, 125: 0, 153: 0, 177: 0, 200: 0, 214: 0, 297: .10}.get(day, .5)
        output.append(value)
    # Preserve the existing collection and append a varied sequence of new works.
    selected = {value["id"] for value in output}
    interleaved = [value for value in previous if value["id"] in selected]
    additions = [value for value in output if value["id"] not in preserved]
    artists = list(dict.fromkeys(value["artist"] for value in additions))
    groups = [[value for value in additions if value["artist"] == artist] for artist in artists]
    while any(groups):
        for group in groups:
            if group:
                interleaved.append(group.pop(0))
    catalog.write_text(json.dumps(interleaved, ensure_ascii=False, indent=2) + "\n")
    cache_path.parent.mkdir(exist_ok=True)
    cache_path.write_text(json.dumps({key: value for key, value in cache.items() if key in selected}, indent=2) + "\n")
    wanted = {Path(value["asset"]).name for value in interleaved}
    for generated in destination.glob("collection-*.jpg"):
        if generated.name not in wanted:
            generated.unlink()
    size = sum((destination / Path(value["asset"]).name).stat().st_size for value in interleaved)
    print(f"Prepared {len(interleaved)} artworks, {size / 1024 / 1024:.1f} MiB. Source originals unchanged.")

if __name__ == "__main__":
    main()
