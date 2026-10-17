"""Generate src/main/resources/.../seeds.json from the OSRS wiki's "<kind> patch/Seeds" tables.

For every seed: its patch kind, Farming level, item id, the sapling it grows into (tree kinds), the
gardener's protection payment (items, item ids, amounts), experience and growth time. Item ids come from each item's wiki infobox.

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


def header_columns(table: str):
    """Column names from a table's two header rows: "Level", "Seed", "Protection:Farmer", "Experience:Plant"...
    A heading spanning two rows names its columns itself; one spanning columns takes the second row's names."""
    rows, current = [], []
    for line in table.split("\n"):
        if line.startswith("|-"):
            if current:
                rows.append(current)
                current = []
            if len(rows) == 2:
                break
        elif line.startswith("!"):
            current.append(line[1:])
        elif current and line.startswith("|"):
            break  # the first data row: the header is over
    if current:
        rows.append(current)
    second = [heading_name(c) for c in (rows[1] if len(rows) > 1 else [])]
    columns = []
    for cell in rows[0] if rows else []:
        name = heading_name(cell)
        rowspan = re.search(r'rowspan="(\d+)"', cell)
        colspan = int((re.search(r'colspan="(\d+)"', cell) or re.match(r"()", "")).group(1) or 1)
        if len(rows) == 1 or (rowspan and rowspan.group(1) == "2"):
            columns += [name] * colspan
        else:
            columns += [f"{name}:{second.pop(0)}" for _ in range(colspan)]
    return columns


def heading_name(cell: str) -> str:
    """A header cell's text: footnotes dropped first (they contain '|'), then any attributes before the '|'."""
    return re.sub(r"\{\{efn.*", "", cell).split("|")[-1].strip()


def cell_text(cell: str) -> str:
    """A data cell without its leading attributes (e.g. 'align="left" |')."""
    return re.sub(r'^\s*(?:[a-z-]+="[^"]*"\s*)+\|', "", cell)


def parse_number(cell: str):
    found = re.match(r"\s*([\d,]+(?:\.\d+)?)", cell)
    return float(found.group(1).replace(",", "")) if found else None


def parse_xp(row: dict):
    """Experience columns as {"plant": 27, "harvest": 30.5, "checkHealth": ..., "total": ...}."""
    xp = {}
    for column, cell in row.items():
        key = None
        if column.startswith("Experience:"):
            key = column.split(":", 1)[1].lower().replace("-", " ")
        elif column == "Experience":  # anima: experience only for planting
            key = "plant"
        elif column == "XP":
            key = "total"
        value = parse_number(cell) if key else None
        if value is not None:
            words = key.split()
            xp[words[0] + "".join(w.capitalize() for w in words[1:])] = value
    return xp


def parse_growth(cell: str):
    """Growth time in one style, e.g. '1 h 20 min'. The wiki writes 'N x M mins' (N growth stages of M minutes),
    sometimes with the total in brackets ('(16 Hours)'); the stages are multiplied out."""
    stages = re.search(r"(\d+)\s*(?:×|\{\{x\}\}|x)\s*(\d+)\s*min", cell)
    if stages:
        minutes = int(stages.group(1)) * int(stages.group(2))
    else:
        hours = re.search(r"(\d+)\s*h", cell, re.I)
        mins = re.search(r"(\d+)\s*min", cell, re.I)
        if not hours and not mins:
            return None
        minutes = (int(hours.group(1)) * 60 if hours else 0) + (int(mins.group(1)) if mins else 0)
    h, m = divmod(minutes, 60)
    return " ".join(part for part in (f"{h} h" if h else "", f"{m} min" if m else "") if part)


def parse_page(page: str, default_kind):
    text = raw_page(f"{page}/Seeds")
    seeds = []
    for table in re.finditer(r"\{\|.*?\n\|\}", text, re.S):
        # The sub-heading just above a table (special patches) says which kind its rows are.
        headings = re.findall(r"^===\s*(.+?)\s*===", text[:table.start()], re.M)
        section = headings[-1] if headings else None
        columns = header_columns(table.group(0))
        for chunk in re.split(r"\n\|-", table.group(0)):
            cells = [c for c in re.split(r"\n\|(?![-}])", "\n" + chunk.split("\n|}")[0]) if c.strip()]
            if len(cells) < 3 or not re.match(r"^\s*\d+\s*$", cells[0].split("\n")[0]):
                continue
            if len(cells) != len(columns):
                print(f"  {page}: a row has {len(cells)} cells but the header has {len(columns)}: {cells[1][:40]!r}")
                continue
            row = dict(zip(columns, (cell_text(c) for c in cells)))
            seed_cell = row.get("Seed") or row.get("Frag") or cells[1]
            seed = (re.search(r"\{\{plinkp\|([^}]+)\}\}", seed_cell)
                    or re.search(r"\[\[(?!File:)([^\]|]+)", seed_cell))
            name = seed.group(1).strip()
            kind = default_kind or SPECIAL_SECTIONS.get(section) or SPECIAL_SEEDS.get(name)
            if kind is None:
                print(f"  no kind for {name} (section {section}) on {page}")
                continue
            payment_cell = row.get("Protection:Farmer") or row.get("Protection") or ""
            payment = PAYMENT_FIXES.get(name, parse_payment(payment_cell))
            growth = next((v for k, v in row.items() if k.startswith("Growth time")), "")
            seeds.append({"name": name, "kind": kind, "level": int(cells[0].split()[0]), "payment": payment,
                          "xp": parse_xp(row), "growth": parse_growth(growth)})
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
        entry["xp"] = s["xp"]
        entry["growth"] = s["growth"]
        out.append(entry)

    data = {"_comment": "Generated by scripts/update_seed_data.py from the OSRS wiki; do not edit by hand.",
            "seeds": out}
    OUT.write_text(json.dumps(data, indent="\t", ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")
    print(f"wrote {OUT.relative_to(ROOT).as_posix()}: {len(out)} seeds")
    for name in sorted(set(wanted) - set(ids)):
        print(f"  no item id for {name}")


if __name__ == "__main__":
    main()
