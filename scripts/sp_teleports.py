"""Teleport items that Shortest Path knows about, read from its transports/teleportation_items.tsv.

Shortest Path may route a leg through any teleport item you carry. Adding them all to teleports.json (after our own
hand-edited ones) lets the plugin recognise the click, offer it as a stop's teleport, ask for it in the inventory
check and track its limits. Items our own list already covers are left out.

The table is read from the Shortest Path jar the RuneLite client installed from the Plugin Hub (the newest copy), or
from a path given with --shortest-path.
"""
import csv
import io
import re
import zipfile
from pathlib import Path

TSV = "transports/teleportation_items.tsv"
GROUP = "More items (from Shortest Path)"
# Each use takes the item itself, not a charge.
USED_UP = re.compile(r"(tablet|teleport|sphere|basalt|scroll)\b", re.IGNORECASE)
# A destination named only "Teleport" says nothing about where it goes.
GENERIC = {"teleport", ""}


def read_table(path=None):
    """The TSV's text: from a given .tsv or .jar, or the newest installed Shortest Path jar."""
    if path is None:
        jars = sorted((Path.home() / ".runelite" / "plugins").glob("shortest-path_*.jar"),
                      key=lambda p: p.stat().st_mtime)
        if not jars:
            raise SystemExit("no Shortest Path jar in ~/.runelite/plugins; pass --shortest-path <jar or tsv>")
        path = jars[-1]
    path = Path(path)
    if path.suffix == ".jar":
        with zipfile.ZipFile(path) as jar:
            return jar.read(TSV).decode("utf-8")
    return path.read_text(encoding="utf-8")


def slug(text):
    return re.sub(r"[^a-z0-9]+", "_", text.lower().replace("'", "")).strip("_")


def teleports(table_text, known_item_ids, known_ids):
    """Teleport methods in teleports.json form, grouped by item name ("Sailors' amulet") with their destinations."""
    methods = {}
    for row in csv.reader(io.StringIO(table_text), delimiter="\t"):
        if not row or row[0].startswith("#") or len(row) < 7 or not row[0].strip():
            continue
        x, y, plane = (int(v) for v in row[0].split())
        ids = {int(i) for i in re.findall(r"(\d+)=\d+", row[3])}
        info = row[6].strip()
        if not ids or not info or ids & known_item_ids:
            continue
        name, _, destination = info.partition(":")
        method = methods.setdefault(name.strip(), {"itemIds": set(), "destinations": {}})
        method["itemIds"] |= ids
        entry = method["destinations"].setdefault(destination.strip(), {"tile": [x, y, plane], "itemIds": set()})
        entry["itemIds"] |= ids

    out = []
    for name, method in methods.items():
        method_id = slug(name)
        if method_id in known_ids:
            method_id += "_sp"
        all_ids = sorted(method["itemIds"])
        teleport = {"id": method_id, "name": name, "group": GROUP, "type": "item", "itemIds": all_ids,
                    "usedUp": bool(USED_UP.search(name))}
        destinations = method["destinations"]
        if len(destinations) == 1:
            (where, entry), = destinations.items()
            if where.lower() not in GENERIC:
                teleport["name"] = f"{name} ({where})"
        else:
            teleport["destinations"] = []
            used = set()
            for where, entry in destinations.items():
                destination_id = slug(where) or "teleport"
                while destination_id in used:
                    destination_id += "_2"
                used.add(destination_id)
                destination = {"id": destination_id, "name": where, "landing": entry["tile"]}
                if sorted(entry["itemIds"]) != all_ids:
                    destination["itemIds"] = sorted(entry["itemIds"])
                teleport["destinations"].append(destination)
        out.append(teleport)
    return out
