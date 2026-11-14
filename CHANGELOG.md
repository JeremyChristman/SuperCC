# Changelog

Every release of this fork, newest first, in the spirit of [Keep a Changelog](https://keepachangelog.com/).

Versions are build tags, `jc-N`, not semantic versions — the fork tracks upstream SuperCC rather
than versioning independently. Dates are the tagged commit's date.

**Two jars must never report the same build tag.** jc-6 exists only because a one-line fix landed
after jc-5 was already deployed and reusing the number was not an option.

This file is a summary. `README.txt` section 7 is the user-facing history that ships in the download,
and `FORK.md` is the engineering record: why each change exists, what broke first, and what was
measured. All three are updated together — see [`.github/RELEASING.md`](.github/RELEASING.md).

## jc-29 — 2026-11-14 (the fix layer: a set opens at its first level that loads)

The second of the no-level-loaded fixes goes back on the upstream baseline. It closes UB-14 in
[`BASELINE-ISSUES.md`](BASELINE-ISSUES.md), and UB-30 with it. No stored solution changes.

### Fixed
- **A set whose level 1 will not load opens at its first level that does (`48a3eca`, UB-14)**,
  with one error naming every failure before it by position ("Levels 1 to 3 could not be loaded …
  Opened at level 4."). A set where nothing loads leaves the previous set open (or none), put back
  before its error shows, so a save can no longer go into the wrong set's folder; a level that loaded
  is not reported as failed when only its repaint threw. jc-17's `openLevelset`, re-applied alone;
  `loadLevel`'s body is `tryLoadLevel`, which returns its failure, and `loadLevel` reports it as
  before. One change to jc-17's code: the previous set goes back in a `finally`, so an `Error` (a jar
  replaced under a running instance, measured by review) cannot leave one set's file under another's
  level either. Corpus against jc-28: 24,217 of 24,217 identical.
- **UB-30 is fixed with it:** a set opened at launch whose level 1 fails now opens at a level, so its
  levels are reachable again.

### Known issue
- A damaged file whose header claims far more levels than it holds is tried level by level: a 4-byte
  file claims 65,535, and the open spends about 4 seconds on the event thread trying them (jc-17 to jc-20 the same). A real set
  costs tens of milliseconds.

### Changed (tests only)
- `EngineTest` §6c/§6d's 7 pins and `GuiWindowTest` §3a/§5's 6 are checks again. New in `EngineTest`:
  no set is left open without a level, `loadLevel` still reports a failure, and an `Error` escaping
  an open puts the previous set back; in `GuiWindowTest`, that no set's file is left open after a
  failed fresh open. On jc-28's jar the two tests fail 12 and 8 checks, each
  accounted for; 13 defects planted in `SuperCC`, all caught. Suite 2,386 assertions, 41 pins.
  Coverage re-baselined: target 57.1% (1524/2670), `emulator\**` 33.8% (159/470).

## jc-28 — 2026-11-14 (the fix layer: the menu guard)

The first of the no-level-loaded fixes goes back on the upstream baseline. It closes UB-11 in
[`BASELINE-ISSUES.md`](BASELINE-ISSUES.md). No stored solution changes.

### Fixed
- **With no level set open, a menu control that needs a level does nothing (`d57d419`, UB-11)**,
  instead of 28 of them failing with a `NullPointerException` on the event thread (each written to
  the error log): jc-17's `MenuBar.requireALevel()`, re-applied alone, wraps every menu button's
  action listeners, and `worksWithoutALevel(...)` exempts what works without one (Open levelset,
  Open tws, the tilesets, the tile sizes, Game window size, the View toggles, Controls). The Copy
  macro moves hover checks for itself, and `ChooseTileSize`'s unused `getKeys()` line is gone, so
  View › Tile size › custom works with no level open. A tileset chosen before a set is open is saved
  again, as the MS one. Tools › Record gif and TWS › Load solution do nothing with no level open
  too, as in jc-17 to jc-20. One change to jc-17's code: the wrapper re-adds a button's listeners
  oldest first, so they keep Swing's order (jc-17's loop reversed them; latent, since no item has
  two). Corpus against jc-27: 24,217 of 24,217 identical.

