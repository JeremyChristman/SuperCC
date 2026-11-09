# Issues introduced by the upstream baseline

The fork is being returned to upstream SuperCC v2.0.3 **exactly**, one change at a time, before its
fixes are layered back on top (FORK.md, *The upstream baseline*). Every revert brings back upstream
behavior the fork had fixed. **This file is the list of those problems, so that none is forgotten
when the fixes come back.** It was started on 2026-11-08 at Jeremy's request: *"Please keep track of
all issues doing these revisions introduces so that we can fix them later!"*

How to keep it:

- **Every baseline step adds its issues here, in the same push**, numbered `UB-n` in order and never
  renumbered.
- An issue is closed by its fix, not by deleting it: set **Status** to `FIXED in <commit> (jc-N)`
  and keep the entry.
- Each entry says what a user sees, what it was measured to affect, where it came from, how to fix
  it, and which tests pin it. The pins are `Harness.upstreamBug` checks that pass only on
  upstream's own wrong answer; when a fix returns they fail with "the correct answer came back",
  which is the cue to make them ordinary checks again.
- Problems noticed along the way that the baseline did **not** introduce go in the last section,
  so they are not lost either.

## Summary

| ID | Problem | Who sees it | Introduced | Status |
|---|---|---|---|---|
| UB-1 | MS levels with a trap that starts with Chip or a block on it, wired to 2+ buttons, do not open | anyone opening such a level; 5 levels in the collection | `79b0032`, jc-19 | open |
| UB-2 | ...and wired to 1 button, that trap starts SHUT (MSCC and Tile World start it open) | anyone playing such a level; 6 solved routes lost, 2 unsolved changed | `79b0032`, jc-19 | open |
| UB-3 | An empty trap with 2+ buttons, one held: open or shut depending on hash order | none seen in a real route | `79b0032`, jc-19 | open |
| UB-4 | Lynx `isTrapOpen` throws on a position no button is wired to | code only; nothing in the game asks | `79b0032`, jc-19 | open |
| UB-5 | Trap bits in a `.ser` savestate are numbered by hash order | anyone loading a `.ser` saved by another build | `79b0032`, jc-19 | open |
| UB-6 | A tick that reports both "discard" and "automatic ticks" is recorded as a wait, so the route saves short | nobody today: no real tick does that | `923d07c`, ships in jc-20 | open (latent) |
| UB-7 | Seed Search with a negative start: one thread does all the work | anyone typing a negative start | `397c7bb`, ships in jc-20 | open |
| UB-8 | Seed Search's threads race at the start: "running" can drop mid-search, a Pause can be undone | anyone, mostly on short searches | `397c7bb`, ships in jc-20 | open |
| UB-9 | Seed Search never shows a success at a negative seed as the example | anyone searching from a negative start | `397c7bb`, ships in jc-20 | open |
| UB-10 | Seed Search's Pause flag is not `volatile` | nobody observed (theoretical) | `397c7bb`, ships in jc-20 | open |
| UB-11 | With no level set open, most menu controls fail with an internal error | everyone, from launch until a set is opened | `c57f217`, ships in jc-21 | open |
| UB-12 | Canceling Go to... or Set RNG Seed fails with an internal error | anyone who cancels either | `c57f217`, ships in jc-21 | open |
| UB-13 | Canceling the file chooser of Solution › Open, Search for seeds or Load states fails | anyone who cancels one | `c57f217`, ships in jc-21 | open |
| UB-14 | A set whose level 1 will not load opens badly: an error, then no level, or the previous set's level under the new file | none of the 331 sets in the collection | `c57f217`, ships in jc-21 | open |
| UB-15 | A level whose savestates cannot be built is left half-loaded | nobody today (no level reaches it) | `c57f217`, ships in jc-21 | open (latent) |
| UB-16 | Choosing a custom tileset saves it as tileset number 5, which builds before jc-18 cannot start with | anyone sharing the settings file with an older build | `5d3f0a3`, ships in jc-22 | open |
| UB-17 | Custom tileset pictures are saved as full paths, which the other computer does not have | anyone sharing the folder between computers | `5d3f0a3`, ships in jc-22 | open |
| UB-18 | Canceling View › Tileset › custom asks for the second picture anyway, then shows an error, and leaves the mark on custom | anyone who cancels it | `5d3f0a3`, ships in jc-22 | open |
| UB-19 | A custom picture is saved before it is known to load; a bad one fails with an internal error or an unclear message | anyone choosing a wrong file | `5d3f0a3`, ships in jc-22 | open |
| UB-20 | SuperCC does not start (no window) when the saved MS tileset cannot be drawn | anyone with a bad TilesheetNum or missing custom pictures | `5d3f0a3`, ships in jc-22 | open |
| UB-21 | A Lynx tileset that cannot be drawn, or a failing custom tileset at a window resize, leaves the screen wrong | anyone with such a choice | `5d3f0a3`, ships in jc-22 | open |

