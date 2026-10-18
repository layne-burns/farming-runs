"""Generate teleports.json and items.json from the hand-edited scripts/teleports.in.json and items.in.json.

The .in files name items by their wiki page; this looks up every item id (all charge variants, minus the
excluded versions), every spell's rune cost and spellbook, and the fairy ring codes, so the plugin never relies on
ids typed by hand.

The outputs are generated: edit the .in files, not the outputs, then re-run. The script stops if a page is
missing or has no ids, so a typo can't slip through.

Teleport items Shortest Path knows that teleports.in.json doesn't are added after them (sp_teleports.py), read from
the installed Shortest Path jar.

Usage: python scripts/update_item_data.py [--shortest-path <jar or teleportation_items.tsv>]
"""
import argparse
import json
import re
from pathlib import Path

import sp_teleports
from wiki import clean_wikitext, infobox_ids, page_texts, raw_page

ROOT = Path(__file__).resolve().parent.parent
SCRIPTS = ROOT / "scripts"
RESOURCES = ROOT / "src/main/resources/com/layneburns/farmingruns"
# In teleports.in.json, a method whose destinations are every fairy ring code from the wiki.
FAIRY_RING_CODES = "wiki fairy ring codes"


def resolve_items(entries, texts, where):
    ids = []
    for entry in entries:
        text = texts.get(entry["page"], "")
        if "Infobox Item" not in text:
            raise SystemExit(f"{where}: wiki page {entry['page']!r} is not an item page (a spell or disambiguation?)")
        found = infobox_ids(text, entry.get("excludeVersions", ()))
        if not found:
            raise SystemExit(f"{where}: no item ids found on wiki page {entry['page']!r}")
        ids += found
    return sorted(set(ids))


def fairy_ring_codes():
    """Every fairy ring code on the wiki's Fairy ring page, with where it goes: {id, name, detail}."""
    text = raw_page("Fairy ring")
    codes = []
    for row in re.finditer(r'\|- id="([A-Z]{3})"(.*?)(?=\n\|-|\n\|\})', text, re.S):
        # Cells: code, map, location, points of interest. A cell can wrap onto a second line, so split on "\n|".
        cells = [c for c in re.split(r"\n\|", row.group(2)) if c.strip()]
        if len(cells) < 3:
            raise SystemExit(f"fairy ring {row.group(1)}: unexpected row layout on the wiki")
        code = row.group(1)
        codes.append({"id": code.lower(), "name": code, "detail": clean_wikitext(cells[2])})
    if len(codes) < 40:
        raise SystemExit(f"only {len(codes)} fairy ring codes found on the wiki; its table may have changed")
    return codes


