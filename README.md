# Farming Runs

A personal RuneLite plugin for farming runs. It keeps a seed log, knows which patches you have unlocked, checks
your inventory before a run, orders the stops, and points the [Shortest Path](https://github.com/Skretzo/shortest-path)
plugin at each one in turn.

- What it does: [SPEC.md](SPEC.md)
- How it's built, and the phase it's at: [PLAN.md](PLAN.md)

## Running it

Shortest Path must be installed from the Plugin Hub, and RuneLite's Time Tracking plugin must be enabled.

```
./gradlew run      # launch RuneLite with this plugin loaded
./gradlew build    # compile, run the unit tests and checkstyle
```

## Layout

| Path | What's there |
|---|---|
| `src/main/java/com/layneburns/farmingruns/` | The plugin, one package per concern (PLAN.md §2) |
| `.../vendor/timetracking/` | RuneLite's patch decoding, copied by `scripts/vendor_timetracking.py`; don't edit by hand |
| `src/main/resources/com/layneburns/farmingruns/` | Game data files (PLAN.md §3) |
| `scripts/` | Data and vendoring scripts. `*.in.json` are their hand-edited inputs; see PLAN.md §3 for which file each script makes |