---

## The trap code (`79b0032`, released in jc-19)

The fork's four fixes to upstream's trap rework (`858dbd0`), merged in jc-15, were reverted. All four
come back with `git revert 79b0032` (proven in a scratch worktree: `java/` identical to jc-18, all
2,365 checks passing); one at a time, re-apply by hand from that commit's diff. FORK.md step 1 has the
full engineering record.

### UB-1 — a level with an occupied trap wired to two or more buttons does not open

- **What a user sees:** under MS, opening such a level shows *"Could not load level: Cannot invoke
  "java.util.Map.get(Object)" because "this.trapIndexMap" is null"* and does not open it. Next and
  Previous cannot get past it; Level › Go to... skips it.
- **Measured:** 5 levels in the 24,212-solution collection — EEfor5subs #16, IsaacD1-Old #116,
  JRB_CCLP1 #18, TCCLP #110, TomP3 #66 (four had solved routes). They cannot be re-solved until this
  is fixed.
- **Cause:** `MSLevel`'s constructor presses every trap that starts holding Chip, a block or an ice
  block, *before* it builds its trap index; the second wire's press reads the null map.
- **Fix:** build `trapButtons` and `trapIndexMap` before the start-of-level press (jc-15's Fix 1).
- **Pinned by:** `MsCloneTrapTest` §6, the two-wire cases (`openOrUpstreamCrash`).

### UB-2 — an occupied trap wired to one button starts shut

- **What a user sees:** under MS, a block or Chip that starts on a trap cannot leave it; MSCC and
  Tile World let it. A route made against MSCC or Tile World stops working.
- **Measured:** 6 solved routes no longer reach the exit — C1059-1 #47, EEfor5subs #26, geodave1 #52,
  geodave4 #7, IsaacD1-Old #121, TLFC3 #89 — and 2 unsolved routes end elsewhere (MO1 #61,
  TCCLPpgchip #2). A route re-solved under this behavior would not replay in Tile World, so do not
  re-solve these until it is fixed.
- **Cause and fix:** the same as UB-1 (with one wire the early press finds no button and silently
  does nothing).
- **Pinned by:** `MsCloneTrapTest` §6, the one-wire cases.

### UB-3 — an empty trap's state depends on hash order

- **What a user sees:** nothing known. An empty trap with two or more buttons, one of them held, can
  read shut at the end of a tick, depending on which wire a hash map visits last.
- **Measured:** shut in 3 of the 6 test arrangements; no stored route depends on it (a creature
  entering a trap re-checks its buttons).
- **Fix:** decide each trap once, after all its wires: open if any button is held (jc-15's Fix 2,
  `MSLevel.finaliseTraps`). Tile World's `istrapopen()` is the reference.
- **Pinned by:** `MsCloneTrapTest` §7 (three arrangements). The order is fixed for a given
  `Position.hashCode`, map capacity and JDK; if one of those changes, those pins can fail with no fix
  involved.

### UB-4 — Lynx `isTrapOpen` throws on an unwired position

- **What a user sees:** nothing. Inside the game the question is only ever asked about a wired
  position (through `BrownButton`), and every Lynx route replays unchanged.
- **Fix:** answer "shut" when no button is wired there (jc-15's Fix 3), as MS does.
- **Pinned by:** `ConnectionTest` §4 (the exact `NullPointerException`).

### UB-5 — `.ser` trap bits are numbered by hash order

- **What a user sees:** a savestate file saved by an earlier build may load with a trap wrongly open
  or shut: one from jc-15 to jc-18 on a level with more than one trap; one from jc-14 or earlier also
  wherever a trap has two or more buttons. And states saved now may load wrongly in those builds.
- **Measured:** the collection holds no `.ser` files.
- **Fix:** number each trap by its first wire's record index, as jc-14's per-wire bits were (jc-15's
  Fix 4, `LevelFactory.getTrapIndexMap`).
- **Pinned by:** `MsCloneTrapTest` §10.

## The move recorder (`923d07c`, ships in jc-20)

### UB-6 — a tick reporting both bits is recorded as a wait

- **What a user sees:** nothing today. If `Level.tick` ever reported a key as ignored *and* ran the
  automatic ticks of a move, `SuperCC.tick` would record a one-tick wait where the engine ran a whole
  move, and the saved route would replay short (FORK.md step 2 measured 3 ticks).
- **Measured:** no real tick does it: all 24,212 routes record exactly as on jc-19 (a census of the
  recorded move lists, whose control found step 2's 40 re-recorded Lynx routes), and step 2's fuzzing
  saw it in none of 104,756 ticks. But `LynxLevel.tick` *can* return both bits, so this is load-bearing
  reasoning with nothing guarding it — **check any change to `LynxLevel.tick`, `selectChipMove` or
  `moveChip` for it** (CLAUDE.md §6).
- **Fix:** keep the key when the automatic-tick bit is also set (jc-15's recorder fix):
  `git revert 923d07c`.
- **Pinned by:** `InputDiscardTest` §5, through a level wrapped to report both bits (upstream records
  `r--UUUUUu`; the fix records `rUUUUUUUu`).

## Seed Search (`397c7bb`, ships in jc-20)

The fork's four fixes from jc-16 were reverted. All four come back with `git revert 397c7bb`, which
also restores the compiled form classes and both baselines it changed. FORK.md step 3 has the
engineering record.

### UB-7 — a negative start does not share the work

- **What a user sees:** Solution › Search for seeds from a negative start: one thread searches
  billions of seeds alone while the others sit idle — 3.22 billion at 2 threads, 3.76 billion at 4,
  4.22 billion at 28 (the slider's default is the CPU count; the desktop has 28). A third to about
  half of those (1.07 billion at 2 threads, 1.61 at 4, 2.07 at 28) are negative seeds below the start
  that nobody asked for, and the RNG keeps 31 bits, so a negative seed only repeats a positive one. Against the correct split that is about 7 times slower at 4
  threads and about 55 times at 28.
- **Measured (playtest, 2026-11-08, CCLP1 #1, start -5, 4 threads):** after 1.5 seconds threads 0-2
  were still at their starting seeds (-5, -536,870,915, -1,073,741,825) and thread 3 had done all
  219,765 attempts from -1,610,612,735.
- **Cause:** `Integer.MAX_VALUE - start` is computed in `int`, which wraps for a negative start, so
  the pool size is negative and every range but the last is empty.
- **Fix:** `(long) Integer.MAX_VALUE - start`.
- **Pinned by:** `SeedSearchTest` §3 (each thread's outcome, and the attempt total, at upstream's
  split).

### UB-8 — the search threads race at the start

- **What a user sees:** each thread starts as soon as it is built, and building the next one takes
  longer than a short range takes to search. So an early thread can finish, decide it is the last one,
  and mark the search as stopped while others still run. While it reads stopped:
  - the button acts as Begin, not Pause: a click starts a second set of threads over the same ranges,
    and its totals can overwrite the real ones;
  - the main window unlocks what a running search locks (`SuperCC.areToolsRunning()`): Restart, Next,
    Previous, Go to..., Set RNG Seed, the ruleset, stepping and RFF-direction changes, the move keys
    and mouse clicks.

  A Pause can also be lost twice over: each thread clears the Pause flag as it starts, and an early
  thread's "last one cleans up" clears it as well, so a Pause pressed before the last thread begins
  is undone.
- **Measured:** with 8 seeds over 4 threads, "running" went false once per thread in every search the
  test's watcher thread caught — but the watcher itself can miss the flicker (1 search in 4 on 2
  CPUs), which is why the pin takes five searches. On a 3,000-seed search it is a race: four times in
  three of five searches on the laptop, never in five on the 28-CPU desktop. It can also strike
  mid-search: an empty-range thread's late cleanup can write "stopped" after the searching thread set
  "running" (found by review; `SeedSearchTest` §3 avoids depending on it).
- **Fix:** build every thread first, count them all as alive, set the flags once, then start them;
  clear the Pause flag before publishing "stopped" (jc-16).
- **Pinned by:** `SeedSearchTest` §2: five 8-seed searches, pinned on "running ever went false more
  than once". The 3,000-seed check is skipped on this code, because a race cannot be pinned.

### UB-9 — a success at a negative seed is never shown

- **What a user sees:** "Example seed" stays blank although successes are counted.
- **Measured (same playtest):** 219,765 successes from start -5, and no example seed shown.
- **Cause:** "no success yet" is -1, and a success is shown only if `>= 0`.
- **Fix:** a `Long.MIN_VALUE` sentinel compared with `!=` (jc-16).
- **Pinned by:** `SeedSearchTest` §4.

### UB-10 — Seed Search's Pause flag is not `volatile`

- **What a user sees:** nothing observed. `killFlag` and `running` are set on one thread and read in
  the search threads' loops; without `volatile`, Java does not promise the search threads ever see a
  Pause. HotSpot happens to make it work: Pause worked in every test and playtest.
- **Fix:** declare both `volatile` (jc-16).
- **Pinned by:** nothing. A memory-model guarantee cannot be tested reliably.

## The no-level-loaded guards (`c57f217`, ships in jc-21)

Upstream's `72476bc` guarded the state every launch starts in — no level set open — in three places:
the key listener, the main window's play button and sliders, and six Level-menu items. jc-17 extended
that to every menu and fixed what it found on the way; all of it was reverted, and the two upstream
lines jc-17 had left out are in. Upstream's own guards stay. All of it comes back with
`git revert 7ba0d45 c57f217` (newest first: `7ba0d45` is a test fix made after CI showed that
Paste solution depends on the clipboard), which also restores `ChooseTileSize`'s compiled class
and both baselines. If step 5 (custom tilesets, below) is still reverted, re-apply it first: both
steps rewrote View › Tileset's listener in `MenuBar`.
FORK.md step 4 has the engineering record.

"Fails with an internal error" below means a `NullPointerException` on the event thread: nothing
visible happens except that it is written to the error log (`succ_error-<MACHINE>.log`).

### UB-11 — most menu controls fail before a level set is open

- **What a user sees:** until a level set is opened, these controls fail instead of doing nothing:
  - Level › Change stepping value, and Level › Go to... when its box is canceled (UB-12; a number
    typed there does nothing with no set open);
  - Solution › Save as, Save (so Ctrl+S and Ctrl+Shift+S), Open, Search for seeds, Copy solution,
    Paste solution, Copy all previous moves, Copy all future moves, Save all states to disk and Load
    states from disk, and merely hovering over Copy macro moves;
  - TWS › Write solution to new tws and Verify tws;
  - all five built-in View › Tileset choices — the tileset is drawn, but not saved, so it is gone at
    the next launch — and View › Tileset › custom the same way once two pictures are chosen (its
    pictures ARE saved, then it fails before the choice of tileset is), and View › Tile size ›
    custom;
  - Tools › Variation testing and TSP Solver;
  - all six Cheats.

  Upstream's own guards keep Restart, Next, Previous, Set RNG Seed, Change ruleset and Change Initial
  RFF Direction safe, and the keys, play button and sliders.
- **Measured:** `GuiWindowTest` clicks every control in a real window with no level open, canceling
  every box it opens: on the desktop 29 controls throw (jc-16 had 46; upstream's guards remove the
  rest), and all others work. Solution › Paste solution reads the clipboard before it looks at the
  level, so it fails this way only when the clipboard holds text; with nothing there (CI) it writes a
  clipboard error trace to the error log instead. Custom with two pictures CHOSEN is a 30th case:
  the walk cancels custom's choosers, so it is clicked again afterward with real pictures.
- **Cause:** upstream guards six items by hand.
- **Fix:** jc-17's `MenuBar.requireALevel()` — one guard over every menu, so a new item is covered
  too — with `worksWithoutALevel(...)` on what makes sense without a level (Open levelset, Open tws,
  the tile sizes, the window size, the View toggles and tilesets — a tileset chosen with no level is
  saved as the MS one, the one drawn at startup — and Controls); the Copy macro hover checking for
  itself; and `ChooseTileSize`'s dead `getKeys()` line removed.
- **Pinned by:** `GuiWindowTest` §2: 28 of them, with a check that all 28 were found, the tileset
  save, and custom with two pictures chosen (its file choosers answered with real pictures). Paste
  solution is not clicked there, so the run never depends on — or writes to — the system
  clipboard. (The custom pin still fails on jc-21's code, which already had this step reverted; it
  flips on jc-20's.)

### UB-12 — canceling Go to... or Set RNG Seed fails

- **What a user sees:** pressing Cancel (or the close box) in either box fails with an internal
  error, with a level open or not.
- **Cause:** the reply to a canceled box is `null`, and upstream tests it for empty only.
- **Fix:** test for `null` too (jc-17).
- **Pinned by:** `GuiWindowTest` §4 (both, with a level open) and §2 (Go to..., with none).

### UB-13 — canceling a file chooser fails

- **What a user sees:** canceling the file chooser of Solution › Open, Solution › Search for seeds
  or Solution › Load states from disk fails with an internal error. (jc-12 fixed this; its clearer
  message for a file that is not there stays.) With assertions on (`java -ea`, never a normal
  launch), canceling TWS › Write solution to new tws fails too, on upstream's `assert tws != null`.
- **Cause:** upstream's `72476bc` wraps the chooser's reply in `Objects.requireNonNull`, which jc-17
  left out because it undoes jc-12; the baseline has upstream's line.
- **Fix:** treat a `null` reply as "nothing to do", as jc-12 did.
- **Pinned by:** `GuiWindowTest` §4 (Solution › Open, then Cancel, with a level open).

### UB-14 — a set whose level 1 will not load opens badly

- **What a user sees:** a level set opens at level 1 only. If level 1 will not load:
  - on a fresh start, its error is shown and then the open fails (an internal error), leaving no
    level loaded;
  - with a set already open, the previous set's level stays on screen under the new set's file, so
    Solution › Save would put that route in the new set's save folder;
  - a set with no levels at all behaves the same;
  - opened from the command line or a file association (`SuperCC.jar set.dat`), the failure also
    escapes the startup code, so the tileset is never set up and a level asked for with `-l` is
    never loaded: the window is left half-built. (Reproduced to the error dialog on the jc-21
    candidate; the rest is jc-16's code, measured then — FORK.md step 4, *The second review*.)

  Its other levels can still be reached with Level › Go to.... Separately, an error in the window's
  repaint during a load (CLAUDE.md §9's jar replaced under a running instance) is reported as
  "Could not load level" although the level loaded, and the title bar keeps the previous level's
  name.
- **Measured:** none of the collection: a scan opening all 331 sets (2026-11-08) found every level 1
  loads. The only levels that do not load are UB-1's five, none of them a level 1.
- **Fix:** jc-17's `openLevelset`: open at the first level that loads, report all failures once
  ("Levels 1 to 3 could not be loaded … Opened at level 4"), put the previous set back before the
  error is shown when nothing loads, and count positions, not record numbers — through
  `tryLoadLevel`, which returns a failure instead of reporting it.
- **Pinned by:** `EngineTest` §6c and §6d; `GuiWindowTest` §3a (including the open level kept under
  the new set's file, in a real window) and §5.

### UB-15 — a level whose savestates cannot be built is half-loaded

- **What a user sees:** nothing today. `loadLevel` assigns the level before it builds the level's
  savestates, so if that second step failed the level would be "loaded" with no savestates, or with
  the previous level's, and slip past the guards that ask whether a level is loaded.
- **Measured:** no level reaches it. The one cause ever seen, a level with no title, is handled by the
  fork's own untitled-level fix (jc-17, kept: it is not part of this step), and all 23,476 levels of
  the collection load except UB-1's five, which fail earlier, while parsing.
- **Fix:** build the level and its savestates first, then assign both, or neither (jc-17).
- **Pinned by:** nothing — no fixture reaches it while untitled levels load.

## Custom tilesets (`5d3f0a3`, ships in jc-22)

The fork's changes to upstream's custom tilesets (`d78f6f3`), merged in jc-18, were reverted:
`TileSheet`, `SmallGamePanel` and `Gui` are upstream's byte for byte, `MenuBar`'s View › Tileset
listener and `SuperCC.initialiseTilesheet` are upstream's, and `SuccPaths` stores the custom tileset <!-- aen-exempt: upstream method name -->
as upstream does, inside the fork's own settings structure. All of it comes back with
`git revert 5d3f0a3`, which also restores `Gui`'s compiled class and both baselines; re-apply it before
step 4's (newest first), since both touched View › Tileset's listener. FORK.md step 5
has the engineering record. Nobody's tileset changes by itself: these all start with choosing a
custom tileset, or with a settings file that names one that cannot be drawn.

### UB-16 — custom is saved as tileset number 5

- **What a user sees:** View › Tileset › custom saves `TilesheetNum = 5` (or `LynxTilesheetNum = 5`).
  A build from before jc-18 has no tileset 5, so on a settings file shared between computers (this
  one is, through Dropbox) such a build does not start, and its next settings write erases the
  custom picture lines it does not know. A settings file written by jc-18 to jc-21 with custom on
  shows its built-in tileset instead (their `CustomTileset` switch is no longer read) — and the
  other way round, jc-22's next settings write drops those `CustomTileset` lines from the shared
  file, so a computer still on jc-18 to jc-21 then falls back to its built-in tileset too.
- **Measured:** `GuiWindowTest` §5b, in a real window with two pictures chosen: the file reads
  `TilesheetNum = 5`. A pre-jc-18 build on such a file was measured in jc-18's review (FORK.md step 5).
- **Fix:** custom as a switch of its own (`CustomTileset` / `LynxCustomTileset`) beside a built-in
  `TilesheetNum`, which every build can read (jc-18).
- **Pinned by:** `GuiWindowTest` §5b.

### UB-17 — custom pictures are saved as full paths

- **What a user sees:** a custom tileset chosen on one computer names that computer's folders
  (`C:\Users\Jeremy\...` against `C:\Users\jerem\...`), so on the other computer its pictures are
  missing — and then, with UB-20, SuperCC does not start there.
- **Fix:** store a picture inside the Chip's Challenge folder relative to it, as Levelset and TWS
  are (jc-5's `toStoredPath`/`toUsablePath`; jc-18).
- **Pinned by:** `SettingsTest` §13; `GuiWindowTest` §5b.

### UB-18 — canceling custom asks again, then complains

- **What a user sees:** canceling the first picture's chooser still opens the second; canceling
  either shows "No tileset image provided"; and the View › Tileset mark stays on custom though the
  old tileset is still drawn.
- **Fix:** the first Cancel ends it, with no error, and the mark goes back to what is drawn
  (`MenuBar.markActiveTileset`; jc-18).
- **Pinned by:** `GuiWindowTest` §2 (canceled with no level open: both choosers and the error) and
  the radio-mark check after it.

### UB-19 — a custom picture is saved before it is known to load, and a bad one fails badly

- **What a user sees:** choosing a file that is not a picture saves it anyway, then fails with an
  internal error (`NullPointerException`) before the choice of tileset is saved. If custom was
  already the saved tileset, the next start then fails (UB-20); if not, only the stored picture
  paths are wrong. Other bad files fail with internal errors as well: a broken GIF
  (`IndexOutOfBoundsException`) and a picture whose header claims 50000 x 50000 pixels
  (`IllegalArgumentException`); one claiming about 46000 x 46000 makes the loader try to decode about
  8 GB. A missing picture's message names no file ("Can't get input stream from URL!"). With no
  pictures chosen, custom fails with an internal error: upstream's getter returns the pair
  whenever both keys exist, and this fork's settings always have them (empty), so the empty paths
  reach the image reader as the working folder.
- **Fix:** load both pictures before saving them; make every failure an IOException that names
  the file and says what is wrong; refuse a picture over 32 million pixels from its header, before
  decoding; close both streams (jc-18).
- **Pinned by:** `EngineTest` §6f (six: missing, not a picture, no settings, none chosen, the GIF,
  the 50000 x 50000 header; the 8 GB case is skipped, not run); `SettingsTest` §13 (none chosen,
  half chosen and a pre-jc-18 file read as a pair, not as "none"); `GuiWindowTest` §5b (the click on
  a file that is not a picture, and that it is saved).

### UB-20 — SuperCC does not start when the saved MS tileset cannot be drawn

- **What a user sees:** if `succ_settings.ini` names an MS tileset this version does not have, or
  custom whose pictures are missing or unreadable, no window appears at all: startup throws, and
  the program keeps running unseen (on a double-click, simply nothing happens). Setting
  `TilesheetNum` back to 0–4 by hand is the way out.
- **Measured (2026-11-09, launching the jc-22 candidate from a sandbox):** `TilesheetNum = 99` →
  `ArrayIndexOutOfBoundsException` in `initialiseTilesheet`, no window; `TilesheetNum = 5` with the <!-- aen-exempt: upstream method name -->
  pictures missing → `IIOException`, then a `NullPointerException` on the missing images, no window.
  Opening a set from the command line first shows "Could not load relevant tilesheet", then the same.
- **Fix:** jc-18's `TileSheet.loadForRuleset`/`load`: fall back to the ruleset's built-in choice,
  or the default, and say why once a session (`SuperCC.throwErrorOnce`).
- **Pinned by:** nothing — the start needs a window and a settings file of its own; measured by
  launching.

### UB-21 — a tileset that cannot be drawn, met while running, leaves the screen wrong

- **What a user sees:** opening a Lynx set whose Lynx tileset cannot be drawn shows "Could not load
  relevant tilesheet" (naming nothing) — every time such a set is opened — and keeps the previous
  ruleset's tileset; a Lynx number this version does not have fails with an internal error instead.
  Changing the window size while a custom tileset fails leaves the game area drawn at the old size.
- **Measured:** by reading upstream's `Gui.swapRulesetTilesheet` (catches only IOException, with
  that message) and `SmallGamePanel.setWindowSize` (swallows the IOException); the swap's dialog was
  seen in the command-line launch above.
- **Fix:** both through jc-18's `loadForRuleset`, which falls back, says why once a session, and
  moves the View › Tileset mark to what is drawn.
- **Pinned by:** nothing (a running window and a broken settings file; measured as above).

---

## Seen while testing — NOT introduced by the baseline

These were already in upstream and in every fork build. They are listed so they are not lost.

- **Seed Search's button still says "Pause" after a search finishes by itself.** Seen in the
  2026-11-08 playtest (CCLP1 #1, the last 3,000 seeds: 3000/3000, button "Pause"). Since the search
  is no longer running, pressing it starts a new round of threads rather than pausing; what that
  round does was not checked. Upstream and jc-16 to jc-19 alike; the fork never touched that code.
