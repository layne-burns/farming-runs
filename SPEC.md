# Farming Runs — Specification (v0.2, approved 2026-10-17)

## Context

You want a personal RuneLite plugin that runs farming runs from start to finish. It knows which patches you have unlocked, keeps a seed log (bank, seed vault, inventory, seed box), and lets you set up a run in a side panel: which kinds of patch, which seeds, what to skip, and whether to pay. When you press Start, it checks your inventory, orders the stops, and points the Shortest Path plugin at each one in turn. Along the way it outlines patches and puts local reminders over your head.

**Approved 2026-10-17.** Next step: the full build-planning session (phases, classes, data files).

## 1. What we build on

| Thing | Where | Role |
|---|---|---|
| Shortest Path's PluginMessage API | `C:\dev\shortest-path\src\main\java\shortestpath\ShortestPathPlugin.java:295` | Messages are `shortestpath`/`path` with `target` (a point, or a **Set**, where the nearest one wins), an optional `start`, and an optional `config` override, plus `clear`. Nothing comes back, so SP only draws. Each leg uses **your own SP settings**. |
| Template for driving SP | `C:\dev\port-tasks\src\main\java\com\nucleon\porttasks\routing\RoutingService.java` | Its "land target" mode: send only on change, resend after logout or hop, clear on stop. |
| Project layout | `C:\dev\port-tasks\build.gradle`, `PortTasksPluginTest.java` | A personal plugin launched with `./gradlew run` via `ExternalPluginManager.loadBuiltin`, with SP loaded from the Hub install. |
| RuneLite Time Tracking | `net.runelite.client.plugins.timetracking.farming` (package-private) | The patch table (regions, varbits, kinds) is **copied** into our data files (BSD-licensed). Patch states and ready predictions are **read from Time Tracking's saved config** (group `timetracking`), so Time Tracking must be enabled, and the panel warns if it isn't. |

Assumptions: you play an ironman (payment comes from your own produce), you've completed the Barbarian Training seed planting (no dibber), and Song of the Elves isn't done, so the Prifddinas patches, including the crystal tree, show as locked.

## 2. Features

