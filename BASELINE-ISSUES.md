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
| UB-1 | MS levels with a trap that starts with Chip or a block on it, wired to 2+ buttons, do not open | anyone opening such a level; 5 levels in the collection | `79b0032`, jc-19 | **FIXED in `0cd4140` (jc-24)** |
| UB-2 | ...and wired to 1 button, that trap starts SHUT (MSCC and Tile World start it open) | anyone playing such a level; 6 solved routes lost, 2 unsolved changed | `79b0032`, jc-19 | **FIXED in `0cd4140` (jc-24)** |
| UB-3 | An empty trap with 2+ buttons, one held: open or shut depending on hash order | none seen in a real route | `79b0032`, jc-19 | **FIXED in `be0d0fb` (jc-25)** |
| UB-4 | Lynx `isTrapOpen` throws on a position no button is wired to | code only; nothing in the game asks | `79b0032`, jc-19 | **FIXED in `dc3bb11` (jc-27)** |
| UB-5 | Trap bits in a `.ser` savestate are numbered by hash order | anyone loading a `.ser` saved by another build | `79b0032`, jc-19 | **FIXED in `d974875` (jc-26)** |
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
| UB-22 | With Auto diagonals on, a quick tap of any game key makes no move | anyone with Auto diagonals on (it is off by default) | `d08e6ef`, ships in jc-23 | open |
| UB-23 | With Auto diagonals on, keys pressed within 50 ms make ONE move between them: a held key runs at about half speed, a wait pressed with two arrows is lost, three arrows go by table order | anyone with Auto diagonals on | `d08e6ef`, ships in jc-23 | open |
| UB-24 | With Auto diagonals on, MS arrows wait too, and Up held with Right pressed moves only Up | anyone with Auto diagonals on, playing MS | `d08e6ef`, ships in jc-23 | open |
| UB-25 | With Auto diagonals on, other input jumps ahead of a waiting move: a state saved with Shift+digit misses it | anyone with Auto diagonals on | `d08e6ef`, ships in jc-23 | open |
| UB-26 | With Auto diagonals on, the move is made from a background thread, beside the one that draws and takes input | anyone with Auto diagonals on; no clash seen | `d08e6ef`, ships in jc-23 | open |
| UB-27 | With Auto diagonals on, a key let go while the main window does not have the keyboard stays "held" | anyone with Auto diagonals on | `d08e6ef`, ships in jc-23 | open |
| UB-28 | `AutoDiagonals = 1` is OFF, and a value read as OFF is usually written back as it was, so the file can look ON | anyone editing the file by hand | `d08e6ef`, ships in jc-23 | open |
| UB-29 | A `.ser` savestate saved by jc-19 to jc-25 can load a trap wrong in other builds (two or more traps) | anyone keeping such a file; none in the maintainer's collection | `79b0032` (jc-19), exposed by jc-26 | open, accepted |

---

## The trap code (`79b0032`, released in jc-19)

The fork's four fixes to upstream's trap rework (`858dbd0`), merged in jc-15, were reverted. **All
four are back**, one per release (the fix layer), each by hand from `79b0032`'s diff: Fix 1 in jc-24
(`0cd4140`, UB-1 and UB-2 below), Fix 2 in jc-25 (`be0d0fb`, UB-3), Fix 4 in jc-26 (`d974875`,
UB-5) and Fix 3 in jc-27 (`dc3bb11`, UB-4). No trap issue is open; UB-29, the cost of Fix 4, is
accepted. FORK.md step 1 has the full engineering record, and *The fix layer* how each fix went back.

### UB-1 — a level with an occupied trap wired to two or more buttons does not open

