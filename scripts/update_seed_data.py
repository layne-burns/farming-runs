"""Generate src/main/resources/.../seeds.json from the OSRS wiki's "<kind> patch/Seeds" tables.

For every seed: its patch kind, Farming level, item id, the sapling it grows into (tree kinds), and the
gardener's protection payment (items, item ids, amounts). Item ids come from each item's wiki infobox.

The output is generated: don't edit it by hand. If a payment is wrong, fix it on the wiki or add an entry to
PAYMENT_FIXES below, then re-run. The script prints rows it couldn't read and items it found no id for.

Usage: python scripts/update_seed_data.py
"""
import json
import re
from pathlib import Path

from wiki import infobox_id, page_texts, raw_page

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "src/main/resources/com/layneburns/farmingruns/seeds.json"

# Wiki seed page -> patch kind (RuneLite PatchImplementation names). "Special" rows are sorted out below.
PAGES = {"Allotment patch": "ALLOTMENT", "Flower patch": "FLOWER", "Herb patch": "HERB", "Hops patch": "HOPS",
         "Bush patch": "BUSH", "Tree patch": "TREE", "Fruit tree patch": "FRUIT_TREE", "Special patches": None}
SPECIAL_SECTIONS = {"Coral frags": "CORAL", "Anima seeds": "ANIMA", "Hardwood tree seeds": "HARDWOOD_TREE",
                    "Cacti seeds": "CACTUS"}
SPECIAL_SEEDS = {"Grape seed": "GRAPES", "Mushroom spore": "MUSHROOM", "Belladonna seed": "BELLADONNA",
                 "Hespori seed": "HESPORI", "Seaweed spore": "SEAWEED", "Calquat tree seed": "CALQUAT",
                 "Crystal acorn": "CRYSTAL_TREE", "Spirit seed": "SPIRIT_TREE", "Celastrus seed": "CELASTRUS",
                 "Redwood tree seed": "REDWOOD"}
# Kinds planted as a sapling grown from the seed (the seed log tracks both).
SAPLING_KINDS = {"TREE", "FRUIT_TREE", "HARDWOOD_TREE", "CALQUAT", "CRYSTAL_TREE", "SPIRIT_TREE",
                 "CELASTRUS", "REDWOOD"}
# Seeds planted several at a time.
SEEDS_PER_PATCH = {"ALLOTMENT": 3, "HOPS": 4}
# Corrections the wiki tables don't parse cleanly: seed name -> list of {item, amount} ([] = can't protect).
PAYMENT_FIXES = {}

# "x 3" in the wiki's markup: a multiplication sign, the {{x}} template, or a plain x right after a link.
AMOUNT = re.compile(r"(?:×|\{\{x\}\}|(?<=\]\])\s*x|(?<=\]\]s)\s*x)\s*(\d+)")


def item_ids(names):
    """Item id (the main version's) of each named item page."""
    texts = page_texts(names)
    ids = {name: infobox_id(text, name) for name, text in texts.items()}
    return {name: item_id for name, item_id in ids.items() if item_id}


def sapling_name(seed: str) -> str:
    base = {"Acorn": "Oak", "Crystal acorn": "Crystal"}.get(seed) or re.sub(r"( tree)? seed$", "", seed)
    return f"{base} sapling"


def payment_item_name(text: str):
    """The item named in one payment entry, e.g. '{{plinkp|Empty sack}}<br/>[[Potatoes|Potatoes(10)]]'."""
    container = re.search(r"\[\[[^\]|]+\|([^\]]+\(\d+\))\]\]", text)  # Potatoes(10), Apples(5), ...
    if container:
        return container.group(1)
    # Item links name the item page itself; a plain [[link]] may point at a plant page instead.
    for pattern in (r"\{\{plink\|([^}|]+)", r"\{\{plinkp\|([^}|]+)", r"\[\[(?!File:)([^\]|]+)"):
        found = re.findall(pattern, text)
        if found:
            return found[-1].strip()
    return None


def parse_payment(cell: str):
    """Protection payment as a list of {item, amount}; empty when the crop can't be protected.
    Handles '[[Compost]]x2', '[[Marigold]] {{x}} 1', '[[Watermelon]]sx6' and several items in one cell."""
    payment, start = [], 0
    for found in AMOUNT.finditer(cell):
        name = payment_item_name(cell[start:found.start()])
        if name:
            payment.append({"item": name, "amount": int(found.group(1))})
        start = found.end()
    return payment


def parse_page(page: str, default_kind):
    text = raw_page(f"{page}/Seeds")
    seeds, section = [], None
    for chunk in re.split(r"\n\|-", text):
        cells = re.split(r"\n\|(?![-}])", "\n" + chunk.split("\n|}")[0])
        cells = [c for c in cells if c.strip()]
        if len(cells) >= 3 and re.match(r"^\s*\d+\s*$", cells[0].split("\n")[0]):
            seed = (re.search(r"\{\{plinkp\|([^}]+)\}\}", cells[1])
                    or re.search(r"\[\[(?!File:)([^\]|]+)", cells[1]))
            name = seed.group(1).strip()
            kind = default_kind or SPECIAL_SECTIONS.get(section) or SPECIAL_SEEDS.get(name)
            if kind is None:
                print(f"  no kind for {name} (section {section}) on {page}")
            else:
                # Allotment tables have a Flower column before the Farmer (payment) column.
                payment = parse_payment(cells[3 if kind == "ALLOTMENT" else 2])
                payment = PAYMENT_FIXES.get(name, payment)
                seeds.append({"name": name, "kind": kind, "level": int(cells[0].split()[0]), "payment": payment})
        # A sub-heading sits at the end of a chunk, after the previous table's last row: it applies to the
        # rows that follow, so it is read after this chunk's row.
        heading = re.findall(r"^===\s*(.+?)\s*===", chunk, re.M)
        if heading:
            section = heading[-1]
    return seeds


def main() -> None:
    seeds = []
    for page, kind in PAGES.items():
        seeds += parse_page(page, kind)

    wanted = [s["name"] for s in seeds]
    wanted += [sapling_name(s["name"]) for s in seeds if s["kind"] in SAPLING_KINDS]
    wanted += [p["item"] for s in seeds for p in s["payment"]]
    ids = item_ids(wanted)

    out = []
    for s in seeds:
        entry = {"name": s["name"], "itemId": ids.get(s["name"]), "kind": s["kind"], "level": s["level"],
                 "perPatch": SEEDS_PER_PATCH.get(s["kind"], 1)}
        if s["kind"] in SAPLING_KINDS:
            entry["sapling"] = {"name": sapling_name(s["name"]), "itemId": ids.get(sapling_name(s["name"]))}
        entry["payment"] = [{**p, "itemId": ids.get(p["item"])} for p in s["payment"]]
        out.append(entry)

    data = {"_comment": "Generated by scripts/update_seed_data.py from the OSRS wiki; do not edit by hand.",
            "seeds": out}
    OUT.write_text(json.dumps(data, indent="\t", ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")
    print(f"wrote {OUT.relative_to(ROOT).as_posix()}: {len(out)} seeds")
    for name in sorted(set(wanted) - set(ids)):
        print(f"  no item id for {name}")


if __name__ == "__main__":
    main()
