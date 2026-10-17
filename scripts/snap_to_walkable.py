"""Add a walkable "stand" tile next to every patch (and location target) in patch_locations.json.

The wiki's patch tiles are on the patch itself, which can't be walked on, so Shortest Path reports a path to one as
unreachable. This finds the nearest tile beside it that Shortest Path's own collision map says you can stand on and
walk from, and writes it as "stand". The plugin points Shortest Path at the stand tiles.

Run it after generate_patch_locations.py (which writes the file without stand tiles; the plugin's data test fails
until this has been run).

Usage: python scripts/snap_to_walkable.py [path to Shortest Path's collision-map.zip]
       (default: the upstream copy on GitHub)
"""
import io
import json
import sys
import urllib.request
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PATCHES = ROOT / "src/main/resources/com/layneburns/farmingruns/patch_locations.json"
UPSTREAM = "https://raw.githubusercontent.com/Skretzo/shortest-path/master/src/main/resources/collision-map.zip"
REGION = 64
# How far from the patch tile to look for somewhere to stand.
SEARCH = 12


class CollisionMap:
    """Shortest Path's map: per region, two bits per tile (can step north, can step east)."""

    def __init__(self, data: bytes):
        archive = zipfile.ZipFile(io.BytesIO(data))
        self.regions = {tuple(map(int, name.split("_"))): archive.read(name) for name in archive.namelist()}

    def flag(self, x, y, plane, which):
        data = self.regions.get((x // REGION, y // REGION))
        if data is None:
            return None
        bit = (plane * REGION * REGION + (y % REGION) * REGION + (x % REGION)) * 2 + which
        return (data[bit // 8] >> (bit % 8)) & 1 if bit // 8 < len(data) else 0

    def known(self, x, y, plane):
        return self.flag(x, y, plane, 0) is not None

    def exits(self, x, y, plane):
        """How many of the four directions you can step from this tile."""
        return sum(bool(self.flag(*t)) for t in ((x, y, plane, 0), (x, y, plane, 1),
                                                  (x, y - 1, plane, 0), (x - 1, y, plane, 1)))


def stand_tile(tile, world: CollisionMap):
    """The tile itself if it's open, else the nearest one around it you can walk from (two exits or more)."""
    x, y, plane = tile["x"], tile["y"], tile["plane"]
    if not world.known(x, y, plane):
        return None
    if world.exits(x, y, plane) >= 2:
        return dict(tile)
    best = None
    for dx in range(-SEARCH, SEARCH + 1):
        for dy in range(-SEARCH, SEARCH + 1):
            if world.exits(x + dx, y + dy, plane) >= 2:
                distance = dx * dx + dy * dy
                if best is None or distance < best[0]:
                    best = (distance, {"x": x + dx, "y": y + dy, "plane": plane})
    return best[1] if best else None


def main():
    source = sys.argv[1] if len(sys.argv) > 1 else UPSTREAM
    data = Path(source).read_bytes() if Path(source).exists() else urllib.request.urlopen(source).read()
    world = CollisionMap(data)
    patches = json.loads(PATCHES.read_text(encoding="utf-8"))
    missing = []
    for location in patches["locations"]:
        targets = [(location["id"], location, "target")]
        targets += [(p["id"], p, "tile") for p in location["patches"] if p.get("tile")]
        for name, holder, key in targets:
            stand = stand_tile(holder[key], world)
            if stand is None:
                missing.append(name)
                stand = dict(holder[key])
            holder["stand" if key == "tile" else "targetStand"] = stand
    PATCHES.write_text(json.dumps(patches, indent="\t", ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")
    print(f"stand tiles written; no walkable tile found near (kept as is): {missing or 'none'}")


if __name__ == "__main__":
    main()
