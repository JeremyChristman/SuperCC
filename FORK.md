# Jeremy's SuperCC fork — notes

A personal fork of **[SicklySilverMoon/SuperCC](https://github.com/SicklySilverMoon/SuperCC)**
(the Chip's Challenge / Tile World TASing emulator), with a few display-only mods and a
self-contained build. Upstream is GPLv2+; see `COPYING`.

## What's changed vs upstream

Each mod is its own commit on top of the pristine import, so `git log`/`git diff` show exactly
what's mine.

**Through jc-18, no change altered the outcome of an existing solution** — movement, RNG and every
stored replay ended exactly as before. Through jc-14 that was true because the engine source was
untouched. The upstream merge (from 2026-11-02, see *Upstream merge*) changed engine source, so it
was held to the measured version of the claim instead: every stored solution in the maintainer's
collection replayed under the previous release and the new build, byte-identical. **jc-19 breaks
the claim on purpose:** it carries upstream's own trap code exactly, bugs included (*The upstream
baseline*), and 13 of the 24,212 stored solutions end differently. The fork's own changes are still
held to the claim; upstream's code is now held to upstream. From jc-24 fixes go back on top (*The
fix layer*), each moving exactly the routes it is for: jc-24's trap fix moves back those 13. What the
fork *does* change, by release:

