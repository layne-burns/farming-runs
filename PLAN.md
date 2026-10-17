# Farming Runs: build plan (v1, 2026-10-17)

This is how [SPEC.md](SPEC.md) gets built: the code layout, the data files, the tests and the build phases. Each phase ends with `./gradlew build` passing (unit tests plus checkstyle), then an in-game check by you. Section numbers like §2.3 refer to SPEC.md.

## 0. Facts checked against the RuneLite API (client/api 1.13.2)

| Need | What exists |
|---|---|
| Tool leprechaun storage | Varbits `FARMING_TOOLS_RAKE/DIBBER/SPADE/SECATEURS/FAIRYSECATEURS/TROWEL/WATERINGCAN/BUCKETS/EXTRABUCKETS/EXTRA2BUCKETS`, `FARMING_TOOLS_COMPOST/SUPERCOMPOST/ULTRACOMPOST` (+ `EXTRA*`), `FARMING_TOOLS_PLANTCURE`, `FARMING_TOOLS_BOTTOMLESS_BUCKET_TYPE/QUANTITY` |
| Seed vault / seed box | Item containers `InventoryID.SEED_VAULT` (626) and `InventoryID.SEED_BOX` (573) |
| Farming contract | Varbits `FARMGUILD_CONTRACT_TYPE/LEVEL/COMPLETE/COUNT` |
| Spellbook | Varbit `SPELLBOOK` (4070) |
| Time Tracking's saved patch states | RS-profile config, group `timetracking`, one key per `<regionId>.<varbit>` holding `<varbitValue>:<epochSeconds>`, plus `farmTickOffset` and `farmTickOffsetPrecision`. The `protected` and `compost` keys hold payment and compost state. |
| Turning a varbit value into crop, stage and ready | `PatchImplementation.forVarbitValue`, `Produce`, `PatchState`, `CropState`. They're **package-private**, so they're vendored (§2). The patch table also has **GRAPES** and **CORAL**, which the spec left out; they're included as kinds that are off by default. |
| Outlining a patch | A patch's game object is a multiloc driven by the patch's varbit (`ObjectComposition.getVarbitId()`). The outline overlay matches objects by varbit, so **no patch coordinates are needed**. |
| Local overhead text | `Player.setOverheadText(...)` plus `setOverheadCycle(...)`, as in port-tasks' "n to take" text. |

## 1. Repository

- `C:\dev\farming-runs` is linked to https://github.com/layne-burns/farming-runs (empty, **public**; anything committed is visible).
- The layout copies port-tasks:
  - `build.gradle` with `run` and `shadowJar`;
  - `config/checkstyle/`;
  - `src/test/.../FarmingRunsPluginTest.java` as the launcher;
  - `runelite-plugin.properties`;
  - a BSD-2 `LICENSE`, matching RuneLite, because RuneLite code is vendored;
  - `README.md` and `scripts/`.
- **Commits:** one per phase, at least, after your in-game check, so `git log` reads as the phase list.

## 2. Code layout

Package root `com.layneburns.farmingruns`. Each class has one job. ~300 lines is a rough guide, not a cap: past it, check whether the class has a second job to split out, but never split just to hit the number. Vendored code doesn't count. The **(pure)** classes never touch `Client`, so they can be unit tested without the game.

