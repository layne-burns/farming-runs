"""Generate src/main/resources/.../patch_locations.json: every farming location, its patches and where they are.

Two sources are joined:
  * RuneLite's FarmingWorld.java (a release tag): regions, patch kinds, the varbit each patch reports through,
    and the gardener NPC. This is what the game tells us, so it decides which patches exist.
  * The OSRS wiki's "<kind> patch/Patches" tables: the map tile of each patch and the wiki's list of
    teleports to it. Rows are matched to RuneLite regions by the tile's region id and the patch kind.

The output is generated: don't edit it by hand. Hand-chosen settings (default teleport, unlock rule) live in
location_settings.json, which this script never touches. Run it again after a RuneLite update or a new area;
it prints every wiki row it couldn't match and every location left without a tile, so check that output.

Usage: python scripts/generate_patch_locations.py [runelite-tag]
"""
import json
import re
import sys
from pathlib import Path

from wiki import clean_wikitext, fetch, raw_page

DEFAULT_TAG = "runelite-parent-1.13.2"
FARMING_WORLD = ("https://raw.githubusercontent.com/runelite/runelite/{tag}/runelite-client/src/main/java/"
                 "net/runelite/client/plugins/timetracking/farming/FarmingWorld.java")
WIKI_PAGES = ["Allotment patch/Patches", "Hops patch/Patches", "Bush patch/Patches", "Tree patch/Patches",
              "Fruit tree patch/Patches", "Spirit Tree (Farming)/Patches", "Special patches/Patches"]
ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "src/main/resources/com/layneburns/farmingruns/patch_locations.json"

# Patch kinds we don't visit: compost bins.
SKIP_KINDS = {"COMPOST", "BIG_COMPOST"}

# Wiki map "group" -> the RuneLite patch kinds it can describe.
WIKI_GROUP_KINDS = {
    "allotment": {"ALLOTMENT", "FLOWER", "HERB"}, "herb": {"HERB"}, "flower": {"FLOWER"}, "hops": {"HOPS"}, "bush": {"BUSH"}, "tree": {"TREE"},
    "fruit": {"FRUIT_TREE"}, "spirit": {"SPIRIT_TREE"}, "seaweed": {"SEAWEED"}, "coral": {"CORAL"},
    "grape": {"GRAPES"}, "mushroom": {"MUSHROOM"}, "belladonna": {"BELLADONNA"}, "hespori": {"HESPORI"},
    "anima": {"ANIMA"}, "hardwood": {"HARDWOOD_TREE"}, "calquat": {"CALQUAT"}, "crystal": {"CRYSTAL_TREE"},
    "celastrus": {"CELASTRUS"}, "redwood": {"REDWOOD"}, "cactus": {"CACTUS"},
}

# Clearer names than RuneLite's for some regions (keyed by region id). Ids are made from these names.
NAMES = {
    6967: "Hosidius", 7223: "Hosidius vinery", 14391: "Port Phasmatys", 13622: "Canifis mushroom",
    12083: "Falador", 11828: "Falador Park", 10548: "Ardougne", 10290: "Ardougne bush",
    11062: "Catherby", 11317: "Catherby fruit tree", 12851: "Lumbridge hops", 12594: "Lumbridge tree",
    15008: "Fossil Island seaweed", 5021: "Farming Guild Hespori", 4922: "Farming Guild",
    13151: "Prifddinas", 15148: "Harmony Island",
}


def region_of(x: int, y: int) -> int:
    return ((x >> 6) << 8) | (y >> 6)


def slug(name: str) -> str:
    return re.sub(r"[^a-z0-9]+", "_", name.lower().replace("'", "")).strip("_")


# ---------------------------------------------------------------- RuneLite

def add_blocks(source: str):
    """Yields the text of each add(...) call in FarmingWorld's constructor, using bracket matching."""
    for match in re.finditer(r"\badd\(", source):
        depth, i = 0, match.end() - 1
        while True:
            depth += {"(": 1, ")": -1}.get(source[i], 0)
            if depth == 0:
                break
            i += 1
        yield source[match.start():i + 1]


def parse_farming_world(source: str):
    regions = []
    for block in add_blocks(source):
        head = re.search(r'new FarmingRegion\("([^"]+)", (\d+), (true|false),', block)
        if not head:
            continue
        patches = re.findall(r'new FarmingPatch\("([^"]*)", VarbitID\.(\w+), PatchImplementation\.(\w+)'
                             r'(?:, NpcID\.(\w+))?', block)
        extras_text = re.search(r"[)}]\s*((?:,\s*(?:/\*.*?\*/\s*)?\d+\s*)+)\)$", block, re.S)
        extras = [int(n) for n in re.findall(r"\d+", re.sub(r"/\*.*?\*/", "", extras_text.group(1)))] \
            if extras_text else []
        regions.append({
            "rlName": head.group(1), "regionId": int(head.group(2)), "definite": head.group(3) == "true",
            "extraRegionIds": extras,
            "patches": [{"name": n, "varbit": v, "kind": k, "gardener": g or None}
                        for n, v, k, g in patches if k not in SKIP_KINDS],
        })
    return [r for r in regions if r["patches"]]


# ---------------------------------------------------------------- wiki

MAP_RE = re.compile(r"\{\{Map\|([^{}]*)\}\}")


