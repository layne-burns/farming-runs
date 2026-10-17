"""Copy RuneLite's farming patch decoding classes into this plugin.

RuneLite's Time Tracking plugin knows how to turn a patch varbit value into a crop, a growth stage and a
state (growing, harvestable, diseased...), but those classes are package-private, so a plugin can't call
them. This script downloads them from a RuneLite release tag into our own package and makes the members
we use public. Nothing else is changed, so after a RuneLite update, re-run it and read the git diff.

Usage: python scripts/vendor_timetracking.py [tag]      (default tag below)
"""
import re
import sys
import urllib.request
from pathlib import Path

DEFAULT_TAG = "runelite-parent-1.13.2"
BASE = ("https://raw.githubusercontent.com/runelite/runelite/{tag}/runelite-client/src/main/java/"
        "net/runelite/client/plugins/timetracking/")
FILES = ["farming/PatchImplementation.java", "farming/Produce.java", "farming/PatchState.java",
         "farming/CropState.java", "Tab.java"]
PACKAGE = "com.layneburns.farmingruns.vendor.timetracking"
ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "src/main/java" / PACKAGE.replace(".", "/")

# (pattern, replacement) applied line by line; each makes one package-private member public.
PUBLIC = [
    (r"^class PatchState$", "public class PatchState"),
    (r"^(\t+)(abstract )?PatchState forVarbitValue\(", r"\1public \2PatchState forVarbitValue("),
    (r"^\tint (getStages|getTickRate)\(\)$", r"\tpublic int \1()"),
    (r"^\tstatic Produce (getBy\w+)\(", r"\tpublic static Produce \1("),
]


def transform(source: str, tag: str) -> str:
    lines = []
    for line in source.splitlines():
        if line.startswith("package "):
            line = f"package {PACKAGE};"
        elif line == "import net.runelite.client.plugins.timetracking.Tab;":
            continue  # Tab is vendored into the same package
        for pattern, replacement in PUBLIC:
            line = re.sub(pattern, replacement, line)
        lines.append(line)
    text = "\n".join(lines) + "\n"
    # Mark the file as vendored right after the licence header.
    return text.replace(f"package {PACKAGE};",
                        f"// Vendored from RuneLite ({tag}) by scripts/vendor_timetracking.py. Do not edit by hand.\n"
                        f"package {PACKAGE};", 1)


def main() -> None:
    tag = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_TAG
    OUT.mkdir(parents=True, exist_ok=True)
    for name in FILES:
        with urllib.request.urlopen(BASE.format(tag=tag) + name) as response:
            source = response.read().decode("utf-8").replace("\r\n", "\n")
        target = OUT / Path(name).name
        target.write_text(transform(source, tag), encoding="utf-8", newline="\n")
        print(f"wrote {target.relative_to(ROOT).as_posix()}")


if __name__ == "__main__":
    main()
