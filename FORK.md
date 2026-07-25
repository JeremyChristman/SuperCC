# Jeremy's SuperCC fork — notes

A personal fork of **[SicklySilverMoon/SuperCC](https://github.com/SicklySilverMoon/SuperCC)**
(the Chip's Challenge / Tile World TASing emulator), with a few display-only mods and a
self-contained build. Upstream is GPLv2+; see `COPYING`.

## What's changed vs upstream

Each mod is its own commit on top of the pristine import, so `git log`/`git diff` show exactly
what's mine. All mods are **display-only** — no gameplay/solution/RNG behavior changes (verified: a
headless replay of a known solution reproduces the exact same tick-by-tick result before and after).

1. **Window title = build tag + pack + current level** (`java/emulator/SuperCC.java`).
   Title reads `SuperCC [jc-1] - <pack> - <level>` (bump the `[jc-N]` tag per production deploy so the
   running build is identifiable). Upstream showed only the level name; an earlier mod showed only the
   pack — this shows the version, pack, and current level together, updating as you change levels.
2. **Hint shown in the level panel** (`java/graphics/LevelPanel.java`). The level's hint text is drawn
   under "Author:" (uncapped width so even the longest hint wraps fully); hidden on hintless levels.
3. **Clone/Trap connections on by default** (`java/graphics/MenuBar.java`, `java/graphics/GamePanel.java`).
   The Clone- and Trap-connection overlays render by default (matching Monster/Slip list), and the
   "Show Clone Connections" menu label casing is fixed.

## Building

Requires a **JDK 16+** (upstream targets Java 16). From this folder:

```powershell
powershell -ExecutionPolicy Bypass -File build.ps1     # -> SuperCC.jar
```

`build.ps1` recompiles all of SuperCC's own source under `java/` with `javac --release 16`, keeps the
bundled third-party libs (`com/intellij` annotations, `org/json`) and the `resources/` assets as-is,
and repackages everything with the manifest — reproducing the upstream fat-jar structure (571 entries,
193 classes). It resolves a real JDK bin automatically (the Oracle `javapath` dir on PATH exposes
`javac` but not `jar`).

## Note on history / source vs. bytecode

Upstream ships its `.java` source *inside* the jar under `java/`. The prior hand-built mods were
spliced in as recompiled `.class` files only — the bundled source stayed pristine. This repo
reconstructs the mods **into the source** (from the documented changes), so source and behavior are
finally consistent and the whole jar builds cleanly from `java/`.