def parse_map(args: str):
    plane = re.search(r"plane=(\d+)", args)
    plane = int(plane.group(1)) if plane else 0
    group = re.search(r"group=(\w+)", args)
    group = group.group(1) if group else None
    named = re.search(r"x[:=]\s*(\d+)\s*[,|]\s*y[:=]\s*(\d+)", args)
    if named:
        points = [(int(named.group(1)), int(named.group(2)))]
    else:
        points = [(int(a), int(b)) for a, b in re.findall(r"(?<![=\w.])(\d{3,5}),\s*(\d{3,5})", args)]
    if not points:
        return None
    if "rectangle" in args:  # rectangle maps give a corner and a size; use the centre
        rx = int(re.search(r"rectX=(\d+)", args).group(1))
        ry = int(re.search(r"rectY=(\d+)", args).group(1))
        x, y = points[0][0] + rx // 2, points[0][1] + ry // 2
    else:  # a point, or a polygon's corners: use the centre
        x = round(sum(p[0] for p in points) / len(points))
        y = round(sum(p[1] for p in points) / len(points))
    return {"x": x, "y": y, "plane": plane}, group


def parse_wiki_rows(page: str, text: str):
    """Each table row with a map: its tile, group, location text and teleport bullet list."""
    rows = []
    for chunk in re.split(r"\{\{!\}\}-|\n\|-", text):
        found = MAP_RE.search(chunk)
        if not found:
            continue
        parsed = parse_map(found.group(1))
        if not parsed:
            continue
        tile, group = parsed
        before = chunk[:found.start()]
        cells = [c for c in re.split(r"\n(?:\{\{!\}\}|\|)(?!-)", before) if c.strip()]
        teleports = [clean_wikitext(t) for t in re.findall(r"^\*(.+)$", cells[-1], re.M)] if cells else []
        location = clean_wikitext(cells[-2]).split("Gardener:")[0].strip() if len(cells) >= 2 else ""
        rows.append({"page": page, "group": group, "tile": tile, "location": location,
                     "teleports": [t for t in teleports if t]})
    return rows


# ---------------------------------------------------------------- join

NEARBY_TILES = 128


def nearest_row(rows, used, kind, region_id):
    """Fallback when no wiki tile lies in the region: the closest unused row of the same kind, if near.
    (The wiki puts Hespori's tile just outside the region RuneLite reports it in.)"""
    cx, cy = (region_id >> 8) * 64 + 32, (region_id & 0xFF) * 64 + 32
    best, best_distance = None, NEARBY_TILES
    for i, row in enumerate(rows):
        if i in used or kind not in WIKI_GROUP_KINDS.get(row["group"], ()):
            continue
        distance = max(abs(row["tile"]["x"] - cx), abs(row["tile"]["y"] - cy))
        if distance < best_distance:
            best, best_distance = i, distance
    if best is not None:
        print(f"  nearby match for {kind} in region {region_id}: {rows[best]['tile']} ({best_distance} tiles)")
    return best


def main() -> None:
    tag = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_TAG
    regions = parse_farming_world(fetch(FARMING_WORLD.format(tag=tag)))
    rows = []
    for page in WIKI_PAGES:
        rows += parse_wiki_rows(page, raw_page(page))

    used = set()
    locations = []
    names_seen = {}
    for region in regions:
        name = NAMES.get(region["regionId"], region["rlName"])
        location_id = slug(name)
        if location_id in names_seen:
            raise SystemExit(f"duplicate location id {location_id}: add a NAMES entry for region "
                             f"{region['regionId']} ({region['rlName']})")
        names_seen[location_id] = region["regionId"]
        region_ids = {region["regionId"], *region["extraRegionIds"]}

        patches = []
        for p in region["patches"]:
            # The wiki row for this patch: same region, a group covering the patch kind.
            # A row just for this kind (a "herb" row) beats a shared one ("allotment" covers herbs too).
            in_region = [i for i, r in enumerate(rows)
                         if region_of(r["tile"]["x"], r["tile"]["y"]) in region_ids
                         and p["kind"] in WIKI_GROUP_KINDS.get(r["group"], ())]
            match = min(in_region, key=lambda i: len(WIKI_GROUP_KINDS[rows[i]["group"]]), default=None)
            if match is None:
                match = nearest_row(rows, used, p["kind"], region["regionId"])
            if match is not None:
                used.add(match)
            suffix = "_" + slug(p["name"]) if p["name"] and slug(p["name"]) != p["kind"].lower() else ""
            patches.append({
                "id": f"{location_id}.{p['kind'].lower()}{suffix}", "name": p["name"], "kind": p["kind"],
                "varbit": p["varbit"], "gardener": p["gardener"],
                "tile": rows[match]["tile"] if match is not None else None,
                "wikiTeleports": rows[match]["teleports"] if match is not None else [],
            })

        tiles = [p["tile"] for p in patches if p["tile"]]
        teleports = next((p["wikiTeleports"] for p in patches if p["wikiTeleports"]), [])
        for p in patches:
            del p["wikiTeleports"]
        locations.append({
            "id": location_id, "name": name, "regionId": region["regionId"],
            "extraRegionIds": region["extraRegionIds"], "definite": region["definite"],
            "target": tiles[0] if tiles else None, "wikiTeleports": teleports, "patches": patches,
        })

    data = {
        "_comment": "Generated by scripts/generate_patch_locations.py; do not edit by hand.",
        "runeliteTag": tag,
        "locations": sorted(locations, key=lambda loc: loc["id"]),
    }
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(data, indent="\t", ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")

    print(f"wrote {OUT.relative_to(ROOT).as_posix()}: {len(locations)} locations, "
          f"{sum(len(loc['patches']) for loc in locations)} patches")
    for loc in locations:
        missing = [p["id"] for p in loc["patches"] if not p["tile"]]
        if missing:
            print(f"  no wiki tile: {', '.join(missing)}")
    for i, row in enumerate(rows):
        if i not in used:
            print(f"  unmatched wiki row: {row['group']} at {row['tile']} ({row['location'][:50]}) "
                  f"region {region_of(row['tile']['x'], row['tile']['y'])}")


if __name__ == "__main__":
    main()
