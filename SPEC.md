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
  - Turn Auto off to choose by hand.
  - **Buffer:** never use the last *N* of any seed. Settings: Allotment 9, Saplings 2, Other 3.
  - **Farming contract:** a contract you hold but haven't planted makes the matching Farming Guild patch use the contract seed. The buffer doesn't apply to it.
- **Payment:** one toggle per kind. A data file holds each crop's payment item and amount.
- **Tree routine,** set per kind: "check health, pay to remove" (200 coins) or "check health, chop and dig" (needs an axe). Trees with a crop (fruit trees, calquat, celastrus) are harvested first; you can switch that off per kind (added 2026-10-17).
- **Compost** setting: None, Compost, Supercompost (current), Ultracompost, Bottomless bucket or Fertile Soil.
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
   - the farmer's outfit, worn.

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