### Known issue
- **UB-30 (new, open until UB-14's fix):** a set opened at launch whose level 1 will not load is
  left open with no level loaded (UB-14), and Go to..., which reached its other levels on jc-21 to
  jc-27, is now guarded, so none of them can be reached from the menus. jc-17 shipped this guard
  together with UB-14's fix. None of the 331 sets in the collection is affected.

### Changed (tests only)
- `GuiWindowTest` §2's 30 UB-11 pins are checks again, and Paste solution is clicked again (the
  guard stops it before it reads the clipboard). New: each of the 22 controls that work without a
  level must do its job, and the 30 that need one (and the 4 submenus hovered) must open nothing,
  so a guard that blocks too much fails too; dialogs in the walk are counted with
  `closeEveryDialog`, so a second one fails the count instead of hanging the child; a final sweep
  catches a window that shows late; §3b checks that the guard keeps a two-listener item's order;
  and TWS › Open tws is run with a file chosen, not only canceled. On jc-27's jar the test fails
  37 checks, each accounted for; eleven of twelve defects planted in `MenuBar` fail it (the twelfth
  is equivalent on today's menus), and so do the three planted later (jc-17's listener order, a
  second chooser, a level read after Open tws's pick). Suite
  2,376 assertions, 54 pins. Coverage re-baselined: the target is unchanged at 57.0%; the non-target
  pool is 52/1941.

## jc-27 — 2026-11-09 (the fix layer: trap Fix 3; all four trap fixes back)

The fourth and last trap fix goes back on the upstream baseline. It closes UB-4 in
[`BASELINE-ISSUES.md`](BASELINE-ISSUES.md); every trap issue the baseline brought back (UB-1 to
UB-5) is now fixed. No stored solution changes.

### Fixed
- **Lynx `isTrapOpen` answers shut on a position no brown button is wired to (`dc3bb11`, UB-4)**,
  as MS always did, instead of throwing a `NullPointerException` from a null list (jc-15's Fix 3,
  re-applied alone). Unreachable in play: Lynx springs traps through
  `LynxCreatureList.springTrappedCreature`, and the method's one caller, `BrownButton.isOpen`, is
  called by nothing in the jar. Corpus against jc-26: 24,212 of 24,212 identical.

### Changed (tests only)
- `ConnectionTest` §4's `upstreamBug` pin is a check again, plus a check that a wired trap with
  nothing on its buttons is shut, and under Lynx four that it is open with a block or Chip resting
  on either of its buttons, as Tile World's `lxlogic.c` springs a Lynx trap (the path past the guard
  and the loop's open answer, which no test reached; four defects planted in that loop each fail
  them, whatever order the trap's buttons are listed in). On jc-26's jar exactly the converted
  check fails. Suite 2,340 assertions, 84 pins. Coverage re-baselined: target 57.0% (1523/2670), `game\Lynx\**` 63.5%
  (466/734).

## jc-26 — 2026-11-09 (the fix layer: trap Fix 4)

The third trap fix goes back on the upstream baseline. It closes UB-5 in
[`BASELINE-ISSUES.md`](BASELINE-ISSUES.md). No stored solution changes.

### Fixed
- **A trap's savestate bit is its first wire's record index again (`d974875`, UB-5).**
  `LevelFactory.getTrapIndexMap` numbers each trap by the record of the first wire into it, and
  `MSLevel` takes that map instead of numbering traps in HashMap order (jc-15's Fix 4, re-applied
  alone). The number is part of the `.ser` file "Save all states to disk" writes, so it must not
  depend on hashing: `.ser` files from jc-15 to jc-18 load right again, and those from jc-14 and
  earlier wherever each trap has one wire (on a trap with two or more, one saved while only a later
  wire's button held it loads it shut, the cost documented since jc-15); a state played in jc-26
  and saved loads right in all of them (one loaded from another build's file keeps that file's
  extra bits and saves them back, as jc-15 to jc-18 did). ⚠ The other cost, the same problem in reverse: a `.ser` saved by jc-19 to
  jc-25 can load with a trap wrongly open or shut, on a level with two or more traps (UB-29). The
  maintainer's Dropbox holds no `.ser` file at all (searched 2026-11-09). Corpus against jc-25:
  24,212 of 24,212 identical.

### Changed (tests only)
- `MsCloneTrapTest` §10's 3 `upstreamBug` pins are checks again, and a new check pins the jc-14
  two-wire cost and tells the numberings apart; on jc-25's jar exactly those 4 checks fail. Suite 2,334 assertions, 85 pins. Coverage re-baselined: target unchanged at 56.9% (1517/2668);
  two branches moved from `MSLevel` to `LevelFactory` (`game\MS\**` 558/1170, `io\**` 313/423).

## jc-25 — 2026-11-09 (the fix layer: trap Fix 2)

The second trap fix goes back on the upstream baseline. It closes UB-3 in
[`BASELINE-ISSUES.md`](BASELINE-ISSUES.md). No stored solution changes.

### Fixed
- **An empty trap with two or more buttons, one held, is open whatever the hash order (`be0d0fb`,
  UB-3).** `MSLevel.finaliseTraps` decides each trap once, after all its wires: open if any wire
  into it comes from a held button (jc-15's Fix 2, re-applied alone) — SuperCC's own rule from
  before upstream's rework. Upstream decided wire by wire, so the last wire a hash map visited won.
  The same update also shut, every time, a trap wired twice from one held button; that comes out
  open now too (review; measured). A button wired to several traps still counts for its first one
  only, as in every SuperCC and in Tile World's `springtrap()`. Latent: a creature entering a trap
  re-checks its buttons, and the engine's other reads are of occupied traps, so no play depended on
  it; the wrong bit reached only savestates, in memory and on disk. Corpus against jc-24: 24,212 of
  24,212 identical.

### Changed (tests only)
- `MsCloneTrapTest` §7's 3 `upstreamBug` pins are checks again, and a new check covers the trap
  wired twice; on jc-24's jar exactly those 4 fail. Suite 2,333 assertions, 88 pins. Coverage
  re-baselined: target 56.8% → 56.9% (1517/2668), `game\MS\**` 560/1172.

## jc-24 — 2026-11-09 (the fix layer begins: trap Fix 1)

The upstream baseline is done: the fork is the latest official SuperCC, 2.0.3, plus its own
pre-merge changes, as checked against the official build itself. Fixes now go back on top of it one
at a time (FORK.md, *The fix layer*). The first closes UB-1 and UB-2 in
[`BASELINE-ISSUES.md`](BASELINE-ISSUES.md).

### Fixed
- **MS levels with a trap that starts with Chip, a block or an ice block on it work again
  (`0cd4140`).** `MSLevel`'s constructor builds its trap maps before it presses those traps (jc-15's
  Fix 1, re-applied alone). With two or more buttons wired to such a trap the level would not open
  (UB-1); with one, the trap started shut (UB-2). Upstream's trap numbering is unchanged; Fixes 2 to 4
  stay reverted (UB-3 to UB-5).

  Corpus against jc-23: exactly 13 of 24,212 stored solutions change, the 13 the jc-19 revert broke:
  5 levels open again (EEfor5subs #16, IsaacD1-Old #116, JRB_CCLP1 #18, TCCLP #110, TomP3 #66; 4 of
  their routes reach the exit), 6 solved routes reach the exit again (C1059-1 #47, EEfor5subs #26,
  geodave1 #52, geodave4 #7, IsaacD1-Old #121, TLFC3 #89), and 2 unsolved routes end differently from jc-23, back where jc-18
  had them (MO1 #61, TCCLPpgchip #2). Against jc-18, which had all four trap fixes: 24,212 of 24,212 identical.

### Changed (tests only)
- `MsCloneTrapTest` §6's 9 `upstreamBug` pins are ordinary checks again, its position checks compare
  Chip's whole position, and new fixtures cover an ice block on a trap (one and two wires — the part
  of the press condition nothing had run; SuperCC's own design, since the MS game has no ice
  block). On jc-23's jar exactly those 14 checks fail. Suite 2,332 assertions, 91 pins. Coverage
  re-baselined: target 56.5% → 56.8% (1513/2664), `game\MS\**` 556/1168.

## jc-23 — 2026-11-09 (the upstream baseline, step 6: auto-diagonals)

Upstream's auto-diagonals (`5f928e0`, `fb8fe25`, `da35e33`, `8c95b3e`) go back to upstream's code
exactly, the last of the five merge deviations. Seven problems come back, UB-22 to UB-28 in
[`BASELINE-ISSUES.md`](BASELINE-ISSUES.md), all of them only with Tools › Controls › Auto diagonals
on. It is off by default, and with it off every key acts at once, as before.

### Changed — deliberately, to match upstream
- **jc-18's rewrite of the key handling is reverted (`d08e6ef`).** `EmulatorKeyListener` and
  `ControlGUI` are upstream's byte for byte, `DiagonalCombiner` is gone, and `SuccPaths.Controls`,
  `getControls` and `setControls` are upstream's, in the fork's own settings structure. With the
  switch on:
  - **every game key waits 50 ms** (the arrows, the diagonal keys, both waits, rewind, play), so a
    **tap makes no move** (UB-22);
  - **keys inside one window make ONE move**, from those still held: a held key runs at about half
    speed, a wait pressed with two arrows is lost, three arrows go by the order of upstream's table,
    and Up held with Right tapped makes Up (UB-23);
  - **MS waits too**, and Up held with Right pressed moves only Up (UB-24);
  - **other input jumps ahead** of a waiting move: a state saved with Shift+digit misses it (UB-25);
  - **the move is made on a `java.util.Timer`'s own thread**, beside the event thread (UB-26);
  - **a key let go while the main window does not have the keyboard stays held** (UB-27).
- **`AutoDiagonals` is read with `Boolean.parseBoolean` and written back raw**, between `UpRight`
  and `Rewind` as upstream writes it: `1` is OFF now, and stays `1` in the file until Tools ›
  Controls next saves that section (UB-28).

  Every stored solution replays exactly as on jc-22 (24,212 of 24,212). README section 6 describes
  the setting as it now behaves.

### Added (tests only)
- `GuiWindowTest` §6 rewritten against upstream's listener in a real window: 12 pins, with the keys
  held across upstream's window where it decides from them, each also measured at 1 and 2 CPUs.
  `SettingsTest` §14 pins how the switch reads and is written back. `AutoDiagonalTest` is removed
  with `DiagonalCombiner`; re-applying the rewrite brings both back.
- Coverage re-baselined: target unchanged at 56.5% (1502/2660); `emulator\**` 170/502 → 134/444.

## jc-22 — 2026-11-09 (the upstream baseline, step 5: custom tilesets)

Upstream's custom tilesets (`d78f6f3`) go back to upstream's code exactly. Six problems come back,
UB-16 to UB-21 in [`BASELINE-ISSUES.md`](BASELINE-ISSUES.md). Nobody's tileset changes by itself:
each one starts with choosing a custom tileset, or with a settings file naming a tileset that
cannot be drawn.

### Changed — deliberately, to match upstream
- **jc-18's fixes to upstream's custom tilesets are reverted (`5d3f0a3`).** `TileSheet`,
  `SmallGamePanel` and `Gui` are upstream's byte for byte; `MenuBar`'s View › Tileset listener and
  `SuperCC.initialiseTilesheet` are upstream's; `SuccPaths` stores the custom tileset as upstream <!-- aen-exempt: upstream method name -->
  does, in the fork's own settings structure:
  - **custom is saved as tileset number 5** (`TilesheetNum` / `LynxTilesheetNum`), which builds
    before jc-18 cannot start with; the `CustomTileset` / `LynxCustomTileset` switches are gone
    (UB-16);
  - **its pictures are saved as full paths**, which the other computer does not have (UB-17), and
    read back with upstream's getter exactly: the pair whenever both keys exist, which in this
    fork's settings (absent keys seeded empty) is always, so "none chosen" reaches the image reader;
  - **canceling asks for the second picture anyway, then shows an error**, and the View › Tileset
    mark stays on custom (UB-18);
  - **a picture is saved before it is known to load**, and bad ones fail with internal errors or
    messages that name no file; the 32-million-pixel limit is gone (UB-19);
  - **SuperCC does not start — no window — when the saved MS tileset cannot be drawn** (a number
    this version does not have, or custom with its pictures missing); measured by launching (UB-20);
  - **a Lynx tileset that cannot be drawn fails at every Lynx set opened**, keeping the previous
    tileset, and a failing custom tileset at a window resize leaves the drawing at the old size
    (UB-21).

  Every stored solution replays exactly as on jc-21 (24,212 of 24,212). README section 6 describes
  the settings as they now behave.

### Added (tests only)
- `EngineTest` §6f rewritten through upstream's `TileSheet.getTileSheets`, with the settings written
  as a file: six exact pins on what each bad picture does. `SettingsTest` §13 pins how the pictures
  are stored and how none or half chosen read. `GuiWindowTest` closes every dialog custom opens and
  pins them, pins the radio mark, and answers custom's file choosers with real pictures in a real
  window -- with no level open (§2) and with one (a new §5b).
- Coverage re-baselined: target 56.6% → 56.5% (1502/2660), `io\**` 311/421, `emulator\**` 170/502,
  `tools\**` + `graphics\**` 52/1919.

## jc-21 — 2026-11-08 (the upstream baseline, step 4: the no-level-loaded guards)

The guards for the state every launch starts in, no level set open, go back to upstream's `72476bc`
exactly. Five problems come back, UB-11 to UB-15 in [`BASELINE-ISSUES.md`](BASELINE-ISSUES.md).

### Changed — deliberately, to match upstream
- **jc-17's extensions of `72476bc` are reverted (`c57f217`)**, and the two upstream lines jc-17 left
  out are in:
  - `MenuBar.requireALevel()` and its exemptions are gone, with the Copy macro hover check and
    `ChooseTileSize`'s removed line: **with no level set open, 29 menu controls fail** with a
    `NullPointerException` on the event thread (UB-11). Upstream's six Level-menu guards, and its
    guards in the key listener and main window, stay.
  - **Canceling Go to... or Set RNG Seed fails** again (UB-12).
  - `Objects.requireNonNull(openFile(...))` is in, so **canceling the file chooser** of Solution ›
    Open, Search for seeds or Load states **fails** again (UB-13); jc-12's message for a file that is
    not there stays. `assert tws != null` is in too (inert: assertions are off).
  - `openLevelset` and `loadLevel` are jc-16's again: **a set whose level 1 will not load opens
    badly** (UB-14; none of the 331 sets in the collection) and **a level whose savestates cannot be
    built is half-loaded** (UB-15; no level reaches it).

  Every stored solution replays exactly as on jc-20 (24,212 of 24,212). Kept, as the fork's own and
  not part of this step: jc-9's AlwaysOpenInMS, jc-11's guards, jc-14's window title, jc-10 and
  jc-12's file handling, and jc-17's untitled-level fix.

### Added (tests only)
- `GuiWindowTest`'s child reports PIN lines (what it saw, upstream's answer, the correct one), judged
  by `Harness.upstreamBug`; a new check pins Cancel on Solution › Open (UB-13). `EngineTest` §6c/§6d
  pin the open, and no longer throw out of the section on upstream's missing level. Solution › Paste
  solution is not clicked with no level open: on upstream's menus it reads the clipboard first, so
  its outcome depended on what the clipboard held (CI caught it, `7ba0d45`).
- Coverage re-baselined: `emulator\**` 36.0% → 33.6% (170/506), the step-4 code gone; the target is
  unchanged at 56.6%.

## jc-20 — 2026-11-08 (the upstream baseline, steps 2 and 3: the move recorder and Seed Search)

Two more deviations go back to upstream v2.0.3 exactly. Every problem the baseline brings back is now
tracked in [`BASELINE-ISSUES.md`](BASELINE-ISSUES.md) (UB-1 to UB-10), so that each can be fixed later.

### Changed — deliberately, to match upstream
- **Seed Search is upstream's again: the fork's four fixes from jc-16 are reverted (`397c7bb`).** A
  negative start no longer shares the work (one thread searches ~3.76 billion seeds; UB-7), the
  threads race at the start so "running" can go false mid-search and a Pause can be undone (UB-8),
  a success at a negative seed is never shown (UB-9), and the Pause/running flags are not `volatile`
  (UB-10, not observable). Measured in a scripted playtest in the real window: from start -5 on four
  threads, three threads never moved and the fourth did all 219,765 attempts, none shown as the
  example. The engine is untouched. `SeedSearch` is form-bound, so its class was compiled with
  JetBrains' form compiler and byte-checked.
- **`SuperCC.tick` records a discarded key as a wait exactly as upstream does (`923d07c`).** The
  fork's fix from jc-15, keeping the key when the engine also reports automatic ticks, is reverted.
  No engine path reports both, so nothing a user can see changes: on all 24,212 stored solutions,
  both where each route ends and the move list SuperCC records are identical to jc-19 (a census
  whose control, jc-14 against jc-19, finds step 2's 40 re-recorded Lynx routes). The difference
  shows only through a level forced to report both bits, which `InputDiscardTest` §5 now pins at
  upstream's recording. It changes nothing a user can observe, so it rode with this release rather
  than getting its own (UB-6).

### Added (tests and documentation only)
- `BASELINE-ISSUES.md`: every problem the baseline brought back, with what a user sees, what was
  measured, the fix, and the tests that pin it.
- `SeedSearchTest` pins upstream's answers. Two of its bugs are races, so the pins were measured
  under CPU affinity (1, 2, 3, 4 and 28 CPUs): the 8-seed check runs five searches, the 3,000-seed
  "running went false once" check is skipped on this code, and section 3 checks the threads
  themselves instead of `running`. A new section 5 presses Pause on a search from seed 0, where no
  thread can finish early, so Pause stays covered on both codes.
- Coverage re-baselined: `emulator\**` 36.3% → 36.0% (191/530), `tools\**` + `graphics\**`
  88/2025 → 86/2023.

### Fixed (tooling only)
- `formclass.ps1 -SelfTest`'s "the byte check FAILS a class built by the transplant" relied on the
  fork's SeedSearch happening to compile to different bytes under the transplant and JetBrains'
  compiler; upstream's does not, so the case went red. Its specimen's constructor now opens with code
  whose stack-map frames are known to differ (`821066c`).

## jc-19 — 2026-11-08 (the upstream baseline, step 1: the trap code is upstream's exactly)

The fork is being returned to upstream v2.0.3 **exactly**, one deviation at a time, before fixes are
layered back on top (FORK.md, *The upstream baseline*). This release is the first step, and it
deliberately changes how 13 of the 24,212 stored solutions in the maintainer's collection end.

### Changed — deliberately, to match upstream
- **The trap rework is upstream's again: the fork's four fixes to `858dbd0` are reverted.**
  Upstream presses a trap that starts with Chip, a block or an ice block on it before its trap
  wiring exists, so **5 levels no longer open** (EEfor5subs #16, IsaacD1-Old #116, JRB_CCLP1 #18,
  TCCLP #110, TomP3 #66), **6 solved routes no longer reach the exit** (C1059-1 #47, EEfor5subs #26,
  geodave1 #52, geodave4 #7, IsaacD1-Old #121, TLFC3 #89) and 2 unsolved routes end differently
  (MO1 #61, TCCLPpgchip #2) — measured on the whole collection, 24,212 solutions. Also back to
  upstream: trap state that depends on hash order, Lynx `isTrapOpen` throwing on an unwired cell,
  and trap savestate bits numbered by hash order. The fixes are one `git revert 79b0032` away, and their
  tests are kept as `Harness.upstreamBug` pins: each passes only on upstream's own measured answer,
  so it fails the moment a fix returns, and on any other change too.

### Added (tests only)
- `Harness.upstreamBug(what, actual, upstream, correct)`, the upstream baseline's pin, and its yes/no
  form. A pinned pass carries its answers in the JUnit XML (`<system-out>`) and JSON results, so the
  reports can tell a pin from a check.

## jc-18 — 2026-11-07 (upstream merge, steps 5 and 6: level with upstream v2.0.3)

### Added
- **Custom tilesets (upstream `d78f6f3`, upstream merge step 5).** View › Tileset › custom takes a
  base and an overlay image of your own; `[Graphics] CustomBaseImage` / `CustomOverlayImage` hold
  them. Fork changes: paths stored relative to the Chip's Challenge folder; Cancel is a no-op and
  the radio mark returns; both images load before anything is saved, in one write; every unusable
  image is reported in words, and one over 32 million pixels is refused from its header, before it
  is decoded (a 46000 x 46000 header asked ImageIO for about 8 GB); and a saved tileset that cannot
  be drawn falls back with the reason, once a session — custom images gone, to the ruleset's own
  built-in choice; a number out of range (which used to stop startup), to Tile World (CCEdit).
  **Custom is stored as its own switch per ruleset (`CustomTileset`, `LynxCustomTileset`), never as
  tileset number 5**: `TilesheetNum` keeps a built-in sheet, so a build before jc-18 still starts on
  the shared settings file (it threw at startup on 5 — two reviews; measured by running jc-17 on a
  jc-18 file). Upstream's labeled file filters come too.
- **Auto-diagonals (upstream `5f928e0`, `fb8fe25`, `da35e33`, upstream merge step 6).** Tools ›
  Controls › Auto diagonals (`[Controls] AutoDiagonals`, default off): arrows pressed together make
  a diagonal. The Controls window is upstream's verbatim, its `.form` edit compiled by JetBrains'
  form compiler. The key handling is rewritten: upstream ticked the game from a background thread,
  let other input overtake a waiting arrow (so the recorded moves differed from what was typed),
  lost taps, chose by table order, and ate keys under MS. Now an event-thread timer, a flush of the
  waiting arrow before any other key, click, drag, wheel turn or focus loss, Tile World's
  newest-per-axis rule (`emulator/DiagonalCombiner`), no waiting under MS, and — from the
  failure-mode review, reproduced in the real window — an auto-repeat of a held arrow makes its
  move at once (joining the window had halved a held arrow's speed and turned a run-then-turn into
  a diagonal). `AutoDiagonalTest` (50) and `GuiWindowTest` §6; six mutants each caught.
- **`formclass.ps1 -Compile` and `-DownloadCompiler`: a `.form` edit no longer needs IntelliJ**
  (ADR 0011). JetBrains' own form compiler (`java-gui-forms-compiler` 211.7628.21, six jars pinned
  by SHA-256, cached in `%LOCALAPPDATA%`, never shipped), driven as IntelliJ's IDE build drives it by
  `formclass/FormCompile.java`. From unchanged sources it reproduces all fifteen IntelliJ-built form
  classes and their 22 inner classes byte for byte. `-Rebuild` is now an alias of `-Compile`.
- **`formclass.ps1 -Check` compares bytes**, from the committed `.java` and `.form`, and is the
  first check that can see a `.form` edit nobody compiled. CI and the release workflow download the
  compiler fresh and pass `-RequireCompiler`, so a missing compiler fails rather than falling back.
  `-SelfTest` grows to 22: the byte check catches an uncompiled `.form` edit and a transplant-built
  class, and `-Compile`'s lint refuses a constructor IntelliJ's own compiler misplaces.

### Changed
- `tools/SeedSearch`, `graphics/Gui` and `tools/ChooseTileSize` recompiled with the real compiler.
  The transplant (ADR 0010) had built them with javac's stack-map frames; code and strings are
  identical to jc-17's, and only frames and constant-pool layout differ.
- `docs/THIRD_PARTY.md`: the vendored `com/intellij/uiDesigner/core` runtime is pinned at last —
  byte-identical to `java-gui-forms-rt` 211.7628.21 from JetBrains' repository.
- **The release review's tooling findings, fixed:** `-SelfTest` now runs `-Check`'s own byte
  comparison (`Test-CompiledBytes`) rather than a copy of it — a broken gate had self-tested green;
  each run copies the compiler jars into its own folder and hashes and uses the copies, with the
  repo's driver first on the classpath (a jar swapped after the hash had run); `-DownloadCompiler`
  refuses without `LOCALAPPDATA` and no longer says "ok" after a failed move; the release workflow
  runs `-SelfTest` too; `verify-splice.ps1`'s messages point to `formclass.ps1 -Compile`, not
  IntelliJ. A reviewer's accidental write to the checkout (a planted `.form` label, reverted within
  two minutes) was caught unprompted by both `-Check -RequireCompiler` and `verify-splice.ps1`.
- Suite: 2,365 assertions in 26 classes (new: `AutoDiagonalTest`). Coverage re-baselined on JDK
  16.0.2: target 56.8% (1525/2686), `io\**` 74.0%, `emulator\**` 36.3%; the engine's figures are
  unchanged. Corpus identical to jc-17 on all 24,212 stored solutions after each step.

## jc-17 — 2026-11-07 (upstream merge, step 4)

### Fixed
- **Levels with no title open** (fork, `MOD (Jeremy, jc-17)` in `LevelFactory.makeLevel`). Title is
  optional field 3; without it the title was `null`, and `SavestateManager`'s constructor
  (`getTitle().getBytes()`), `getJSONPath()` and Load states all dereferenced it, so such a level
  could never be opened. Scanning every level of the collection through `loadLevel` found exactly two
  that failed, of 23,476 in 331 sets: MO3 #12 and #14, both for this reason. A missing title is now
  `""`, as in Tile World (`series.c` zero-fills each record, and only field 3 writes the name). All
  23,476 now load. A titled level is untouched: the corpus is identical, 24,212 of 24,212.
- **The Black and White tileset loads** (upstream `f701c85`). The jar held
  `resources/BW-overlay.png` while `TileSheet` asks for `bw-overlay.png`; jar entries are
  case-sensitive, so View › Tileset › MSCC (Black and White - Editor) threw `NullPointerException`
  in every release (measured on the deployed jc-16; the other four loaded). The file is renamed;
  `EngineTest` §6e loads every tileset from the built jar.
- **View › Tileset works with no level open** (fork, after the second `/code-review`). It was the one
  menu `requireALevel()` skipped while its radio mark still moved, so the menu showed a tileset
  that was neither drawn nor saved. It now applies the choice and saves it as the MS tileset, the
  one drawn at startup.
- **Two fixes in `openLevelset`'s new message** (same review). When no level loads, the previous
  set is put back BEFORE the error dialog, not after: the dialog's nested event loop ran with the
  new set's file under the old level (`GuiWindowTest` reads the open set from inside that loop). And
  "Opened at level N" names the landing level's POSITION, not its record number, which the format
  lets differ (`EngineTest` §6d).
- **No-level-loaded guards (upstream `72476bc`, hand-merged).** With no level set open — how every
  launch starts — these threw `NullPointerException` on the event thread, each one written to
  jc-11's error log:
  - Level › Restart, Next, Previous, Set RNG Seed, Change ruleset, Change Initial RFF Direction;
  - a key press;
  - the play button and the speed slider;
  - `Gui.repaint(boolean)`.

  All measured on jc-16 by the new `GuiWindowTest`. The maintainer's own desktop error log holds
  Restart (twice) and Next/Previous from real use.
  - `EmulatorKeyListener` and `Gui` take upstream's change verbatim. `EmulatorKeyListener` is now
    spliced; `Gui` was rebuilt with `formclass.ps1`, its first rebuild of the main window.
  - `MenuBar` is hand-merged. Skipped: upstream's `Objects.requireNonNull(openFile(…))`, which
    would undo jc-12's cancel fix, and its `assert tws != null`, which the fork already handles.
- **Every menu, not just the Level menu: one guard for the whole bar** (fork,
  `MOD (Jeremy, upstream merge)`, after `/code-review`). Clicking every button in every menu with no
  level open showed how much upstream's guards missed:
  - **on jc-16:** 46 of the 56 menu controls threw;
  - **after upstream's six guards:** 26 still did — most of Solution (Ctrl+S included), TWS, View ›
    Tileset, Tools › Variation testing and TSP Solver, and every cheat; and hovering over Copy macro
    moves threw.

  `MenuBar.requireALevel()` now wraps every menu button's action listeners, so the button does
  nothing until a level is loaded. The exceptions are controls marked `worksWithoutALevel(…)` where
  they are made: Open levelset, Open tws, the tile sizes, the window sizes, the View toggles
  and Tools › Controls. A new item is covered without anyone having to remember. Copy macro moves'
  menu listener checks for itself.
- **Canceling Go to… or Set RNG Seed no longer throws** (fork): the reply is `null` on Cancel, and
  the code tested only for empty.
- **A level that fails to load no longer leaves a half-loaded state** (fork, from the hostile
  review). `SuperCC.loadLevel` assigned the level before building its savestates, whose constructor
  threw for a title-less level (MO3 #12 and #14, which now load — above). That left a level loaded
  with no savestates, or with the previous level's: measured, an untitled level 1 after level 2 ran
  against level 2's. The tests now use a level whose record does not parse
  (`DatBuilder.unreadable()`), the kind of failure that remains.
  Now both are built, then both assigned, or neither. The hostile review's re-check then found two
  callers that relied on the old way:
  - **`openLevelset`.** A set whose level 1 failed kept the previous set's level under the new set's
    file, so `getJSONPath()` pointed into the new set's save folder. Restoring the previous set
    instead closed off the whole new set, and turned every save in such a pack into an OPENFAIL in
    the corpus harness. It now opens the set at its first level that loads, with ONE message
    ("Levels 1 to 3 could not be loaded: … Opened at level 4."), not one dialog per bad level.
    `loadLevel`'s body moved into `tryLoadLevel`, which returns the failure instead of reporting
    it; `loadLevel` still reports it exactly as before. Only a set where nothing loads leaves the
    previous set open ("No level in … could be loaded"), which is jc-11's rule for a file that does
    not read. A fresh open no longer throws. A failure counts only when the level did not change:
    the repaint and title run after the level is assigned, and with the repaint made to throw, a
    good set reported "Level 1 could not be loaded … Opened at level 1." (review; reproduced). That
    one is now logged, not shown.
  - **`trace/TraceLevel`.** It traced the previous level, labeled as the requested one, with exit 0.
    It now exits 2.

  `EngineTest` §6c and `GuiWindowTest` §3a cover both.
- **TWS › Open tws and View › Tile size › custom work with no level open.** Open tws only chooses a
  file. Custom tile size failed only because of a dead `getLevel().getKeys()` line in
  `ChooseTileSize`, now removed (the class rebuilt with `formclass.ps1`).

### Added
- **`GuiWindowTest`**: the main window, built for real. `graphics.Gui` IS the `JFrame`, so nothing
  headless reaches its setup. The test launches a non-headless child JVM that runs `new SuperCC()`,
  then checks:
  - every component `Gui.form` binds exists, and the content pane is the form's root;
  - with no level open, EVERY button in every menu (found by walking the menu bar, so a later item
    is covered too), every submenu hovered, a key press, the play button, the speed slider and a
    full repaint each do nothing rather than throw;
  - the level set then opens with no error dialog, and the full repaint works;
  - canceling both input dialogs works, closed by a helper thread as the close box would;
  - sets that fail to open show exactly one error each, counted by a closer that stays on for the
    whole open, and a load whose repaint throws is not reported as failed;
  - nothing reaches the error log except those expected failures.

  Where no desktop exists, it skips. It fails 28 times on a build with only upstream's guards.

### Changed
- **`EngineTest` §7 works.** The local-only collection check read one level past the end of every
  set (`lastLevel()` is the count plus one) and failed on all 331 sets when run on the collection
  (2026-11-04). It now passes on all 331 (23,476 levels), and fails on an empty folder.
- Coverage re-baselined on JDK 16.0.2. The target is unchanged at 56.5% (1507/2668); `emulator\**`
  is 35.7% (157/440, up from 136/414: `openLevelset`'s new paths, now covered by `EngineTest` §6c
  and §6d), and `tools\**` + `graphics\**` 2.5% (49/1933, the new guard code is reached only in
  `GuiWindowTest`'s child JVM, which JaCoCo does not instrument). Suite: 2,249 assertions, 25 classes
  (2,232 at the first jc-17 commit; the second review's fixes added 17, each failing on that commit).
- jc-11's startup-repaint comment in `SuperCC.java` no longer says Gui cannot be recompiled.
  `FormClassTest`'s Gui skip now points to `GuiWindowTest`.

Engine untouched (see FORK.md for the jar comparison).

## jc-16 — 2026-11-03 (upstream merge, step 3)

### Fixed
- **Seed search ends at the last seed (upstream `40495dd`).** The last thread's range ended at
  `Integer.MAX_VALUE` in an `int`, so the next seed wrapped to `Integer.MIN_VALUE` and the search
  never stopped, retrying masked-equal seeds and counting them again; the totals were `int`s too.
  Seeds, ranges and totals are `long` now. Measured on jc-15: asked for the last 3,000 seeds, 12.3
  million attempts after 8.4 s and still running; jc-16 makes exactly 3,000 and stops.
- **A negative starting seed splits the work evenly** (fork fix, `MOD (Jeremy, upstream merge)`):
  upstream kept `Integer.MAX_VALUE - start` in `int` arithmetic, so start −5 gave three of four
  threads an empty range, and the search reported itself stopped while the fourth ran on.
- **Search threads are built, then started** (fork fix, found by `/code-review` and reproduced).
  Upstream started each thread as it was built, and a short range finishes faster than the next
  thread is built. So an early thread ran the "last one cleans up" block mid-search: `running`
  went false once per thread in 3 of 5 runs of a 3,000-seed search. A Pause click then started a
  second set of threads over the same seeds. Now `numAlive` counts every thread before any starts;
  `running`/`killFlag` are set once, before the start, and are `volatile`; and `run()` no longer
  resets `killFlag`, which had undone an early Pause. Result: false exactly once in 8 of 8 runs.
  The threads are counted at the start, not in their constructor: if building a later one threw,
  counted ones would never run. And the cleanup clears `killFlag` before it publishes
  `running = false`. Both of those came from a second `/code-review`.
- **A success at a negative seed is shown** (fork fix): "no success yet" was `-1`, tested as `>= 0`,
  which hid it. It is `Long.MIN_VALUE` now.

### Added
- **`formclass.ps1` + `formclass/FormTransplant.java` (ADR 0010).** `SeedSearch` is one of the
  fifteen form-based classes the splice build cannot compile. This rebuilds one whose `.form` is
  unchanged: javac's class, plus the members IntelliJ generated (copied from the committed class),
  plus the constructor call placed by a line-for-line port of IntelliJ's `FormConstructorVisitor`.
  - **Proven:** from unchanged source it matches IntelliJ's originals instruction for instruction —
    all 37 classes, four top-level ones byte-identical. The rest differ only in pool numbering and
    stack-map frames.
  - **Refuses** what would break the window: a static or final bound field; a called method changed
    between static and instance; a call before the real `super(…)`; a `new <ThisClass>()` or a
    bound read before the call; a loop, switch, throw or try block before the call; a second class
    in the `.java`; and more. A `this(…)` constructor is always left alone.
  - **`-Check`** (CI and release) fails a form `.java` edited without a rebuild.
  - **`-SelfTest`** (CI) makes 18 checks on 17 specimens.
  - **JDK 16.0.2 only**, by design.
  - **Reviews:** two `/code-review` passes and two hostile passes found ten defects, each
    reproduced, then fixed:
    - **the rule was not IntelliJ's:** the call was put on one branch only, or before
      `new Object()` had been assigned;
    - **accepted, though broken when the window opened:** a final bound field; a called method made
      static; an exception that skips setup; a `new` inside `super(…)` (failed verification); a
      dropped second class;
    - **IntelliJ's own flag is wrong:** a `new <ThisClass>()` before the call (NullPointerException
      on open), and `this(new Object(), 1)` (setup run twice);
    - **`-Rebuild` with a work path over 260 characters** deleted an inner class and still printed
      DONE. It now fails loudly and verifies what it wrote.
- **`FormClassTest`** (67): every form window that can run headless (14 classes, all 17 of their
  constructors) is built through its real constructor, which `-Check` alone cannot vouch for once a
  class has been rebuilt. A constructor it does not run fails it. It also checks that all 15
  classes carry their generated members.
- **`SeedSearchTest`** (25): Seed Search driven headless through a spy `ButtonUI`. The search stops
  at the top seed, `running` goes false once, a negative start splits evenly, the totals pass 2^31,
  and a negative example seed is shown. Six planted defects each caught.

### Changed
- `verify-splice.ps1` now tells a form `.java` edit to use `formclass.ps1 -Rebuild`, and only a `.form`
  edit to use IntelliJ. Its comparison key and canonical-path helper moved to `build-config.ps1`,
  shared with `formclass.ps1`.
  ADRs 0001 and 0002 are marked amended by 0010.
- Docs: AGENTS.md, CONTRIBUTING.md and RELEASING.md describe the form-class route. RELEASING.md and
  CLAUDE.md §8 no longer say only two classes inline `BUILD_TAG`: `ErrorLog` does too.
- Coverage re-baselined on JDK 16.0.2. Target 56.5% (1505/2666: one more MS branch, reached by the
  new tests' seed searches); `emulator\**` 32.9%; `tools\**` + `graphics\**` 2.6%, the first tests
  ever to reach them.

Engine untouched: 7 of the jar's 575 entries differ from the published jc-15 jar —
- `SuperCC.class` and `ErrorLog$Sink.class` (the build tag, which javac inlines; nothing else
  changed in either, per `javap -v`);
- the two edited sources;
- the three `SeedSearch` classes.

No `game/**` class. Suite 2,109 assertions / 24 classes.

## jc-15 — 2026-11-02 (upstream merge, steps 1 and 2)

The first release carrying upstream code newer than the fork's 2021 import. Every stored solution in
the maintainer's collection (24,204) ends identically to the deployed jc-14 jar.

### Changed
- **Lynx: a key the engine ignores is recorded as a wait (upstream `b997c27` + `98366f7`, step 2).**
  `Level.tick` returns two bits instead of a boolean: `MASK_TICK_MULTI` (the old meaning) and
  `MASK_DISCARD_INPUT`. `LynxLevel` sets the second when Chip is between tiles (no move is chosen
  at all) or when choosing finds a forced move (ice without skates, a force floor without boots and
  without the slide token, the tick after a teleport), and `SuperCC.tick` then records `-` in the
  move list in place of the key. That is Tile World's rule: `lxlogic.c`'s `choosechipmove` drops a
  key when `discard` is set, and `play.c` records a move only when one is accepted. `b997c27` also
  made `MSLevel` return the discard bit on every tick without automatic ticks; `98366f7` took that
  back out, because an MS key during a force-floor slide is an override and must be kept — so MS
  records exactly as before — and must: even an MS key on ice, whose move is refused, changes the
  engine's state. Applied verbatim, with one fork fix (`MOD (Jeremy, upstream merge)`): where a
  tick returns BOTH bits, `SuperCC.tick` keeps the key instead of writing `-`, which would have
  recorded one tick where the engine ran four. The automatic-tick bit only comes from a direction
  Chip chose from the key, so the key was used; and a key that was not is ignored again on replay.
  No engine path returns both today; the fix is so a saved route does not depend on that staying
  true. All four files were already spliced. **The trade-off:** the move list now records what happened, not what was typed, so a
  macro or a copy of moves, played from a square where an ignored key would have counted, waits
  where jc-14 moved (README section 7 says so; `InputDiscardTest` §3 pins it).

  **Measured:** corpus identical to the deployed jc-14 jar on all 24,204 solutions; `-CrossCheck`
  agrees on all 24,203 replayable saves. A census of the move list SuperCC records on loading each
  save: 40 of 1,085 Lynx routes record differently (2,500 keys become waits: 1,678 typed between
  tiles, in 30 routes, and 822 on forced moves), no MS route does, and no tick set both bits. All 40,
  saved as the menu saves them and reloaded, end in the same state. Tile World's batch verifier
  accepts both the jc-14 and the jc-15 `.tws` export of the three routes where a key typed between
  tiles is followed only by waits — the shape where Tile World, which keeps an unconsumed key until
  Chip next chooses, looked likely to apply it late. It did not matter in any of the three, so this
  is a cleaner move list, not a fix to old exports. Three review passes (`/code-review`, a hostile
  review of every move-list consumer, a line-by-line Tile World parity review) found no key SuperCC
  reports ignored that it or Tile World would use; their fuzzing, 23,000 random Lynx levels between
  them, replayed every ignored tick with other keys and never changed the outcome.
- **Upstream's trap rework (`858dbd0`, step 1) is merged — with four fixes of the fork's own, and no
  change to any stored replay.** Trap state is now one bit per trap instead of one per wire,
  through a new trap → buttons map (`Level.getTrapButtons()`). Applied from upstream's commit
  verbatim, then fixed (each marked `MOD (Jeremy, upstream merge)` in the source):
  * **Traps that start with Chip or a block on them work again.** Upstream pressed them before its
    trap index existed: with two wires the level would not open (NPE), with one the trap stayed
    shut. On the 24,204-solution collection that was 13 changed replays: 5 levels that would not
    open (4 of them solved) and 8 that ended differently (6 solved routes that stopped solving),
    10 solved routes lost in all. The maps are now built before the press.
  * **An empty trap with one of its buttons held is open, regardless of hash order.** The rework
    let the last wire visited decide; measured shut in 3 of 6 arrangements where jc-14 says open.
    No replay depended on it (entering a trap re-checks its buttons), but the engine's own
    `isTrapOpen` did. Each trap is now decided once, by the old any-button rule (Tile World's too).
  * **A trap's bit is numbered by its first wire's record in the file**, not by `HashMap` order.
    The number is written into `.ser` savestate files, so it must not move with hashing; and it is
    the bit jc-14 used for that wire, which keeps old `.ser` files loading correctly (see below).
  * Lynx `isTrapOpen` on a cell no button is wired to threw; it answers shut, as MS does.

  Kept from upstream and noted, neither reachable in Lynx play: `isTrapOpen` now asks about a
  creature on a wired button rather than one in the trap, and the Cheats menu's *Press button* on a
  multi-wire trap springs its creature once instead of once per wire.

  **Measured:** corpus identical to the deployed jc-14 jar on all 24,204 solutions; `-CrossCheck`
  agrees on all 24,203 replayable saves. Five more `java/game/**` files are now spliced.
  **The one compatibility cost, in README section 7:** a `.ser` saved with *Save all states to
  disk* by jc-14 or earlier loads correctly wherever each trap has one wire; on a trap with two or
  more wires, its state is read from its first wire's bit alone, so it can restore shut when it was
  open (392 of 23,476 levels in the maintainer's collection have such a trap). A `.ser` saved now
  loads correctly in older builds. Lynx states and solution files are unaffected.
- **Coverage re-baselined on JDK 16.0.2** (Temurin 16.0.2+7, what CI and the release workflow pin):
  the target scope is 56.4% (1504/2666), up from 50.3% at jc-14. `coverage.ps1`'s notes no longer say only four `io\**` classes are compiled
  locally: since step 1, 1,544 of the target's 2,666 branches are.

### Added
- **`test/InputDiscardTest.java`** (45 assertions) — step 2's rules, tick by tick: Lynx force floors
  (forced on the first, the slide token freeing Chip on the second), ice with and without skates, a
  teleport, the exit, a start on a force floor (tick 1 is never forced), and keys that bump, which
  are used and never discarded. The safety property itself, on five routes: on every discarded tick,
  all nine keys from the same savestate give the same savestate. What the move list records, the
  recorded list saved, reloaded and replayed to the same square and tick, the macro trade-off, and
  MS never setting the bit — with the force-floor override alone, recorded as a wait, ending on a
  different square, and an MS key on ice changing the savestate. Expected values come from
  `lxlogic.c`, worked out before each first run. And the both-bits rule, through a real level
  wrapped to report both bits. Ten planted defects are each caught, upstream's recorder line among
  them.
- **The fixtures whose absence let the trap bug through** — `MsCloneTrapTest` §6–§10: a block or
  Chip starting on a trap with one wire and with two, an empty trap with one of two buttons held
  (six arrangements, plus a neither-held control), a bare press, one button wired to two traps, a
  shared trap's release, upstream's own regression level for the commit ("Multi Trap", rebuilt
  with `DatBuilder`), and the bit each trap is saved under, with jc-14-style bits loaded back.
  Expected values cite Tile World's `mslogic.c` and were confirmed against jc-14. `ConnectionTest`
  §4 now pins `getTrapButtons` under both rulesets in place of the removed record index. Upstream's
  build fails 12, upstream with only the first fix fails 3, upstream's numbering fails 3 savestate
  checks, and four defects planted in the new code are each caught.
- **`test/LinkTest.java`** — every method and field reference between the fork's own classes,
  read from each class file's constant pool in the BUILT jar, must resolve, as the JVM resolves it
  (including an interface call to a public `Object` method). A splice build recompiles only the
  edited files, so a baseline class still calling a removed method would ship and throw
  `NoSuchMethodError` at the call; `verify-splice.ps1` cannot see that for the fifteen form
  classes. Planted: jc-14's `MSCreature.class` in the new jar is reported by name. It checks names
  and descriptors only, not access or static-ness.

## Unreleased (tooling, shipped alongside jc-15)

### Added
- **`corpus.ps1` + `corpus/CorpusCheck.java`: the corpus replay, committed.** Replays every stored
  solution in a Chip's Challenge folder through one jar and writes each one's final state —
  outcome, tick, timer, chips, Chip's cell, facing and sliding, keys and boots, RNG, and CRC32s of
  both layers, the creature list and the slip list — then `-Compare` diffs two runs and classifies
  every difference (STATUS, LOST THE EXIT, GAINED THE EXIT, FINAL STATE ONLY, plus a "solved routes
  lost in all" total). Exit codes: 0 identical, 1 differences, 2 error or refused, 3 cross-check
  mismatch. Its previous version proved jc-11 replay-neutral from a scratch folder and was lost, as
  the trace harness's once was.

  **Why it earns a place: it caught what the test suite could not.** Measuring upstream's trap
  rework (`858dbd0`) on 2026-11-01, all 1,878 assertions passed while 13 of 24,204 real solutions
  changed: five levels would not open at all (four of them solved), six more solved routes stopped
  reaching the exit, and two unsolved routes ended differently — ten solved routes lost in all —
  because no fixture starts a block or Chip on a wired trap. The cause was one upstream ordering bug
  (the `MSLevel` constructor presses start-occupied traps before its trap index exists); with that
  fixed, the rework was byte-identical across the whole collection. That measurement is the reason
  for the new step in `RELEASING.md`: a release touching `game/**`, `io/**` or `emulator/**` (where
  `tick`, `loadLevel` and `Solution` live) replays the corpus before and after.

  **The ways a naive replay reports a wrong answer as a result, or dies, are each closed — most of
  them found by twenty review passes and reproduced before being fixed:**
  * **A save belongs to the level carrying its number, not necessarily the level at that
    position.** SuperCC names solution files after the number stored in the level's record but
    loads by position; replaying by position would test the moves on the wrong level and print an
    ordinary result under BOTH jars. Each set is indexed once by record number, the save goes to the
    level carrying its number (`pos=N`), and where several levels share a number the filename title
    picks one or the save is `AMBIGUOUS`. They agree in every set in the collection today — a
    cross-check run of all 24,204 saves skipped none for this.
  * **That index is read from the set's bytes, never through the engine under test.** Built with
    `DatParser.parseLevel`, it ran the candidate's level constructor in the SET's ruleset, so a jar
    whose `MSLevel` constructor throws — 858dbd0's exact failure — erased those levels' numbers,
    and their LYNX saves read `LOADFAIL` without ever being loaded (reproduced with that jar; now
    they replay exactly as under jc-14). It agrees with SuperCC's own parser on the number and the
    exact title of all 23,476 levels in the collection (the LAST title field wins, as in
    `DatParser`, and each optional field is stepped over by exactly the bytes `DatParser` reads for
    it, not its stated length). A level whose record runs past the end of the file reads `NOLEVEL` saying its
    number is unknown, instead of a guess.
  * Error lines carry no source line numbers, nor a helpful NPE's local-variable slot (`<local4>`:
    the spliced classes are compiled without `-g`) — both move when the throwing method is edited
    and would make an unchanged failure "differ"; only the first line can be the `#jar` header (a
    pack may be named `#anything`, and an empty line is refused rather than skipped); a truncated
    output file, a missing JDK and a `-Pack` naming no folder are errors (exit 2).
  * **A compare of a jar against itself is refused — and so is one whose jar is unknown.** Each
    output opens with `#jar <sha256>` for the jar the JVM actually loaded, and `-Compare` exits 2 on
    a same-jar pair, or a pair where either side's jar cannot be read, unless `-SameJar`; an
    explicit `-Jar` resolves against the current directory, so "`-Jar SuperCC.jar`" in a release
    folder cannot mean the repo's fresh build.
  * **A change in the collection is never blamed on the engine.** Every line ends with
    `src=<save>:<level>`, CRC32s of the solution file and of the level record its FILENAME points at
    (the whole set when no one record is picked), so a save re-recorded or a pack updated between
    the before and after runs is classified INPUT CHANGED — reported, exit 1, but not counted in
    "solved routes lost in all" — and so is a save present on only one side. The fingerprint is
    worked out from the inputs alone, before the jar touches them: its first version followed the
    STATUS (a save that stopped parsing switched to the whole-set CRC), which would have filed a jar
    that breaks parsing or opening as INPUT CHANGED and out of the headline — caught by the sixth
    review, and the self-test now checks the level fingerprint is identical across runs over one
    set, even for a save whose status changed. `pos=N` is input identity and follows `src=`, so a
    level that only moved within its pack is not a different end state. A solved route lost where
    the input ALSO changed is counted on a line of its own, never silently: nothing can attribute
    it, and the compare says to re-run the baseline jar on the current collection.
  * **So is a change in the harness.** The header records the harness version (`harness=<sha256>`
    of `CorpusCheck.java`, line endings normalized), and `-Compare` refuses a pair written by two
    versions, or an unknown one, unless `-MixedHarness` — measured: counting the fatal move alone
    changed 23,095 lines, every one of which an old-vs-new compare blamed on the engine. It records
    the JDK too (`java=`), and a pair on different JDKs gets a NOTE: no end state depends on it,
    but an exception line quotes the JDK's own wording.
  * **A run cut short can never pass for a whole one.** Every run ends with `#end <count>`, and
    `-Compare` refuses a file without it, or whose count does not match. Before, a run killed
    part-way — as one was here, for low memory — compared as a short collection: measured against
    a killed run's 435 lines, the compare said "solved routes lost in all: 0" and put 22,605
    unreplayed solved routes down to the collection. The header records the scope too (`pack=`):
    a one-pack run against a whole-collection run compares that pack alone, and two different
    single packs are refused. The level fingerprint includes the set's signature, since that alone
    decides whether SuperCC opens it — a re-signed pack is the collection's change.
  * **The jar is replayed from a private copy** taken into the run's temp folder, and the hash
    printed and recorded is the copy's. A run takes minutes, and Windows lets `build.ps1` overwrite
    a jar a live JVM has open, after which late-loaded classes read as garbage (CLAUDE.md section 9,
    measured) — a rebuild mid-run would have shown as engine failures under the old jar's hash. If
    the jar on disk changed during the run, the run says so.
  * The same failure failing differently (an `EXC` that moved, an `OPENFAIL` with a new reason) is
    `STATUS <s>, detail changed`, not FINAL STATE ONLY: a line that is not OK has no end state. A
    path with a trailing backslash and a space — `"...\Chip's Challenge\"`, as tab completion
    writes it — no longer swallows the next argument (PowerShell 5.1 quotes it, and to java the
    `\` escaped that quote; reproduced); the self-test now runs on folders with spaces in their
    names. Only an absolute CC path is masked in messages (a relative `.` would have replaced every
    period). Run outputs go in the gitignored `corpus-results\` in every documented workflow:
    they list a personal collection.
  * In a set cut short, one readable level carrying a save's number is not taken on trust: an
    unreadable record might carry it too, so the title must confirm it or the save is `AMBIGUOUS`.
  * Error messages are stripped of what differs between runs with no engine change — the CC
    folder's own path (`<CC>`; it is a different user folder on each PC) and identity hashes
    (`@<hash>`). The slip-list probe catches only the `UnsupportedOperationException` Lynx throws,
    so an MS engine crash there surfaces as `STATEEXC` rather than passing for `slip=-`. A jar hash
    that is not a well-formed sha256 counts as unknown and is refused, where it used to crash the
    compare and exit 0.
  * The move that ends a route is counted as replayed (MS marks Chip dead on reaching the exit, so
    every solved route read one move short before).
  * `loadLevel` swallows its exceptions (a failed load is detected by the identity of the level AND
    the savestate manager — a title-less level parses but leaves the old savestates behind) and
    WRAPS out-of-range numbers to level 1 (never passed one). `openLevelset` swallows an unreadable
    set, after which the next call threw outside any handler and aborted the whole run — now
    `OPENFAIL` for that pack, with the parser's reason. A number too long for an `int` aborted the
    run too; now `NOLEVEL`. A `.ccl` set used to read as having no set at all.
  * An error and "differences found" no longer share exit 1.

  Verified on the real collection (331 sets, 24,204 solution files), with every version of the
  harness above: the jc-14 build writes a line for every file (24,203 replay, 23,012 of them to the
  exit; one misnamed file reads BADNAME) and matches the deployed jc-14 jar on all of them; the
  cross-check agrees with SuperCC's own replay on all 24,203; upstream's trap rework with the
  ordering fix is identical; and the broken-trap jar is classified exactly as measured (5 status,
  4 of them solved; 6 lost the exit; 2 final state only; 10 solved routes lost in all; 0 filed as
  input changes). The index agrees with SuperCC's parser on all 23,476 levels, and between the
  first and last versions of the harness, the output changed on no line except as intended.
- **Found along the way, NOT fixed here (it is shipped behavior): `Solution.load` reloads by the
  level's stored NUMBER, used as a position** (`Solution.java`, `loadLevel(getLevelNumber(), …)`),
  so in a set where a level's number is not its position, opening its solution in SuperCC replays
  it on the wrong level. No set in the collection has such a level today. `-CrossCheck` has to skip
  those saves, and now says so in a `NOTE` line rather than counting them silently.
- **`-CrossCheck`.** The harness has to COPY `Solution.tickBasicMoves`, which cannot be observed
  from outside; this replays every solution a second time through the real `Solution.load` and
  fails on any final state that differs — a replay that throws included — so the copy is measured
  rather than trusted. Run it after anything touches `Solution.java`.
- **`corpus.ps1 -SelfTest`, and a CI step that runs it.** A synthesized collection
  (`corpus/CorpusFixture.java`, built with `test/DatBuilder.java`) produces every status a real
  collection can — all but the two defensive catch-alls no known input reaches — and checks the
  jar and harness header, the `#end` line, the cross-check, byte-identical reruns, every refusal
  (same jar, unknown jar, mixed harness, a run cut short, different single packs),
  error-vs-difference exit codes, and that REAL engine-level differences are caught and classified
  with every count asserted exactly — and that a re-recorded save is INPUT CHANGED while the same
  end-state differences on unchanged inputs are the engine's. Its fixture folders have spaces in
  their names, as the real one does; its saves are written as SuperCC writes them (with
  `"Encode":"UTF-8"`), plus an older Latin-1 one and a Lynx one, so both decodings and the Lynx
  `bg=-`/`slip=-` lines are checked. Forty-three planted defects in the harness, one per protection,
  each turn it red. Thirteen protections are NOT exercised by it, because proving them would take
  a second build with shifted lines or locals, a second PC, a full disk, no JDK, a jar whose engine
  throws (often, or out of memory), or a rebuild, edit or kill during a run: (1) error lines free of
  line numbers, local slots, paths (the CC folder, and the run's own temp folder) and identity
  hashes; (2) the truncated-output check; (3) the missing-JDK exit code;
  (4) the slip-list probe NOT hiding an MS engine crash (that it still catches what Lynx throws IS
  checked); (5) the index's independence from the engine (proven instead
  with 858dbd0's jar, above); (6) the private jar copy; (7) the harness hash taken from the compiled
  copy; (8) an emulator constructor that throws being one pack's `OPENFAIL`; (9) `-CrossCheck`
  counting a reload whose `SavestateManager` threw as skipped, as `loadPosition` does, rather than
  comparing it; (10) a run that dies leaving an existing output untouched (seen working on a real
  kill: the stopped run left only its `.partial`); (11) a run stopping, refused, when a set
  changes on disk while its saves replay — the collection syncs through Dropbox, and the engine
  re-reads the file at every load, so the fingerprint would describe bytes that were not all
  replayed; (12) the JVM's `-XX:-OmitStackTraceInFastThrow`, without which HotSpot, once it has
  compiled a site that throws often, throws a preallocated exception with no message or stack — so
  the same failure would read differently partway through a run, and two runs of one jar would
  stop matching; and (13) out-of-memory and the VM's internal errors killing the run (no `#end`,
  refused) instead of being written as EXC lines that depend on the heap, not the input.
  The CI step that keeps level sets out of the repo now also refuses a tracked corpus output — in
  `corpus-results\`, or any file whose first line, read through a BOM-aware reader, is the `#jar`
  header (so a copy re-saved with a UTF-8 BOM or as UTF-16 is caught too: a plain `git grep` missed
  both, reproduced) — and fails if `git` itself fails, rather than reporting that it found nothing. The header names the jar the user gave, beside the
  hash of the private copy that ran.
- **A save is decoded exactly as SuperCC's own Open decodes it** (`MenuBar`): UTF-8 only when its
  `"Encode"` field says so, else ISO-8859-1 — the format of older saves, whose click moves are single
  bytes 0xAF–0xFF. Read as UTF-8, most such saves would not decode at all, and a pair like `C3 BF`
  would become ONE different move, replayed without a word; `-CrossCheck` could not see it, since it
  replays the same decoded solution. In the collection today it changes nothing: 1,877 saves have
  no `"Encode"` field, all plain ASCII, and all 66 with other bytes declare UTF-8. The self-test
  pins it with a Latin-1 save. Output is written to `<out>.partial` and moved into place only when
  complete, so a run that fails or is killed cannot destroy a baseline kept at that path; `-Jar`
  must name a file, not a folder; and paths are resolved by PowerShell's own resolver, so a
  drive-relative `C:foo` or root-relative `\foo` means what it means in the console.
- **Nothing that is not a result can become a baseline.** A `-CrossCheck` run that finds its replay
  disagreeing with SuperCC's is left as `.partial`, never moved into place (exit 3), and so is a run
  that could not identify its own jar; `-Out` may not be a folder, nor anywhere in a git working
  tree that git does not ignore, nor whose `.partial` it does not ignore (git itself is asked, so no
  spelling of the path slips past); a stack overflow
  is recorded by its class alone, since the frame it strikes in depends on the JIT; the JVM flags
  that shape the output are part of the harness version; and the pack order no longer depends on
  the machine's locale. After a run, the file at `-Out` must carry the hash of the copy that just
  ran (and the exit code is reset before java starts), so an older file can never be reported as
  this run's; an `"Encode"` that is not a string fails as it does in SuperCC; and the CI guard
  reads tracked paths as UTF-8 and fails on any file it cannot open, instead of passing it. The
  level fingerprint covers every byte SuperCC's parser reads for the level — beyond its stated size
  where a malformed record makes it read on — and a set cut off inside its level count is read as
  `DatParser` reads it (65535 levels, all unreadable), not as an empty set.
- `test/DatBuilder.java`: `Level.number(n)`, to write a level whose record number differs from its
  position — which the format allows and SuperCC's file naming depends on.

### Fixed
- `CLAUDE.md` had an unclosed code fence after the differential-trace section — a stray
  `test/ErrorLogTest.java` line from the test map, followed by a fence that never closed — so on
  GitHub everything from there to the end of the file (the rest of §4 and all of §5–§10) rendered
  as one code block. The line is back in the test map.

## jc-14

### Added
- **The window title is fully switchable: `ShowLevelPack` and `ShowLevelName` join `ShowBuildTag`.**
  The title has always been `SuperCC [jc-N] - <pack> - <level>` with only the tag optional. Both
  remaining halves are now `[Graphics]` switches, shipped as `ShowLevelPack = false` and
  `ShowLevelName = true` — the level name stays on because that is how every build to date looked,
  and the pack is off because that is the default Jeremy asked for.

  **The separators are computed, not templated.** With both halves off the title is bare `SuperCC`;
  with one off there is exactly one ` - `; only with both on are there two. That rule lives in one
  static, pure `SuperCC.composeWindowTitle()` with no settings and no `Level` in sight, so it can be
  walked exhaustively — `WindowTitleTest` takes all eight combinations of the three switches, and
  asserts the separator COUNT as well as the string, because "no extra dashes" is the actual
  requirement and reading it off eight expected strings is not the same as checking it.

  **The two new switches use OPPOSITE predicates, deliberately.** `ShowLevelPack` is strictly opt-in
  like `ShowBuildTag` (default OFF, so a typo leaves it off); `ShowLevelName` is the mirror
  `shownUnlessOptedOut()` (default ON, so only an explicit `false` or `0` hides it). The case that
  separates them is an unrecognized VALUE — a file with no key at all is filled in by
  `seedDefaults()` before either predicate sees it, and the comment in `SuccPaths` says so rather
  than claiming more for the mirror than it earns.

  ⚠ **This changes the stock window title**, which is the one thing here a downloader sees without
  asking for it: `SuperCC - CCLP5 - Lesson Zero` becomes `SuperCC - Lesson Zero`. Anything matching
  a window by its level set — `supercc_driver.ps1` does, and uses the same expression to assert the
  right set is loaded — needs `ShowLevelPack = true` in its own settings file. README section 6 says
  so in the same words.

  Five mutations planted and all five killed: dropping the empty-pack guard, swapping either
  predicate for the other, bypassing the predicate in `render()`, and flipping the headless
  `paths == null` fallback.

### Added (since jc-13, no shipped change — released here)
- **`verify-splice.ps1` check 5: the vendored third-party bytecode is tamper-evident.** `com/` and
  `org/` hold 23 class files with no source anywhere in this repo, and until now nothing watched
  them — checks 1-4 compare `java/**` against its shipped bytecode and these have no `java/**` to
  compare against, while Dependabot covers `github-actions` and cannot cover code with no manifest.
  Hashed against `docs/vendor-baseline.sha256`, regenerated with `-UpdateVendorBaseline`, and
  verified by tampering with one byte of `JSONValue.class` and watching it fail.
- **`docs/THIRD_PARTY.md` now states the versions with evidence instead of disclaiming them.** It
  used to say they "cannot be recovered from the bytecode with confidence" and suggested comparing
  class hashes against candidate jars. That was done: all twelve `org.json.simple` classes are
  **byte-identical to json-simple 1.1.1** from Maven Central and match none of 1.1, and OSV reports
  **no known vulnerabilities** for it as of 2026-09-03. The IntelliJ runtime matches **no** published
  `forms_rt` release (7 checked), consistent with extraction from an IDE install, so that one
  genuinely cannot be pinned — and the file now says so with the evidence behind it. The absence of
  vulnerability alerting is recorded as an accepted risk with the one-line OSV query that stands in
  for it, rather than left as an omission.
- **Repository rulesets**: `jc-*` tags are now immutable (no deletion, no update) and `main` refuses
  force-pushes and deletion. Deliberately NOT requiring pull requests — that would block the
  single-maintainer workflow this repo actually uses, and the goal was safety that costs nothing
  operationally.

### Changed
- **CLAUDE.md §4 documents mutation testing and the trap that makes it lie.** Planting a defect in a
  file that is not in `$SPLICE_MODIFIED` overlays nothing: the suite runs against the committed
  baseline bytecode and **every mutation passes while testing nothing**. That cost real time in this
  session, four separate times, and was written down nowhere. The procedure, the three ways it has
  actually gone wrong, and how to tell a weak test from an equivalent mutant from a build that never
  happened are now in the docs.

### Added (tests)
- `test/MsCloneTrapTest.java`: 29 assertions over the MS clone-machine and beartrap DISPATCH, which
  was the gap MsCreatureListTest left behind. ConnectionTest covers the .dat records and ButtonTest
  covers a press producing a clone and a trap opening; none of them drives a creature through the
  machinery afterwards.

  Covers `addClone`'s "can it discharge" precondition (a cloner facing a wall is inert -- the
  duplicate guard was already tested, this one was not), the block-versus-monster split in how a
  clone is queued, a creature held by a shut trap and released by a brown button, and
  `springTrappedCreature`'s guards.

  **A lifecycle contract worth knowing, found by a fixture that failed:** `addClone` queues into
  `newClones`, and `initialise()` clears that queue at the top of every tick. So a clone queued from  <!-- aen-exempt: upstream method name -->
  OUTSIDE the tick cycle is discarded before `finalise()` ever sees it and never becomes a creature.
  That is not a defect -- in a real level only a creature or Chip standing on the button presses it,
  which happens mid-tick -- but it means the population counted right after a bare `press()`, which
  is what ButtonTest measures, is a transient. Both halves are now pinned.

  Mutation testing found two weaknesses in the first draft. `addClone` has TWO duplicate guards, one
  scanning `list` and one scanning `newClones`, and ButtonTest's fixture only ever reaches the
  second -- deleting the first passed everything until a fixture put a clone in the live list.
  And the shut-trap assertions passed for the wrong reason, because `springTrappedCreature` is inert
  before the clock has run at all.

  One EQUIVALENT MUTANT is recorded rather than chased: deleting `springTrappedCreature`'s
  `!isTrapOpen` check changes no result, because `MSCreature.canLeave` already answers
  `case TRAP -> level.isTrapOpen(position)` and a creature sprung from a shut trap still cannot go
  anywhere. The guard is an early-out in front of a refusal that happens regardless; the behavior it
  stands for is covered by the section that walks a tank through a real tick.

  `MSCreatureList` 41/112 -> 72/112 branches; `game\MS\**` 38.2% -> 42.5%; target scope 48.2% ->
  50.2%. No shipped code changed.

- `test/MsCreatureListTest.java`: 31 assertions over the MS creature list PER TICK. MonsterListTest
  already covered how the list is built; nothing covered what happens to it once the level runs.

  The headline is an ordering contrast that only makes sense as a pair with the Lynx file:
  **MS walks the list FORWARD and Lynx walks it BACKWARD.** The same fixture -- two tanks facing
  each other across one empty square -- gives the square to the FIRST-listed tank here and the
  last-listed one there. Stated as an outcome rather than as a claim about loop syntax, and creature
  order is where desyncs live.

  Also: MS's teeth clock is a different FORMULA, not a different constant
  (`step.isEven() != (tick % 4 == 2)` against Lynx's `((tick - 1 + step.ordinal()) & 4) == 0`); and
  `claimed`, `adjustClaim` and `animationAt` all THROW on MS with "MS does not have a concept of
  claims/animations", while `getClaimedArray` returns empty and `setClaimedArray` is a silent no-op
  -- so the refusal is deliberately NOT uniform, which anything generalizing over `CreatureList`
  needs to know.

  Four planted defects, all caught. The teeth section needed rewriting to get there: deleting the
  gate makes teeth move MORE often, so "it moved eventually" passes either way. It is now measured
  against an ungated creature -- over 24 ticks a tank moves 10 times and teeth and blobs 5 -- which
  is what the gate actually means.

  **Honest note on the ratio:** `MSCreatureList` itself moved only 37/112 -> 41/112 branches. The
  clone-machine and beartrap dispatch (`addClone`, `tickClonedMonster`, `tickTrappedMonster`) is the
  bulk of what remains and none of these fixtures build one. The value here is the ordering
  contrast, not the branch count. Target scope 47.3% -> 48.2%. No shipped code changed.

- `test/MsTryEnterTest.java`: 59 assertions over MS ARRIVAL EFFECTS -- `MSCreature.tryEnter`, 365
  lines and the largest single method in the repo, previously untested. MS splits the question in
  two: `canEnter` answers "is this legal" and CanEnterTest already covers that in 99 assertions;
  `tryEnter` answers "what happens now that you are here", and nothing covered it.

  Most of the value is where MS and Lynx genuinely differ, because MS MUTATES THE MAP where Lynx
  does not: Chip drowning writes `DROWNED_CHIP` and burning writes `BURNED_CHIP` (tiles Lynx has no
  concept of), a block in water leaves `DIRT` but an ICE BLOCK leaves `ICE`, and an ice block pushed
  into fire leaves `WATER` -- after which walking in behind it drowns you, which is asserted as its
  own consequence. That asymmetry is why `CanEnterTest` and `LynxCanEnterTest` disagree about the
  corpse tiles, and a test that looked at one ruleset alone would make either behavior look obvious.

  Also covers the glider's water exemption and the fireball's fire exemption against `mslogic.c`'s
  blunt `if (crid != Glider) dead` / `if (crid != Fireball) dead`, and states explicitly that a bug
  beside fire is alive because it was REFUSED rather than because it survived -- the two look
  identical from outside and only one is right.

  Six planted defects, all caught. Two fixture bugs of my own found by running it: MS builds its
  monster list from the .dat's monster field rather than by scanning the map, unlike Lynx, so the
  monsters needed `.monster(x,y)`; and the block cases need exactly ONE tick, because on the second
  Chip walks onto the square himself and clears the dirt or drowns in the water.

  `game\MS\**` 25.6% -> 36.4%; target scope 42.2% -> 47.3%. No shipped code changed.

- `test/TwsClickTest.java`: 32 assertions covering the .tws CLICK-EXPORT path -- the one jc-2
  fixed, which until now had no test. A click char is an offset into the 9x9 scrolled viewport and
  a .tws stores an offset relative to Chip, so the conversion needs Chip's position AT THE TICK THE
  CLICK WAS MADE. The original bug used his end-of-solution position, which wrote 13 of the corpus's
  19 click-bearing solutions wrong and made BlakeE1 #118 unreplayable in Tile World.

  Reaches the path through the real emulator -- SuperCC, openLevelset, loadLevel, real ticks --
  because TWSWriter only touches its SavestateManager when `level.supportsClick()` (MS yes, Lynx
  no) and SavestateManager's constructor is package-private. That also means it exercises the real
  recording path rather than a stand-in, and it is why `game\MS\**` coverage jumped.

  **The first version of this test did not discriminate the bug, and mutation testing is what said
  so.** `Position.screenPosition` CLAMPS: for any 5 <= chipX < 27 the viewport is placed at
  chipX - 4, so Chip sits at offset (4,4) inside it wherever he is, and the subtraction cancels --
  a wrong position yields the right answer. Two squares mid-map cannot tell the two apart. The
  fixture now clicks from 2,2, inside the top-left clamp, and ends at 13,13. That is also where the
  original was caught: BlakeE1 #118's clicks target 31,14, against the right-hand clamp.

  Four planted defects, all now caught, where the first draft caught none of them: the in-loop
  `level.load` removed, the pre-loop one removed, BOTH removed (the true jc-2 bug), and the
  post-write `savestates.load(-1, level)` removed. The last needed its own case -- exporting while
  REWOUND -- because the loop's final load already leaves the level at the end of the move list,
  so nothing else reaches it.

  Coverage: target scope 36.6% -> 42.2%, `game\MS\**` 17.0% -> 25.6%, `emulator\**` 6.3% -> 24.5%,
  `io\**` 65.5% -> 71.9%. No shipped code changed.

## jc-13

### Fixed
- **A truncated `.tws` was accepted and then misread.** `twsInputStream` extends
  `FileInputStream`, so `read()` answers -1 at end of file instead of throwing, and
  `verifyAndInit` really validated only the four-byte signature. A file holding nothing but its
  signature came out as ruleset LYNX, length byte -1 and `headerLength = 7`, after which
  `readSolution` walked records from an offset that means nothing -- so the symptom was "the wrong
  solution plays back" rather than an error. Now refused, in words, with the numbers.
- **A header claiming to be longer than the file** is refused too. Every field can be present and
  well-formed with the file still truncated after them; only the cross-check catches that.

  Found while writing `TwsRoundTripTest`, where it was pinned as a FINDING for one release because
  `io\TWSReader.java` was not yet in `$SPLICE_MODIFIED` and editing it would have shipped nothing.
  It is in the splice list now.

  **Two checks, deliberately not four.** The first draft also tested each header field for -1;
  mutation testing showed both were provably dead once the length guard exists, so they were
  removed rather than left as a comment claiming a safety net that could not fire. Each remaining
  check was proven load-bearing by planting its removal.

  **It rejects nothing real:** all **23,967** `.tws` files in the maintainer's collection were
  re-opened with the new check and every one was accepted. That regression test cannot live in the
  repo, since none of those files may be committed.

## jc-12

### Fixed
- **Canceling a file chooser threw an uncaught NullPointerException.** `MenuBar.openFile` returns
  null when the user dismisses the dialog; its other two callers test for that and
  `openFileBytes` did not, so Cancel or Escape produced `null.toPath()`. An NPE is not an
  IOException, so the catch below could not see it and it escaped onto the event thread. Affects
  Solution > Open, Search for seeds, and Load states. Reproduced against the pre-fix jar and
  confirmed gone against the fixed one, same keystrokes.
- **Opening a solution that does not exist reported it badly.** That case WAS caught, but handled
  with `e.printStackTrace()` -- fifty lines of JDK frames into the error log -- and by showing the
  user `NoSuchFileException.getMessage()`, which is the bare path with no words around it. It now
  has its own catch, says "There is no file:", and logs nothing, because asking for a solution
  before one has been saved is ordinary rather than a fault.

  Both were found by the jc-11 error log, which had recorded the second three times across two
  sessions on `succsave\SokobanCCLP\65` without anyone looking. The engine is untouched.

## jc-11 — 2026-08-30

### Added
- **An error log.** `java/io/ErrorLog.java` tees `System.err` to `succ_error-<MACHINE>.log` beside
  `succ_settings.ini`, so the 27 existing `printStackTrace` calls and every uncaught exception now
  land somewhere — without editing a single call site. Under `javaw`, which is how a double-clicked
  jar runs, all of that previously went nowhere at all, and "it just closed" was the most anyone
  could report. Created lazily, so a clean session leaves no file; capped at 512 KB with one
  rotation; named per machine because the Chip's Challenge folder is Dropbox-synced between two PCs
  and a shared log would come back as a conflicted copy. The user is told once, and named the file.
  No setting, deliberately — see `docs/adr/0009-the-error-log-is-not-a-setting.md`.
- `test/ErrorLogTest.java`.

### Fixed
- **A `NullPointerException` on every single launch.** `Gui.repaint(boolean)` dereferences
  `getSavestates()` unguarded, so starting with no level open always threw. It was harmless — the
  panels above it had already repainted and only the play-button refresh was skipped — and nobody
  saw it, because `javaw` discarded it. It had to go before the log was worth having: an error
  recorded on every startup teaches everyone to ignore the artifact. `Gui` is form-based and cannot
  be recompiled here, so the fix is at the call site in `SuperCC`.
- **Opening a file that is not a level set no longer throws behind the error message.**
  `openLevelset` reported the problem clearly and then fell through to `loadLevel` with nothing
  open, producing a `NullPointerException` immediately after the dialog. `loadLevel` is guarded too,
  because passing a bad file on the command line (or via a file association) let that NPE escape
  startup entirely and leave the window half-built. Same reasoning as the launch NPE above: an
  error recorded for an ordinary mistake teaches people to ignore the log.
- **A file that fails to open no longer resets you to level 1 of the set you already had open.**

### Verified
- **The emulator is untouched, proven twice.** Every level of all 286 sets re-parsed and all 23,322
  stored solutions re-played under both builds: 45,641 recorded outcomes, byte-identical, same
  SHA-256. Independently, a jar-entry diff shows **zero `game/**` classes changed** — the only
  changed class is `emulator/SuperCC.class`, plus three new `io/ErrorLog*` classes.
- Clean launch produces empty stderr and no log file.

## Unreleased (tooling, shipped alongside jc-11)

### Added
- `CLAUDE.md` and `AGENTS.md`: a zero-context brief for humans and coding agents, leading with the
  splice-build trap.
- `verify-splice.ps1`: mechanical enforcement of the splice invariants. Recompiles every unspliced
  source file and compares it against the bytecode that would actually ship, so an edit left out of
  `$SPLICE_MODIFIED` fails loudly instead of being silently discarded.
- `build-config.ps1`: one definition of the splice list, shared by the build and the verifier, plus
  a version-aware JDK finder and a robocopy wrapper that distinguishes success codes 0–7 from real
  failures.
- GitHub Actions: CI (splice check, build, tests, artifacts), a tag-triggered release workflow that
  drafts the release, and CodeQL.
- `test/Harness.java`: shared assertions with JUnit-XML and JSON output, and reporting that survives
  an exception mid-run.
- `test/DatBuilder.java` and `test/EngineTest.java`: the first engine coverage — 32 assertions over
  `.dat` parsing, both RLE encodings, level indexing, the headless emulator, and the jc-7 off-map
  monster-list bug class — using synthesized fixtures, so no level set is needed.
- `docs/adr/`: the eight decisions that look like bugs and are not.
- `docs/THIRD_PARTY.md`: what is vendored under `com/` and `org/`, and its licensing.
- `build.ps1 -ExpectTag` and `-Manifest`: a guard against a git tag disagreeing with `BUILD_TAG`,
  and a machine-readable record of what was built and by which compiler.
- `run-tests.ps1 -ResultsPath` and `-Isolated`, and automatic discovery of every test class.
- `run-tests.ps1 -JvmArgs`: a general passthrough of extra JVM arguments to every test JVM, so the
  runner needs no knowledge of any particular profiler.
- `coverage.ps1`: JaCoCo branch coverage, scoped to `game\**` + `io\**` and split by ruleset, with
  the least-covered engine classes listed at the end. JaCoCo is cached per machine outside the repo
  and is never committed or shipped, so no dependency is added. First measurement: **19.5% branch**
  on the target scope (JDK 16.0.2), with `game\Lynx\**` (2.0%) and the `io.TWS*` streams (0%) the
  largest gaps. It refuses to report rather than print a number it cannot prove is current: it
  fails on a red suite, on a stale or undeletable exec/CSV, on a jacococli class mismatch, and when
  run from outside the repo. Each of those guards was verified by reproducing the failure it
  prevents.
- `test/TwsRoundTripTest.java`: 25 assertions over the .tws solution format, which was the last
  flat 0% and the only remaining gap that is about DATA rather than a coverage statistic. A .tws
  bug corrupts the file a solution is SAVED into, and jc-2 fixed a real one there. Writes a
  solution, reads it back, and checks the header bytes against the FORMAT rather than against the
  writer, so a signature or ruleset-byte change fails even though a pure round trip would not.
  Five planted defects, all caught. `io\**` 54.3% -> 65.2%; target scope 34.7% -> 36.5%.
  Two behaviors are PINNED rather than changed, with the reasoning in the file: a solution comes
  back with one extra trailing wait (the format stores each move's duration, so the last one
  yields a tick -- Tile World does the same), and **a .tws truncated after its signature is
  currently ACCEPTED**, because verifyAndInit reads the rest of the header past end-of-file where
  DataInputStream answers -1 instead of throwing. That is a real robustness gap; it is asserted as
  it stands so that fixing it is a deliberate act that turns the assertion red. Not fixed here
  because io\TWSReader.java is not in $SPLICE_MODIFIED, so editing it would ship nothing until
  the build changed too.
  Not covered, and stated in the file: the click-conversion path, which needs an MS level and a
  real SavestateManager.
- The differential trace harness was VERIFIED end to end for the first time, and it was broken.
  It had never been run: the comparison needs a level set and a solution, neither of which may be
  committed, so it shipped and sat. Run against CCLP1 #6 -- where the two engines agree on all 109
  state changes -- it reported a divergence at tick 0. Three defects, all fixed:
    * `trace.ps1` wrote the SuperCC trace as UTF-16LE via PowerShell's `>` redirect, throwing away
      TraceLevel.java's deliberate UTF-8 and leaving a file grep and diff could not read.
    * the documented Tile World command was `-r -p`, which is read-only plus a password toggle and
      opens the GUI. Batch replay is `-b`. The recipe in the file is now the verified one.
    * the aligner compared EQUAL TICK NUMBERS, but the engines do not share a clock: Tile World
      emits four ticks per MS move, SuperCC one per move on a half-move counter (with occasional
      single steps), and SuperCC adds a pre-move line with no counterpart. It now aligns by
      state-change sequence, which needs no per-ruleset ratio and so works for Lynx too.
  Verified both ways: agreement reads as agreement, and an injected one-creature difference is
  pinned to the exact state change with both engines' tick numbers.
- `trace.ps1 -SelfTest`, and a CI step that runs it. The harness broke because nothing ever ran
  it; the self-test exercises the alignment on synthetic traces with no level set, no Tile World
  and no jar, so it cannot rot unnoticed again. Two planted regressions caught, one of which
  reproduces the original bug exactly.
- `test/LynxTickTest.java`: 58 assertions over LynxCreature.tick(), which at 217 lines was the
  largest untested method in the repo. It folds THREE Tile World functions into one --
  startmovement(), continuemovement() and endmovement() -- and the seam between them is invisible
  from outside. Covers the speed table as a full countdown (floor 6,4,2,0; ice 4,0; ice skates
  cancel the doubling; a blob steps by one) and the arrival-tile effects for Chip, blocks and
  monsters: drowning and the glider's exemption, a block turning water to dirt, fire and the fire
  boots, dirt and fake blue walls clearing, pop-up walls closing, bombs, the thief, every key and
  boot, and the four doors including green not consuming its key.
  Two places SuperCC and Tile World are written differently are now PINNED rather than fixed,
  with the reasoning recorded: TW guards the IC counter with `if (chipsneeded())` while SuperCC
  decrements unconditionally, so the count can go negative here (cosmetic -- the socket test is
  `<= 0` on both sides); and TW increments boot possession while SuperCC assigns 1, so collecting
  the same boot twice reads 1 rather than 2 (every use is a nonzero test).
  Nine planted defects, all caught.
  `game\Lynx\**` 37.4% -> 51.5%; target scope 30.5% -> 34.7%.
- `test/LynxCreatureListTest.java`: 61 assertions over the LYNX creature list -- ordering, the
  claimed layer, animations, clones and the creature cap. Creature ORDER is where desyncs live,
  and Lynx had nothing covering it. lxlogic.c's `advancegame()` runs three loops per tick and
  every one walks the list BACKWARD; the test pins the consequence rather than the loop, with two
  tanks contending for one square (the later tank in the list wins; forward iteration would give
  it to the other) and two blobs whose RNG draw order is the list walk order.
  Also pins that Chip is index 0 and that the swap putting him there sends the reading-order-first
  creature to the END of the list, which under backward iteration means it moves FIRST.
  Seven planted defects, all caught -- but only after mutation testing exposed a hole: the tank
  contention pins the MOVEMENT phase's order and NOT the choose phase's, because choosing takes
  no claim. Flipping the choose phase to forward iteration passed every assertion. The blob pair
  was added to close it, since blobs draw from the shared RNG as they are visited and therefore
  swap directions when the walk reverses.
- `test/LynxCanEnterTest.java`: 915 assertions covering the LYNX entry rules, transcribed from
  `lxlogic.c`'s `movelaws[]` table plus the three checks that live in `canmakemove()` rather than
  in the table (fire, doors, socket). Tile World asks a different question per entity class --
  chip, block, creature -- where SuperCC folds all three into one `canEnter`, so the test keeps
  the columns separate and lets the assertions do the folding. Covers the two rows where the
  columns genuinely disagree (gravel admits a block but no creature; dirt admits neither), the
  fire rule that sits in TW's creature branch only (a block may be pushed into fire, a bug may
  not walk in), and doors and the socket in both states -- which the MS test skips entirely
  because they consult level state. Eight planted defects, all caught.
  `game\Lynx\**` 4.8% -> 18.3%, now ahead of `game\MS\**`; target scope 20.6% -> 24.3%.
- `test/LynxCreatureMoveTest.java`: 98 assertions covering the LYNX creature move-preference
  table, transcribed from Tile World's `lxlogic.c` `choosecreaturemove()` -- a different oracle
  from the MS tests, and one that carries **no** `MOD (Jeremy)` comments, so it is unmodified
  upstream. Covers all eight creature types across every facing, the Walker/Blob shapes where
  Lynx genuinely diverges from MS, the committed-move gate, the teeth-step tick offset, the
  seek tie-break, and the RNG draws a cheated blob or walker must still make. Every assertion
  mutation-proven: seven planted defects, all caught. `gameynx**` branch coverage 2.0% ->
  4.8%, and the target scope 19.5% -> 20.6%.
- `coverage.ps1 -CheckBaseline` / `-UpdateBaseline` and `docs/coverage-baseline.tsv`: the coverage
  figures documented in `CLAUDE.md` are now enforced. The release workflow fails if the measurement
  has drifted from the documented table, or if `CLAUDE.md` no longer states the headline figure, so
  a release cannot ship documentation that stopped describing the code. Deliberately not run in CI,
  where adding a test is supposed to move the number.

### Fixed
- `run-tests.ps1` discarded a caller's `-JvmArgs`. PowerShell variable names are case-insensitive,
  so the local `$jvmArgs` *was* the `$JvmArgs` parameter and `$jvmArgs = @()` silently emptied it —
  the first coverage run reported "508 passed, all green" with no agent attached and no error. The
  local is now `$jvmLine`.
- `run-tests.ps1` treated a clean exit with a truncated summary file as a pass: the class recorded
  no count, was never added to `$failedClasses`, and the run still printed `all green` and exited 0.

### Changed
- `build.ps1` now fails on a genuine robocopy failure (exit ≥ 8) instead of ignoring every exit
  code, throws on a missing stage directory rather than skipping it, checks `-ExpectTag` before
  anything is compiled or written, and reads `--release`/`-encoding` from the shared config so the
  build manifest cannot describe a build that did not happen.
- `run-tests.ps1` reports per-class assertion counts, clears stale results, treats a `javap` failure
  and a missing per-class result as failures rather than silently dropping a suite.
- `SettingsTest` reports through `Harness`. Same 90 assertions, unchanged.

### Fixed
- `run-tests.ps1 -Isolated` failed 100% of the time: `build.ps1` lacked an `IsPathRooted` guard on
  `-Out`, and PowerShell 5.1's `Join-Path` does not resolve a rooted child, so it produced
  `C:\repo\C:\Temp\x.jar`. It also could delete a jar passed with an explicit `-Jar`.
- `verify-splice.ps1` initially passed a planted edit. The comparison key needed three corrections:
  `javap -c` is blind to string literals (Java 9+ lowers concatenation to `invokedynamic`, parking
  the literal in `BootstrapMethods`), raw class bytes are unusable (68 of 70 files differ on
  constant-pool ordering between IntelliJ and javac), and the baseline is compiled with `-g`. It now
  also compares inner classes, hashes form-based `.java` files (which it cannot recompile at all),
  and walks the baseline to catch a deleted source file leaving an orphaned class in the jar.
- The build manifest was written before `-Package` wipes `dist/`, so the run deleted the file it had
  just reported writing.
- The release workflow interpolated a git tag name straight into a PowerShell string; `jc-$(whoami)`
  is a valid, pushable tag that matches the `jc-*` filter. The tag now travels via the environment
  and is validated against `^jc-\d+$`.
- Test fixtures leaked their temp directories permanently — `File.deleteOnExit` cannot remove a
  non-empty directory. 1,618 had accumulated.

### Notes
- No shipped behavior changed, so no new build tag. No file under `java/**` was touched, and a
  build from this tree was compared entry by entry against the published jc-10 release: **all 193
  compiled classes are byte-identical.**
- That comparison did surface a pre-existing inconsistency worth recording. The rebuilt jar is *not*
  byte-identical to the released one — 17 non-code entries differ: the 15 bundled `.form` files,
  `Forwarder.py`, and `META-INF/MANIFEST.MF`. The cause is line endings. `.gitattributes` declares
  `eol=lf`, but those blobs were committed before it existed and still hold CRLF, and `text=auto`
  does not retroactively renormalize; the released jar was built from a tree that had LF. The files
  are identical once line endings are normalized. Nothing that runs is affected — the `.form` files
  inside the jar are inert copies of the source, and the classes that use them are the committed
  baseline. Fixing it properly means `git add --renormalize .` in its own commit.

## jc-10 — 2026-08-21

The TWS folder remembers where you were again, reversing jc-8's pinning at the maintainer's request
after using it. The jc-8 null guards survive, and the stored value stays relative so it remains
portable between machines. See `docs/adr/0008-tws-folder-remembers-again.md`.

## jc-9 — 2026-08-14

`[Emulation] AlwaysOpenInMS`: open every level set under MS regardless of the ruleset its `.dat`
declares. Scoped to *opening* only — F3 still switches, and loading a solution still follows the
solution's ruleset, because forcing MS there would break every Lynx replay.

## jc-8 — 2026-08-14

The settings file became `succ_settings.ini`, freeing the generic name for Tile World. The build tag
became opt-in and off by default, so downloaders do not see a private build number. The TWS folder
was pinned (reversed in jc-10). `build.ps1 -Package` began producing the release zip.

## jc-7 — 2026-08-09

Three levels that would not open at all now open. An entry in optional field 10 pointing off the
32×32 map either threw out of bounds or — worse — aliased onto an unrelated cell, leaving the
counting and storing loops disagreeing and a trailing `null` that became an NPE. Proven
regression-free by fingerprinting the MS monster list of all 21,838 levels across 273 sets.

## jc-6 — 2026-08-09

`toStoredPath()` and its inverse were not actually inverses: the outside-the-folder branch returned
the caller's original string instead of the absolute path it had computed. Unreachable in the
shipped app, closed anyway.

## jc-5 — 2026-08-09

Portable paths. `Levelset` and `TWS` are stored relative to the Chip's Challenge folder, so a
Dropbox-synced settings file works on two machines with different user directories. See
`docs/adr/0004-store-settings-paths-relative.md`.

## jc-4 — 2026-08-09

The settings file stopped being destroyable. Three pre-existing defects: any read `IOException` was
treated as "file missing" and triggered a truncating rewrite; the write itself truncated in place
and reported nothing; and absent keys persisted as the literal text `null`. The write is now atomic
with retries.

## jc-3 — 2026-08-06

The `[jc-N]` build tag in the window title became switchable from the settings file, with no rebuild.

## jc-2 — 2026-08-05

Exported `.tws` click solutions pointed at the wrong square.

## jc-1 — untagged (2026-06-23/24)

The three display-only mods this fork started as: the level hint shown under the author, the window
title carrying the pack and level names, and clone/trap connections displayed by default.