**Status: FIXED in `0cd4140` (jc-24).** All 5 levels open again; 4 of their stored routes reach the
exit (IsaacD1-Old #116's is an unfinished attempt). Measured on the whole collection.

- **What a user saw (jc-19 to jc-23):** under MS, opening such a level showed *"Could not load level:
  Cannot invoke "java.util.Map.get(Object)" because "this.trapIndexMap" is null"* and did not open it.
  Next and Previous could not get past it; Level › Go to... skipped it.
- **Measured:** 5 levels in the 24,212-solution collection — EEfor5subs #16, IsaacD1-Old #116,
  JRB_CCLP1 #18, TCCLP #110, TomP3 #66 (four had solved routes). They could not be re-solved until
  jc-24.
- **Cause:** `MSLevel`'s constructor pressed every trap that starts holding Chip, a block or an ice
  block *before* it built its trap index; the second wire's press read the null map.
- **Fix:** build `trapButtons` and `trapIndexMap` before the start-of-level press (jc-15's Fix 1).
  Re-applied alone in jc-24, leaving upstream's hash-order trap numbering alone (that was UB-5, fixed
  in jc-26).
- **Checked by:** `MsCloneTrapTest` §6, the two-wire cases (pinned at upstream's crash from jc-19 to
  jc-23; ordinary checks again from jc-24).

### UB-2 — an occupied trap wired to one button starts shut

**Status: FIXED in `0cd4140` (jc-24)**, by the same change as UB-1. The 6 solved routes reach the exit
again, and the 2 unsolved ones end where they did in jc-18 (jc-19 to jc-23 had them end elsewhere).
Measured on the whole collection.

- **What a user saw (jc-19 to jc-23):** under MS, a block or Chip that started on a trap could not
  leave it; MSCC and Tile World let it. A route made against MSCC or Tile World stopped working.
- **Measured:** 6 solved routes no longer reached the exit — C1059-1 #47, EEfor5subs #26, geodave1
  #52, geodave4 #7, IsaacD1-Old #121, TLFC3 #89 — and 2 unsolved routes ended elsewhere (MO1 #61,
  TCCLPpgchip #2). A route re-solved under that behavior would not have replayed in Tile World; from
  jc-24 these routes work as they are, so none needs re-solving.
- **Cause and fix:** the same as UB-1 (with one wire the early press finds no button and silently
  does nothing).
- **Checked by:** `MsCloneTrapTest` §6, the one-wire cases (pinned from jc-19 to jc-23).

### UB-3 — an empty trap's state depends on hash order

**Status: FIXED in `be0d0fb` (jc-25).** Every arrangement now comes out open; the corpus against
jc-24 is unchanged, as expected for a latent defect.

- **What a user saw:** nothing. An empty trap with two or more buttons, one of them held, could read
  shut at the end of a tick, depending on which wire a hash map visited last.
- **Measured:** shut in 3 of the 6 test arrangements; no stored route depends on it. Traced on
  jc-24: a creature entering a trap re-checks its buttons and overwrites the bit, every other read
  in the engine is of an occupied trap (which upstream decides correctly), and nothing in the UI
  shows it. The wrong bit did reach savestates, in memory (rewind) and on disk.
- **Fix:** decide each trap once, after all its wires: open if any wire into it comes from a held
  button (jc-15's Fix 2, `MSLevel.finaliseTraps`). Tile World's `istrapopen()`, which reads every
  wire into a trap, is the reference for the arrangements tested. The same wire-by-wire update also
  shut, every time, a trap wired TWICE from one held button; the fix opens that one too (measured on
  jc-24's jar: shut; jc-25: open). A button wired to several traps still counts for its first wire
  only, as in every SuperCC (see the last section).
- **Checked by:** `MsCloneTrapTest` §7, all six arrangements (three were pinned at upstream's shut from
  jc-19 to jc-24; with the fix, none depends on hash order), and the trap wired twice.

### UB-4 — Lynx `isTrapOpen` throws on an unwired position

**Status: FIXED in `dc3bb11` (jc-27).** It answers shut there now, as MS always did; the corpus
against jc-26 is unchanged, as expected for code nothing in play reaches.

- **What a user saw:** nothing. Nothing in the program asks a Lynx level this question: Lynx springs
  traps through `LynxCreatureList.springTrappedCreature`, and the method's one caller,
  `BrownButton.isOpen`, is called by nothing in the jar (checked in jc-26's and jc-27's built jars;
  only the tests call it). Every Lynx route replayed unchanged throughout.
- **Fix:** answer "shut" when no button is wired there (jc-15's Fix 3), as MS does. Re-applied alone
  in jc-27.
- **Checked by:** `ConnectionTest` §4 (pinned at the exact `NullPointerException` from jc-19 to jc-26;
  an ordinary check from jc-27, failing on jc-26's jar and nowhere else), plus checks that a wired
  trap with nothing on its buttons is shut and, under Lynx, open with a block or Chip resting on
  either of its buttons (Tile World's `lxlogic.c` springs a Lynx trap every tick something rests on
  a wired button): the path past the guard, which no test reached under Lynx before. (What the
  method answers for a creature still moving, or a button wired to two traps, is upstream's and
  differs from what the engine does; see the last section.)

### UB-5 — `.ser` trap bits are numbered by hash order

**Status: FIXED in `d974875` (jc-26).** Each trap's bit is its first wire's record index again, so
`.ser` files from jc-15 to jc-18 load right, and those from jc-14 and earlier wherever each trap has
one wire; a state played in jc-26 and saved loads right in all of them (one loaded from another
build's file keeps that file's extra bits; see the last section). On a trap with two or more wires a jc-14
state saved while only a later wire's button held it loads that trap shut -- the cost FORK.md step
1 has documented since jc-15, now pinned by a check in `MsCloneTrapTest` §10. The reverse cost,
`.ser` files saved by jc-19 to jc-25, is UB-29. The maintainer's Dropbox holds no `.ser` file at
all (searched 2026-11-09). No stored route changes.

- **What a user saw (jc-19 to jc-25):** a savestate file saved by an earlier build could load with a
  trap wrongly open or shut: one from jc-15 to jc-18 on a level with more than one trap; one from
  jc-14 or earlier also wherever a trap has two or more buttons. And states saved then may load
  wrongly in those builds.
- **Measured:** the collection holds no `.ser` files.
- **Fix:** number each trap by its first wire's record index, as jc-14's per-wire bits were (jc-15's
  Fix 4, `LevelFactory.getTrapIndexMap`).
- **Checked by:** `MsCloneTrapTest` §10 (its three pins, at upstream's bit numbers from jc-19 to
  jc-25, are ordinary checks again).

### UB-29 — `.ser` files saved by jc-19 to jc-25 store trap bits in hash order

**Status: open, accepted.** The lasting cost of the baseline's trap numbering, raised by the jc-26
review so it has a row of its own rather than living inside UB-5.

- **What a user sees:** a savestate file saved by jc-19 to jc-25 (Save all states to disk) can load
  in jc-26 or later, or in jc-18 or earlier, with a trap wrongly open or shut, on a level with two or
  more traps where hash order and first-wire order number them differently. The build that saved it
  still loads it right.
- **Measured:** the maintainer's Dropbox holds no `.ser` file at all (searched 2026-11-09). How many
  levels number their traps differently under the two schemes was not counted.
- **Fix:** none planned. A `.ser` does not record which build wrote it, so jc-26 cannot tell a
  hash-ordered file from a first-wire one; the README tells a `.ser` owner to load it in the build
  that saved it. A future savestate version that records its numbering could close it.
- **Checked by:** nothing (it is a property of files, not of the code jc-26 runs).

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

The fork's four fixes from jc-16 were reverted. All four come back at once with `git revert 397c7bb`,
which also restores the compiled form classes and both baselines it changed; under the fix layer
(FORK.md) they go back one at a time instead, by hand from its diff. FORK.md step 3 has the
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
lines jc-17 had left out are in. Upstream's own guards stay. All of it comes back at once
with `git revert 7ba0d45 c57f217` (the fix layer takes it back one fix at a time instead; newest first: `7ba0d45` is a test fix made after CI showed that
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
as upstream does, inside the fork's own settings structure. All of it comes back at once with
`git revert 5d3f0a3` (the fix layer takes it back one fix at a time instead), which also restores `Gui`'s compiled class and both baselines; re-apply it before
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

## Auto-diagonals (`d08e6ef`, ships in jc-23)

The fork's rewrite of upstream's auto-diagonals (`5f928e0`, `fb8fe25`, `da35e33`, `8c95b3e`), merged
in jc-18, was reverted: `EmulatorKeyListener` and `ControlGUI` are upstream's byte for byte
(`ControlGUI` compiled with `formclass.ps1 -Compile`), `DiagonalCombiner` is gone, and
`SuccPaths.Controls`, `getControls` and `setControls` are upstream's, inside the fork's own settings
structure (the line is written where upstream writes it, between `UpRight` and `Rewind`). All of it
comes back with `git revert d08e6ef`, which also restores `AutoDiagonalTest`, `ControlGUI`'s
compiled classes and both baselines; re-apply it first (newest first), before step 5's. FORK.md
step 6 has the engineering record. Two code-level changes come back that no user sees:
`Controls.keyNames` is a mutable public static array again (nothing reassigns it), and the key
listener asks `getControls()` — twelve key codes parsed — on every key press AND release, switch on
or off, where jc-18 read one value (a dozen integer parses each time; not measured, too small to
notice).

**Nobody is affected unless Tools › Controls › Auto diagonals is on.** It is off by default, and with
it off upstream's listener acts on every key at once, exactly as before (`GuiWindowTest` §6 checks
that). How upstream's works, so the entries below make sense: with the switch on, EVERY game key —
the arrows, the diagonal keys, both waits, Backspace (rewind) and Enter (replay) — opens or joins a
50 ms window instead of acting. When the window closes, a `java.util.Timer` looks, on its own
thread, at the keys STILL HELD and makes one move from them: a diagonal if both keys of a pair are
held (the last pair in its table wins), otherwise one held key, picked by hash order. Every other
key (states, macros, menu shortcuts) and the mouse act at once.

### UB-22 — a tap makes no move

- **What a user sees:** with Auto diagonals on, a key pressed and let go within 50 ms makes no move
  at all. That is any game key, not only the arrows: a quick tap of a diagonal key, a wait,
  Backspace or Enter is lost too.
- **Measured:** `GuiWindowTest` §6, in a real window: a tapped arrow, a tapped wait key and a tapped
  diagonal key each make no move, at once or after the window.
- **Fix:** only the four arrows wait, and a tap counts (jc-18's `DiagonalCombiner`, after Tile
  World's rule: an axis nothing holds goes to its newest key tapped inside the window).
- **Pinned by:** `GuiWindowTest` §6 (three).

### UB-23 — keys inside one window make one move between them

- **What a user sees:** everything pressed within the same 50 ms makes ONE move.
  - **A held key runs at about half speed.** Windows repeats a held key about every 33 ms (its
    default rate); a repeat that lands in an open window is dropped, so only every other one moves.
    That is true of a held arrow and of a held Backspace.
  - **A wait pressed with Up and Right held is lost:** the diagonal only.
  - **Three arrows go by the order of upstream's table**, not by what was pressed last: Down, then
    Up, then Right held make Down-Right.
  - **Up held, Right tapped makes Up again**, where it should make Up-Right.
  - **Two keys that are not a pair** (an arrow and a wait, say) make ONE of the two, picked by hash
    order, which can differ from one start of SuperCC to the next.
- **Measured:** `GuiWindowTest` §6:
  - four presses of Up inside one window make one move;
  - Up, Right and Space make the diagonal only;
  - Down, Up, Right make Down-Right;
  - Up held with Right tapped makes Up, Up.

  The half speed is reasoned from the code at a 33 ms repeat, not timed. The hash-order pick is
  from the code too (a `HashSet` of enum constants, whose hash codes are identity hashes) and is
  not pinned, since it can change between runs.
- **Fix:** only arrows wait, and any other key makes the waiting move first and then acts. Each axis
  goes to its newest held key. A repeat of an arrow already held is a move of its own, at once
  (jc-18).
- **Pinned by:** `GuiWindowTest` §6 (four).

### UB-24 — under MS, arrows wait too, and a key is lost

- **What a user sees:** MS has no diagonal moves, but with the switch on its arrows wait like
  Lynx's. A tap makes no move (UB-22). Up held with Right pressed makes the diagonal, which
  `SuperCC.tick` reduces to its vertical half, so Chip moves Up and the Right is lost; Tile World's
  MS mode moves Right.
- **Measured:** `GuiWindowTest` §6, on an MS set: an arrow waits; Up held with Right pressed records
  only Up.
- **Fix:** no window under MS at all: arrows act as with the switch off (jc-18).
- **Pinned by:** `GuiWindowTest` §6 (two).

### UB-25 — other input jumps ahead of a waiting move

- **What a user sees:** keys that are not game keys, and the mouse, act at once while a move waits,
  so they land BEFORE it, and the recorded route is not what was typed:
  - Shift+digit saves a state without the move;
  - a digit loads a state, and the waiting move is then made after the load;
  - macro keys behave the same way;
  - the mouse too: a drag of the time slider rewinds, and a wheel turn over the move list moves
    through it, before the move is made.
- **Measured:** `GuiWindowTest` §6: Up, then Shift+1 inside the window; loading state 1 afterward
  undoes the Up. The other cases follow from the same code (only game keys wait, and the key
  listener never sees the mouse) and are not pinned.
- **Fix:** a waiting arrow's move is made first, before any other key, any mouse press, drag or
  wheel turn, and any loss of focus: `keyPressed` plus an AWT event listener that runs before the
  component sees the event (jc-18).
- **Pinned by:** `GuiWindowTest` §6 (one).

### UB-26 — the move is made on a background thread

- **What a user sees:** usually nothing. The waiting move is made from the timer's own thread, while
  the event thread draws the screen and handles every other key and click; the held keys are a
  plain `HashSet` that both threads use. A clash could show as a drawing glitch, an internal error
  in the log, or a move made while a level is being changed. It can also LOSE the move: if a key is
  pressed or let go while the timer reads the held keys, that read can fail
  (`ConcurrentModificationException`), and then no move is made at all. And the flag that says a
  window is open is a plain field written on one thread and read on the other, so in principle a
  stale value could stop every later window from opening. None of this was seen in testing.
  - Each window also starts a thread of its own (a new `java.util.Timer` per window, never
    canceled), which lingers until garbage collection; holding a key makes about 15 a second.
  - Tools › Controls builds its window in a `SwingWorker`, off the event thread, and reads the switch
    there with `getControls()`, as the key listener does on the event thread. That call rewrites the
    settings file only when a key code does not parse; launch has already repaired any such code,
    so in practice both only read.
- **Measured:** `GuiWindowTest` §6: with the event thread kept busy for the whole window, the move
  is made anyway.
- **Fix:** a `javax.swing.Timer`, which fires on the event thread, created once and reused; Tools ›
  Controls reads the switch without `getControls()` (jc-18).
- **Pinned by:** `GuiWindowTest` §6 (one).

### UB-27 — a key let go elsewhere stays held

- **What a user sees:** upstream sees a key being let go only through the main window's key listener.
  Let go of a game key while something else has the keyboard, and SuperCC goes on counting it as
  held. That something else can be another program (Alt+Tab), a dialog, a tool window, or the move
  list. Every later window includes the stuck key: a tapped arrow comes out as that key, and a held
  one as a diagonal with it. This lasts until the key is pressed and let go again in the main
  window. The same happens to a key let go after the switch was turned off, or after that key was
  remapped, while it was held: upstream's release does nothing with the switch off, or for a code
  that no longer maps to a key.
- **Measured:** `GuiWindowTest` §6: Up held, let go on the move list, then Right tapped records Up, Up.
- **Fix:** releases are seen anywhere in the program (the AWT event listener), and focus leaving the
  program forgets what is held (jc-18).
- **Pinned by:** `GuiWindowTest` §6 (one).

### UB-28 — `AutoDiagonals = 1` is OFF, and the file can say otherwise

- **What a user sees:** only `true`, in any casing, turns the switch on. `1` is OFF, though the
  README of jc-18 to jc-22 listed it as ON. A value read as OFF is also written back as it was by
  every settings write except one that writes the Controls section itself, so the file can say
  `AutoDiagonals = 1` while the switch is off. The exceptions write `true` or `false`: closing
  Tools › Controls, and launch repairing a key code that does not parse. So only a hand edit meets
  this, and it lasts until one of those.
- **Measured:** `SettingsTest` §14 (the raw write-back, through a tileset write); the exceptions are
  from the code (`setControls` writes `String.valueOf` of the switch).
- **Fix:** read it through `optedIn()` (`true` or `1`), and write it as the value read (CLAUDE.md
  §5; jc-18).
- **Pinned by:** `SettingsTest` §14 (two).

---

## Seen while testing — NOT introduced by the baseline

These were already in upstream, and in the fork before the baseline began. They are listed so they
are not lost.

- **Seed Search's button still says "Pause" after a search finishes by itself.** Seen in the
  2026-11-08 playtest (CCLP1 #1, the last 3,000 seeds: 3000/3000, button "Pause"). Since the search
  is no longer running, pressing it starts a new round of threads rather than pausing; what that
  round does was not checked. Upstream and jc-16 to jc-19 alike; the fork never touched that code.
- **A held brown button counts only for the FIRST trap it is wired to, at the end of a tick.**
  `MSLevel.finaliseTraps` keeps a set of buttons already used (`pressedButtons`), so a button wired
  to two traps holds only the first in its own list, which is record (file) order, not hash order.
  That is the same first-wire rule as Tile World's `springtrap()`; Tile World's `istrapopen()`, used
  when a creature enters a trap, reads every wire instead, and so does SuperCC's own entry check.
  Both engines are split the same way, so this is worth knowing rather than a known divergence:
  whether they ever disagree because of it is not established, and no stored route shows a
  difference (desync count 0). SuperCC has done this since before upstream's trap rework, and
  jc-25's Fix 2 keeps it. Recorded from the jc-25 review so it is not lost.
- **Leaving a trap releases every unheld wire, which shuts the trap's ONE bit even while another
  button holds it.** `MSCreature`'s release loops (a creature leaving a trap, and the two block-push
  paths) call `release` on each wire whose button is up; since upstream's rework gave each trap a
  single bit, that shuts the trap mid-tick although another wire's button is held. Entering re-checks
  the buttons, and from jc-25 the end-of-tick update opens it again, so no stored route depends on
  it. In upstream since its trap rework, and in every fork build since jc-15 merged it. Raised by
  the jc-25 review; verified in the code, not measured in play.
- **A loaded `.ser`'s trap bits that belong to no trap are kept, and saved back out.** `MSSavestate`
  restores the trap bits as they are in the file, and the end-of-tick update only ever touches the
  bits of known traps, so any other bit set in a file from a different numbering stays set and is
  written into the next save. It changes nothing in play -- nothing reads those bits -- but a state
  that started from another build's file can carry that build's bits back to it: load a jc-14 file
  with only a two-wire trap's second bit set, save it from jc-26, and jc-14 then opens that trap. So
  "what a build saves loads right in the others" holds for states played in that build. In upstream
  and in every fork build (jc-15 to jc-18 kept the bits the same way); raised by the jc-26 review,
  verified in the code, not measured. The maintainer has no `.ser` files.
- **Lynx `isTrapOpen` answers a broader question than the one the Lynx engine acts on.** Since
  upstream's trap rework (`858dbd0`, kept from jc-15) it asks whether any creature's POSITION is a
  button wired to the trap. The engine springs a trap only once a creature has ARRIVED on a button
  (`getTimeTraveled() == 0`, in `LynxCreatureList.tick` and `LynxLevel.moveChip`, as Tile World's
  `lxlogic.c` waits for `cr->moving <= 0`), and only the trap on that button's FIRST wire
  (`getBrownButtons().get`). So it answers open for a creature still moving onto a button, and for
  every trap a held button is wired to. Nothing in the program reads the answer -- its one caller,
  `BrownButton.isOpen`, is called by nothing in the jar -- so no route is affected; jc-27's Fix 3
  changed only the unwired case, and its tests pin only a creature at rest on a single-wired
  button, where the two agree. Raised by the jc-27 review; verified in the code, not measured.