def spell_cost(text, spell, rune_ids):
    book = re.search(r"^\|spellbook\s*=\s*(.+?)\s*$", text, re.M)
    cost = re.search(r"\{\{RuneReq\|([^}]*)\}\}", text)
    if not book or not cost:
        raise SystemExit(f"spell {spell!r}: no spellbook or rune cost on its wiki page")
    runes = []
    for name, amount in re.findall(r"(\w+)=(\d+)", cost.group(1)):
        rune = f"{name} rune"
        if rune not in rune_ids:
            raise SystemExit(f"spell {spell!r} needs {rune}; add it to runes in items.in.json")
        runes.append({"rune": rune, "itemId": rune_ids[rune], "amount": int(amount)})
    return book.group(1), runes


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--shortest-path", help="Shortest Path jar or teleportation_items.tsv (default: installed jar)")
    args = parser.parse_args()
    sp_table = sp_teleports.read_table(args.shortest_path)
    teleports_in = json.loads((SCRIPTS / "teleports.in.json").read_text(encoding="utf-8"))["teleports"]
    items_in = json.loads((SCRIPTS / "items.in.json").read_text(encoding="utf-8"))

    pages = [e["page"] for t in teleports_in for e in t.get("items", [])]
    pages += [e["page"] for t in teleports_in if isinstance(t.get("destinations"), list)
              for d in t["destinations"] for e in d.get("items", [])]
    pages += [t["spell"] for t in teleports_in if "spell" in t]
    pages += [e["page"] for entries in items_in["groups"].values() for e in entries]
    pages += items_in["runes"]
    pages += list(items_in.get("spells", {}).values())
    texts = page_texts(pages)

    rune_ids = {}
    for rune in items_in["runes"]:
        found = infobox_ids(texts.get(rune, ""))
        if not found:
            raise SystemExit(f"no item id for {rune}")
        rune_ids[rune] = found[0]

    teleports = []
    for t in teleports_in:
        where = f"teleport {t['id']}"
        out = {"id": t["id"], "name": t["name"], "group": t["group"], "type": t["type"]}
        destinations = t.get("destinations", [])
        if destinations == FAIRY_RING_CODES:
            destinations = fairy_ring_codes()
        resolved = []
        for d in destinations:
            entry = {"id": d["id"], "name": d["name"]}
            if "detail" in d:
                entry["detail"] = d["detail"]
            if "items" in d:
                entry["itemIds"] = resolve_items(d["items"], texts, f"{where}:{d['id']}")
            resolved.append(entry)
        if "items" in t:
            out["itemIds"] = resolve_items(t["items"], texts, where)
        elif any("itemIds" in d for d in resolved):
            # Any destination's items count as "having" the method (e.g. any Ardougne cloak).
            out["itemIds"] = sorted({i for d in resolved for i in d.get("itemIds", [])})
        if "spell" in t:
            out["spellbook"], out["runes"] = spell_cost(texts.get(t["spell"], ""), t["spell"], rune_ids)
        # Tablets and scrolls are used up on each use; so is anything marked "usedUp" (the basalts).
        out["usedUp"] = t.get("usedUp", t["group"] == "Tablets & scrolls")
        if "waivedByVarbit" in t:
            out["waivedByVarbit"] = t["waivedByVarbit"]
        if resolved:
            out["destinations"] = resolved
        teleports.append(out)

    known_item_ids = {i for t in teleports for i in t.get("itemIds", [])}
    known_item_ids |= {i for t in teleports for d in t.get("destinations", []) for i in d.get("itemIds", [])}
    from_sp = sp_teleports.teleports(sp_table, known_item_ids, {t["id"] for t in teleports})
    teleports += from_sp

    groups = {name: resolve_items(entries, texts, f"item group {name}")
              for name, entries in items_in["groups"].items()}

    generated = "Generated by scripts/update_item_data.py from scripts/{}; do not edit by hand."
    (RESOURCES / "teleports.json").write_text(json.dumps(
        {"_comment": generated.format("teleports.in.json"), "teleports": teleports}, indent="\t") + "\n",
        encoding="utf-8", newline="\n")
    descriptions = items_in.get("descriptions", {})
    unknown = set(descriptions) - set(groups)
    if unknown:
        raise SystemExit(f"items.in.json: descriptions for unknown groups {sorted(unknown)}")
    # Non-teleport spells a run can need (Fertile Soil): runes and spellbook from the wiki.
    spells = {}
    for spell_id, page in items_in.get("spells", {}).items():
        book, runes = spell_cost(texts.get(page, ""), page, rune_ids)
        spells[spell_id] = {"name": page, "spellbook": book, "runes": runes}
    (RESOURCES / "items.json").write_text(json.dumps(
        {"_comment": generated.format("items.in.json"), "groups": groups, "descriptions": descriptions,
         "runes": rune_ids, "spells": spells}, indent="\t", ensure_ascii=False) + "\n",
        encoding="utf-8", newline="\n")
    print(f"wrote teleports.json ({len(teleports)} teleports, {len(from_sp)} from Shortest Path) and items.json ({len(groups)} groups, "
          f"{len(rune_ids)} runes)")


if __name__ == "__main__":
    main()
