"""Small helpers for reading the OSRS wiki, shared by the data scripts in this folder."""
import json
import re
import urllib.parse
import urllib.request

USER_AGENT = "farming-runs data script (https://github.com/layne-burns/farming-runs)"
RAW = "https://oldschool.runescape.wiki/w/{page}?action=raw"
API = "https://oldschool.runescape.wiki/api.php"


def fetch(url: str) -> str:
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request) as response:
        return response.read().decode("utf-8").replace("\r\n", "\n")


def raw_page(title: str) -> str:
    """A page's wikitext, e.g. raw_page("Herb patch/Seeds")."""
    return fetch(RAW.format(page=urllib.parse.quote(title.replace(" ", "_"))))


def page_texts(titles):
    """Wikitext of many pages, 50 per request, following redirects. Returns {asked title: text}."""
    texts, titles = {}, sorted(set(titles))
    for start in range(0, len(titles), 50):
        batch = titles[start:start + 50]
        query = urllib.parse.urlencode({
            "action": "query", "prop": "revisions", "rvprop": "content", "rvslots": "main", "format": "json",
            "redirects": 1, "titles": "|".join(batch)})
        data = json.loads(fetch(f"{API}?{query}"))["query"]
        # Follow normalised and redirected titles back to the names we asked for.
        resolved = {title: title for title in batch}
        for step in data.get("normalized", []) + data.get("redirects", []):
            for asked, now in list(resolved.items()):
                if now == step["from"]:
                    resolved[asked] = step["to"]
        by_title = {page["title"]: page["revisions"][0]["slots"]["main"]["*"]
                    for page in data["pages"].values() if "revisions" in page}
        for asked, title in resolved.items():
            if title in by_title:
                texts[asked] = by_title[title]
    # The API leaves out some pages' text when a batch is large; fetch those one at a time.
    for title in titles:
        if title not in texts:
            try:
                text = raw_page(title)
                redirect = re.match(r"#REDIRECT\s*\[\[([^\]#]+)", text, re.I)
                texts[title] = raw_page(redirect.group(1)) if redirect else text
            except OSError:
                pass  # missing page: the caller reports it
    return texts


def infobox_id(text: str, name: str):
    """The id of the item called `name` on its page: the version whose nameN matches (so "Tomatoes(5)" is the
    full basket, not the page's first version, Tomatoes(1)), else the page's only or first id. None if none."""
    box = re.search(r"\{\{Infobox Item\n(.*?)\n\}\}", text, re.S)
    text = box.group(1) if box else text
    names = dict(re.findall(r"^\|name(\d+)\s*=\s*(.+?)\s*$", text, re.M))
    ids = dict(re.findall(r"^\|id(\d*)\s*=\s*(\d+)", text, re.M))
    for number, version_name in names.items():
        if version_name.lower() == name.lower() and number in ids:
            return int(ids[number])
    first = ids.get("") or ids.get("1")
    return int(first) if first else None


def infobox_ids(text: str, exclude_versions=()):
    """Item ids from an Infobox Item: every version's id, minus the excluded versions (e.g. "Uncharged")."""
    # Read only the item infobox: image boxes further down reuse the same versionN fields.
    box = re.search(r"\{\{Infobox Item\n(.*?)\n\}\}", text, re.S)
    text = box.group(1) if box else text
    versions = dict(re.findall(r"^\|version(\d+)\s*=\s*(.+?)\s*$", text, re.M))
    ids = []
    for number, value in re.findall(r"^\|id(\d*)\s*=\s*([\d,\s]+?)\s*$", text, re.M):
        if number and versions.get(number) in exclude_versions:
            continue
        ids += [int(i) for i in re.findall(r"\d+", value)]
    return sorted(set(ids))


def clean_wikitext(text: str) -> str:
    """Wiki markup to plain text: links to their text, templates and HTML tags dropped, fairy codes upper-cased."""
    text = re.sub(r"\{\{Fairycode\|(\w+)\}\}", lambda m: m.group(1).upper(), text, flags=re.I)
    text = re.sub(r"\{\{NoCoins\|([^}]*)\}\}", r"\1", text)
    text = re.sub(r"\[\[(?:[^|\]]*\|)?([^\]]*)\]\]", r"\1", text)
    text = re.sub(r"\{\{[^{}]*\}\}", "", text)
    text = re.sub(r"<[^>]+>|''+", "", text)
    return " ".join(text.split())