```
FarmingRunsPlugin        wiring only: creates services, forwards events, nothing else
FarmingRunsConfig        RuneLite settings: buffers, reminder colour/duration, debug logging

vendor/timetracking/     RuneLite's PatchImplementation, Produce, PatchState, CropState (BSD), copied
                         unchanged apart from making them public. scripts/vendor_timetracking.sh re-fetches
                         them for a given RuneLite tag, so updates are a script run plus a diff.

data/                    (pure) loads resources/*.json into immutable records, checks references
  GameData               facade: getLocation(id), patchesAt(loc), seed(itemId), teleport(id)…
  DataLoader             Gson loading + validation errors that name the file and entry
  Location, PatchDef, SeedDef, PaymentDef, TeleportDef, UnlockRule, ToolDef, CostTable   (records)

state/                   the only package that reads the client (varbits, containers, config)
  PlayerInfo             levels, spellbook, diary tiers, quests → simple snapshot object
  UnlockService          evaluates UnlockRule per location + manual overrides (profile config)
  ItemSource             (pure) bank, seed vault, seed box (remembered), inventory, worn, leprechaun (live)
  StorageSnapshot        (pure) tracked item counts in one place + when they were seen
  ItemLedger             (pure, immutable) counts across all sources
  ItemStore              holds the current ledger; saves bank/vault/seed box to the RS profile; tells listeners
  ContainerWatcher       item containers → snapshots (noted items and placeholders made plain)
  LeprechaunDecoder      (pure) FARMING_TOOLS_* varbits → counts, using the game's own client-script arithmetic
  LeprechaunWatcher      re-decodes when those varbits change
  BufferSettings         (pure) seeds kept back per type: allotment / sapling / other
  PatchStates            reads Time Tracking's config + vendored decode → PatchStatus (ready / ready in / dead…)
  ReadyPredictor         (pure) next-ready-time maths, adapted from Time Tracking's prediction
  ContractService        current contract → seed + Farming Guild patch, if not yet planted

run/
  RunSetup               (pure) kinds, skips, seed mode/manual seeds, payment per kind, tree routine per kind, compost
  PresetStore            named presets as JSON in profile config; remembers last used
  SeedPicker             (pure) auto seed choice with buffers + contract override; mixing when short
  RunRequirements        (pure) setup + picks + teleports → list of RequiredItem (id/alternatives, qty, why)
  RoutePlanner           (pure) Held–Karp ≤ 20 stops, else greedy + 2-opt, over CostTable
  RunSession             (pure) state machine: stops, current, per-patch done flags, skip/jump
  RunController          client-thread glue: Start flow, varbit changes → RunSession → pathing/overlays

inventory/
  ItemCounts             (pure) merged counts: inventory, equipment, leprechaun, bank snapshot
  InventoryChecker       (pure) RunRequirements × ItemCounts → CheckResult (ok / via leprechaun / missing n)
  BankHighlightOverlay   outlines missing items in the open bank

pathing/
  ShortestPathClient     the only class that posts to "shortestpath": target, clear, send on change only,
                         resend after login/hop (pattern from port-tasks RoutingService)

ui/
  FarmingRunsPanel       side panel with tabs: Run, Bank, Locations
  RunTab                 setup form → Start; during a run, the ordered stop list with skip/go-next
  BankTab                draws BankView; redraws on item or buffer changes
  BankView, BankViewBuilder  (pure) what the Bank tab lists: seeds by kind (total / spare / where), compost,
                         payments (and what they protect), teleport items, leprechaun
  SectionPanel, ItemRow, TabPanel   shared look: collapsible sections, icon rows, dark background
  LocationsTab           unlock state + reason, override box, teleport dropdown, patch ready times
  NotReadyDialog, StartAnywayDialog
  PatchOutlineOverlay    outlines to-do patches at the current stop (match by varbit)
  OverheadReminder       pay / compost / tree-step text over the player
  DebugInfo              "Copy debug info": setup, session, last SP message, data version
```

Each external dependency has exactly one home: SP is `pathing`; game state is `state`; Time Tracking's format is `PatchStates`. When something breaks, the log tag (`[farmrun]`, `[seedlog]`, `[sp]`, `[unlock]`, `[patch]`) tells you which file to open.

## 3. Data files (`src/main/resources/com/layneburns/farmingruns/`)

**Generated** files come from a script and should never be edited by hand: edit the script's input and re-run it. **Hand** files are edited directly. All the scripts live in `scripts/` and share `scripts/wiki.py`.