### 2.1 Unlocks
- Each location has a requirement table in a data file: quests, diaries, Farming level and Farming Guild tier (45/65/85), read from varps and varbits once per login. Each location then shows **unlocked**, **locked (reason)** or **overridden**.
- The panel has a manual override per location.
- Spellbook and diary tiers (Ardougne cloak, Explorer's ring, Morytania legs, elite Lumbridge for no dramen staff) are detected. A POH portal nexus or jewellery box can't be detected, so those are tick boxes.

### 2.2 Seed log (Bank tab)
- **Sources:** bank and seed vault (snapshotted each time you open them and saved in your profile), plus inventory and seed box (live).
- **Tracks:** seeds, saplings and seedlings, and payment produce, grouped by kind.
- **Each line shows:** total, spare after the buffer (§2.3), and where the items are (bank, vault, carried).
- **Run supplies:** compost, payment produce and teleports, with "runs affordable" per kind.
- **Tool leprechaun storage** (added 2026-10-17) is read live from its varbits. It shows tools (rake, dibber, spade, secateurs/magic secateurs, trowel, watering can, buckets, bottomless bucket) and stored compost (compost, super, ultra). It appears as its own section in the Bank tab, and the inventory check uses it (§2.4).

### 2.3 Run setup (Run tab)
- **Kinds:** herb, allotment, flower, tree, fruit tree, hops, bush, cactus, mushroom, belladonna, calquat, hardwood, redwood, celastrus, spirit tree, crystal tree, Hespori, anima and seaweed. Pick any combination; one run visits each location once and does every selected kind there.
- **Skip patches:** a tick list of the unlocked patches of the chosen kinds.
- **Seed choice:**
  - **Auto** (default): fills each kind with the best seed you can spare, and mixes seeds when one runs short.
  - Turn Auto off to choose by hand. Your chosen seeds are given patches either **by share** (the shares or exact counts you set) or **most XP first** (the highest-XP seed fills patches first, as far as your spare goes, then the next; added 2026-10-17).
  - **Buffer:** never use the last *N* of any seed. Settings: Allotment 9, Saplings 2, Other 3. It's only for kinds a farming contract can ask for: the Farming Guild's patches, except its spirit tree, anima and Hespori patches. Those three, and every kind with no Guild patch (belladonna, calquat, coral, crystal tree, grapes, hardwood, hops, mushroom and seaweed), keep none (added 2026-10-21).
  - **Farming contract:** a contract you hold but haven't planted makes the matching Farming Guild patch use the contract seed. The buffer doesn't apply to it.
- **Payment:** one toggle per kind. A data file holds each crop's payment item and amount.
- **Tree routine,** set per kind: "check health, pay to remove" (200 coins) or "check health, chop and dig" (needs an axe). Trees with a crop (fruit trees, calquat, celastrus) are harvested first; you can switch that off per kind (added 2026-10-17).
- **Amulet of bounty** (allotments; added 2026-10-17): worn while planting, it gives a 25% chance to use one seed instead of three. On by default when an allotment seed above strawberry is planted (watermelon, snape grass); a tick box on the allotment card overrides that.
- **Compost** setting: None, Compost, Supercompost (current), Ultracompost, Bottomless bucket or Fertile Soil. Coral can't take compost, so coral patches never get any, and the coral card has no compost choice (added 2026-10-20).
- **Teleports:** each location has a ranked list of teleports (default order from the data, e.g. skills necklace, then fairy ring CIR). You can reorder, add or remove them per location in the Locations tab. The inventory check uses the first one you can use, and warns only if none work (added 2026-10-17).
- **Goal presets (settled 2026-10-17):** a Presets block at the top of the Run tab. It lists built-in goal presets (pick a product such as Prayer potion, and the run plants its farmable ingredients: ranarr in herb patches, snape grass in allotments) next to your saved presets.
  - Tick several to combine them.
  - Built-ins cover Potions, Cooking, Crafting & other, and Farming XP. Which ones, and their ingredients and yields, come from a researched data file.
  - When combined presets want the same patch kind (e.g. ranarr and snapdragon for herbs), the patches are split evenly by default (50:50 for two). You can change the split by proportion, or by entering an exact number of patches (seeds) for each crop.
- **Presets:** save, load and delete named setups (kinds, skips, seed mode, payment, routines, compost). The last-used one is remembered.

### 2.4 Start Run
1. **Not-ready check.** Patches that aren't ready (per Time Tracking) are listed, e.g. "ready in 1h 20m". You tick which ones to visit anyway.
2. **Inventory check.** It compares inventory and equipment against what the run needs:
   - seeds by type;
   - compost;
   - payment;
   - coins for removal;
   - tools: spade, secateurs if owned, axe if needed, rake;
   - teleport items or runes for the chosen teleports;
   - dramen staff, unless you have the Lumbridge elite diary;
   - the farmer's outfit, worn;
   - an amulet of bounty, when it's on for the allotments.

   **Items in the tool leprechaun count as available**, because every patch has a leprechaun. A tool or compost that is only stored there passes, with a note such as "withdraw from leprechaun". During the run, the overhead reminder says to grab it at the first stop.

   It lists what's missing with counts, and highlights those items in the bank.
3. If anything is missing, a **"Start anyway" / "Cancel"** dialog appears.
4. The route is planned (§2.5) and the first leg goes to SP.

### 2.5 Route ordering
- Stops are **locations**, and the cost of each pair comes from a **hand-tuned data file**: the teleport's cost plus the walk from its landing spot, plus a few direct links that are really cheaper, such as spirit-tree hops and two Ardougne-cloak stops.
- Exact solve (Held–Karp) up to about 20 stops, then greedy plus 2-opt. The start is your position; the end is free.
- The panel shows the **full ordered list**: done stops ticked, the current one highlighted, and a click on any stop to skip it or go there next.

### 2.6 During the run
- **Auto-advance:** once every selected patch at the current location is planted (and protected, if payment is on), detected from varbits, SP is sent to the next stop.
- **Outlines** on the patches still to do at the current location.
- **Overhead reminders** (local, above your head):
  - "Pay the gardener: 3 × sack of potatoes" on arrival, while the patch is still unprotected;
  - "Compost!" before planting;
  - the tree routine's next step.
- **Stop run** clears SP and the overlays. There's no end-of-run summary.

### 2.7 Compost bin filling (approved and built 2026-10-20)
Today a run that stops at the Farming Guild fills its big bin (30 items) with pineapples, and offers a trip to buy them when you're short. Instead, the bin is filled from what you can spare in the bank, best candidates first. A stockpile like 464 watermelons gets used, and coconuts never do.

- **Which bin:** still only the Farming Guild's big bin. It's always filled to make supercompost, so only super-compostable items count: one ordinary item would turn the whole bin into plain compost.
- **Candidates:** a hand-edited list in `compost_bin.json`, checked against the wiki's Supercompost page: pineapple, watermelon, papaya fruit, calquat fruit, coconut (whole coconuts count), dragonfruit, jangerberries, white berries and poison ivy berries. Items are named by RuneLite's item constants, which give their ids. Grimy herbs, tree roots, snape grass, mushrooms, white lilies and celastrus bark also make supercompost, but they're left off the list as too useful to compost.
- **Held back, per item.** Each item has a *keep*: either **never**, or a number to keep. Only what you have above it (bank plus inventory) is spare. Defaults:
  - **Sold in a shop** (pineapples, from the Trader Crewmember at Deepfin Point): keep 0, all spare.
  - **Pays for a crop at Farming level 65 or above:** never.
    - Coconut: magic tree 75, dragonfruit tree 81.
    - Dragonfruit: redwood 90, rosewood 92.
    - Poison ivy berries: calquat 72.
    - Papaya fruit: palm tree 68.
    - White berries: camphor 66.
  - **Anything else:** keep 100, spare the rest. That covers watermelon (jangerberry bush 48, pineapple plant 51), jangerberries (snape grass 61) and calquat fruit (pays for nothing).
  - You can change any item's keep in the panel.
- **Automatic ranking:** shop-sold items first, then the largest spare first. Items with nothing spare drop to the bottom, greyed out.
- **Your choices:**
  - **Default item** (e.g. watermelon): always used first while it has any spare.
  - **Fallback:** when the default runs short, the bin is topped up down the ranking.
  - **Reorder:** you can move items up or down the ranking. "Reset order" puts it back to automatic, and items new to your bank join in automatic order.
- **Filling:** what the bin needs (30, or what's left in a part-filled bin) is taken from the default, then down the ranking, mixing items as needed. Every candidate is super-compostable, so the result is still supercompost. For example: 30 watermelons; or 18 watermelons (all the spare) and 12 pineapples.
- **Short:** if everything spare together is less than the bin needs, the check shows the shortfall, and the existing pineapple trip covers it.
- **Where it shows:**
  - **Run tab:** the Compost section has a "Farming Guild bin, use first" drop-down. The ranked list is in its own "Compost bin ranking" section, closed until you open it. Each row shows the item and why it's there, e.g. "33 spare, shop-bought", "364 spare (keep 100)" or "never: pays for Magic tree, Dragonfruit tree". Each row has ▲ ▼ to reorder and a "keep" menu: automatic, none, 100, a number of your own, or never.
  - **Bank:** the inventory check, bank tab and highlights list the chosen items with counts, in place of the single pineapple line.
  - **During the run:** the reminder at the bin names what to put in.
- **Data and state:**
  - **Static data:** the candidates and their shop source are in `compost_bin.json`, and the payment levels come from `seeds.json`.
  - **Your settings:** the default item, your order and your keeps are saved per account.
  - **Bank tracking:** calquat fruit and any other candidate the bank doesn't record yet are added to the tracked items.
- **Code:** one pure class (inputs: candidates, counts, keeps, order, default, how many are needed). It returns the ranked rows with their reasons and how many of each go in the bin, and is unit tested like the payment packer.
- **Not in this:** the small 15-item bins, plain compost, ultracompost (volcanic ash), Grand Exchange prices (ironman), and a compost mode on the Making tab.

## 3. Engineering practices (requested 2026-10-17)

You aren't a software engineer by trade, so the code is laid out to be easy to read, debug and change:

- **Small, single-purpose classes, grouped in packages by concern:**
  - `data`: tables loaded from files;
  - `state`: unlocks, seed log, patch states (the only code that reads the client);
  - `run`: setup, planner, run loop;
  - `pathing`: the only code that talks to Shortest Path;
  - `inventory`: the check;
  - `ui`: panel tabs and overlays.

  As a rough guide, a class over ~300 lines is worth a look for a second job it could hand off. It is not a cap: a class that genuinely needs more stays whole rather than being split for the sake of the number. The plugin class only wires the parts together.
- **Game facts live in data files** (JSON/TSV under `resources/`): locations, patches, seeds, payments, teleports, costs and unlock requirements. Fixing a payment or adding a patch is an edit to a data file, and a unit test checks every file loads and every reference resolves.
- **Pure logic doesn't touch the client.** The planner, seed picker, inventory check and unlock rules take plain inputs and are unit tested, as port-tasks' routing tests are.
- **Debugging aids:**
  - consistent log tags (`[farmrun]`, `[seedlog]`, `[sp]`, `[unlock]`);
  - a **Debug logging** setting;
  - a **Copy debug info** button that puts the run state, setup and last SP message on the clipboard.
- **UX rules:**
  - heavy work runs off the client thread, so the panel never freezes the game;
  - every failed check names the item and how many;
  - nothing acts on the game for you: the plugin only shows, highlights and reminds.
- **Each build phase ends with `./gradlew build` passing** (tests plus checkstyle), then an in-game check by you.

## 4. Remaining defaults (assumed unless you say otherwise)

- Repo: `C:\dev\farming-runs`, package `com.layneburns.farmingruns`, personal, not on the Hub.
- **Herb runs:** magic secateurs counted if owned, and the herb sack ignored. Hespori and anima are handled as single, once-in-a-while patches inside the same model.
- **Payment reminder** wording as in §2.6. Its colour and duration are settings.
- **Seaweed:** the two underwater patches count as one stop. The diving gear (fishbowl helmet and diving apparatus) is added to the check.

## 5. Verification (once built)

- **Unit tests:** data files load and their references resolve; the planner on hand-checked small cases; the seed picker with buffers and the contract; the inventory check's missing list.
- **In game:**
  - the seed log matches the bank and vault after opening each;
  - the leprechaun section matches what Tool Leprechaun's "Exchange" screen shows;
  - unlock states look right, with Prifddinas locked;
  - a herb + allotment run from Start to the last stop, with SP drawing each leg and auto-advancing;
  - the payment reminder appears and clears;
  - "Start anyway" works with an item missing.