| | Scope |
|---|---|
| Mods 1–3 | **Display-only** — window title, hint panel, connection overlays. |
| jc-4, jc-5 | **Disk behavior** — how the settings file is read and written. See *Settings-file resilience* and *Portable paths*. |
| jc-7 | **Level loading** — three levels that could not be opened at all now open. See *Off-map monster-list entries*. |
| jc-8 | **Settings file + release packaging** — renamed to `succ_settings.ini`, the TWS folder stops drifting, the build tag defaults to off, and the release ships as a documented zip. See *The settings file (jc-8)*. |
| jc-9 | **Opening ruleset** — an opt-in setting forces every set to open under MS. See *Always open under MS (jc-9)*. |
| jc-10 | **Settings behavior** — the TWS folder remembers the last folder used again, reversing that part of jc-8. See *The TWS folder remembers again (jc-10)*. |
| jc-11 | **Diagnostics** — errors are written to a log file instead of vanishing under `javaw`, and the NPE that fired on every launch is gone. The emulator is untouched: all 286 sets re-parsed and all 23,322 stored solutions re-played, byte-identical. See *The error log (jc-11)*. |
| jc-12 | **GUI robustness** — canceling a file chooser threw an uncaught NPE on the event thread, and a missing solution file produced a stack trace and a message that was just a path. Engine untouched. See *jc-12 -- the two ways MenuBar.openFileBytes could fail*. |
| jc-13 | **File-format robustness** — a truncated `.tws` was accepted and then misread; it is refused now, with a message. 23,967 real solution files re-verified, none rejected. Engine untouched. See *jc-13 -- a truncated .tws was accepted*. |
| jc-14 | **Display** — every part of the window title is a setting now, and the separators are computed from what is left rather than templated. The stock title changes: it no longer names the level set. Engine untouched. See *jc-14 -- the window title is fully switchable*. |
| jc-15 | **Upstream merge, steps 1 and 2** — upstream's trap rework (`858dbd0`) with four fixes of the fork's own, and its Lynx input discarding (`b997c27` + `98366f7`), which records a key the engine ignores as a wait. Replay-identical on all 24,204 stored solutions; 40 Lynx move lists record differently once loaded. See *Upstream merge*. |
| jc-16 | **Upstream merge, step 3** — Seed Search stops at the last seed instead of wrapping into negative seeds forever (`40495dd`), plus three fork fixes: a negative start, threads that finished before the next one started, and an example seed that hid negative seeds. `SeedSearch` is form-based, so this added `formclass.ps1`, which rebuilds a form class whose `.form` is unchanged (ADR 0010). Engine untouched. See *Upstream merge*, step 3. |
| jc-17 | **Upstream merge, step 4** — nothing a user can touch throws before a level set is open any more (upstream `72476bc`, hand-merged, extended by the fork to one guard over every menu — 46 of the 56 menu controls threw on jc-16 — plus canceling Go to… / Set RNG Seed no longer throws). Levels with no title open (MO3 #12 and #14, the only two of 23,476 that could not), and the Black and White tileset loads (upstream `f701c85`; broken in every earlier release). First rebuild of the main window (`Gui`) with `formclass.ps1`, and `GuiWindowTest` builds the real window in a non-headless JVM. Replay-identical on all 24,212 stored solutions. See *Upstream merge*, step 4. |
| jc-18 | **Upstream merge, steps 5 and 6 — the fork now carries every upstream commit through v2.0.3.** Custom tilesets (`d78f6f3`) with fork fixes for shared paths, Cancel, unreadable images, and a fallback when a saved tileset cannot be drawn (a number out of range used to stop startup). Auto-diagonals (`5f928e0`, `fb8fe25`, `da35e33`) with the key handling rewritten: upstream ticked from a background thread, let other input overtake a waiting arrow, lost taps and ate keys under MS. Form classes are now compiled by JetBrains' own form compiler and checked byte for byte (ADR 0011). Replay-identical on all 24,212 stored solutions. See *Upstream merge*, steps 5 and 6. |
| jc-19 | **The upstream baseline, step 1 — the trap code is upstream v2.0.3's exactly, bugs included.** The fork's four fixes to the trap rework are reverted (`79b0032`), at the maintainer's request, to be layered back later. Under MS a trap that starts with Chip or a block on it starts SHUT, or, with two or more wires, the level does not open: 5 levels in the collection no longer open, 6 solved routes lose the exit, 2 more end elsewhere; Lynx unchanged. See *The upstream baseline*. |
| jc-20 | **The upstream baseline, steps 2 and 3 — Seed Search and the move recorder are upstream's exactly.** Seed Search's four fixes from jc-16 are reverted (`397c7bb`): a negative start leaves one thread doing all the work, the threads race at the start, and a success at a negative seed is never shown. The recorder's both-bits fix from jc-15 is reverted too (`923d07c`), which changes nothing measurable: every stored route ends and records exactly as on jc-19. Every problem the baseline brings back is now tracked in `BASELINE-ISSUES.md`. See *The upstream baseline*. |
| jc-21 | **The upstream baseline, step 4 — the no-level-loaded guards are upstream's exactly.** jc-17's extensions of upstream's `72476bc` are reverted (`c57f217`), and the two upstream lines it left out are in: with no level set open 29 menu controls fail again, canceling Go to..., Set RNG Seed or a file chooser fails again, a set whose level 1 will not load opens badly again (none in the collection does), and a level whose savestates fail is half-loaded again (none does). UB-11 to UB-15. Every stored route replays exactly as on jc-20. See *The upstream baseline*. |
| jc-22 | **The upstream baseline, step 5 — custom tilesets are upstream's exactly.** jc-18's fixes to upstream's `d78f6f3` are reverted (`5d3f0a3`): custom is saved as tileset number 5 with full paths, canceling asks again and then complains, a picture is saved before it is known to load, and a saved tileset that cannot be drawn stops SuperCC from starting (no window) or fails at every Lynx set opened. UB-16 to UB-21. Nobody's tileset changes by itself, and every stored route replays exactly as on jc-21. See *The upstream baseline*. |
| jc-23 | **The upstream baseline, step 6 — auto-diagonals are upstream's exactly.** jc-18's rewrite of the key handling is reverted (`d08e6ef`). With Tools › Controls › Auto diagonals on, every game key waits 50 ms and a background timer makes ONE move from the keys still held: taps are lost, a held key runs at about half speed, keys pressed together make one move, MS waits too and loses keys, other input jumps ahead, and a key let go elsewhere stays held; `AutoDiagonals = 1` is OFF. UB-22 to UB-28. The switch is off by default, nothing changes with it off, and every stored route replays exactly as on jc-22. That finishes the five merge deviations. See *The upstream baseline*. |
| jc-24 | **The fix layer begins — trap Fix 1 is back.** On the baseline declared done after jc-23 (the fork is the latest official SuperCC, 2.0.3, plus its own pre-merge changes), fixes now go back one at a time. The first is jc-15's Fix 1 (`0cd4140`): `MSLevel` builds its trap maps before pressing the traps that start occupied. UB-1 and UB-2 are fixed: 5 MS levels open again (4 of their routes reach the exit), 6 solved routes reach the exit again, and 2 unsolved routes end as before — exactly the 13 the jc-19 revert broke, and no other stored route changes. See *The fix layer*. |
| jc-25 | **The fix layer — trap Fix 2.** `MSLevel.finaliseTraps` decides each trap once, after all its wires (`be0d0fb`), so an empty trap with a held button is open whatever the hash order (and a trap wired twice from one held button, which upstream always shut, opens too). UB-3 is fixed. The defect was latent (a creature entering a trap re-checks its buttons), so every stored route replays exactly as on jc-24; only the trap bits kept in savestates, in memory and on disk, can differ. See *The fix layer*. |
| jc-26 | **The fix layer — trap Fix 4.** Each trap's savestate bit is numbered by its first wire's record in the .dat again (`d974875`), not by hash order. UB-5 is fixed: `.ser` savestate files from jc-15 to jc-18 load right again, and jc-14's wherever each trap has one wire, and the numbering no longer depends on hashing. The cost: a `.ser` saved by jc-19 to jc-25 can load with a trap wrong, on a level with two or more traps (UB-29). Every stored route replays exactly as on jc-25. See *The fix layer*. |
| jc-27 | **The fix layer — trap Fix 3; all four trap fixes are back.** Lynx `isTrapOpen` answers shut on a position no brown button is wired to, as MS does, instead of throwing (`dc3bb11`). UB-4 is fixed. Nothing in play asks that question (its one caller, `BrownButton.isOpen`, is called by nothing in the jar), so every stored route replays exactly as on jc-26. See *The fix layer*. |

> **`settings.ini` is `succ_settings.ini` from jc-8 on.** Older sections below are left in their
> original wording where they describe historical work; read "settings.ini" in those as the same
> file under its old name.

1. **Window title = build tag + pack + current level** (`java/emulator/SuperCC.java`).
   Title reads `SuperCC [jc-N] - <pack> - <level>` (bump `BUILD_TAG` per production deploy so the
   running build is identifiable). Upstream showed only the level name; an earlier mod showed only the
   pack — this shows the version, pack, and current level together, updating as you change levels.
   **As of jc-14 all three parts are toggleable — see below.**
2. **Hint shown in the level panel** (`java/graphics/LevelPanel.java`). The level's hint text is drawn
   under "Author:" (uncapped width so even the longest hint wraps fully); hidden on hintless levels.
3. **Clone/Trap connections on by default** (`java/graphics/MenuBar.java`, `java/graphics/GamePanel.java`).
   The Clone- and Trap-connection overlays render by default (matching Monster/Slip list), and the
   "Show Clone Connections" menu label casing is fixed.

## Upstream merge

The fork was imported from upstream commit `e34672b` (2021-09-10), 13 commits short of upstream's
latest release, v2.0.3 (2024-06-23, still its master tip). **All of them are in as of jc-18.** They
were brought in one logical change at a time, each replayed against the corpus before it landed, because SuperCC is the
reference engine the Tile World desync work was measured against: an engine commit moves the
yardstick. Upstream lays its source out under `src/main/java`, this repo under `java/`, so each
commit is applied as a patch (`git apply -p4 --directory=java`), not merged by git.

| Upstream | What | Status |
|---|---|---|
| `858dbd0` (+ `82198a3`, its test data) | Trap rework: one trap bit per TRAP, not per wire | **merged, jc-15** — step 1, below. The fork's four fixes to it were **reverted** for the upstream baseline in jc-19 (next section), then **all four put back, one per release, jc-24 to jc-27** (*The fix layer*) |
| `b997c27`, `98366f7` | Input discarding (Lynx forced moves); the second undoes the first's MS effect | **merged, jc-15** — step 2, below |
| `40495dd` | Seed search: an `int` overflow made long searches go negative | **merged, jc-16** — step 3, below |
| `72476bc` | Linux test runner, plus guards for "no level loaded" in `EmulatorKeyListener`, `MenuBar` and `Gui.java` | **merged, jc-17** — step 4, below; hand-merged (its `requireNonNull` around `openFile` would undo jc-12's cancel fix, so it was left out); the Linux test runner and Maven workflow do not apply here |
| `5f928e0`, `fb8fe25`, `da35e33`, `8c95b3e` | Auto-diagonals | **merged, jc-18** — step 6, below; the Controls window verbatim (its `.form` edit compiled by JetBrains' own form compiler, ADR 0011), the key handling rewritten. ⚠ The rewrite was **reverted** for the upstream baseline (jc-23): the key handling is upstream's exactly |
| `f701c85` | Renames `BW-overlay.png` to the `bw-overlay.png` the code asks for | **merged, jc-17** — the Black and White tileset failed in every release, since jar entries are case-sensitive (step 4, *The second review*) |
| `d78f6f3`, `3e92380` | Custom tilesets | **merged, jc-18** — step 5, below; with fork fixes for paths shared between machines, Cancel, unreadable images and a missing image at startup |

### The upstream baseline (from jc-19; in progress)

**The decision (Jeremy, 2026-11-08):** *"I want this to be as true to actual upstream as possible."*
Every place the merged upstream code was changed or left out is being put back to upstream v2.0.3
**exactly**, one at a time, each in a commit of its own. Once that baseline stands, fixes are layered
back on top of it as separate, clearly marked fork changes — so the fork is upstream plus a readable
list of fixes, not upstream rewritten in places. A deviation reverted for the baseline is not
judged wrong; most were bug fixes, and the plan is to restore them deliberately afterward.

**How a revert keeps its fix recoverable:**
- each revert is ONE commit holding the code AND its test changes (the notes are committed
  separately), so `git revert <that commit>` puts ALL its fixes back exactly, tests included (from
  jc-24 the fix layer takes them back one at a time instead, by hand: *The fix layer*). Then, by
  hand: the notes that list it as reverted (this section, CLAUDE.md §6, CHANGELOG), and the
  numbers the revert moves — `coverage.ps1 -UpdateBaseline` on JDK 16.0.2, the CLAUDE.md §4
  coverage table, and its count of `upstreamBug` pins — or the release's coverage gate fails;
- while reverted, the fix's tests are kept as `Harness.upstreamBug(what, actual, upstream, correct)`
  pins: each PASSES only on upstream's own wrong answer, as measured on upstream's code, so it
  holds the code AT upstream's behavior rather than merely away from the correct one. The correct
  answer fails as "the correct answer came back" (so a fix that returns any other way is noticed
  too), and any third answer fails as a plain regression. Run against a build with the fix, they
  fail and nothing else does (measured for the trap revert below: 23 such failures, 0 others, on
  jc-18's jar). The helper itself was committed separately, as groundwork, so reverting one step
  never removes what the others' pins call;
- **every problem a revert brings back is entered in [`BASELINE-ISSUES.md`](BASELINE-ISSUES.md)**
  (`UB-n`) in the same push — what a user sees, what it was measured to affect, the fix, and the
  pins — so that none is forgotten when the fixes come back (Jeremy, 2026-11-08: *"keep track of
  all issues doing these revisions introduces so that we can fix them later"*). A fix closes its
  entry; nothing is deleted.

**Done** (the problems each one brings back are UB-1 to UB-29 in `BASELINE-ISSUES.md`):

| Deviation | Reverted in | What the upstream behavior costs |
|---|---|---|
| The four fixes to the trap rework (step 1 below: start-occupied traps, hash-order trap state, Lynx `isTrapOpen` null guard, trap bit = first wire's record) | `79b0032`, released as jc-19 (its pin helper: `e9918c9`) | 13 stored solutions replay differently, measured 2026-11-08 on the whole collection (24,212) against jc-18 — exactly the 13 of 2026-11-01. **5 levels do not open at all** (load throws `NullPointerException`; a trap with 2+ wires starts occupied): EEfor5subs #16 Amaerica, IsaacD1-Old #116 Switch flip, JRB_CCLP1 #18 ISLANDS, TCCLP #110 World War II, TomP3 #66 RAINBOW DOOR KEY (4 of them solved). **6 solved routes no longer reach the exit** (a 1-wire trap that starts occupied stays SHUT): C1059-1 #47 Two Sets Of Items, EEfor5subs #26 Ugly cry, geodave1 #52 Circle of Life, geodave4 #7 Circle of Life, IsaacD1-Old #121 Whats in there, TLFC3 #89 MORE CHARACTER. **2 unsolved routes end differently**: MO1 #61, TCCLPpgchip #2. Behavior on those levels now differs from MSCC and Tile World, which open a trap that starts occupied; a route re-solved under it would not replay in Tile World. Also: a `.ser` savestate file saved by an earlier build may load with a trap wrongly open or shut — from jc-15 to jc-18 on a level with more than one trap (upstream numbers trap bits by hash order, jc-15 to jc-18 by first wire's record), and from jc-14 or earlier also wherever a trap has two or more wires (one bit per wire then; jc-19 reads one bit per trap). The collection holds no `.ser` files. |
| Input discarding, the recorder: the key kept when `Level.tick` reports the automatic-tick bit as well as the discard bit (step 2 below) | `923d07c`, released with jc-20 | **Nothing measurable.** No engine path returns both bits, so `SuperCC.tick` records exactly as before: a scratch census (the corpus harness plus the length, CRC32 and wait count of the move list SuperCC records) gave all 24,212 stored solutions identical against jc-19, final states and recordings alike. Its control, jc-14 against jc-19, found step 2's 40 Lynx routes and 2,500 keys recorded as waits, so the census does see a recording change. Only a level FORCED to report both bits shows the difference (`InputDiscardTest` §5): upstream records `r--UUUUUu` where the fix recorded `rUUUUUUUu`, and FORK.md step 2 measured that such a route would replay 3 ticks short. |
| Seed Search's four fixes from jc-16 (step 3 below: the `(long)` for a negative start, threads built before any starts, the "no success yet" sentinel, `volatile` flags) | `397c7bb`, released as jc-20 | Four problems back, UB-7 to UB-10. **A negative start does not share the work** — measured in a scripted playtest in the real window (CCLP1 #1, start -5, 4 threads): threads 0-2 never moved and thread 3 did all 219,765 attempts, from -1,610,612,735. **The threads race at the start**, so "running" can go false mid-search (a Pause click in that moment starts a second set of threads; each thread start also clears a pending Pause). **A success at a negative seed is never shown** (the same playtest: 219,765 successes, no example seed). The `volatile` flags have no observed effect on HotSpot. Engine untouched, nothing replays differently. `SeedSearch` is form-bound: compiled with `formclass.ps1 -Compile`, byte-checked by `-Check`. Two of upstream's bugs are races, so `SeedSearchTest`'s pins were measured under CPU affinity (1, 2, 3, 4 and 28 CPUs, every run green on upstream's code; one 8-seed search alone missed the race 1 time in 4 on 2 CPUs, so the pin takes five; §3 checks the threads themselves rather than `running`, which the same race can drop mid-search, and a new §5 keeps Pause covered from a start where no thread can finish early), and the 3,000-seed check is skipped on this code. The revert also broke a tooling self-test that relied on the fork's SeedSearch source (`formclass.ps1 -SelfTest`, the transplant case); fixed first, in `821066c`, with a specimen whose constructor differs by construction. |
| The no-level-loaded guards and fixes from jc-17 (step 4 below: `MenuBar.requireALevel()` and its exemptions, Cancel on Go to... / Set RNG Seed, the Copy macro hover, `ChooseTileSize`'s dead line, `openLevelset` at the first level that loads, the all-or-nothing `loadLevel`), plus upstream's two lines jc-17 left out (`Objects.requireNonNull(openFile(...))`, `assert tws != null`) | `c57f217`, released as jc-21 | Five problems back, UB-11 to UB-15. **With no level set open, 29 menu controls fail** with an internal error (a `NullPointerException` on the event thread, written to the error log) — measured on the desktop; `GuiWindowTest`'s walk of every control in a real window pins 28, since Paste solution reads the clipboard first and fails this way only when it holds text (CI caught that: `GuiWindowTest` no longer clicks it); upstream's own guards cover the rest. **Canceling Go to... or Set RNG Seed fails**, and so does **canceling the file chooser** of Solution › Open, Search for seeds or Load states (jc-12's fix, undone by upstream's `requireNonNull`). **A set whose level 1 will not load opens badly** (an error, then no level on a fresh start, or the previous set's level under the new file) — none of the 331 sets in the collection, all scanned. **A level whose savestates fail to build is half-loaded** — no level reaches it while untitled levels load (the fork's own jc-17 fix, kept). Upstream's own guards, the Black and White rename and the fork's older features (jc-9, jc-10, jc-11, jc-12's missing-file message, jc-14) stay. Corpus vs jc-20: 24,212 of 24,212 identical. `ChooseTileSize` is form-bound: compiled with `formclass.ps1 -Compile`. `GuiWindowTest`'s child gained PIN lines, judged by `Harness.upstreamBug` on the parent side; a new check pins Cancel on Solution › Open (UB-13). |
| The custom-tileset fixes from jc-18 (step 5 below: relative paths, Cancel, load-before-save, the per-ruleset switches instead of `TilesheetNum = 5`, the fallback loaders at startup, the ruleset swap and the window resize, the size and format checks, the radio mark following what is drawn) | `5d3f0a3`, released as jc-22 | Six problems back, UB-16 to UB-21. **Custom is saved as tileset number 5**, which builds before jc-18 cannot start with (pinned in a real window by `GuiWindowTest` §5b, which answers custom's file choosers with real pictures). **Its pictures are saved as full paths**, missing on the other computer. **Canceling asks for the second picture anyway, then shows an error**, and leaves the mark on custom. **A picture is saved before it is known to load**, and bad ones fail with internal errors or unclear messages (six exact pins in `EngineTest` §6f, rewritten through upstream's API with the settings written as a file). **SuperCC does not start — no window — when the saved MS tileset cannot be drawn**: measured by launching, with `TilesheetNum = 99` and with custom's pictures missing. **A Lynx tileset that cannot be drawn fails at every Lynx set opened.** Nobody's tileset changes by itself; the maintainer's settings use no custom tileset. `TileSheet`, `SmallGamePanel` and `Gui` are upstream's byte for byte (`Gui` compiled with `formclass.ps1 -Compile`); `SuccPaths` keeps the fork's own settings structure. Corpus vs jc-21: 24,212 of 24,212 identical. |
| The auto-diagonals rewrite from jc-18 (step 6 below: `DiagonalCombiner`, a Swing timer on the event thread, only arrows waiting, the flush before any other input, newest key per axis, repeats at full speed, no window under MS, releases seen program-wide, the switch through `optedIn()` and its cheap `getAutoDiagonals()`) | `d08e6ef`, released as jc-23 | Seven problems back, UB-22 to UB-28, all only with Tools › Controls › Auto diagonals on (off by default; off in the maintainer's settings). **Every game key waits 50 ms, and ONE move is made from the keys still held**, on a `java.util.Timer`'s own thread: a tap makes no move, four presses inside one window make one, a wait pressed with two arrows is lost, three arrows go by table order, and Up held with Right tapped makes Up. **MS waits too**, and Up held with Right pressed moves only Up. **Other input jumps ahead**: a state saved with Shift+1 misses the waiting move. **A key let go elsewhere stays held.** `AutoDiagonals = 1` reads OFF and is written back as `1`. Pinned in a real window by `GuiWindowTest` §6 (12 pins, every one also measured at 1 and at 2 CPUs) and by `SettingsTest` §14 (2). Against jc-22's jar exactly those 14 fail, as "the correct answer came back", and nothing else does. `EmulatorKeyListener` and `ControlGUI` are upstream's byte for byte (`ControlGUI` compiled with `formclass.ps1 -Compile`); `SuccPaths` keeps the fork's own settings structure. Corpus vs jc-22: 24,212 of 24,212 identical (replay never goes through the key listener). |

**After the baseline:** the trap fixes go back one at a time (*The fix layer*, below). **Fix 1 is
back in jc-24**, so those 5 levels open again and the 6 routes solve again. A whole
`git revert 79b0032` (all four fixes, proven before jc-19 to leave `java/` identical to jc-18) no
longer applies cleanly, because jc-24 rewrote the same constructor lines. **Fix 2 is back too**
(`be0d0fb`, jc-25), **then Fix 4** (`d974875`, jc-26) **and Fix 3** (`dc3bb11`, jc-27), each by
hand from that commit's diff, so all four trap fixes are back.

**Where the remaining fixes are, for the fix layer** (each goes back by hand, one fix at a time, by
*The fix layer*'s procedure below, never as one whole `git revert` of a commit that bundled several):
- the recorder fix — `923d07c`, one fix (`InputDiscardTest` §5);
- the four Seed Search fixes — `397c7bb` (`SeedSearchTest`; it also changed the compiled form
  classes and the form and coverage baselines, which a fix to `SeedSearch` must rebuild the same way);
- the no-level guards — `c57f217`, several, plus `7ba0d45`, a later test fix (`EngineTest` and
  `GuiWindowTest` pins; `ChooseTileSize`'s compiled class);
- the custom-tileset fixes — `5d3f0a3`, several (`EngineTest` §6f, `SettingsTest` §13,
  `GuiWindowTest` §2 and §5b; `Gui`'s compiled class);
- the auto-diagonals rewrite — `d08e6ef`, one rewrite (`GuiWindowTest` §6, `SettingsTest` §14, and
  `AutoDiagonalTest` and `DiagonalCombiner` themselves).

Where two of these commits changed the same lines, a fix taken from the older one meets the newer
one's code: steps 4 and 5 both rewrote View › Tileset's listener in `MenuBar`, and steps 5 and 6
both edited `SuccPaths`. Resolve those hunks by hand against the code as it is.

**The queue — every `MOD (Jeremy, upstream merge)` in `java/`, one at a time** (all five reverted as
of jc-23):
1. ~~**Input discarding** (`SuperCC.tick`): the key is kept when the multi-tick bit is also set.~~
   Reverted, `923d07c` (table above).
2. ~~**Seed search** (`SeedSearch`, six edits): the `(long)` cast for a negative start, threads built
   before any starts, `volatile` flags, the "no success yet" sentinel.~~ Reverted, `397c7bb`.
3. ~~**No-level-loaded guards** (step 4): `MenuBar.requireALevel()` over every menu (upstream guarded
   six items), Cancel on Go to… / Set RNG Seed, Copy macro hover, `ChooseTileSize`'s removed line,
   `openLevelset` opening at the first level that loads and the all-or-nothing `loadLevel`; and the
   two upstream lines left out — `Objects.requireNonNull(openFile(...))` (it undoes jc-12's Cancel
   fix) and `assert tws != null`.~~ Reverted, `c57f217` (and the two lines put in).
4. ~~**Custom tilesets** (step 5): relative paths, Cancel, load-before-save, the per-ruleset switches
   instead of `TilesheetNum = 5`, the fallback loaders, size/format checks.~~ Reverted, `5d3f0a3`.
5. ~~**Auto-diagonals** (step 6): the rewritten key handling (`DiagonalCombiner`), `SuccPaths.Controls`
   edits, `ControlGUI` reading `getAutoDiagonals()`.~~ Reverted, `d08e6ef`.

Separate from these are the fork's own features and fixes from before the merge (jc-1–jc-14, and
jc-17's untitled-level fix), which change upstream's behavior without having been part of a merged
upstream commit. They stay: on 2026-11-09 Jeremy declared the baseline done without reverting them
(next section).

### The fix layer (from jc-24)

**The decision (Jeremy, 2026-11-09):** *"We have a good baseline now."* The fork is, in essence,
forked off the latest official SuperCC, 2.0.3, plus its own pre-merge changes. That was checked
against the official build itself (installed outside this repo, replayed by the maintainer's
`official_baseline.ps1`): on all 24,212 stored solutions the official jar and jc-23 differ on exactly
3, jc-7's off-map monster-list levels. Fixes now go back on top of that baseline **one at a time,
each against the true latest upstream code**, so every difference from upstream stays explainable.

**How a fix goes back:**
- one fix per commit, re-applied by hand from the baseline commit that removed it — never a whole
  `git revert` of a baseline commit that bundled several fixes;
- its code marked `MOD (Jeremy, fix layer, UB-n)`, so the fix layer is one search;
- its own `upstreamBug` pins turned back into checks, every other pin left alone, and the flip
  check run: on the previous release's jar exactly those checks fail and nothing else;
- the corpus against the previous release moves exactly the routes the fix is for;
- its `BASELINE-ISSUES.md` entries marked FIXED (never deleted).
- released like any change: a fix changes the jar, so it gets a build tag of its own, even when no
  stored route moves (RELEASING.md lets only work that does not ship in the jar -- tests, docs,
  tooling -- ride along with the next release).

**Done:**

| Fix | Released | What it changes |
|---|---|---|
| Trap Fix 1 (closes UB-1, UB-2): `MSLevel`'s constructor builds `trapButtons` and `trapIndexMap` before pressing the traps that start occupied | `0cd4140`, jc-24 | Corpus vs jc-23: exactly the 13 the jc-19 revert broke, and nothing else. **5 levels open again** (EEfor5subs #16, IsaacD1-Old #116, JRB_CCLP1 #18, TCCLP #110, TomP3 #66; 4 of their routes reach the exit), **6 solved routes reach the exit again** (C1059-1 #47, EEfor5subs #26, geodave1 #52, geodave4 #7, IsaacD1-Old #121, TLFC3 #89), 2 unsolved routes end differently from jc-23, back where jc-18 had them (MO1 #61, TCCLPpgchip #2). Against jc-18 (all four trap fixes, replayed with the same harness): **24,212 of 24,212 identical**, so Fix 1 alone accounts for every stored route the trap fixes affected. Trap numbering is still upstream's hash order (Fix 4, UB-5), and Fixes 2 and 3 stay reverted (UB-3, UB-4). `MsCloneTrapTest` §6's 9 pins are checks again, plus new ice-block fixtures; on jc-23's jar exactly those 14 checks fail. |
| Trap Fix 2 (closes UB-3): `MSLevel.finaliseTraps` decides each trap once, after all its wires, so an empty trap with a held button is open whatever the hash order (a button wired to several traps still counts for its first one only) | `be0d0fb`, jc-25 | **Latent: no stored route moves** — corpus vs jc-24 24,212 of 24,212 identical. The defect was real but unobservable: traced on jc-24, a creature entering a trap re-checks its buttons, the engine's other reads are of occupied traps, and the UI never shows the bit; only savestates carried the wrong value. The same update also shut, every time, a trap wired twice from one held button; that opens too (review, measured). Fix 2 does not touch the "shuts behind" release that Tile World's FIX_TRAP_SHUT_BEHIND matches (PB_Gourami #254): that trap is occupied, and no button is held. `MsCloneTrapTest` §7's 3 pins are checks again (all six arrangements open), plus a check for the trap wired twice; on jc-24's jar exactly those 4 fail. Fixes 3 and 4 stay reverted (UB-4, UB-5). |
| Trap Fix 4 (closes UB-5): each trap's savestate bit is its FIRST wire's record index in the .dat, built by `LevelFactory.getTrapIndexMap` and passed to `MSLevel`, instead of upstream's HashMap order | `d974875`, jc-26 | **No stored route moves** — corpus vs jc-25 24,212 of 24,212 identical. The bit number is a `.ser` file format: files from jc-15 to jc-18 load right again, and jc-14's wherever each trap has one wire (the two-wire cost documented under step 1 since jc-15 is back too, and now pinned by a check), and a state played in jc-26 loads right in all of them (one loaded from another build's file keeps its extra bits and saves them back, as in jc-15 to jc-18). ⚠ The other cost, the same problem in reverse: a `.ser` saved by jc-19 to jc-25 (hash order) can load with a trap wrong in jc-26, on a level with two or more traps (UB-29, accepted). The maintainer's Dropbox holds no `.ser` file at all (searched 2026-11-09). `MsCloneTrapTest` §10's 3 pins are checks again; plus a check pinning the jc-14 two-wire cost; on jc-25's jar exactly those 4 checks fail. Only Fix 3 stays reverted (UB-4). |
| Trap Fix 3 (closes UB-4): Lynx `isTrapOpen` answers shut on a position no brown button is wired to, instead of iterating a null list | `dc3bb11`, jc-27 | **Unreachable in play: no stored route moves** — corpus vs jc-26 24,212 of 24,212 identical. Lynx springs traps through `LynxCreatureList.springTrappedCreature`, and the method's one caller, `BrownButton.isOpen`, is called by nothing in the jar (scanned jc-26's and jc-27's). `ConnectionTest` §4's pin is a check again, plus checks that a wired trap with nothing on its buttons is shut and, under Lynx, open with a block or Chip resting on either of its buttons, in turn (the path past the guard, which no test reached under Lynx; the reference is `lxlogic.c`, which springs a Lynx trap every tick something rests on a wired button; both buttons are tried because a trap's button list comes in HashMap order, so a loop stopping at the first button fails one of them whatever that order is — that, a loop asking about the trap end of the wire, one never answering open and one leaving Chip out were each planted and caught). Upstream's method still answers more broadly than the engine acts (a creature still moving onto a button, every trap a button is wired to); unreachable, and recorded in `BASELINE-ISSUES.md`'s last section; on jc-26's jar exactly the one converted check fails. **All four trap fixes are back**; no trap issue is open (UB-29 is accepted). |

### Step 1 — the trap rework (`858dbd0`), and four fixes to it

> **Reverted for the upstream baseline (2026-11-08, jc-19 to jc-23), and all four fixes are back
> since jc-27** — Fix 1 from jc-24, Fix 2 from jc-25, Fix 4 from jc-26 and Fix 3 from jc-27, each by
> hand on upstream v2.0.3's code (*The fix layer*). *The upstream baseline* above says what the
> revert cost while it lasted. The record below describes the fixes as they were first written, in
> jc-15 to jc-18.

**What upstream changed.** Each `BrownButton` used to carry its record's index in the file, and
`MSLevel.traps` held one bit per *wire*: a trap was open if any wire into it had its bit set.
`858dbd0` gives each *trap* one bit instead, found through a new trap-position → buttons map,
`Level.getTrapButtons()` (the reverse of `getBrownButtons()`). It removes
`BrownButton.getTrapIndex()`, the three-argument `BrownButton` constructor and
`Level.setRedButtons`/`setBrownButtons`, and adds `LevelFactory`'s own off-map guard on the monster
list (it agrees with jc-7's). Applied verbatim, plus the four changes below, each marked
`MOD (Jeremy, upstream merge)` in the source.

**Fix 1 — start-occupied traps (the bug that broke 13 real solutions).** `MSLevel`'s constructor
presses every trap that starts with Chip, a block or an ice block on it (in MSCC every trap is open
at level start). Upstream built the trap maps *inside* that press loop, so the press ran before
they existed. With two or more wires into the trap it threw a `NullPointerException` and the level
would not open; with one wire it silently did nothing and the trap stayed shut. Measured on
2026-11-01 across all 24,204 stored solutions: 5 levels would not open (EEfor5subs #16, IsaacD1-Old
#116, JRB_CCLP1 #18, TCCLP #110, TomP3 #66; four of the five were solved), and 8 replays ended differently, 6 of which stopped
reaching the exit (C1059-1 #47, EEfor5subs #26, geodave1 #52, geodave4 #7, IsaacD1-Old #121, TLFC3
#89) — 10 solved routes lost. The test suite (1,878 assertions) passed throughout: no fixture
started anything on a wired trap. Fix: build both maps first, then press.

**Fix 2 — an empty trap with one button held is open, whatever the hash order.** Before the rework,
the end-of-tick update set each wire's bit separately, and "open if any bit" made the result
order-free. The rework kept that loop wire by wire but writes one shared bit, so the *last* wire
visited decided — and wires are visited in `HashMap` order. Measured with two buttons on one empty
trap, one held by a block: open under jc-14 in all six arrangements tried, shut in three of them
under upstream's rework. No stored replay changed because of it — a creature *entering* a trap
re-checks its buttons, which hides the stale bit — but `isTrapOpen` is the engine's own question,
and an answer that depends on hash order is a desync waiting for the right level. Fix:
`finaliseTraps` collects which traps have a held button, then decides each trap once. That is
exactly the old any-wire rule, and Tile World's `istrapopen()`. (Unchanged, and pinned by §6–§8: a
button wired to several traps opens only the first, and releasing *either* button of a shared trap
shuts it — the old code cleared every wire's bit on release, too.)

**Fix 3 — Lynx `isTrapOpen` on an unwired position.** The rework's Lynx version iterated the map's
list for the position without checking for null, so asking about a cell no button is wired to threw.
Nothing in the engine asks that today (MS's version already answered `false`); guarded anyway.

**Fix 4 — a trap's bit is numbered by its first wire's record, not by hash order.** The trap bits
are written into savestates, and *File > Save all states to disk* writes those to a `.ser` file, so
the number a trap is stored under is a file format. Upstream numbered traps by the iteration order
of a `HashMap` keyed by position, which also depends on the map's initial capacity: any later change
to how that map is built, or a JDK's `HashMap`, would silently renumber them. `LevelFactory` now
gives each trap the record index of the first wire into it, in file order, and passes that map to
`MSLevel`. That is also exactly the bit jc-14 and earlier stored for that wire, which turns
upstream's compatibility break into a narrow one (below).

**Two Lynx changes upstream made, kept and noted.** Neither touches play — the Lynx engine springs
traps through `LynxCreatureList.springTrappedCreature` directly and never calls `setTrap` or
`isTrapOpen` (checked: no caller outside `BrownButton` and the Cheats menu):
- `isTrapOpen` now asks whether a creature is on a wired *button*; it used to ask whether one was
  *in the trap*. Only `BrownButton.isOpen` sees it.
- `setTrap(pos, true)` — reached only from the Cheats menu's *Press button* — springs the trapped
  creature once. It used to spring it once per wire into the trap, so a two-wire trap's creature got
  two moves from one cheat press, which no real button press does.

**Measured.** With all four: the corpus (24,204 solutions, 316 packs) is **identical** to the
deployed jc-14 jar, and `-CrossCheck` agrees on all 24,203 replayable saves. A probe of the trap
arrangements above gives jc-14's answer in every case. Against the new tests: upstream's own build
fails 12 of `MsCloneTrapTest`'s new assertions, upstream plus Fix 1 alone fails 3 (Fix 2's), the
build with Fixes 1–3 but upstream's numbering fails 3 savestate checks (Fix 4's), and
`ConnectionTest`'s unwired-position check fails without Fix 3. Four defects planted in the new code
(no end-of-tick clear; clearing an occupied trap; no first-trap-only rule; numbering by a trap's
LAST wire) are each caught.

**Tests added.** `MsCloneTrapTest` §6 (a block or Chip starting on a trap with one wire and with two:
open from the first tick and still open four ticks later, its occupant leaves, and Chip stepping into
the vacated trap is caught by it), §7 (the six held/free arrangements; the neither-held control; a
bare press, as the Cheats menu makes, lasting only to the end of the tick; one held button wired to
two traps opening only the first), §8 (two buttons, one trap: a release shuts it), §9 (upstream's own regression level for
this commit, "Multi Trap" from `unitTest.dat` #25, rebuilt with `DatBuilder` since a `.dat` may not
be committed, replayed with its solution `----r-r-r`), §10 (the bit each trap is saved under, read
from the savestate bytes, and jc-14-style trap bits loaded back). `ConnectionTest` §4, which pinned
the removed record index, now pins `getTrapButtons` under both rulesets and the unwired-position
answer. `LinkTest` (new) resolves every call between the fork's classes in the built jar: the five
classes this step recompiles changed signatures that baseline classes could still have been calling.
Each was confirmed against the build it guards against. Expected values cite Tile World's
`mslogic.c` (`initgame`, `istrapopen`, `trapfrombutton`, and the tworld fork's `FIX_TRAP_SHUT_BEHIND`
for the release rule) and were confirmed against jc-14.

**One narrow compatibility cost.** A `.ser` savestate file holds the trap bits. Before this change
there was one bit per wire; now there is one per trap, stored at its first wire's bit. So:
- a `.ser` saved by jc-14 or earlier loads correctly on any level where each trap has one wire;
- on a trap with two or more wires, its state is read from its first wire's bit alone, so a state
  saved while only a later wire's bit was set restores that trap shut (392 of the 23,476 levels in
  the maintainer's collection have such a trap);
- a `.ser` saved by this build loads correctly in jc-14 and earlier, everywhere.
Lynx states carry no trap bits; solutions (`.json`, `.tws`) carry no state at all. The maintainer's
collection holds no `.ser` files. README section 7 carries this from jc-15 on.

### Step 2 — input discarding (`b997c27` + `98366f7`)

**What upstream changed.** `Level.tick` returned a boolean, "make the next move automatically". It
now returns two bits: `MASK_TICK_MULTI`, the same thing, and `MASK_DISCARD_INPUT`, "the key given
to this tick was not used". `LynxLevel.tick` sets the second when Chip is between tiles (it does
not call `selectChipMove` at all then) and when `selectChipMove` finds a forced move — ice without
skates, a force floor without boots and without the slide token, the tick after a teleport — and
`SuperCC.tick` then records `WAIT` in the move list in place of the key. `b997c27` also made
`MSLevel.tick` return the discard bit on every tick without automatic ticks; `98366f7`, four hours
later, returned 0 there instead. Net effect on MS: none. The pair also adds a `break` to
`SuperCC.tick`'s diagonal-to-vertical loop, harmless because each direction has exactly one key.

**Why it is right.** It is Tile World's rule. In `lxlogic.c`, `advancegame` calls `choosemove` only
for a creature with `moving <= 0`; `choosemove` calls `choosechipmove(cr, getforcedmove(cr))`, which
drops the key when `discard` is set ("the player's input should not be retained"); and `play.c`'s
`doturn` records a move only `if (state.lastmove)`, which is set only when a key is accepted. Tile
World never records a key it threw away; SuperCC's move list now agrees. The MS half is right for
the opposite reason: `mslogic.c` discards only on `CS_SLIP` (ice), and an MS key during a
force-floor slide is an *override* that moves Chip off the floor. Recording it as a wait, as
`b997c27` alone did, loses the move. Not even an MS key on ice may become a wait, though `moveChip`
refuses the move it asks for there: the key still resets `MSLevel`'s idle-move count and
voluntary-move state, and would cancel a pending click — so the savestate after keys differs from
the one after waits, mid-slide, on the same square (`InputDiscardTest` §4). An "ice-only MS
discard" would desync replays; this is the reason not to add one.

**How it was applied.** The net diff of both commits, verbatim (upstream's files are CRLF, so the
patch was converted to LF first). All four files were already in `$SPLICE_MODIFIED`. Fork edits:
`MSLevel.tick`'s `@return` still said "true if"; `MsCloneTrapTest`'s replay helper used the boolean;
and one fix from review, marked `MOD (Jeremy, upstream merge)` — ⚠ **REVERTED for the upstream
baseline (2026-11-08, `923d07c`)**: `SuperCC.tick` is upstream's again, and `InputDiscardTest` §5 pins
upstream's recording; the paragraph describes the fix as it was in jc-15 to jc-19. Upstream's `SuperCC.tick` writes a
plain `WAIT` whenever the discard bit is set — even on a tick that also reports automatic ticks,
after its multi-tick loop has made the key a capital — so such a tick would be recorded as one tick
where the engine ran four. No path returns both bits today (every discard path leaves Chip's chosen
direction empty, so `moveChip` reports no automatic move; none of 1,085 Lynx solutions and none of
the reviews' fuzzing hit it), but `Level.tick`'s contract makes the bits independent. The fix lets
the automatic-tick bit win and keeps the key. A first version recorded the full wait `_` instead;
the hostile review showed why that is still wrong: the automatic-tick bit can only come from a
direction Chip *chose* from the key, so both bits together mean the discard bit is the mistaken
one — and keeping the key is safe even if it is not, since a truly ignored key is ignored again on
replay. Checked with two planted engine defects (every discard also claiming automatic ticks; a
half-blocked diagonal reported discarded): upstream's line records routes that replay 3 ticks short
and one square off; the fix replays both in step. `LinkTest` confirms no class in the built jar
still calls the boolean `tick`.

**The trade-off it brings.** The move list now records what *happened*, not what was *typed*. A
replay from the same start is unaffected, but a macro (`SavestateManager.macroRecorder` slices the
move list) or a copy of the moves is replayed wherever Chip stands: from a square where an ignored
key would have counted, it now waits where jc-14 moved. Upstream chose this; it is in README
section 7 for users and pinned by `InputDiscardTest` §3.

**What was measured.**
- Corpus: identical to the deployed jc-14 jar on all 24,204 solutions; `-CrossCheck` agrees on all
  24,203 replayable saves.
- The final state is not where this change shows, so a one-off census (a scratch copy of
  `CorpusCheck`, not committed) compared the move list SuperCC *records* on loading each save with
  what jc-14 records. **40 of 1,085 Lynx routes record differently** — 2,500 keys become waits:
  1,678 typed between tiles (in 30 routes) and 822 on forced moves; 20 of the 40 are CCLP1–5 routes
  and 14 are JeremyLP's. **No MS route records differently.** No tick
  set both bits, so a full move is never written as a one-tick wait. The real recording path matched
  the prediction from the raw flags on every tick. All 40, saved as the menu saves them (through
  JSON) and reloaded, end in the same state.
- Tile World. Its replay keeps a key it has not consumed until Chip next chooses a move (`doturn`
  sets `currentinput` only on recorded ticks; only `choosechipmove` clears it). So a key typed
  between tiles and followed only by waits, exported by jc-14, looked as if Tile World would apply
  it late. Three routes have that shape (JeremyLP #11 and #17, TomB1 #130). Each was exported by
  both jars and replayed with `Tile World.exe -b -r` from a scratch folder, after the ZK2
  known-answer control (91 valid / 9 invalid, as always): **all six exports are valid.** So the
  theory did not hold on real data, and the change is claimed only as a cleaner move list, not as a
  fix to old exports. The mechanism is real in principle — the parity review below modeled Tile
  World's buffering on SuperCC's engine, and a synthetic key typed between tiles before a free
  choice moved Chip late — but why it was harmless in those three was not established. The `.tws`
  export shrinks (#17's went from 7,397 bytes to 2,885: a wait is not written as a move there); the
  move list and the saved `.json` keep the same length, a `-` in the key's place.
- Review (a Tile World parity pass, line by line against `lxlogic.c`): no case where SuperCC
  reports a key discarded that Tile World would use. It traced between-tiles, tick 1, ice, force
  floors with and without boots, the slide token's whole lifecycle (including boots picked up
  mid-slide), teleports, traps, clone machines and diagonals, each agreeing. And independently of
  Tile World: replaying every discarded tick with all nine possible keys over 3,000 random Lynx
  levels (104,756 ticks, 47,201 discarded) gave **zero** differences in the full savestate — so a
  recorded wait cannot change SuperCC's own replay — and no tick returned both bits. The only
  disagreements run the harmless way (SuperCC records a key Tile World would drop): after Chip dies,
  on the tick a Lynx time limit runs out, and when Chip is stuck on a teleport, which SuperCC
  handles by killing him.
- Found by the same review, **not fixed, pre-existing**: Tile World starts Lynx Chip facing SOUTH
  whatever his tile (`lxlogic.c:1840`, `cr->dir = SOUTH`); SuperCC takes his facing from his tile
  (`LynxCreature`'s constructor). On a level whose Chip tile faces another way, a first diagonal key
  or a start on ice can go differently. Its own investigation, not a merge step.
- Review (hostile, on every consumer of the move list): nothing breaks. No class in the jar
  references the boolean `Level.tick`; `SuperCC.tick`'s own signature is unchanged, so `Solution`,
  `SavestateManager`, the key listener, `MenuBar`, `GamePanel`, the TSP and variation tools,
  `CorpusCheck` and `TraceLevel` are unaffected; save/reload, keep-moves reloads and rewind-then-branch
  ended in the same states (end states compared, not every tick). Its fuzz (20,000 random Lynx levels, 366,456 discarded ticks, each re-run
  with five keys) found no key that mattered. It did find the both-bits recording hole above, three
  discard rules no fixture reached, and four wrong sentences in these docs — all fixed.

**Tests.** `test/InputDiscardTest.java`, 45 assertions. Lynx flags tick by tick: two force floors
(forced on the first; the slide token frees Chip on the second), ice with and without skates, a
teleport (the tick after it is forced), the exit (the arrival tick's key is unused), a start ON a
force floor (tick 1 is never forced), and two keys that bump — into a wall, and a diagonal blocked on
one half — which are USED, never discarded. Then the safety property itself, on five of those routes:
on every discarded tick, all nine keys from the same savestate give the same savestate and flags.
Then what the move list records, the recorded list saved, reloaded and replayed to the same square
and tick, the macro trade-off pinned, and MS never setting the bit — with the force-floor override
alone recorded as a wait ending on 6,0 instead of 5,0, and an MS key on ice changing the savestate.
Last, the both-bits rule: a real Lynx level wrapped so that every tick it reports discarded also
claims automatic ticks, through which SuperCC must record every key and no wait.
The expected values were derived from `lxlogic.c` before each first run, and every Lynx one held;
the one that did not was an MS fixture of mine (the ice ran out, and the next key moved Chip on
floor), fixed by comparing mid-slide.

Planted defects, each caught: a key between tiles not discarded (11 failures), a key on a forced
move not discarded (10), MS returning the bit as `b997c27` did (5), `SuperCC.tick` recording the
discarded key (2), the tick after a teleport not forced (3), the exit tick not discarded (2), the
tick-1 rule off by one (4 — it lives in `LynxCreature`, which is not spliced, so the mutation run
had to add it to `$SPLICE_MODIFIED`, CLAUDE.md §4), a blocked key reported discarded (1), and a
half-blocked diagonal reported discarded (1), and upstream's recorder line restored (1 — `r--UUUUUu`
through the wrapped level; before §5 existed this one survived, since no real engine path returns
both bits). ⚠ Since `923d07c` (the upstream baseline) upstream's recorder line IS the shipped code
and §5 pins it, so replanting that one changes nothing; the other nine still apply.

### Step 3 — seed search overflow (`40495dd`), and the tool that made it possible

> ⚠ **The fork's four Seed Search fixes are REVERTED for the upstream baseline (2026-11-08,
> `397c7bb`, jc-20)** — `SeedSearch.java` is upstream v2.0.3's again; the problems that brings back
> are UB-7 to UB-10 in `BASELINE-ISSUES.md`. The record below describes the fixes as they were in
> jc-16 to jc-19. (`formclass.ps1`, built in this step, stays: it is tooling, not a deviation.)

**What upstream changed.** Solution > Search for seeds splits the seeds from the starting one up to
`Integer.MAX_VALUE` among its threads; the last thread's range ends at `Integer.MAX_VALUE` itself.
Held in an `int`, the seed after it is `Integer.MIN_VALUE`, `currentSeed <= endSeed` is still true,
and the loop runs on through every negative seed — which `RNG.setCurrentValue` masks to 31 bits, so
it retries seeds already tried and counts them again. The attempt and success totals were
`AtomicInteger`s too, so a full search (2^31 seeds) overflowed them as well. `40495dd` makes the
seeds, ranges and totals `long`.

**Measured before fixing, on jc-15:** a search of the last 3,000 seeds, four threads, had made
12,285,648 attempts after 8.4 seconds and was still running. On jc-16: exactly 3,000 attempts and
3,000 successes, then it stops; started at `Integer.MAX_VALUE` itself, exactly one.

**One fork fix (`MOD (Jeremy, upstream merge)`), reproduced first.** Upstream left
`long numSeeds = Integer.MAX_VALUE - start` in `int` arithmetic, so a negative start wraps it. With
start −5 and four threads the pool came out negative: threads 0–2 got empty ranges and never
advanced, thread 3 searched from −1,610,612,735 up — about 3.8 billion seeds alone — and
`SeedSearch.isRunning()` read false 1.3 seconds in while thread 3 kept going (the empty threads
count `numAlive` down to zero and clear `running`). A `(long)` cast fixes the split; all four
threads now advance through their own quarter.

**A second fork fix: threads are built, then started (found by `/code-review`, reproduced first).**
Upstream started each search thread the moment it was built, and building the next one (an
emulator, the level set, the level) takes longer than a short range takes to search. So an earlier
thread finished first, counted `numAlive` down to zero and ran the "last one cleans up" block
mid-search. Sampling `isRunning()` during a 3,000-seed, four-thread search: it went false four
times in three of five runs (once per thread). In that moment a Pause click started a second set
of threads over the same ranges, and an early thread's `updateText()` could overwrite the final
totals. Each `run()` also reset `killFlag`, undoing a Pause that landed before the last thread
began. Now all threads are built first, `running`/`killFlag` are set once before any starts (and
are `volatile`), and `run()` no longer touches them: eight of eight runs went false exactly once,
at the end.

The second `/code-review` found two holes in that fix, both true by reading:
- **The count.** Workers were counted in their constructor, so if building a later one threw, the
  counted ones would never run and `numAlive` would never reach zero again. Now all of them are
  added at once, at the start.
- **The cleanup order.** It published `running = false` before clearing `killFlag`, so a Resume
  started between the two had its Pause cleared by the old batch. The two lines are swapped.

**A third fork fix: the example seed.** "No success yet" was `-1`, tested as `>= 0`, so a success at
a negative seed — reachable now that a negative start is split properly — was counted but never
shown. Three reviewers raised it; it is `Long.MIN_VALUE` now, which no seed can be.

Left alone, all upstream's own design and none made worse here:
- The button still reads Pause after a search finishes, and pressing it starts a batch with
  nothing left to search. What it should say then is a UI decision, not part of an overflow fix.
- `run()` has no `try/finally`, so an exception inside a search would leave the static flags set.
- A non-numeric start throws `NumberFormatException`.
- Two open Seed Search windows share that static state.

**The real work: `SeedSearch` is form-based.** Its `.class` is committed IntelliJ bytecode
(ADR 0001/0002), and javac alone cannot rebuild it — it would ship with no components and die on
its constructor's first line. Four of the remaining upstream commits edit a form class too, and
only one of them edits a `.form`. So this step added `formclass.ps1` and
`formclass/FormTransplant.java` (ADR 0010): javac compiles the edited `.java`, the members IntelliJ
generated from the unchanged `.form` are copied from the committed class, and the constructor call
goes where IntelliJ's own `FormConstructorVisitor` puts it — a line-for-line port, read at
`idea/211.7628.21` (the 2021 line that built these classes) and unchanged on master.

**It was proven against IntelliJ's real output before it was trusted.** Rebuilt from the UNCHANGED
source of all fifteen form classes, against IntelliJ's originals (jc-15's `SeedSearch`):
- all 37 classes (15 plus 22 inner) match instruction for instruction, the `$$$setupUI$$$` call
  included, in the odd spot IntelliJ puts it;
- four come out byte-identical: ChangeTimer, ChooseWindowSize, GameGifRecorder, SeedSearch;
- HelpWindow and TSPHelpWindow differ only in constant-pool numbering;
- the other nine also differ in stack-map frames. IntelliJ recomputed the frames for the whole
  class and chose different, equally valid encodings (`same_frame_extended` where javac
  wrote `chop`, for one). This tool keeps javac's frames.

**What the reviews found in the tool, every one reproduced before it was fixed.** The first
version's insertion rule was my reading of IntelliJ's, not IntelliJ's:
- **One branch only.** `/code-review` showed it putting the call inside one arm of
  `killFlag ? new Object() : null`.
- **Before `lock` was assigned.** The hostile review read IntelliJ's source and found that a jump
  is also a trigger, and that `invokespecial <init>` on the superclass is not. The old rule called
  `$$$setupUI$$$` before `lock = new Object()` was assigned.

The same review found three classes the tool rebuilt that every gate passed, and that broke the
moment the window opened:
- a `final` bound field (IllegalAccessError — IntelliJ refuses it: "Cannot bind: field is final");
- an exception before the call, caught past it (no components);
- a second top-level class in the `.java`, silently dropped (NoClassDefFoundError).

The second `/code-review` found two more, both reproduced the same way:
- **A static method.** A `createUIComponents()` made static passed, because method calls were
  checked by name and type only; the window would throw IncompatibleClassChangeError.
- **A `new` inside `super(…)`.** `super(new JPanel().getLayout())` in a `JPanel` subclass put the
  call before the real `super(…)`: IntelliJ's flag reads the inner `JPanel.<init>` as `super(…)`.
  That class failed verification on load.

The hostile review's second pass found two shapes where IntelliJ's own flag goes wrong, and the
port with it, both reproduced:
- **`new ChangeTimer(0)` before the call.** The flag reads it as `this(…)` and then ignores bound
  reads, so the call landed after `getfield spinner` (NullPointerException on open).
- **`this(new Object(), 1)`.** The inner `Object.<init>` set the flag first, and the delegating
  constructor got a second call, so every component was built twice.

Now:
- the placement is the ported rule;
- a constructor whose real first call is `this(…)` is always left alone, deciding by the `<init>`
  that initializes `this`, not by IntelliJ's flag;
- a static or final bound field and an explicit `$$$setupUI$$$` call are refused;
- a copied call whose target changed between static and instance is refused;
- a call that would land before the `<init>` that really initializes `this` is refused;
- a `new <ThisClass>(…)`, or a read of a bound field, before the call is refused, as is a
  constructor where the rule finds no point at all;
- a loop, switch, throw or try block before the call is refused, because there not even
  IntelliJ's placement is proven to run exactly once;
- a second class from the `.java` is refused.

None of the fifteen forms trips any refusal.

The reviews also caught four PowerShell slips, all now fixed:
- a comma list;
- a missing `-Jar`;
- a wrong-case path;
- a work path over 260 characters, where `Copy-Item` failed quietly and `-Rebuild` deleted a
  committed inner class but still printed DONE. My first fix covered only the final copy. The
  second hostile pass reproduced the same loss through the staging copy before it, and I
  reproduced it too. Now:
  - both copies use .NET calls that throw;
  - the staged set must name exactly what javac wrote;
  - the committed set is checked byte for byte;
  - temp paths are short;
  - the same deep TEMP fails with exit 1 and a message naming the cause, and leaves the committed
    classes untouched.

Two limits, deliberate and documented:
- **The try refusal is conservative.** It refuses a try block before the call even when nothing
  in it could throw first: `try { emulator.getLevel(); } catch …`, or a `synchronized` block.
  A shape like that needs IntelliJ, or the try moved after the first call.
- **Nothing headless reaches `graphics.Gui`'s setup.** For Gui, a placement mistake is stopped only
  by the refusals and a real launch. Upstream's 72476bc and d78f6f3 both edit `Gui.java`, so the
  merge step that takes them should add a non-headless construction of Gui (a separate JVM,
  skipped where no desktop exists) before it trusts a rebuilt Gui. (Done in step 4:
  `GuiWindowTest`.)

**The checks:**
- `formclass.ps1 -Check` (CI and the release workflow) rebuilds all fifteen and compares them with
  the committed classes, so a form `.java` edited without a rebuild fails the build. Before the
  rebuild, it flagged exactly `SeedSearch` and passed the other fourteen.
- `-Check` has a blind spot: once a class has been rebuilt, both sides of the comparison came from
  the tool, so a tool defect passes it. Hence `FormClassTest`.
- `-SelfTest` (CI) makes 18 checks on 17 specimens, built from `SeedSearch`, `ChangeTimer` and
  `ChangeInventory`:
  - **refused:** a bound field renamed; a bound field retyped; a final bound field; a called method
    made static; an instrumented class passed in as javac's; a non-form class passed in as
    IntelliJ's; a loop, a switch, or a try block before the first call; a second top-level class;
    a `new JPanel()` inside `super(…)`'s arguments; a `new ChangeTimer(0)` before the call;
  - **placed correctly:** a `this(…)` constructor left alone, including `this(new Object(), 1)`;
    `new Object()` not taken as a trigger; an early `if` getting the call before the branch;
  - **plus** the unchanged class rebuilding with exactly one call.
- It does not exercise every refusal: field static-ness, a missing method, a generated field, a name
  or superclass mismatch, and "no insertion point" are reasoned, not specimen-tested.

**Tests.**
- **`FormClassTest`** (67) runs the real constructor of every form window that can run headless:
  14 classes, all 17 of their constructors. A form class that gains a constructor fails it until
  that constructor is added, because the review's delegating-constructor case was in a
  constructor nothing ran. Reaching the window's `HeadlessException` — or returning, for the three
  no-window constructors — means `$$$setupUI$$$` ran, and a spy `PanelUI` also counts the panels
  built. `graphics.Gui` cannot be reached: it is the `JFrame`, and launching the program is its
  check. Built with the old tools, the `final`-field and one-branch `ChangeTimer` fail it
  (IllegalAccessError, NullPointerException), as does a javac-only `ChangeTimer`.
- **`SeedSearchTest`** (25) drives Seed Search through its real constructor headless.
  - **How:** the constructor dies at its `JFrame` after building everything, so a spy `ButtonUI`
    catches the one `JButton` the form makes. Its listener is the constructor's own lambda,
    holding the instance.
  - **What it checks:** the search stops at the top seed exactly; `running` goes false only once,
    including with two seeds a thread (where upstream's thread order flips it once per thread); a
    negative start splits evenly; the totals pass 2^31; a success at a negative seed is shown.

Planted defects, each caught:

| Planted defect | `SeedSearchTest` | `FormClassTest` |
|---|---|---|
| the jc-15 jar | 12 failures, then `ClassCastException` (`int[]` seeds) | — |
| the cast removed | 5 | — |
| upstream's thread start restored | 1 (3 of 3 runs) | — |
| upstream's `-1` "no success" sentinel restored | 1 | — |
| `SeedSearch` compiled by javac alone | 7 of 7 | 2 |
| the transplant without the constructor call | 7 of 7 | 1 (the member-presence check passes) |

**The engine is untouched:** of the jar's 575 entries, 7 differ from the published jc-15 jar —
`SuperCC.class` and `ErrorLog$Sink.class` (the build tag, inlined; `javap -v` shows nothing else),
the two edited sources, and the three `SeedSearch` classes. No `game/**` class differs, so the corpus,
which replays `game/**`, was not re-run.

### Step 4 — no-level-loaded guards (`72476bc`), hand-merged

> ⚠ **The fork's step-4 guards and fixes are REVERTED for the upstream baseline (2026-11-08,
> `c57f217`, jc-21)**, and the two upstream lines left out below are in. Upstream's own guards and
> the Black and White rename stay; so does the fork's untitled-level fix (`LevelFactory`), which is
> the fork's own and not part of this step. The problems that brings back are UB-11 to UB-15 in
> `BASELINE-ISSUES.md`. The record below describes jc-17 to jc-20.

**What upstream changed.** "Made the tests multi-platform … Fixed the commit warnings." Most of it
does not apply here: a Linux Maven workflow, renamed test data, its own JUnit tests. What does apply
is a set of guards for the state every launch starts in, no level set open, across three files:
- **`EmulatorKeyListener.keyPressed`** returns at once.
- **`Gui`**: `repaint(boolean)` and the play button, speed slider and time slider listeners return
  early.
- **`MenuBar`**: the Level menu's Restart, Next, Previous, Set RNG Seed, Change ruleset and Change
  Initial RFF Direction check `isLevelLoaded()`.

The rest is warning cleanup: `final` fields, `Objects.requireNonNull` around resource lookups, and
`List.of`.

**Measured before merging, on jc-16.** The new `GuiWindowTest` clicked each of those with no level
open, and every one threw `NullPointerException` on the event thread. That is not hypothetical: the
maintainer's desktop error log (`succ_error-JEREMYSPC.log`, jc-11 to jc-14) holds Restart twice and
Next/Previous once.

**How it was applied:**
- **`EmulatorKeyListener` and `Gui`** are verbatim: both were byte-identical to upstream's base.
  `EmulatorKeyListener` joined `$SPLICE_MODIFIED`. `Gui` is form-based, so its committed class was
  rebuilt with `formclass.ps1 -Rebuild graphics\Gui.java` — the tool's first rebuild of the main
  window. `-Check` and the new test both pass on it.
- **`MenuBar`** is spliced and much changed by the fork, so it was hand-merged. Every guard and
  cleanup was applied, with two exceptions:
  - **`Objects.requireNonNull(openFile(…))` in `openFileBytes`.** It turns a canceled file chooser
    back into an uncaught `NullPointerException`, undoing jc-12.
  - **`assert tws != null`.** The fork's code already null-checks both `tws` and its parent (jc-8 and
    jc-10).

**The fork fixes, each reproduced on the build that had only upstream's guards:**
- **Every menu, with one guard.** The first `/code-review` pass pointed out that upstream's six
  guards cover only the Level menu. A probe that clicked every button in every menu with no level
  open, after the merge, found 26 of the 56 controls still threw:
  - most of Solution, including Ctrl+S, which a user can hit from the keyboard;
  - TWS's write and verify items;
  - all five View › Tileset choices, and Tile size › custom;
  - Tools › Variation testing and TSP Solver;
  - all six cheats;
  - hovering over Copy macro moves.

  (On jc-16 the count was 46, since its unguarded `Gui.repaint` failed the View toggles and sizes
  too.) Guarding each by hand is the special-casing CLAUDE.md §7 warns against, and the next item
  added would be missed. So `MenuBar.requireALevel()`, run at the end of the constructor, inverts
  the rule:
  - every button in every menu gets its action listeners wrapped to do nothing until a level is
    loaded;
  - exempt are the controls marked `worksWithoutALevel(…)` where they are made: Open levelset, Open
    tws, the five tile sizes, the Game window size submenu, the six View toggles, and Tools ›
    Controls (Open tws and custom tile size were added after the hostile review, below);
  - accelerators go through the same listeners;
  - only the fork's own action listeners are wrapped, never Swing's;
  - the one menu listener (Copy macro moves) checks for itself.

  The guard upstream left off Change stepping value is covered by the same wrapper rather than by a
  line of its own.
- **Canceling Go to… or Set RNG Seed** threw, because the reply is `null` and the code tested only
  for empty. This is the input-dialog twin of jc-12's file-chooser fix.

Both are marked `MOD (Jeremy, upstream merge)`. Upstream's six per-item guards stay as written.

**The test that closes the Gui gap.** `graphics.Gui` is the `JFrame`, so nothing headless reaches its
setup: ADR 0010 and step 3 named it the gap to close before Gui was rebuilt. `GuiWindowTest` closes
it. It launches a child JVM without `java.awt.headless` that runs `new SuperCC()` in a temp folder,
as a double-click does, and reports one line per check:
- all 12 components `Gui.form` binds exist, and the content pane is the form's root;
- with no level open, nothing throws:
  - every button in every menu, found by walking the menu bar so a later item is covered too;
  - every submenu hovered;
  - a key press, the play button, the speed slider, and a full repaint;
- each click runs with a helper that closes any dialog or file chooser it opens, as the close box
  would, and is stopped afterward; tool windows a click opens are disposed;
- the level set then opens with no error dialog, and the full repaint works;
- canceling both input dialogs works;
- the session writes no error log.

Where there is no desktop, the child says so and the test skips. It fails 28 times on the build with
only upstream's guards; it passes on jc-17.

**What the hostile review found, each reproduced, then fixed.** It found nothing wrong in the
rebuilt `Gui.class`: it matches IntelliJ's original apart from pool numbering, with the call at the
same offset inside the same `catch` range. It did find:
- **A level "loaded" without its savestates.** `loadLevel` assigned `level` before building its
  `SavestateManager`, whose constructor throws for a level with no title (`getTitle().getBytes()`;
  MO3 #12 and #14 are real ones). Measured: load level 2, then an untitled level 1, and level 1
  ran against level 2's savestates. On a fresh open, `isLevelLoaded()` was true with
  `getSavestates()` null, which slipped past every guard above. Now the load builds both and assigns
  both, or neither (a `MOD` in `SuperCC.loadLevel`; `EngineTest` §6c). Title-less levels
  themselves were left for their own step at first; they load now (*The second review*, below).

  The re-check found two callers that relied on the old order, and both were reproduced:
  - **`openLevelset`.** It assigned the new set's file before loading level 1, so a set whose
    level 1 failed kept the previous set's level under the new file. `getJSONPath()` then pointed
    into the new set's save folder, so Ctrl+S would have written one set's route into the other's.
    On a fresh open, it threw on a null level.

    My first fix restored the previous set, and the reviewer's final pass showed what that cost:
    all of the new set became unreachable. Its other levels were closed to the GUI, every save in
    such a pack became an OPENFAIL in the corpus harness, and TraceLevel was refused. The set now
    opens at its first level that loads. Only a set where nothing loads leaves the previous set
    open, which is jc-11's rule for a file that does not read. `CorpusCheck`'s comment that "a set
    whose level 1 fails to load is still open" is true again.

    Reporting each failure as `loadLevel` does would show one dialog per bad level before the
    first good one, and nothing bounds that count. So `loadLevel`'s body is now
    `tryLoadLevel`, which returns the failure instead of reporting it. `loadLevel` reports it
    exactly as before, and `openLevelset` reports once: "Levels 1 to 3 could not be loaded: <the
    first reason> … Opened at level 4.", or "No level in <set> could be loaded" (with "-- it has no
    levels." for an empty set). `EngineTest` §6c and `GuiWindowTest` §3a cover one bad level,
    three bad levels, an all-bad set and an empty set; each must show exactly one dialog.

    Two consequences were reproduced and left as they are:
    - **`ArgumentParser`** (the command line; upstream code, unspliced) calls `openLevelset`, then
      `loadLevel` on the level it was given, so a set whose level 1 fails and is named on the
      command line without a level number reports level 1 twice: once from the open, once from its
      own load. Nothing is left half-loaded. A scan that opened all 331 sets of the collection
      (23,476 levels) on the jc-17 jar found no set whose level 1 fails and no set where nothing
      loads; only MO3 #12 and #14 have no title.
    - **`CorpusCheck`** builds a fresh emulator per pack, so a pack where NO level loads now has no
      set open and reads `OPENFAIL` (an NPE from `lastLevelNumber()`) instead of one `LOADFAIL` per
      save. That is a different detail on lines that fail either way, and the jc-17 corpus replay
      below shows no such pack in the collection.

    The reviewer's pass over the report-once change found two more, both low, both reproduced
    here before they were fixed:
    - **A level that loaded could be reported as failed.** `tryLoadLevel`'s repaint and title run
      after it assigns the level, inside the same `try`. With the repaint made to throw (a null
      `playButton`; the real trigger is CLAUDE.md §9's jar replaced under a running instance), a
      good set showed "Level 1 could not be loaded … Opened at level 1." The loop now counts a
      failure only when the level did not change, and logs the other kind. `GuiWindowTest` §5
      pins it, and fails on the build before the fix.
    - **`GuiWindowTest`'s "once" checks could not see a second dialog.** Each open's closer
      dismissed one dialog and stopped, so a build that went back to a dialog per bad level hung the
      child on the second until the parent's 90-second kill, and the count checks never ran
      (83 passed, 3 failed, 90 s). `closeEveryDialog()` now stays on for the whole open and counts
      each dialog once; that mutant fails both count checks in 14 seconds.
  - **`trace/TraceLevel`.** It never checked that `loadLevel` worked, so an unloadable level 2 was
    traced as level 1's start, labeled level 2, with exit 0. It now uses `CorpusCheck`'s identity
    test and exits 2.

  `CorpusCheck`'s own comment on the same test is updated. Its results cannot change: it reports
  the same `LOADFAIL` either way.
- **TWS › Open tws needs no level.** It only chooses the file, so it is marked `worksWithoutALevel`.
- **View › Tile size › custom needed a level for one dead line.** `ChooseTileSize`'s constructor
  read `getLevel().getKeys()` into an unused array, which threw with no level. The line is removed
  (`MOD`; the class rebuilt with `formclass.ps1`), and the item is marked `worksWithoutALevel`.
- **Gaps in `GuiWindowTest`:**
  - a click that quit the program with exit code 0 skipped the remaining checks, and the run still
    looked clean; the child now prints `END` last, and the parent requires it;
  - the Cancel checks passed even when no dialog appeared; they now require one;
  - a broken window was reported only as `InvocationTargetException`; the real cause is now
    unwrapped (`this.playButton is null`).

  The review's own broken builds now fail the test.

The section also added a time-slider drag, clicks and moves over the game area, and a scroll of
the move list, all with no level open. All passed already.

**A test bug found on the way, and fixed.** `EngineTest` §7, the local-only check that every
level of every set in a collection parses, looped to `i <= parser.lastLevel()`. `lastLevel()` is
the level count plus one (`DatParser` skips index 0), so it read one past the end and failed on
all 331 sets when run on the collection for the scan above. It now stops at `lastLevel() - 1`, as
`openLevelset` does, counts the levels, and fails on an empty folder: 331 sets, 23,476 levels, all
pass. It skips in CI (no level set may be committed), which is how it stayed hidden.

**Left alone, found on the way.**
- ~~**View › Tileset with no level:** the radio mark moves to the clicked tileset though nothing is
  applied.~~ Fixed after the second review, without selection-restoring code: the tileset now works
  with no level open (below).
- **Tools › Controls** opens its window inside a `SwingWorker`, which keeps any exception to itself,
  so `GuiWindowTest` cannot see one there.
- **Paste solution** prints an `UnsupportedFlavorException` trace when the clipboard holds no text,
  whether or not a level is open. That is upstream behavior and not a no-level problem.
- **The desktop error log's most frequent entry** (18 times, `Image.getProperty` on a null image) is
  unrelated: it is the trace CLAUDE.md §9 explains, from a jar replaced under a running instance.

**The second review (2026-11-07, desktop, before tagging).** jc-17 was committed on 2026-11-04 but
never tagged, and the session that made it left no record of a final sign-off on the final code, so
a fresh `/code-review high` of the commit gated the release. It reported ten findings. Each one
below was reproduced first: every new check fails on the jar as committed, and passes after.
- **Levels with no title now load** (`MOD (Jeremy, jc-17)` in `LevelFactory.makeLevel`). Four
  findings — TWS › Verify, `testTWS`, the command line, and Next/Previous — are one pattern: each
  loads a level and then assumes it worked. jc-17's all-or-nothing load changed what a failure
  leaves behind for exactly one cause, `SavestateManager`'s constructor. A scan of every level of
  the collection through `loadLevel` (not just the parser, as `EngineTest` §7 does) found two
  failures in 23,476, MO3 #12 and #14, and both were that cause, a null title. Title is optional
  field 3. Tile World zero-fills each level record (`series.c`, the `memset` at line 429) and only
  field 3 writes the name (line 310), so there a title-less level is named `""`. A null title now
  becomes `""` in the one constructor path every level passes through, which fixes every consumer
  at once, including `getJSONPath()` and so Ctrl+S, which would have thrown on such a level too.
  After it, all 23,476 load. A titled level is untouched, and the corpus is identical.

  The four callers are **defended, not changed**. For three of them (TWS › Verify, `testTWS`,
  Next/Previous) a level that fails to PARSE already left the previous level in place on jc-16, so
  they behaved this way for that kind of failure before jc-17. The command line is different, and
  my first defense of it was wrong (the reviewer's re-check; confirmed in jc-16's source): on jc-16
  a fresh open whose level 1 failed showed one dialog and then threw out of `openLevelset` at
  `swapRulesetTilesheet(level.getRuleset())` on the null level, so `ArgumentParser`'s own load never
  ran. jc-17 survives the open, lands on level 2, and `ArgumentParser` then fails level 1 again: a
  second dialog where jc-16 had a crash. That is kept as the better of the two. No level in the
  collection fails at all now, and making each caller skip a level that will not load is new
  behavior that would want its own design. `EngineTest` §6c, `GuiWindowTest` §3a and
  `corpus/CorpusFixture.java`'s position 4, which had used an untitled level as their fixture for
  "cannot load", now use a record whose layer does not decode (`DatBuilder.unreadable()`: runs that
  claim 1,025 cells). The corpus one was missed at first and caught by CI's harness self-test, before
  any tag: run every step of `ci.yml` locally, not just the suite.
- **View › Tileset works with no level open.** It was the one menu `requireALevel()` skipped while
  its radio mark still moved: the menu showed a tileset neither drawn nor saved. The first review's
  verdict, "cosmetic, defended", is reversed by a fix simpler than restoring the selection. The
  tileset buttons are now `worksWithoutALevel`, and with no level the choice is saved as the MS
  tileset, the one drawn at startup and so the one on screen. `GuiWindowTest` §2 checks that the
  last tileset its walk clicks is drawn and saved, and that the Lynx choice is untouched.
- **That check found the Black and White tileset broken in every release** (upstream `f701c85`,
  brought in here). The jar held `resources/BW-overlay.png` while `TileSheet` asks for
  `bw-overlay.png`. A folder on Windows does not care about case, but a jar entry does, so
  `getResource` returned null and choosing "MSCC (Black and White - Editor)" threw
  `NullPointerException`. Measured on the deployed jc-16 jar, where the other four loaded. The
  rename is upstream's, byte-identical. `EngineTest` §6e loads every tileset from the built jar.
- **`openLevelset` restored the previous set only after its error dialog closed.** The dialog
  runs a nested event loop, and for as long as it was open the new set's file sat under the old
  level. `GuiWindowTest` now reads the open set from inside that loop, through a closer that runs
  code on the event thread before disposing each dialog. On the jar as committed it saw
  `nolevels.dat`. The set is now put back first.
- **"Opened at level N" printed the record's own number**, while "Levels 1 to M" counts positions,
  and the format lets a record's number differ from its position. It now prints the position
  (`EngineTest` §6d, with a set whose landing record is numbered 7 at position 3).
- **`CorpusCheck`'s comment** described pre-jc-17 behavior. It is corrected; no result changes.
- **Left as it is, from the re-check of these fixes:** Solution › Load states accepts a `.ser` when
  its stored title bytes equal the level's, and that is its only check. Every untitled level is now
  `""`, so states saved on MO3 #12 would load into #14 without the "cannot load" message. Before
  jc-17 neither level opened at all, two levels that share a title already had the same weakness,
  and the file chooser opens at the level's own `.ser` (named after its number), so reaching it
  takes picking another level's file by hand. A real fix belongs in the `.ser` format.
- **Defended:** six per-item `isLevelLoaded()` checks in the Level menu, and `REWIND`/`FORWARD`
  re-fetching `getSavestates()` after using a local. Both are upstream's own lines from `72476bc`,
  merged verbatim. The extra checks are redundant under `requireALevel()`, not wrong. The re-fetch
  happens on the event thread, where nothing can swap the manager between two statements. The fork
  keeps upstream's lines where they do no harm, so the next diff against upstream stays readable.

**Verification (JDK 16.0.2, the final tree).**
- **The engine's behavior is unchanged for every stored solution:** 24,212 of 24,212 end in the same
  state under the deployed jc-16 jar and jc-17, run on the desktop with the current harness on
  both sides. (The laptop's run of the first jc-17 commit was 24,204 of 24,204; the collection has
  grown since.) `game/**` is untouched; `LevelFactory` changes only what a level with no title is
  called, and no stored solution is for one.
- **Every level of all 331 sets loads** through `loadLevel`, 23,476 of 23,476. On the first jc-17
  commit, 2 did not.
- **The suite:** 2,249 assertions in 25 classes, all passing. All 17 new checks fail on the jar as
  first committed.
- **The other checks:**
  - `verify-splice` passes (15 spliced, 63 unspliced);
  - `formclass.ps1 -Check` and `-SelfTest` pass;
  - coverage was re-baselined (the target is unchanged at 56.5%).

### Compiling a .form without IntelliJ (tooling for steps 5 and 6, docs/adr/0011)

Upstream's auto-diagonals change edits `tools/ControlGUI.form`, which ADR 0010's transplant cannot
follow. The maintainer chose, from four options, to download **JetBrains' own form compiler** as a
hash-pinned test-time tool (2026-11-07). The measurement came first: driven exactly as IntelliJ's
IDE build drives it (`FormsInstrumenter.java` at `idea/211.7628.21`), javac 16.0.2 plus
`java-gui-forms-compiler` 211.7628.21 reproduce **all fifteen IntelliJ-built form classes and their
22 inner classes byte for byte**, against the tree at `331a9bd`. The transplant had matched 4 of 15.

- `formclass.ps1 -DownloadCompiler` fetches six jars by pinned SHA-256 into `%LOCALAPPDATA%`;
  `-Compile` builds a class with them; `-Rebuild` became an alias of it.
- `-Check` adds a BYTE comparison against javac + the compiler from the committed `.java` **and
  `.form`**: the first check here that can see a `.form` edit. CI and the release workflow download
  the compiler fresh (uncached, as JaCoCo is) and pass `-RequireCompiler`, so its absence fails.
- First run of the byte check: **exactly the three classes the transplant had built failed** —
  SeedSearch (jc-16), Gui and ChooseTileSize (jc-17) — while the other twelve passed. They were
  recompiled; against jc-17's jar their code and strings are identical (`Get-ClassKey`), and only
  stack-map frames and pool layout moved.
- **The architecture review's sharpest point:** the real compiler is faithful to IntelliJ's bugs
  too. ADR 0010's transplant refuses the constructor shapes where IntelliJ misplaces the setup call,
  so every compile still runs it as a lint and requires the two to agree on placement.
  `-SelfTest` proves it: JetBrains' compiler builds the `new ChangeTimer(0)` specimen without
  complaint, and the lint refuses it. Self-test 22 of 22.
- Found on the way: the vendored `com/intellij/uiDesigner/core` runtime, recorded as "not a
  published artifact", is byte-identical to `java-gui-forms-rt` 211.7628.21 in JetBrains' own
  repository (`docs/THIRD_PARTY.md`). Maven Central, the only place compared before, never had it.
- And: the desktop's checkout still had CRLF in the fifteen `.form` files and one `.py`, left from
  before `.gitattributes` normalized them, so a desktop-built jar's `java/**/*.form` entries differed
  from CI's. Re-checked out as LF.

### Step 5 — custom tilesets (`d78f6f3`)

> ⚠ **The fork's step-5 fixes are REVERTED for the upstream baseline (2026-11-09, `5d3f0a3`,
> jc-22)**: `TileSheet`, `SmallGamePanel` and `Gui` are upstream's byte for byte, and `MenuBar`,
> `SuperCC.initialiseTilesheet` and `SuccPaths` store and load the custom tileset as upstream does <!-- aen-exempt: upstream method name -->
> (`SuccPaths` inside the fork's own settings structure). The problems that brings back are UB-16
> to UB-21 in `BASELINE-ISSUES.md`. The record below describes jc-18 to jc-21.

**What upstream added.** View › Tileset › custom: two file choosers ask for a base and an overlay
image, their paths are saved as `[Graphics] CustomBaseImage` / `CustomOverlayImage`, and `TileSheet`
gains `CUSTOM` (ordinal 5) and a `getTileSheets(SuperCC, w, h)` that reads them. Plus labeled file
filters for every chooser ("Levelset file", "JSON route file", …), applied verbatim. Five of its
seven files applied cleanly; `MenuBar` and `SuccPaths` were hand-merged.

**What the fork changed, each `MOD (Jeremy, upstream merge)`, the design checked by an architecture
review before any code:**
- **Paths are stored relative to the Chip's Challenge folder** where possible, through jc-5's
  `toStoredPath`/`toUsablePath`. Upstream stored them absolute, and this file is shared by two
  machines whose user folders differ. The README says to keep the images inside the folder.
- **Cancel is a no-op** at either chooser, as every chooser has been since jc-12; upstream turned it
  into an error dialog. The radio mark goes back to the tileset still drawn (`markActiveTileset()`).
- **Both images are loaded before anything is saved**, and the paths and the ruleset's custom
  switch go in ONE write (`SuccPaths.setCustomTileset`). Upstream saved first, so a file that was
  not an image was saved too and failed again at every launch.
- **Custom is a switch per ruleset (`CustomTileset`, `LynxCustomTileset`), not tileset number 5.**
  Upstream stored it as the number. The first version here did too, with a README warning, and two
  reviews flagged it; the second measured it by building jc-17 and running it on a file jc-18 had
  written: `ArrayIndexOutOfBoundsException` at startup (a half-built window) and at every level set
  opened, and jc-17's next settings write erased the custom keys, after which jc-18 claimed "your
  choice is kept" about images that were gone. This file is shared by two machines, and going back
  a build is the usual answer to a bad one, so `TilesheetNum` now always holds a built-in sheet:
  the one chosen before custom, which an older build simply shows. `TileSheet.settingsNumber`
  turns a ruleset's switch and number into the tileset to draw; choosing a built-in tileset
  (`SuccPaths.setBuiltInTileset`) saves its number and turns the switch off in one write.
- **Every unusable image is an `IOException` with words:** none chosen (upstream's had no message,
  so the user read "null"), a missing file, and a file that is not an image (upstream's was a
  `NullPointerException` no caller catches).
- **A saved tileset that cannot be drawn falls back** to Tile World (CCEdit) with the reason, once a
  session (`SuperCC.throwErrorOnce`), through one loader, `TileSheet.load`, used at startup and at
  every ruleset swap. That covers custom images moved or deleted and — a pre-existing upstream
  crash — a tileset number out of range, which stopped startup. The setting is kept, so the
  tileset returns when the file does. `TileSheet` and `SmallGamePanel` joined `$SPLICE_MODIFIED`;
  `Gui` and `ChooseTileSize` were compiled with `formclass.ps1 -Compile`.

**From the release reviews, also fixed:**
- **Oversized images.** An image whose header claims 50000 x 50000 made `ImageIO` throw
  `IllegalArgumentException`, past every fallback; one claiming 46000 x 46000 stays under ImageIO's
  own overflow check and asks for about 8 GB. `loadTilesetImage` now reads the dimensions first
  and refuses more than 32 million pixels before decoding (`EngineTest` §6f builds both files, with
  real CRCs, as the review did). Rewriting it introduced a leak — closing an `ImageInputStream` does
  not close the stream it wraps, so every image read left a handle open, which on Windows LOCKS
  the user's own file — and §6f's move of a loaded image is what caught it, before any commit.
  And ANY runtime exception from the image reader is now that file's problem, said in words: the
  last review found a GIF that ends with no image in it throws `IndexOutOfBoundsException` from
  `getWidth(0)`, past every fallback (§6f builds that file too, named `.png`).
- **One loader for what a ruleset's settings say, `TileSheet.loadForRuleset`.** A custom tileset
  that fails now falls back to the ruleset's own built-in choice (what an older build shows from the
  same file), not always to Tile World (CCEdit); and a stored `TilesheetNum = 5` (left by the
  unreleased build that kept custom as a number, or written by hand) is refused like any number out
  of range instead of being taken as custom against the switch.
- **View › Game window size** swallowed a tileset failure and left the panel's buffers at the old
  size; it now goes through the same loaders and re-marks View › Tileset itself, so the custom size
  dialog does too.
- The custom-tileset message no longer says "no images chosen" when the settings are simply not
  loaded; and a `SettingsTest` check that matched "TilesheetNum = 0" inside "LynxTilesheetNum = 0"
  (it passed with MS's line reading 5) now compares whole lines and parsed values.
Each was shown to fail on its mutant: the number-5 write, a stored 5 read as custom, no size cap, the
leaked handle.

**Left as they are, documented:** MS and Lynx share one custom pair (README; upstream's design); a
build older than jc-18 erases the custom lines on its next settings write, so after going back to
one, custom is chosen again (README); re-reading the images on a tile-size change is how every
tileset already works.

**Verification (JDK 16.0.2).** `EngineTest` §6f (the loader: two real images, a missing one, a
non-image, none chosen, and the fallback for custom, 99 and -1); `SettingsTest` §13 (relative
storage inside the folder, absolute outside, the switch in the same write, `TilesheetNum` still a
built-in number with custom on for both rulesets, a built-in choice turning the switch off, an old
file gaining the keys);
`GuiWindowTest` (the walk now ends on custom, whose chooser is canceled: Black and White stays drawn
and saved, the mark returns to it, nothing is saved). Corpus identical to jc-17, 24,212 of 24,212.

### Step 6 — auto-diagonals (`5f928e0`, `fb8fe25`, `da35e33`)

> ⚠ **The fork's rewrite of the key handling is REVERTED for the upstream baseline (2026-11-09,
> `d08e6ef`, jc-23)**: `EmulatorKeyListener` and `ControlGUI` are upstream's byte for byte,
> `DiagonalCombiner` and `AutoDiagonalTest` are gone, and `SuccPaths` reads and writes the switch
> as upstream does (`Boolean.parseBoolean`, written back raw between `UpRight` and `Rewind`), inside
> the fork's own settings structure. The problems that brings back are UB-22 to UB-28 in
> `BASELINE-ISSUES.md`. The record below describes jc-18 to jc-22.

**What upstream added.** Tools › Controls › Auto diagonals (`[Controls] AutoDiagonals`): arrows
pressed together make a diagonal, and the diagonal keys' remap boxes gray out while it is on.
`ControlGUI.java` and `ControlGUI.form` are taken verbatim — the `.form` edit compiled by
`formclass.ps1 -Compile`, the first `.form` change this fork has shipped without IntelliJ — and so
is upstream's `SuccPaths.Controls` type that window uses.

**Why the key handling was rewritten.** Upstream's `EmulatorKeyListener` started a
`java.util.Timer` per arrow press and, 50 ms later, ticked the game from that timer's own thread,
beside the event thread, through an unsynchronized `HashSet`. The architecture review measured four
more problems against Tile World's rule for the same job (`generic/dirinput.c`):
- **Input jumped the queue.** Every other key ran at once while an arrow waited, so arrow then
  Backspace rewound the move BEFORE it, arrow then Shift+1 saved the state a move early, and a click
  on the game ticked first: the recorded moves were not what was typed.
- **A tap made no move**, and holding Up while tapping Right made plain Up: the decision used only
  the keys still held when the timer fired.
- **Three keys went by table order**, not by what was pressed last.
- **In MS it ate keys.** MS has no diagonals, so `SuperCC.tick` reduces one to its vertical half:
  hold Up and press Right, and Chip moved Up, where Tile World's MS mode moves Right.

**What it does now** (`MOD (Jeremy, upstream merge)`): a `javax.swing.Timer` on the event thread,
started only when a window opens (auto-repeat is faster than 50 ms and would otherwise hold it off);
only the four arrows wait; a waiting arrow's move is made at once before any other key, any mouse
press and any loss of focus (from `keyPressed` and from an AWT event listener, which runs before the
component sees the event); MS makes no window at all. Which move a window makes is
`emulator/DiagonalCombiner`, pure logic after Tile World's rule: each axis is claimed by its newest
HELD key, an axis nothing holds by its newest key tapped inside the window, and a tap never
displaces a held key on its own axis. One deliberate difference, documented there: two keys both
tapped in one window make a diagonal, where Tile World's per-tick poll keeps one. The switch reads
through `optedIn()` with its own rendered line (CLAUDE.md §5), and the listener asks the cheap
`getAutoDiagonals()` — upstream asked `getControls()` on every key press, which parses all twelve
codes and rewrites the settings file whenever one does not parse.

**Recording is untouched.** The move a window makes goes through the same `processKeyPress` →
`tick` as the key for that move would, and Solution replay, the corpus and TWS export never pass
through the key listener. ⚠ One interaction to know: diagonal opening moves become easier to make
under Lynx, and FORK.md step 2 records that Tile World starts Lynx Chip facing SOUTH while SuperCC
uses his tile's facing, so a route opening with a diagonal is likelier to meet that known,
unfixed difference.

**Verification.** `test/AutoDiagonalTest.java` (50): every combining rule, including each of the
review's counterexamples. `GuiWindowTest` §6, against the real window with real dispatched
`KeyEvent`s, each sequence compared with the same moves made the plain way: setting off moves at
once; on under MS still at once; on under Lynx two arrows wait; a wait pressed meanwhile lands AFTER
their diagonal; a mouse press and a loss of focus make the waiting move; a tap waits and then moves
when the Swing timer fires. Four mutants — no flush before other keys, none on a mouse press, MS
waiting too, taps ignored — each fail the checks written for them. `FormClassTest` gives ControlGUI
an emulator with real settings, which the program always has and the headless one never did.

**The release reviews (2026-11-07), and what they changed.** Three reviewers ran on the merged
range before tagging: a `/code-review`, a hostile failure-mode and concurrency review, and a
supply-chain review.
- **Auto-repeat (failure-mode review, reproduced in the real window).** Windows repeats a held key
  about every 33 ms; each repeat joined the open 50 ms window, so a held arrow moved at half speed
  (four key events, two moves), and the held key, released inside a window it had repeated into,
  counted as a tap — running Up then turning Right came out Up, Up-Right. Now a press of an arrow
  already held is a move of its own at once, from what is held (holding Up and Right still repeats
  as the diagonal). `GuiWindowTest` §6 sends real repeats and compares each run with the setting off.
- **Drags and wheel turns flush too.** A drag of the time slider rewinds at every step and a wheel
  over the move list moves through it, so a waiting arrow must land before them.
- **Focus.** Forgetting held keys on every component focus change (`/code-review`) dropped a held
  arrow when focus merely moved inside the program; releases anywhere in it are seen anyway. Now
  only focus leaving the program forgets them; any focus change still flushes.
- **Tools › Controls** now reads `getAutoDiagonals()`. Upstream's `getControls()` there could
  rewrite the settings file, and that window is built inside a `SwingWorker`, off the event thread.
- **Defended:** the diagonal keys' remap boxes gray out while the switch is on, as upstream made
  them (the README says how to change one); `keyPressed`'s flush repeats the AWT listener's on
  purpose, because code and tests call it directly; the program-wide listener is never removed,
  because the program has one main window; two Controls windows open at once save whichever closes
  last, as the key codes always have.


## jc-14 -- the window title is fully switchable

The title had one switch (`ShowBuildTag`, jc-8) and two hard-coded halves. jc-14 makes all three
independent `[Graphics]` settings:

```
ShowBuildTag  = false      the [jc-N] tag        (unchanged, opt-in since jc-8)
ShowLevelPack = false      the level SET name    NEW -- shipped OFF
ShowLevelName = true       the LEVEL name        NEW -- shipped ON
```

### The separators are the whole problem

Three switches over two separators is exactly the shape that yields a dangling `" - "` or a doubled
`" -  - "` in whichever combination nobody tried. So the rule does not live at the call site with
the settings and the `Level` around it — it lives in one static, pure function:

```java
public static String composeWindowTitle(boolean showBuildTag, boolean showLevelPack,
                                        boolean showLevelName, String levelPack,
                                        String levelName, String suffix)
```

No settings, no `Level`, no `Gui`, so all eight combinations can be walked without building a level
or opening a window — which `WindowTitleTest` does, asserting the separator COUNT as well as the
expected string. "No extra dashes" is the actual requirement, and reading it off eight expected
strings is not the same as checking it.

`windowTitle(pack, name)` is the four-line settings-aware wrapper, and the single
`window.setTitle(...)` call site now goes through it. The `paths == null` fallbacks there are the
SHIPPED defaults — pack off, name on — not "everything off", so a headless instance composes the
title a fresh install would rather than an arrangement no real install ever shows. (Tile World's
equivalent re-title crashed every batch run for exactly this reason; the guard is not decoration.)

An absent or empty pack or level name is treated as not shown, independent of the switch, so a set
with no title cannot produce a trailing separator either.

### Two switches, two OPPOSITE predicates

`ShowLevelPack` goes through `optedIn()` — the existing strict predicate, ON only for `true`/`1` —
because its default is OFF and a typo must not switch it on. `ShowLevelName` goes through a new
mirror, `shownUnlessOptedOut()`, OFF only for `false`/`0`, because its default is ON and a typo must
not switch it *off*.

Worth being precise about what the mirror actually buys, because two cases look alike and only one
is real:

- **A settings file with no key at all** (every jc-13 file) is handled by `seedDefaults()`, which
  fills the key with `"true"` at load. `optedIn()` would get that case right too.
- **An unrecognized value** — `ShowLevelName = yes`, a blank, a typo — is the case only the mirror
  gets right. Opt-in semantics there would silently hide the level name.

Both are rendered back through the same predicates the getters use, the invariant `ShowBuildTag`
already held: the file can never echo a value the program reads differently. `ShowLevelPack = yes`
is rewritten as `false` on the next settings change, matching what the getter reads.

### What changes for a downloader, and for the automation

The stock title goes from `SuperCC - CCLP5 - Lesson Zero` to `SuperCC - Lesson Zero`. That is the
requested default and it is the one visible behavior change in this release.

⚠ It breaks any window-title matching that expects the level set. `supercc_driver.ps1` anchors on
`^SuperCC(\s\[[^\]]*\])?\s-\s<Set>` and uses the same expression twice: once to FIND the window and
once to assert the right set is loaded — the second is a safety property, not a convenience.
Anything relying on it needs `ShowLevelPack = true` in its own settings file. Noted in
`getShowLevelPack()`'s comment, README section 6, and the CHANGELOG.

### Mutation testing

Five planted, five killed — dropping the `!levelPack.isEmpty()` guard, swapping either predicate for
the other, bypassing the predicate in `render()`, and flipping the headless `paths == null` fallback
to `paths != null`. Both edited files were already in `$SPLICE_MODIFIED`, so no widening was needed
(CLAUDE.md §4). The predicate swap killed only ONE assertion, which is what prompted the correction
above: it is the unrecognized-value case doing the work, not the absent-key case.

## jc-13 -- a truncated .tws was accepted

`twsInputStream` extends `FileInputStream`, so `read()` answers -1 at end of file rather than
throwing. `verifyAndInit` genuinely checked only the four-byte signature; every field after it was
read straight past the end. A file holding nothing but its signature therefore produced

    ruleset      = LYNX      (the byte read as -1, and -1 != 2)
    len          = -1
    headerLength = 8 + (-1) = 7

with no complaint, and `readSolution` then walked records from an offset that means nothing. The
symptom is "the wrong solution plays back", not a clean error -- the worst shape for something
reading a file that holds work you cannot reconstruct. It was found while writing
`TwsRoundTripTest`, and pinned there as a FINDING for one release before being fixed here, because
`io\TWSReader.java` was not in `$SPLICE_MODIFIED` and editing it would have shipped nothing.

### Two checks, and deliberately not four

The first draft had four: the file-length guard, an end-of-file test after each header field, and
the header-length cross-check. Mutation testing killed two of them. With `fileLength >= 8`
established, `readInt` takes bytes 0-3, the ruleset byte is 4, `skip(2)` covers 5 and 6 and the
length byte is 7 -- all present, so neither -1 test can fire. Deleting either changed no result.
They were provably dead code wearing a comment that called them a safety net, which is worse than
not having them, so they are gone.

What remains, each proven load-bearing by planting its removal:

| Check | What only it catches |
|---|---|
| `fileLength < 8` | a file too short to hold a header at all |
| `headerLength > fileLength` | a header whose own length byte claims more than the file holds -- every field present and well-formed, and only the cross-check notices |

### It rejects nothing real

The risk in tightening a reader is refusing files that were always fine. Every `.tws` in the
maintainer's collection was re-opened with the new check: **23,967 accepted, 0 rejected**, across
`save\` and the whole of `tws\`. That is the regression test that matters, and it cannot live in
the repo, because none of those files may be committed.

## jc-12 -- the two ways MenuBar.openFileBytes could fail

`openFile()` returns `null` when the user dismisses the chooser. Its two other callers, "Open
levelset" and "Open tws", both test for that; `openFileBytes` did not. It read

```java
return Files.readAllBytes(openFile(path, extensions).toPath());
```

so Cancel produced `null.toPath()`. A `NullPointerException` is not an `IOException`, so the
`catch (IOException)` immediately below could not see it and it escaped onto the event thread.
Three menu items are affected: Solution > Open, Search for seeds, and Load states.

Reproduced before fixing, with a control: the pre-fix jar, Ctrl+O then Escape, gives

```
Uncaught exception in thread "AWT-EventQueue-0"
java.lang.NullPointerException: Cannot invoke "java.io.File.toPath()" because the return value of
  "graphics.MenuBar.openFile(String, String[])" is null
    at graphics.MenuBar.openFileBytes(MenuBar.java:792)
    at graphics.MenuBar$SolutionMenu.lambda$new$2(MenuBar.java:228)
```

and the same keystrokes on the fixed jar produce empty stderr with the window still responsive.

The second defect is the one that was actually observed. `succ_error-<PC>.log` had recorded

```
java.nio.file.NoSuchFileException: ...\succsave\SokobanCCLP\65_Cost Benefit Paralysis-MS.json
    at graphics.MenuBar.openFileBytes(MenuBar.java:792)
```

three times across two sessions. That one WAS caught -- but it was handled by calling
`e.printStackTrace()`, which is what put fifty lines of JDK frames in the log, and by showing the
user `e.getMessage()`, which for `NoSuchFileException` is the bare path with no words around it.
Being asked to open a solution before one has been saved is the ordinary case, not a fault, so it
now gets its own catch, a sentence, and no stack trace. Confirmed reachable by keystroke: the same
Ctrl+O, Enter sequence reproduces it on the pre-fix jar and is silent on the fixed one.

Worth recording for its own sake: **this was found by the error log added in jc-11**, in a file
nobody had looked at, describing a defect nobody had reported. That is the whole argument for
having built it.

`graphics\MenuBar.java` is in `$SPLICE_MODIFIED` and has no `.form` sibling, so the edit ships;
`verify-splice.ps1` and the shipped bytecode's exception table (two handlers where there was one)
both confirm it.

## The error log (jc-11)

`java/io/ErrorLog.java`, installed from the static launch path in `SuperCC` before anything else
runs.

**The problem.** Double-clicking a `.jar` runs it under `javaw`, which discards stderr. So all 27
`printStackTrace` calls in this program and every uncaught exception went nowhere. A user reporting
"it just closed" had nothing to send, and nobody had anything to read. For a fork whose entire
debugging culture is measurement, that was the largest hole in it.

**The fix.** Tee `System.err`. That one move captures every existing call site without editing any
of them.

Four details are load-bearing, and each of them is the answer to something that went wrong in
review rather than a preference:

- **The console keeps its stack traces.** On Java 9+, AWT's `EventDispatchThread.processException`
  hands EDT exceptions to the default uncaught-exception handler, and `ThreadGroup` prints to
  `System.err` *only when no handler is installed*. So simply installing a handler would have made
  jc-11 **quieter** than jc-10 — the exact opposite of the goal. The handler prints through
  `System.err`, which is the tee, so the console keeps what it had and the file gets exactly one
  copy. `CLAUDE.md` mandates launching from a console for that trace; it would have silently stopped
  being there.
- **The file name carries `COMPUTERNAME`.** The Chip's Challenge folder is Dropbox-synced between
  two PCs (the reason for ADR 0004). One shared log would be appended by both machines and return as
  a conflicted copy — and rotation could not work anyway, because Windows refuses to move or delete
  a file another process holds open. Measured: `Files.move` on an open log throws
  `FileSystemException`, while a second append-mode stream succeeds, so two instances would silently
  interleave into one file with the cap no longer enforced.
- **The size cap is counted per write, not checked at startup.** The failure this exists to record
  can be a repaint loop throwing every frame, which produces megabytes in seconds; a check that only
  ran at launch would cap nothing within the session.
- **The charset is explicit.** The two-argument `PrintStream` constructor uses the *platform default*
  charset, not the console's — they differ on Windows, and this program prints Unicode arrows and
  level titles.

`ErrorLog` must never throw and never print: it sits underneath `System.err`, so an escaping
exception surfaces inside code that was merely reporting a problem, and a `println` on a failure
path recurses into itself. Every path catches `Throwable`, and a failed open disables it permanently
rather than retrying on every write.

Rationale for it having no on/off setting is ADR 0009. Short version: an opt-in crash log is close
to useless, because to benefit you must already know it is crashing and then reproduce it.

### The startup NPE this forced us to fix

`Gui.repaint(boolean)` ends with `changePlayButton(emulator.getSavestates().isPaused())`, which is
**not** guarded even though the two statements above it already sit behind `isLevelLoaded()`. Every
launch with no level open threw there. It was harmless — the panels above had already repainted, and
only `repaintRightContainer()` and the play-button icon were skipped — and invisible, because
`javaw` ate it.

With a log, that becomes an error recorded on every single startup, which would train everybody to
ignore the file. `Gui` is form-based and cannot be recompiled from this repo, so the call site in
`SuperCC` is the only fixable end:

```java
if (isLevelLoaded() && getSavestates() != null) window.repaint(true);
else window.repaint();
```

`getSavestates() != null` is the belt to that braces: what actually throws is the savestates
dereference, and `level` and `savestates` are not assigned atomically — if the `SavestateManager`
constructor fails, `loadLevel`'s catch leaves `level` set and `savestates` null.

When a level set *was* passed on the command line, `ArgumentParser` has already run and `savestates`
is non-null, so behavior is identical to jc-10. Otherwise `Component.repaint()` covers everything
that actually survived the NPE.

⚠ This kills the every-launch instance, not the bug class. `MenuBar` (four call sites) and the
form-based `tools/ChooseTileSize` / `ChooseWindowSize` also call `window.repaint(true)` and are
reachable with no level open. If one of those fires, the log firing is **correct** — that is a real
defect worth seeing.

### Opening a file that is not a level set

Same argument as the startup NPE, found by review once the log existed. `openLevelset` caught the
`IOException`, showed a perfectly clear *"Could not read file: Invalid signature"*, and then **fell
through** to `loadLevel` with `dat` still null — so a brand-new log's first entry was a
`NullPointerException` out of `startingRuleset()`, for one of the commonest things a stranger does
wrong. It now returns instead.

Two knock-on details, both real:

- **`loadLevel` is guarded too** (`if (dat == null) return;`). Its two `lastLevelNumber()` calls sit
  *outside* its own try block and dereference `dat`, and `ArgumentParser` and `SeedSearch` both call
  `openLevelset` and then `loadLevel` unconditionally. On the command-line path — `SuperCC.jar
  bogus.dat`, or a file association — that NPE escaped startup entirely (the try there catches only
  `IllegalArgumentException`), so the tilesheet setup never ran and the window was left half-built.
  Guarding `loadLevel` covers every caller, present and future, inside a file that is already
  spliced.
- **A bad file no longer resets an open set.** Previously the fall-through reloaded level 1 of
  whatever was already open, because the failed `dat = new DatParser(...)` never assigns.

### Verification

- **Corpus, both builds:** every level of all 286 sets re-parsed (title, chips, timer, password,
  ruleset, the MS monster list **in order**, and SHA-256 of both tile layers) and all 23,322 stored
  solutions re-played (solved state, Chip position, timer, chips left). 45,641 lines, **byte
  identical**, same SHA-256. Creature order drives MS behavior, so an ordering change would be a
  desync and would show here.
- **Jar diff jc-10 → jc-11:** zero `game/**` classes differ. The only changed class is
  `emulator/SuperCC.class`; the rest is three new `io/ErrorLog*` classes and its bundled source.
- **Playtest:** clean launch, stderr empty, no log file created.

## Toggling the build tag (no rebuild)

The tag is handy while the fork is under active modification and just noise the rest of the time,
so it is switched by a setting rather than by a rebuild. In the CC folder's `succ_settings.ini`:

```ini
[Graphics]
ShowBuildTag = true
```

**`true` (any casing) or `1` shows the tag. Everything else hides it, including the key being
absent and the settings file not existing at all.** Settings are parsed once at launch, so a change
applies the **next time SuperCC starts**.

> ⚠️ **The default was INVERTED in jc-8, and must stay inverted.** Up to jc-7 the rule was the
> opposite — "anything but an explicit `false`/`0` is ON" — which meant every fresh download showed
> a build number in its title bar until the user found this key. Jeremy hands the GitHub link to
> other people and does not want them seeing one, so the tag is now strictly opt-in. This is the
> same requirement Tile World has, and there it needed the same one-time source flip. Don't
> "restore" the lenient reading; a release that shows `[jc-N]` on a clean install is a regression.
> The acceptance test is a clean-room one: **no settings file at all → launch → no tag**.

> **Don't put a trailing `;` comment on that line.** `parseSettings()` only honors a `;` at the
> *start* of a line; everything after `=` is the value. `ShowBuildTag = true ; shows it` yields the
> value `true ; shows it`, which is not `true`, so the tag stays **off**.

Three more ways to get this wrong by hand — all of which now fail toward *off*:

- **The space before `=` is load-bearing.** `parseSettings()` reads the key as
  `substring(0, indexOf('=') - 1)`, so `ShowBuildTag=true` parses as the key `ShowBuildTa` and is
  ignored — leaving the tag off.
- **The section matters.** Keys are stored as `<section>:<name>`, so a `ShowBuildTag` line under
  `[Paths]` is a different key that nothing ever reads. It must be inside `[Graphics]`.
- **Don't edit while SuperCC is running.** It keeps the whole settings file in memory and rewrites
  every line of it whenever any setting changes, so a running instance restores its own stale value
  over the edit.

`set_supercc_buildtag.ps1` (in Jeremy's `Dropbox\Claude\CC Audit Scripts\`) mirrors the parser on all
three: it scopes to `[Graphics]`, reports the state SuperCC would actually see (so a no-space line
reads as *off*, and gets repaired rather than reported as working), takes the last of any duplicates,
writes atomically via a temp file, and verifies by reading back. It targets `succ_settings.ini` from
jc-8 on.

Switching the tag off does **not** make a build unidentifiable — `BUILD_TAG` is still a string
constant in the jar, so `unzip`/`javap` on `emulator/SuperCC.class` still names the release.

## The settings file (jc-8)

### Renamed to `succ_settings.ini`

`SuccPaths.SETTINGS_FILE_NAME` is the one place the name lives; `SuperCC`'s constructor reads it
from there instead of repeating a literal. SuperCC and Tile World share the Chip's Challenge folder
here and Tile World is getting an initialization file of its own, so neither may claim the generic
name `settings.ini`.

**There is deliberately no migration path.** An old `settings.ini` is simply not read — it is not
renamed, merged, or deleted, and a fresh `succ_settings.ini` is created next to it. Reading a
legacy file "just once" would mean shipping a compatibility path forever, in the one class whose
whole jc-4 story is about not touching files it does not own; renaming one file by hand, once, is
cheaper and safer. Anyone upgrading renames theirs and keeps their settings.

### The TWS folder was made a fixed starting point (jc-8) — ⚠ REVERSED BY jc-10

> **Superseded.** Read this section as history. jc-10 put the remembering back at Jeremy's
> request; see *The TWS folder remembers again (jc-10)* below for what the code does now. The
> only part of this section still live is the NPE fix in the last bullet.

`MenuBar`'s two TWS actions (open, and write-solution-to-new) used to call `setTWSPath(...)` with
whatever folder the chooser landed in, so the stored value drifted to whichever set was touched
last — it was sitting at `tws\Walls_of_CCLP2-MS`. With one subfolder per set (411 here), opening in
the parent every time seemed both more predictable and fewer clicks than opening in an arbitrary
sibling.

- `DEFAULT_TWS_PATH = "tws"`, and `getTWSPath()` falls back to it when the key is absent **or
  blank**, so clearing the line is a supported way to reset it. *(Still true in jc-10.)*
- A hand-written value was honored, and was the only way the value ever changed. *(No longer true
  — jc-10 writes it again.)*
- `setTWSPath()` was removed, not just left uncalled. *(jc-10 restored it deliberately.)*
- Removing the caller also retired a live NPE: `saveNewFile()` returns `null` when the save dialog
  is canceled, and the old line called `tws.getParent()` on it unconditionally. **This fix
  survives jc-10** — the restored callers guard for it.

### The TWS folder remembers again (jc-10)

Jeremy asked for the pre-jc-8 behavior back (2026-08-21): with one set open for a stretch of work,
having to re-navigate the chooser every single time costs more clicks than landing in the parent
saves. The jc-8 reasoning above was sound in the abstract and wrong in practice, and this is a
deliberate reversal, not a regression.

- **`setTWSPath()` is restored**, and both `MenuBar` TWS actions call it with the *parent folder*
  of the file actually used. `getTWSPath()` and its fallbacks are unchanged from jc-8.
- **The callers guard against null, and so does the setter.** `saveNewFile()` returns `null` on a
  canceled dialog and `getParent()` is `null` for a parentless file; either would have been the
  jc-8-era crash. `setTWSPath()` additionally ignores a null or blank argument rather than blanking
  a good stored value — a caller bug must not destroy the setting.
- **The value is stored relative to the CC folder** via `toStoredPath()`, exactly like `Levelset`,
  so a settings file shared between two machines stays valid on both.
- ⚠ **Choosing a folder OUTSIDE the CC folder stores an absolute path** (that is what
  `toStoredPath()` does, and it is right — there is no portable way to express it). On a
  Dropbox-shared settings file that path may not exist on the other machine. It degrades
  gracefully: `JFileChooser` falls back to a default directory rather than throwing, and the next
  use on that machine overwrites it. Restoring the memory makes this path materially more likely
  to be hit than it was under jc-8, which is why it is called out here.
- Tested by `SettingsTest` section 4 (round trip inside and outside the CC folder, plus the
  null/blank guard) and section 10 (the built jar really does call it from `MenuBar`), and by a
  real GUI run: the chooser opened at the stored folder, selecting a file elsewhere rewrote the
  setting to that file's folder, and reopening the chooser landed there.

### The release is a packaged zip

`build.ps1 -Package` produces `dist\SuperCC-<tag>.zip` containing `SuperCC.jar`, a stock
`succ_settings.ini` and `README.txt`, where:

- the **tag comes from `BUILD_TAG`** in the source, so the zip cannot be named for a build it does
  not contain;
- the **`.ini` is generated by calling `SuccPaths.createSettingsFile()` in the jar that was just
  built**, never hand-written, so the shipped defaults cannot drift from the program's;
- the build **fails** if `README.txt` does not name the tag being packaged — a download whose README
  describes a different build is worse than no README;
- `README.txt` is converted to **CRLF** on the way in (Notepad), while `succ_settings.ini` keeps its
  mixed layout untouched, because matching what SuperCC writes byte for byte is what stops the first
  settings change from rewriting the whole file.

`README.txt` is the user-facing document — what SuperCC is, that it needs Java 16+, every setting
explained, and the revision history. **It is updated with every release**, including a plain-English
entry for whatever changed and an entry for any new setting.

## Settings-file resilience (jc-4)

`java/io/SuccPaths.java` had three inherited defects that could destroy `settings.ini`. All three
were reproduced before being fixed:

| | Was | Now |
|---|---|---|
| **Read failure** | ANY `IOException` was treated as "file missing" → `createSettingsFile()` truncated it and wrote defaults. Windows locks are *mandatory*, so a settings.ini briefly held by Dropbox or antivirus was genuinely unreadable — and destroyed. | `SuccPaths.load()` distinguishes absent from unreadable, retries a locked file, and otherwise runs on in-memory defaults **without writing**. |
| **Write** | `PrintWriter` straight onto the destination — truncates at open, and never throws (errors go to a flag only `checkError()` reads, which nothing called). A failure left a zero-byte file, silently. | Rendered to a String, staged to a uniquely named sibling temp file, moved into place, **retried**, and reported. |
| **Absent keys** | Persisted as the literal text `null`, which never self-healed (`"null"` is a non-null String). `createSettingsFile()` omitted seven keys, which is how they got there. | A `DEFAULTS` table seeds missing keys at load, repairs an existing `null`, and renders the complete set. |

Two things to preserve if you touch this code:

- **The staging file must stay a sibling of the target.** Same directory means same volume, which
  keeps `Files.move` on the `ATOMIC_MOVE` path. The fallback is copy-then-delete, which truncates the
  destination and would reintroduce exactly the torn file this design prevents.
- **The output layout is byte-for-byte compatible** with the old `PrintWriter` — platform separator
  after section headers, bare `\n` after key lines, no trailing newline. Verified against a real
  settings.ini: 449 bytes in, 449 identical bytes out. Don't "normalize" it.

## Portable paths (jc-5)

`settings.ini` is Dropbox-synced between two PCs with different usernames, and `Levelset`/`TWS` held
absolute paths — so each machine overwrote the other's and left it pointing at a folder that didn't
exist. `succ = succsave` never had this problem because it was always stored relative, so those two
keys now work the same way:

```ini
[Paths]
Levelset = data
TWS = tws\JacquesOld-MS
succ = succsave
```

(That `TWS` value is what jc-5 produced, back when the program still rewrote it; since jc-8 it stays
`tws` unless you edit it yourself.)

- **Callers see no change** — the getters still return absolute, usable paths. Only the stored form moved.
- A path **outside** the CC folder stays absolute (nothing to anchor it to); so does a different drive.
- An existing absolute value still resolves and converts the next time that folder is set — no migration step.
- **`getSuccPath()` is deliberately excluded.** It's the one data-bearing path (it decides where
  solution JSONs are written), it's already relative, and its callers resolve it against the working
  directory. Changing what it returns would move where solutions are saved.

`Path.startsWith` compares *name elements*, not characters, so a sibling folder sharing a name prefix
(`<cc>Extra\data`) is correctly treated as outside. Don't "simplify" it to a string prefix test.

## Always open under MS (jc-9)

A CC1 `.dat` declares its intended ruleset in its 4-byte signature — `0x0002AAAC` / `0x0003AAAC`
mean MS, `0x0102AAAC` means Lynx (`DatParser`). `MO3.dat` carries the Lynx signature, so it opens
under Lynx. Correct by the format; a nuisance when working through a collection under MS.

```ini
[Emulation]
AlwaysOpenInMS = true
```

Same strictly opt-in rule as `ShowBuildTag`, and now literally the same predicate: `optedIn()` is
the single definition of "this switch is on", read by both getters and used by `render()`. Any
future switch goes through it rather than growing a second, subtly different rule.

**Where the override is applied, and why there.** In `SuperCC.startingRuleset()`, not in
`DatParser`:

- `DatParser` is a file reader; giving it a dependency on the settings file would be backwards.
- `DatParser.getRuleset()` has exactly one consumer — `openLevelset()` — so this is a genuine choke
  point, not one of several.
- `parseLevel()` records whatever ruleset it is handed (`if (rules != Ruleset.CURRENT) this.rules =
  rules`), so passing MS in at open time makes every later load of that set MS too. The override
  propagates without a second special case.
- The `paths == null` guard is load-bearing: the headless `SuperCC(boolean)` constructor never
  builds a `SuccPaths`, and `openLevelset()` is reachable from it.

**Scope is deliberately narrow — it decides only what a set OPENS in.** `Level > Change ruleset`
(F3) still switches freely, and loading a solution still switches to that solution's own ruleset.
Forcing MS there would break every Lynx replay, which is a far worse outcome than the convenience
this buys.

⚠️ **It does move where solutions are filed, and that is inherent, not a bug.** `getJSONPath()`
names each solution `<n>_<title>-<ruleset>.json`, so a set opened under forced MS saves and looks
for `-MS.json` while anything recorded earlier under Lynx sits in `-LYNX.json`. `Solution > Open`
stops preselecting the old files and `Solution > Save` starts a parallel set beside them. Nothing is
deleted or overwritten, and turning the setting off (or pressing F3) restores the old target — but
it is the consequence most likely to be mistaken for lost work, so it is called out in README.txt's
entry for the setting. The same mismatch makes `--testtws` prompt when a **Lynx-recorded** tws meets
a forced-MS set; for an MS corpus the flag removes a mismatch that used to be there.

Verified end to end by building a real `SuperCC` against a temporary settings file and reading back
`getLevel().getRuleset()`: MO3 gives `LYNX` with the flag absent or false and `MS` with it true,
while CCLP5 (MS signature) gives `MS` either way.

`[Emulation]` is a new section. A jc-8 settings file has no such section; `seedDefaults()` supplies
the key and it appears the next time anything is written — exactly how `ShowBuildTag` arrived in
jc-3, and covered by `test\SettingsTest.java`.

## Off-map monster-list entries (jc-7)

Three levels could not be opened **at all** — `ArrayIndexOutOfBoundsException` on load:

| Set | Level | Title | Bad entry |
|---|---|---|---|
| `geodave1` | 146 | Ooops! Chip can't swim without flippers! | (160, 160) |
| `pi` | 9 | bugs | (24, 34) and (31, 32) |
| `Rock-Alpha` | 21 | Mustache | (88, 88) |

Their CC1 monster-movement list (optional field 10) carries junk entries pointing off the 32×32 map.
`LevelFactory.getMSMonsterList()` has two loops over that array — one counts, one stores. The storing
loop was already correct: it builds a `Position`, and `Layer.get(Position)` returns `Tile.WALL` for an
off-map one, which is not a monster, so the entry is skipped. The **counting** loop used the raw
`int` overload, which has no bounds check. The fix is to count through the same accessor.

Two failure modes existed, and the second is the better argument for the fix:

- `32*y + x >= 1024` → the exception above; the level never opened.
- `x >= 32` but `32*y + x < 1024` → **no exception**. The multiply aliased onto an unrelated in-bounds
  cell; had it held a monster, the counting loop would have over-allocated and left a trailing `null`
  that NPEs in `MSLevel`'s constructor. 10 such entries exist across 8 levels here — all alias onto
  Wall/Water/Dirt/Floor, so none has bitten yet.

**Verified safe across the whole collection**: the MS monster list of all **21,838 levels in 273 sets**
is byte-identical before and after, except the 3 levels above.

**The new rule is exactly the reference engine's.** Tile World decodes each entry as

```c
#define readpos(x, y)  (*(x) < CXGRID ? *(x) + CYGRID * *(y) : POS_INVALID)   /* encoding.c */
                                            /* POS_INVALID = CXGRID*(CYGRID+1) = 1056 */
if (pos < 0 || pos >= CXGRID * CYGRID) continue;                              /* mslogic.c */
```

so it discards an entry iff `x >= 32` (flagged immediately — it never aliases onto another cell) or
`y >= 32` (`pos` lands at 1024 or beyond). That is precisely `Position.isValid()`. It keeps no slot
and does not clamp, so creature *order* agrees with SuperCC on every off-map entry, unconditionally.

**Tile World never had this bug** — it opens all three levels and just logs a warning, e.g.
`level 146: invalid creature location (0 33)` (33 = `POS_INVALID`/32). Only SuperCC needed the fix.

MS only — the Lynx loader never reads this list, so those levels always opened under Lynx.

## Building

Requires a **JDK 16+** (upstream targets Java 16). From this folder:

```powershell
powershell -ExecutionPolicy Bypass -File build.ps1              # -> SuperCC.jar
powershell -ExecutionPolicy Bypass -File build.ps1 -Package     # -> also dist\SuperCC-<tag>.zip
powershell -ExecutionPolicy Bypass -File run-tests.ps1          # builds, then runs test\
```

## Tests

`test\SettingsTest.java`, run by `run-tests.ps1`, covers `SuccPaths`: the settings file's name,
its byte layout, the defaults table, path handling, and both opt-in switches — 90 assertions
including the jc-4 protections (an unreadable file is never overwritten, `createSettingsFile()`
refuses to clobber, a BOM is stripped) and the jc-5 relative-path round trip.

Two properties it deserves a note for:

- **It tests the BUILT JAR, not the source tree.** This is a splice build, so what ships is
  recompiled classes overlaid on a prebuilt baseline — the jar is the only artifact whose behavior
  is authoritative. `run-tests.ps1` builds first unless given `-NoBuild`.
- **It scans every class in the jar for references to a method by name.** Reflection on one class
  proves a method exists or not; it cannot prove anything about the *callers*, which live in
  classes the splice may or may not have recompiled. Either direction fails silently: a stale
  caller of a removed method throws `NoSuchMethodError` on a menu click, and a stale class that
  never got the *new* call simply does nothing, with no error at all. That scan is why
  `setTWSPath`'s removal could be trusted in jc-8 — and, with the assertion inverted, why its
  restoration can be trusted in jc-10.

Two checks skip (rather than fail) without files this repo does not contain: the jar scan without
`-Dsupercc.jar`, and the MO3 signature check without `-Mo3` (MO3 is a third-party level set, not
ours to redistribute).

`build.ps1` recompiles **only the hand-edited, non-form source files** (listed in `$MODIFIED`) with
`javac --release 16` and splices them over the committed baseline of compiled classes, then repackages
with the libs (`com/intellij` annotations, `org/json`), `resources/`, and the manifest — reproducing
the upstream fat jar (571 entries, 193 classes). It resolves a real JDK bin automatically (the Oracle
`javapath` dir on PATH exposes `javac` but not `jar`).

**Why not a full `javac` rebuild?** SuperCC's GUI is built with the IntelliJ GUI Designer — the main
`graphics/Gui` window and the `tools/*` dialogs each have a `.form` file, and IntelliJ's *form compiler*
generates a hidden `$$$setupUI$$$()` method at build time that creates their Swing components. Plain
`javac` doesn't do that, so a full rebuild yields a jar whose GUI dies on launch (`... playButton is
null`). So the IntelliJ-built `.class` files are committed as the authoritative baseline (under
`emulator/ game/ graphics/ io/ tools/ util/`), and only the non-form modified classes are recompiled.
**Adding a mod to a new file?** Add it to `$MODIFIED` in `build.ps1` — and make sure it has no sibling
`.form`.

## Note on history / source vs. bytecode

Upstream ships its `.java` source *inside* the jar under `java/`. The prior hand-built mods were
spliced in as recompiled `.class` files only — the bundled source stayed pristine. This repo
reconstructs the mods **into the source** (from the documented changes) so the source is authoritative
for the non-form classes, while the IntelliJ-built `.class` baseline is committed for the form classes
that `javac` can't reproduce (see the build note above).