| File | Kind | Contents | Made by |
|---|---|---|---|
| `patch_locations.json` | Generated | 43 locations and 99 patches: region, varbit and gardener names (from RuneLite's `FarmingWorld`), plus each patch's map tile and the wiki's teleport hints | `scripts/generate_patch_locations.py` (RuneLite tag + wiki *&lt;kind&gt; patch/Patches* tables) |
| `location_settings.json` | Hand | Per location: default teleport, unlock rule, per-patch unlock rules (Farming Guild tiers, the Harmony herb patch), note | Edited directly |
| `seeds.json` | Generated | 82 seeds: item id, kind, level, seeds per patch, sapling id, protection payment (items, ids, amounts) | `scripts/update_seed_data.py` (wiki *&lt;kind&gt; patch/Seeds* tables); `PAYMENT_FIXES` in it for corrections |
| `teleports.json` | Generated | 28 teleports: type, item ids (every charge variant) or spell runes and spellbook, waiver varbit | `scripts/update_item_data.py` from the hand-edited `scripts/teleports.in.json` |
| `items.json` | Generated | Named item groups (spade, axe, compost types, bottomless bucket, farmer's outfit pieces, seed box, coins) and rune ids | `scripts/update_item_data.py` from the hand-edited `scripts/items.in.json` |
| `unlocks.json` | Hand | Named rules: quest (finished or started), varbit (diary tiers), skill level | Edited directly |
| `costs.tsv` | Hand | `arrive` ticks per location, plus `link` ticks between neighbours (walks, spirit-tree hops) | Edited directly; tuned in phase 9 |

The tool leprechaun needs no data file: its encoding is the game's own `farming_tools_getstored` client script (runelite/cs2-scripts), coded in `LeprechaunDecoder`.

`DataLoader` checks every reference between files when the plugin starts, so a bad edit stops the plugin with a message naming the file and entry. `DataIntegrityTest` loads the real files and adds cross-file checks: every patch kind has seeds, no two patches share a Time Tracking key, every patch has a tile, saplings map back to their seeds, and spot checks against known in-game values.

## 4. Phases

| # | Phase | Delivers | Unit tests | In-game check |
|---|---|---|---|---|
| 0 | **Scaffold** ✅ | `git init`, remote, build, checkstyle, launcher; an empty plugin with a three-tab panel; vendored timetracking plus its sync script; README | The build compiles; the plugin loads in the test harness | The panel appears with three empty tabs |
| 1 | **Data** ✅ | All the §3 files, their scripts and `DataLoader` | `DataIntegrityTest`, `CostTableTest` | The plugin starts (data loads); the tabs are dark |
| 2 | **Seed log and leprechaun** | `ItemStore`, `ContainerWatcher`, `LeprechaunWatcher`, `BankTab` (via the pure `BankViewBuilder`), buffer settings, profile persistence | `LeprechaunDecoderTest`, `ItemLedgerTest`, `BankViewBuilderTest` | Counts match the bank, vault and seed box after opening each, and the leprechaun matches its Exchange screen. **Verify:** is the seed-box container sent at login or only once opened? What do bottomless bucket types 2–4 mean (log line `[leprechaun]`)? |
| 3 | **Unlocks** | `PlayerInfo`, `UnlockService`, `LocationsTab` with overrides and teleport dropdown | Rule evaluation on fake `PlayerInfo` | Prifddinas is locked (SotE) and everything else unlocked; overrides survive a relog |
| 4 | **Patch states** | `PatchStates`, `ReadyPredictor`, ready times in `LocationsTab` | Decode and predict on recorded Time Tracking values | Ready times match Time Tracking's own Farming tab |
| 5 | **Run setup** | `RunSetup`, `PresetStore`, `SeedPicker`, `ContractService`, `RunTab` setup form | Picker: best spare seed, buffers 9/2/3, mixing, contract override, manual mode | Auto picks look right; save, load and relog a preset |
| 6 | **Start checks** | `RunRequirements`, `ItemCounts`, `InventoryChecker`, `BankHighlightOverlay`, `NotReadyDialog`, `StartAnywayDialog` | Requirements per routine, payment and compost; leprechaun counts as available; missing counts | Remove an item → it's listed and highlighted in the bank → "Start anyway" works |
| 7 | **Route and run loop** | `CostTable`, `RoutePlanner`, `ShortestPathClient`, `RunSession`, `RunController`, the stop list in `RunTab` | Planner matches brute force on random small cases; session advance, skip and jump | A herb + allotment run: SP draws each leg and auto-advances when a stop is planted (and paid) |
| 8 | **Guidance** | `PatchOutlineOverlay`, `OverheadReminder`, `DebugInfo` | Reminder text per situation | Outlines on the patches still to do; the pay and compost reminders appear and clear |
| 9 | **Tuning** | Optional `[farmrun] leg` timing logs used to tune `costs.tsv` | — | A full all-kinds run feels well ordered |

The phases are mostly sequential. Phases 2, 3 and 4 depend only on phase 1, so they could run in parallel sessions if you'd like. Phase 5 needs 2 to 4, and phases 6 to 8 build on each other.

## 5. Risks and checks
- **Time Tracking's storage format** isn't a public API, so a RuneLite update could change it. Mitigation: `PatchStates` is the only reader, there's a unit test on recorded values, and the panel says "Time Tracking data unavailable" rather than crashing.
- **Vendored decode tables drift** when Jagex adds crops. Mitigation: re-run `vendor_timetracking.sh` after RuneLite updates.
- **Some SP targets may be blocked** in SP's collision map (port-tasks hit this at Summer Shore). Mitigation: phase 7 checks every location's target, and `locations.json` can hold a nudged tile.
- **Public repo.** Nothing personal goes into data or logs; account-specific state stays in RuneLite's profile config.
