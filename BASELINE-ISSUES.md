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
| UB-6 | A tick that reports both "discard" and "automatic ticks" is recorded as a wait, so the route saves short | nobody today: no real tick does that | `923d07c`, ships in jc-20 | **FIXED in `1ec3b47` (jc-40)** |
| UB-7 | Seed Search with a negative start: one thread does all the work | anyone typing a negative start | `397c7bb`, ships in jc-20 | **FIXED in `06ebf90` (jc-42)** |
| UB-8 | Seed Search's threads race at the start: "running" can drop mid-search, a Pause can be undone | anyone, mostly on short searches | `397c7bb`, ships in jc-20 | **FIXED in `1414d6b` (jc-44)** |
| UB-9 | Seed Search never shows a success at a negative seed as the example | anyone searching from a negative start | `397c7bb`, ships in jc-20 | **FIXED in `86e23d1` (jc-43)** |
| UB-10 | Seed Search's Pause flag is not `volatile` | nobody observed (theoretical) | `397c7bb`, ships in jc-20 | **FIXED in `b2c383c` (jc-41)** |
| UB-11 | With no level set open, most menu controls fail with an internal error | everyone, from launch until a set is opened | `c57f217`, ships in jc-21 | **FIXED in `d57d419` (jc-28)** |
| UB-12 | Canceling Go to... or Set RNG Seed fails with an internal error | anyone who cancels either | `c57f217`, ships in jc-21 | **FIXED in `cd5a644` (jc-31)** |
| UB-13 | Canceling the file chooser of Solution › Open, Search for seeds or Load states fails | anyone who cancels one | `c57f217`, ships in jc-21 | **FIXED in `7469c09` (jc-32)** |
| UB-14 | A set whose level 1 will not load opens badly: an error, then no level, or the previous set's level under the new file | none of the 331 sets in the collection | `c57f217`, ships in jc-21 | **FIXED in `48a3eca` (jc-29)** |
| UB-15 | A level whose savestates cannot be built is left half-loaded | nobody today (no level reaches it) | `c57f217`, ships in jc-21 | **FIXED in `e08c6b5` (jc-30)** |
| UB-16 | Choosing a custom tileset saves it as tileset number 5, which builds before jc-18 do not have (they start in their default tileset, with an internal error) | anyone sharing the settings file with an older build | `5d3f0a3`, ships in jc-22 | **FIXED in `f6d73e7` (jc-37)**; a stored 5 is read as custom on |
| UB-17 | Custom tileset pictures are saved as full paths, which the other computer does not have | anyone sharing the folder between computers | `5d3f0a3`, ships in jc-22 | **FIXED in `0614b86` (jc-38)** |
| UB-18 | Canceling View › Tileset › custom asks for the second picture anyway, then shows an error, and leaves the mark on custom | anyone who cancels it | `5d3f0a3`, ships in jc-22 | **FIXED in `bd2b915` (jc-35)** |
| UB-19 | A custom picture is saved before it is known to load; a bad one fails with an internal error or an unclear message | anyone choosing a wrong file | `5d3f0a3`, ships in jc-22 | **FIXED in `cb55dbf` (jc-33)** |
| UB-20 | A saved MS tileset that cannot be drawn breaks startup partway: the default tileset, no reason given, the saved tile size and TWS notation not applied (until jc-34 this row said "does not start (no window)": wrong, see the entry) | anyone with a bad TilesheetNum or missing custom pictures | `5d3f0a3`, ships in jc-22 | **FIXED in `9e6166a` (jc-34)** for a plain start, and with a set on the command line since jc-36 (UB-21) |
| UB-21 | A Lynx tileset that cannot be drawn, or a failing custom tileset at a window resize, leaves the screen wrong | anyone with such a choice | `5d3f0a3`, ships in jc-22 | **FIXED in `e30ab77` (jc-36)** |
| UB-22 | With Auto diagonals on, a quick tap of any game key makes no move | anyone with Auto diagonals on (it is off by default) | `d08e6ef`, ships in jc-23 | **FIXED in `275a07c` (jc-46)** |
| UB-23 | With Auto diagonals on, keys pressed within 50 ms make ONE move between them: a held key runs at about half speed, a wait pressed with two arrows is lost, three arrows go by table order | anyone with Auto diagonals on | `d08e6ef`, ships in jc-23 | **FIXED in `275a07c` (jc-46)** |
| UB-24 | With Auto diagonals on, MS arrows wait too, and Up held with Right pressed moves only Up | anyone with Auto diagonals on, playing MS | `d08e6ef`, ships in jc-23 | **FIXED in `275a07c` (jc-46)** |
| UB-25 | With Auto diagonals on, other input jumps ahead of a waiting move: a state saved with Shift+digit misses it | anyone with Auto diagonals on | `d08e6ef`, ships in jc-23 | **FIXED in `275a07c` (jc-46)** |
| UB-26 | With Auto diagonals on, the move is made from a background thread, beside the one that draws and takes input | anyone with Auto diagonals on; no clash seen | `d08e6ef`, ships in jc-23 | **FIXED in `275a07c` (jc-46)** |
| UB-27 | With Auto diagonals on, a key let go while the main window does not have the keyboard stays "held" | anyone with Auto diagonals on | `d08e6ef`, ships in jc-23 | **FIXED in `275a07c` (jc-46)** |
| UB-28 | `AutoDiagonals = 1` is OFF, and a value read as OFF is usually written back as it was, so the file can look ON | anyone editing the file by hand | `d08e6ef`, ships in jc-23 | **FIXED in `f4638be` (jc-45)** |
| UB-29 | A `.ser` savestate saved by jc-19 to jc-25 can load a trap wrong in other builds (two or more traps) | anyone keeping such a file; none in the maintainer's collection | `79b0032` (jc-19), exposed by jc-26 | open, accepted |
| UB-30 | A set opened at launch whose level 1 will not load: its other levels cannot be reached (Go to... is guarded while no level is loaded) | anyone opening such a set before any other; none of the 331 sets in the collection | `d57d419` (jc-28): UB-11's fix back before UB-14's | **FIXED in `48a3eca` (jc-29)**, with UB-14 |
| UB-31 | With no custom pictures chosen yet, View › Tileset › custom's two choosers open in the user's default folder (Documents), not the Chip's Challenge folder, and the second does not open where the first picture was | anyone choosing a custom tileset for the first time | `5d3f0a3`, ships in jc-22 (missed by that revert's tracker entries; recorded in jc-35) | **FIXED in `ef27c8d` (jc-39)** |

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

Its one fix is back since jc-40 (`1ec3b47`, UB-6): the whole step is back.

### UB-6 — a tick reporting both bits is recorded as a wait

**Status: FIXED in `1ec3b47` (jc-40). 🏁 With it, the recorder step (`923d07c`) is back.**
jc-15's condition, re-applied alone in `SuperCC.tick`: a discarded tick is recorded as a wait only
when it did not also report automatic ticks; with both bits, the key is kept (a capital, as the
multi-tick loop leaves it). Apart from the comment's wording, `tick()` is jc-19's exactly.
**Measured (2026-11-16, `InputDiscardTest` §5 on jc-39's jar):** through the wrapped level, jc-39
records `r--UUUUUu` -- two one-tick waits where the engine ran a whole move -- and the fix
`rUUUUUUUu`. §5 is an exact check again, and on jc-39's jar it is the only failure. 5 defects
planted in `SuperCC.tick`, all caught (upstream's line; the caller's multi-tick flag in place of the
engine's bit; a wait only when both bits are set; a wait on every tick without automatic ticks; the
multi-tick loop leaving keys lowercase). Corpus against jc-39: 24,217 of 24,217 identical. What SuperCC records on loading every
stored solution, against jc-39: all 24,216 saves it replays record identically (the 24,217th, `CC1\133.json`, is a name the harness cannot place, on every jar).

- **What a user saw (jc-20 to jc-39):** nothing. If `Level.tick` had ever reported a key as ignored
  *and* run the automatic ticks of a move, `SuperCC.tick` would have recorded a one-tick wait where
  the engine ran a whole move, and the saved route would replay short (FORK.md step 2 measured 3
  ticks).
- **Measured:** no real tick does it: all 24,212 routes record exactly as on jc-19 (a census of the
  recorded move lists, whose control found step 2's 40 re-recorded Lynx routes), and step 2's fuzzing
  saw it in none of 104,756 ticks. But `LynxLevel.tick` *can* return both bits, so from jc-20 to
  jc-39 this was load-bearing reasoning with nothing guarding it; since jc-40 the recorder keeps
  the key either way.
- **Fix:** keep the key when the automatic-tick bit is also set (jc-15's recorder fix), re-applied
  by hand in jc-40 (`1ec3b47`).
- **Checked by:** `InputDiscardTest` §5, through a level wrapped to report both bits (pinned at
  upstream's `r--UUUUUu` from jc-20 to jc-39; an exact check of `rUUUUUUUu` since jc-40).

## Seed Search (`397c7bb`, ships in jc-20)

The fork's four fixes from jc-16 were reverted. Until jc-41 all four came back at once with
`git revert 397c7bb`, which also restores the compiled form classes and both baselines it changed;
with UB-10 back by hand that revert no longer applies cleanly. Under the fix layer (FORK.md) they go
back one at a time, by hand from its diff. FORK.md step 3 has the
engineering record. **All four are back**: UB-10, the `volatile` flags (jc-41, `b2c383c`), UB-7, the negative start (jc-42, `06ebf90`), UB-9, the example seed (jc-43, `86e23d1`), and UB-8, the thread start (jc-44, `1414d6b`).

### UB-7 — a negative start does not share the work

**Status: FIXED in `06ebf90` (jc-42).** jc-16's `(long)` on `Integer.MAX_VALUE - start`,
re-applied alone in `SeedSearch` (a form class: compiled with `formclass.ps1 -Compile`, form
baseline updated). `SeedSearchTest` §3's five pins are exact checks again: from start -5 with four
threads, each thread searches part of its own quarter, and the attempt total is exactly what the
threads covered. On jc-41's jar exactly those five fail (threads 0-2 searched nothing, thread 3
searched from upstream's start). 3 defects planted, all caught (the subtraction in `int` again, as
upstream; the cast applied after the `int` subtraction has already wrapped; the start added instead
of subtracted). Corpus against jc-41: 24,217 of 24,217 identical.

- **What a user saw (jc-20 to jc-41):** Solution › Search for seeds from a negative start: one thread searches
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
- **Fix:** `(long) Integer.MAX_VALUE - start`, re-applied by hand in jc-42 (`06ebf90`).
- **Checked by:** `SeedSearchTest` §3 (each thread's outcome, and the attempt total; pinned at
  upstream's split from jc-20 to jc-41).

### UB-8 — the search threads race at the start

**Status: FIXED in `1414d6b` (jc-44).** jc-16's thread start, re-applied alone in `SeedSearch` (a
form class), with both of its review fixes: every thread is built, all are counted alive at once,
`running` and `killFlag` are set once, and only then are the threads started; `run()` no longer
writes either flag, and the last thread's cleanup clears `killFlag` before it publishes `running =
false`. `SeedSearchTest` §2's 3,000-seed skip and 8-seed pin are exact checks again, and new checks
were added: both searches in §2 also watch the alive count (it must fall to zero once per search
and never go below zero), §5 presses Resume for the first time (the search must go on, and a second
Pause must stop it), and §7 runs a real thread's `run()` with the Pause already set (it must make no
attempt). On jc-43's jar 4 checks fail every run, and a fifth (the second Pause) in some runs: that
one is the lost Pause itself, a race on jc-43's code. 7 defects planted: 5 caught. The two that
survive are recorded rather than chased:
- **Counting the threads after starting them** is a real race, but the four starts and the count
  are microseconds apart, and a thread would have to be scheduled and finish inside that gap. No
  test here can force it.
- **The cleanup's old write order** (`running = false` before `killFlag = false`) is nearly
  equivalent now: the listener clears `killFlag` itself before it starts a new batch, so the old
  order matters only if the cleanup thread stalls between two adjacent writes for a whole thread
  build, while a person presses Pause.

Corpus against jc-43: 24,252 of 24,252 identical.

- **What a user saw (jc-20 to jc-43):** each thread starts as soon as it is built, and building the next one takes
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
  clear the Pause flag before publishing "stopped" (jc-16), re-applied by hand in jc-44 (`1414d6b`).
- **Checked by:** `SeedSearchTest` §2, §5 and §7 (from jc-20 to jc-43, §2 pinned five 8-seed
  searches on "running ever went false more than once", and skipped the 3,000-seed check, because a
  race cannot be pinned).

### UB-9 — a success at a negative seed is never shown

**Status: FIXED in `86e23d1` (jc-43).** jc-16's `Long.MIN_VALUE` sentinel, `NO_SUCCESS`, re-applied alone in `SeedSearch`
(a form class) at all four of its sites: the window's starting value, each thread's, and the two
tests, now `!=`. `SeedSearchTest` §4's pin is an exact check again (a success at seed -3 is shown),
and a new check builds a thread as the window does with an empty range and runs its real `run()`:
the example seed is left alone, and the alive count comes back. On jc-42's jar exactly the first fails (the second holds on upstream
too, whose -1 and `>= 0` agree). 5 defects planted, one per site and one in `run()`'s final report; the thread's starting value
SURVIVED the existing checks (a thread that found nothing would report seed -1 as a success once
the tests are `!=`), which is why the new check exists, and all 5 are caught with it. Corpus
against jc-42: 24,219 of 24,219 identical.

- **What a user saw (jc-20 to jc-42):** "Example seed" stays blank although successes are counted.
- **Measured (same playtest):** 219,765 successes from start -5, and no example seed shown.
- **Cause:** "no success yet" is -1, and a success is shown only if `>= 0`.
- **Fix:** a `Long.MIN_VALUE` sentinel compared with `!=` (jc-16), re-applied by hand in jc-43
  (`86e23d1`).
- **Checked by:** `SeedSearchTest` §4 (pinned from jc-20 to jc-42).

### UB-10 — Seed Search's Pause flag is not `volatile`

**Status: FIXED in `b2c383c` (jc-41).** jc-16's `volatile` on `killFlag` and `running`,
re-applied alone in `SeedSearch` (a form class: compiled with `formclass.ps1 -Compile`, form
baseline updated). Checked by a new `SeedSearchTest` §6, which reads the modifier on the two
fields of the class that ships: no timing test can show the difference on HotSpot, but the
modifier is what the memory model's promise rests on, and it can be read exactly. On jc-40's jar
exactly those two checks fail; 3 defects planted, all caught (either field without it, and
neither, as upstream). Corpus against jc-40: 24,217 of 24,217 identical. It does not fix UB-8:
each thread's `run()` still writes both flags as it starts, so a thread starting after a Pause
clears it.

- **What a user saw (jc-20 to jc-40):** nothing observed. `killFlag` and `running` are shared
  between the event thread and the search threads (Pause sets `killFlag`, read in every search
  loop; the threads set `running`, read by the window); without `volatile`, Java does not promise
  either side ever sees the other's write. HotSpot happens to make it work: Pause worked in every
  test and playtest. (Until jc-41 this entry said both are set on one thread and read in the
  search loops; the jc-41 review corrected it.)
- **Fix:** declare both `volatile` (jc-16), re-applied by hand in jc-41 (`b2c383c`).
- **Checked by:** `SeedSearchTest` §6, from jc-41 (until then nothing: the entry said a memory-model
  guarantee cannot be tested reliably, which holds for its EFFECT; the modifier itself can be).

## The no-level-loaded guards (`c57f217`, ships in jc-21)

Upstream's `72476bc` guarded the state every launch starts in — no level set open — in three places:
the key listener, the main window's play button and sliders, and six Level-menu items. jc-17 extended
that to every menu and fixed what it found on the way; all of it was reverted, and the two upstream
lines jc-17 had left out are in. Upstream's own guards stay. All of it comes back at once
with `git revert 7ba0d45 c57f217` (the fix layer takes it back one fix at a time instead; newest first: `7ba0d45` is a test fix made after CI showed that
Paste solution depends on the clipboard), which also restores `ChooseTileSize`'s compiled class
and both baselines. If step 5 (custom tilesets, below) is still reverted, re-apply it first: both
steps rewrote View › Tileset's listener in `MenuBar`.
FORK.md step 4 has the engineering record. **The menu guard (UB-11) is back since jc-28
(`d57d419`), opening a set at its first level that loads (UB-14) since jc-29 (`48a3eca`), the
all-or-nothing level load (UB-15) since jc-30 (`e08c6b5`), Cancel on Go to... and Set RNG Seed
(UB-12) since jc-31 (`cd5a644`), and a canceled file chooser (UB-13) since jc-32 (`7469c09`): the
whole step is back**, each fix by hand from `c57f217`'s diff (a whole `git revert` no longer
applies).

"Fails with an internal error" below means a `NullPointerException` on the event thread: nothing
visible happens except that it is written to the error log (`succ_error-<MACHINE>.log`).

### UB-11 — most menu controls fail before a level set is open

**Status: FIXED in `d57d419` (jc-28).** jc-17's `MenuBar.requireALevel()` is back, re-applied alone
(the Copy macro hover check and `ChooseTileSize`'s dead line with it): with no level set open, every
control that needs a level does nothing, and the 28 that threw on jc-21 to jc-27 (plus Paste
solution, clicked again) are ordinary checks. As in jc-17 to jc-20, Tools › Record gif and
TWS › Load solution now do nothing with no level open as well (on upstream's menus Record gif
opened its window and Load solution showed an error message; neither threw, so neither is listed
below). The 22
controls that work without a level are now each checked to DO their job (a chooser shown, a size
or flag changed, a window opened, a tileset drawn): blocked by the guard, one would neither throw
nor show anything. A tileset chosen with no level, custom included, is saved as the MS choice --
so custom chosen before a set is open is now saved in upstream's form (UB-16, UB-17), exactly as
when a level is open, and UB-20 can follow from it the same way. On jc-27's jar `GuiWindowTest`
fails exactly the 30 converted checks, Paste solution, 5 new checks (4 in the walk, and "the menu
bar has its guard"), and the child's exit check -- 37;
corpus against jc-27: 24,217 of 24,217 identical. Re-applied with one change to jc-17's code: the
wrapper re-adds a button's listeners oldest first, so they keep Swing's order (jc-17's loop reversed
them; latent, since no item has two). Three limits stay, recorded in CLAUDE.md §6: a listener added
to a menu item after the constructor is not guarded, a toggle or radio item in a guarded menu would
still move its mark (none exists), and the guard asks only `isLevelLoaded()` (see UB-15, fixed since
jc-30, after which a loaded level always has its savestates). And one
cost, because UB-14's fix is not back yet: **UB-30** (a set opened at launch whose level 1 fails
has no reachable level).

- **What a user saw (jc-21 to jc-27):** until a level set is opened, these controls fail instead of doing nothing:
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
- **Pinned by (jc-21 to jc-27):** `GuiWindowTest` §2: 28 of them, with a check that all 28 were found, the tileset
  save, and custom with two pictures chosen (its file choosers answered with real pictures). Paste
  solution is not clicked there, so the run never depends on — or writes to — the system
  clipboard. (The custom pin still fails on jc-21's code, which already had this step reverted; it
  flips on jc-20's.) **Checked by (jc-28 on):** the same walk, every pin a check, Paste solution
  clicked (the guard stops it before it reads the clipboard), plus the 22 jobs and "the controls
  that need a level open nothing".

### UB-12 — canceling Go to... or Set RNG Seed fails

**Status: FIXED in `cd5a644` (jc-31).** jc-17's two lines are back, re-applied alone: each box's
reply is tested for `null` (Cancel, or the close box) as well as for empty, so canceling either
does nothing, as in jc-17 to jc-20; with comments stripped, both lines are jc-20's exactly.
`GuiWindowTest` §4 now starts each box afresh on a level of its own (Go to... on level 1,
Set RNG Seed on level 2) with two moves made, then answers it five ways in a real window, as
a person would: its close box (the window-closing event, so `JOptionPane`'s own handler runs),
its Cancel button, and OK with nothing typed, with a letter, and with a number. Canceling must
leave the level exactly as it was (the same `Level` object and the same moves: nothing reloaded,
no move lost) with nothing else shown; OK with nothing typed must do nothing too (upstream's
own behavior, kept); a letter must show its one message ("Please Enter a Whole Number"); and
a number must do the box's job (Go to... loads level 2, not the level 1 that is open; Set RNG
Seed reloads the same level with seed 7, its moves kept), which also proves the answers reach
the box. On jc-30's jar `GuiWindowTest` fails exactly the 4 cancel checks (both ways, both boxes)
and the child's exit check -- 5; 14 defects planted in `MenuBar` (upstream's test in either box,
an empty reply no longer quiet, every reply returning, Cancel reloading the level, reseeding
it or rewinding a move, an empty reply loading level 1, a letter dropped without its message,
the fix comparing with `== ""`, Go to... ignoring the number typed, Set RNG Seed reloading
another level or dropping the moves, a letter sent to a load that fails), all caught; corpus
against jc-30: 24,217 of 24,217 identical.

- **What a user saw (jc-21 to jc-30):** pressing Cancel (or the close box) in either box failed with
  an internal error, with a level open; the level itself stayed as it was (measured on jc-30's jar).
  (With none open, jc-21 to jc-27 failed on Go to... too; since jc-28's menu guard, UB-11, neither
  box opens without a level.)
- **Cause:** the reply to a canceled box is `null`, and upstream tests it for empty only.
- **Fix:** test for `null` too (jc-17).
- **Pinned by (jc-21 to jc-30):** `GuiWindowTest` §4 (both, with a level open, canceled by the close
  box). (§2 pinned Go to... with none open until jc-28.) **Checked by (jc-31 on):** §4's five answers
  to each box, above.

### UB-13 — canceling a file chooser fails

**Status: FIXED in `7469c09` (jc-32).** jc-12's line is back in `MenuBar.openFileBytes`,
re-applied alone: a `null` reply from the chooser (Cancel, or the close box) returns `null`
at once, which all three callers already treat as nothing to do, in place of upstream's
`Objects.requireNonNull`; and upstream's `assert tws != null` after the TWS save chooser is
gone, as jc-17 left it out. With comments stripped, `MenuBar` now differs from jc-20's only
by step 5's tileset code (still reverted) and jc-28's listener order, so the whole no-level
step (UB-11 to UB-15) is back. `GuiWindowTest` §4b answers every file chooser the menus open
with a level loaded -- but View › Tileset › custom's, whose Cancel is UB-18 (pinned in
§2's walk; jc-32 said §5b in error) -- in a real window, as a person would: its close box (the window-closing event) and
its own Cancel button, which must change nothing and show nothing else -- the same `Level`
object, moves, savestates object and TWS folder, no file added, no window opened -- and, for
the four UB-13 concerns (Solution › Open, Search for seeds, Load states from disk, TWS ›
Write solution to new tws), a file too: for the three that open one, a file that is not there,
which must bring jc-12's own message ("There is no file:" and the path) and nothing else; for
the TWS save, a new file in a folder of its own, which must be the one file written and its
folder remembered for the next tws chooser. The other four (Save as, Save all states to disk,
Open tws, Open levelset) never threw on Cancel; they are covered so the whole family is. Each
item starts afresh on level 1 with two moves made and the TWS folder in a folder of its own. The
child JVM runs with `-ea:graphics.MenuBar`, so the canceled TWS save meets an `assert` there as
`java -ea` would. On jc-31's jar `GuiWindowTest` fails exactly the 8 cancel checks (6 throwing
`NullPointerException`, the TWS save's 2 `AssertionError`) and the child's exit check -- 9; 13
defects planted in `MenuBar`, all caught (upstream's two lines back, Cancel showing an error,
Cancel read as an empty file, jc-12's message dropped or replaced by the pre-jc-12 handling,
Cancel moving the TWS folder, a written file's folder not remembered, Cancel falling back to
the default path, Cancel on Load states rewinding a move, a save writing a stray file even when
canceled, a TWS save losing a move, Open tws's own Cancel guard removed); the two TWS-folder
defects survived the first version of the test, whose TWS folder was the child's own folder,
until it got folders of its own; corpus against jc-31: 24,217 of 24,217 identical.

- **What a user saw (jc-21 to jc-31):** canceling the file chooser of Solution › Open, Solution ›
  Search for seeds or Solution › Load states from disk failed with an internal error, with a level
  open (since jc-28's menu guard, UB-11, none of the three opens its chooser without one); nothing
  else changed. (jc-12 fixed this; its clearer message for a file that is not there stayed.) With
  assertions on (`java -ea`, never a normal launch), canceling TWS › Write solution to new tws
  failed too, on upstream's `assert tws != null`.
- **Cause:** upstream's `72476bc` wraps the chooser's reply in `Objects.requireNonNull`, which jc-17
  left out because it undoes jc-12; the baseline had upstream's line.
- **Fix:** treat a `null` reply as "nothing to do", as jc-12 did.
- **Pinned by (jc-21 to jc-31):** `GuiWindowTest` §4 (Solution › Open, then Cancel, with a level
  open). **Checked by (jc-32 on):** §4b, above: three answers to each of the four choosers, and the two
  cancels of the menus' four other choosers.

### UB-14 — a set whose level 1 will not load opens badly

**Status: FIXED in `48a3eca` (jc-29).** jc-17's `openLevelset` is back, re-applied alone: a set
opens at its first level that loads, every failure before it is reported once by position, a set
where nothing loads puts the previous set (or none) back before its one error is shown, and a level
that loaded is not reported as failed when only the repaint after it threw; `loadLevel`'s body is
`tryLoadLevel`, which returns its failure, and `loadLevel` reports it exactly as before. UB-30 is
fixed with it: an open now either loads a level or leaves no new file open. One change to jc-17's
code: the previous set goes back in a `finally`, so an `Error` escaping the loop (not an Exception,
which `tryLoadLevel` catches) puts it back too -- the jc-29 failure-mode review measured one, a jar
replaced under a running instance throwing `NoClassDefFoundError` out of `parseLevel`, which left
set B's file under set A's level on jc-28 (measured; jc-20's code does the same). NOT fixed, because jc-17's code never
fixed them either, and moved to the last section: the title bar keeping the previous level's name
when the repaint throws, and a later load (Next, Go to...) whose repaint throws being reported as
"Could not load level". One cost, as in jc-17 to jc-20: a damaged file whose header claims more
levels than it holds is tried level by level -- a 4-byte file with only an MS signature claims
65,535 (`DatParser` reads 0xFF past the end), measured at 4.3 s headless on the candidate against
0.15 s on jc-28 -- on the event thread, so the window would freeze that long (inferred, not timed in a window); a real set costs tens of milliseconds. Until
jc-30 fixed UB-15, a level whose savestates cannot be built would have been counted as loaded by the
open (see UB-15). Measured: `EngineTest` §6c/§6d's 7 pins and `GuiWindowTest` §3a/§5's 6 are checks
again, plus checks that no set is left open without a level, that an Error puts the previous set
back, and that `loadLevel` still reports a failure; on jc-28's jar `EngineTest` fails 12 and
`GuiWindowTest` 8 (6, its no-file-left-open check, and its exit check), each accounted for; 13 defects planted in `SuperCC` all
caught; a failure-mode fuzz of 120,000 opens of randomly damaged level-1 records kept the set file
and the level together every time (in the 40,000 of those opens run against jc-28, it broke 23,792 times; the fuzz ran before the `finally`, which changes nothing for an Exception); corpus against jc-28 24,217 of
24,217 identical, `-CrossCheck` clean.

- **What a user saw (jc-21 to jc-28):** a level set opens at level 1 only. If level 1 will not load:
  - on a fresh start, its error is shown and then the open fails (an internal error), leaving no
    level loaded;
  - with a set already open, the previous set's level stays on screen under the new set's file, so
    Solution › Save would put that route in the new set's save folder;
  - a set with no levels at all behaves the same;
  - opened from the command line or a file association (`SuperCC.jar set.dat`), the failure also
    escapes the startup code, so the tileset is never set up and a level asked for with `-l` is
    never loaded: the window is left half-built. (Reproduced to the error dialog on the jc-21
    candidate; the rest is jc-16's code, measured then — FORK.md step 4, *The second review*.)

  Its other levels can still be reached with Level › Go to... when a set was already open; on a
  fresh start, not since jc-28 (UB-30, which this fix closes). Separately, an error in the window's
  repaint during a load (CLAUDE.md §9's jar replaced under a running instance) is reported as
  "Could not load level" although the level loaded, and the title bar keeps the previous level's
  name.
- **Measured:** none of the collection: a scan opening all 331 sets (2026-11-08) found every level 1
  loads. The only levels that do not load are UB-1's five, none of them a level 1.
- **Fix:** jc-17's `openLevelset`: open at the first level that loads, report all failures once
  ("Levels 1 to 3 could not be loaded … Opened at level 4"), put the previous set back before the
  error is shown when nothing loads, and count positions, not record numbers — through
  `tryLoadLevel`, which returns a failure instead of reporting it.
- **Pinned by (jc-21 to jc-28):** `EngineTest` §6c and §6d; `GuiWindowTest` §3a (including the open level kept under
  the new set's file, in a real window) and §5. **Checked by (jc-29 on):** the same, as checks.

### UB-15 — a level whose savestates cannot be built is half-loaded

**Status: FIXED in `e08c6b5` (jc-30).** jc-17's all-or-nothing load is back, re-applied alone:
`tryLoadLevel` builds the level and its savestates, then assigns both, so a level whose savestates
cannot be built is not loaded at all. The previous level stays, with its own savestates, and the
failure is reported as any load's is ("Could not load level: ..."); an open counts it as a level
that did not load and goes on to the next. An `Error` thrown while the savestates are built
escapes with nothing assigned, so it leaves the previous level too, and an open puts the previous
set back (UB-14's `finally`). With comments stripped, `loadLevel` and `tryLoadLevel` are now jc-20's
exactly. Both places this reached are closed: the menu guard's `isLevelLoaded()` (UB-11) again
means a level with its savestates, and the open's identity test (UB-14) is exact. Nothing changes
for a level that loads: corpus against jc-29 24,217 of 24,217 identical, `-CrossCheck` clean. No
real level reaches it, so the checks break one on purpose, with a parser whose level parses but has
no title (the historical cause) or throws an `Error` while its state is saved, put into a headless
emulator by reflection for a load, and through the settings for an open, which makes its own parser.
On jc-29's jar `EngineTest` fails exactly the 8 new checks that tell the two apart; 8 defects
planted in `SuperCC` (upstream's order, the previous savestates wiped, Errors caught, an Error or an
Exception assigning the level on its way out, with or without savestates, savestates built from the
previous level, the failure dropped) all caught.

- **What a user saw (jc-21 to jc-29):** nothing. `loadLevel` assigned the level before it built the
  level's savestates, so if that second step failed the level would be "loaded" with no savestates,
  or with the previous level's, and slip past the guards that ask whether a level is loaded.
- **Measured:** no level reaches it. The one cause ever seen, a level with no title, is handled by the
  fork's own untitled-level fix (jc-17, kept: it is not part of this step), and all 23,476 levels of
  the collection load except UB-1's five, which fail earlier, while parsing. One way in that is not a
  level: an `Error` thrown after the level is assigned. The jc-29 failure-mode review replaced the
  jar under a running probe after only the parsing classes had loaded, and `SavestateManager` then
  threw `NoClassDefFoundError`, leaving the level loaded with no savestates -- on jc-28 and the
  jc-29 candidate alike. This fix closes that too: the jc-30 failure-mode review repeated the swap
  under the candidate, and the same `NoClassDefFoundError` left nothing loaded and no set open.
- **Fix:** build the level and its savestates first, then assign both, or neither (jc-17).
- **Also reached the menus (jc-28 and jc-29):** UB-11's menu guard, re-applied as jc-17 wrote it,
  asks only `isLevelLoaded()` (a level is assigned), which jc-17's all-or-nothing load made the same
  as "its savestates exist". Until this fix came back, a half-loaded level would have gotten past the
  guard, and Ctrl+S, the Copy items and the state files would have failed on the missing savestates.
  Widening the guard instead would have been a new deviation from jc-17's fix; this fix is the real
  one (review of jc-28, defended).
- **Also reached the open (jc-29):** UB-14's fix, re-applied without this one, tells a level that
  loaded from one that did not by whether `level` changed, which this fix makes exact. On jc-29 a
  level whose savestates cannot be built changed `level` too, so opening a set would have landed on
  it half-loaded and only logged the failure -- where jc-28's open at least showed "Could not load
  level" (both jc-29 reviews; the failure-mode fuzz of 120,000 damaged records reached it zero
  times). Measured on jc-29's jar by the new checks: a fresh open landed on the broken level 1
  (its title read null) with no savestates at all, and with a set open, opening a set whose only
  level fails that way did not keep the open set and its level.
- **Pinned by (jc-21 to jc-29):** nothing — no fixture reached it while untitled levels load.
  **Checked by (jc-30 on):** `EngineTest` §6c's last 12 checks: a load (the level and its savestates
  kept, the failure reported, an `Error` escaping with both kept), a fresh open (lands on level 2
  with level 2's own savestates, reports level 1), an open of a set whose only level fails that way
  (the open set and its level kept, "No level in ... could be loaded"), an `Error` during an open
  (the open set put back), and that each open broke a level of the set it was opening.

### UB-30 — after a fresh-start open whose level 1 fails, the set's other levels cannot be reached

**Status: FIXED in `48a3eca` (jc-29)**, by UB-14's fix: an open now either loads a level or puts the
previous set (on a fresh start, none) back, so a set's file is never left open without a level --
checked by `EngineTest` §6c after a fresh open where nothing loads, and after an `Error` escapes an
open (both fail on jc-28's jar).

Introduced by the fix layer itself, not by a baseline revert: jc-28 put UB-11's menu guard back
before UB-14's fix, and jc-17 had shipped the two together.
- **What a user sees:** opening, as the first set of a session, a set whose level 1 will not load
  leaves that set's file open with no level loaded (UB-14). On jc-21 to jc-27 its other levels could
  be reached with Level › Go to... (Ctrl+G), which needs only the set's file. From jc-28 Go to...,
  like every control that needs a level, does nothing while no level is loaded, and Next and
  Previous have upstream's own guards, so none of that set's levels can be reached from the menus.
  With a set already open beforehand, Go to... still works (that set's level stays loaded).
- **Measured (2026-11-14, a real window, a scratch probe):** a fresh start, then a set whose first
  three levels cannot load, then Go to... with "4" typed into its box: on jc-27's jar the box
  appears and level 4 loads; on the jc-28 candidate no box appears and no level is loaded. None of
  the 331 sets in the collection is affected: every level 1 loads (scanned 2026-11-08).
- **Cause:** the guard asks `isLevelLoaded()`, and upstream's `openLevelset` (UB-14) can leave a set
  open with none loaded.
- **Fix:** UB-14's, which opens such a set at its first level that loads, so that state no longer
  arises. Not exempting Go to... instead: with no set open at all, its box would then appear on every
  launch and Cancel would fail (UB-12; fixed since jc-31, after which a number typed there with no
  set open would still do nothing).
- **Pinned by:** nothing — it is not upstream's answer (on jc-27 Go to... works), so an
  `upstreamBug` pin does not fit, and UB-14's fix makes the state unreachable. Raised by the jc-28
  review (sixth round), reproduced, and accepted until UB-14's fix.

## Custom tilesets (`5d3f0a3`, ships in jc-22)

The fork's changes to upstream's custom tilesets (`d78f6f3`), merged in jc-18, were reverted:
`TileSheet`, `SmallGamePanel` and `Gui` are upstream's byte for byte, `MenuBar`'s View › Tileset
listener and `SuperCC.initialiseTilesheet` are upstream's, and `SuccPaths` stores the custom tileset <!-- aen-exempt: upstream method name -->
as upstream does, inside the fork's own settings structure. FORK.md step 5 has the engineering
record. The fix layer takes these back one fix at a time, by hand from `5d3f0a3`'s diff (a whole
`git revert` no longer applies: the no-level step's fixes rewrote View › Tileset's listener since),
in dependency order: **UB-19 is back since jc-33 (`cb55dbf`), UB-20 since jc-34 (`9e6166a`), UB-18
since jc-35 (`bd2b915`), UB-21 since jc-36 (`e30ab77`), UB-16 since jc-37 (`f6d73e7`), UB-17
since jc-38 (`0614b86`) and UB-31 (found in jc-35) since jc-39 (`ef27c8d`): the whole step is back**. Nobody's tileset changes by itself: these all start with choosing a
custom tileset, or with a settings file that names one that cannot be drawn.

### UB-16 — custom is saved as tileset number 5

**Status: FIXED in `f6d73e7` (jc-37).** jc-18's switch is back, re-applied alone: custom is
`CustomTileset` / `LynxCustomTileset` in `[Graphics]` (default false, read and written through
`optedIn()`), beside a `TilesheetNum` / `LynxTilesheetNum` that is always a BUILT-IN tileset, the
one every build can read. `SuccPaths.setCustomTileset(lynx, base, overlay)` saves the two pictures
and turns the ruleset's switch on in one write; `setBuiltInTileset(lynx, n)` saves the number and
turns it off in one write; View › Tileset saves through them. `TileSheet.loadForRuleset` reads the
switch and replaces `load` at startup, the ruleset swap and a resize of a custom tileset: custom
that fails falls back to the ruleset's built-in choice -- what an older build shows -- rather than
the default; and, beyond jc-18 (jc-34 and jc-36 reviews), its message for a number that is not a
built-in tileset names the setting (`TilesheetNum = 99 in succ_settings.ini is not a built-in
tileset of this version (0, 1, 2, 3, 4).`). **One deliberate difference from jc-18, the
maintainer's decision (2026-11-16):** a 5 stored by jc-22 to jc-36 -- released builds that saved
custom that way -- is read at load as that ruleset's switch ON, with the stock built-in number (0)
beside it, and saved that way at the next write (every launch makes one); jc-18 refused a stored 5
like any number out of range, but no jc-18 to jc-21 file holds one. A 5 met while running with the
switch off (only code can make one now) is still refused. A computer still on jc-22 to jc-36 that
opens the migrated file shows tileset 0 and drops the switch lines at its next write -- the same
trade jc-18 made with older builds. Checked by `SettingsTest` §13b (new: the stock lines, both
switches through `optedIn()` and written back as read, each setter's one write, whole lines never
5, the migration for MS and Lynx, a 5 beside `CustomTileset = false`, any other number left
alone), `EngineTest` §6g (`loadForRuleset`: a fresh file, the switch on, Lynx's own choice, custom
gone falling back to the built-in MSCC, both settings named, a 5 set while running refused, a
stored 5 drawing custom), `StartupTilesetTest` (a stored 5 started and saved as the switch; custom
beside built-in MSCC failing to MSCC; the number messages naming their setting) and
`GuiWindowTest` (§5b's pin a check; §2's no-level pick saved as the switch beside Black and White;
§5c's resize falling back to Black and White, the built-in choice, not the default). On jc-36's
jar exactly those fail: `SettingsTest` 18, `EngineTest` 8, `StartupTilesetTest` 10, `GuiWindowTest`
5 and its exit check; 18 defects planted in `SuccPaths`, `TileSheet`, `MenuBar`, `SuperCC`, `Gui` (form-compiled for each) and `SmallGamePanel`, all caught (no migration, a migration that keeps the 5, or one for MS only; choosing custom leaving the switch off; a built-in choice leaving it on; the switch written back raw, not through `optedIn()` -- caught only by a check added for it; the getter reading the other ruleset's key; custom that fails falling back to the default; the switch ignored; a 5 with the switch off taken as custom; the message naming `TilesheetNum` for Lynx; Lynx reading the MS number; custom saved as tileset number 5; a built-in choice saved without turning custom off; startup, the swap or a resize going through `load`; a resize reading Lynx's settings with an MS level open); corpus against jc-36: 24,217 of 24,217 identical.

- **What a user saw (jc-22 to jc-36):** View › Tileset › custom saves `TilesheetNum = 5` (or `LynxTilesheetNum = 5`).
  A build from before jc-18 has no tileset 5, so on a settings file shared between computers (this
  one is, through Dropbox) such a build starts in its default tileset with an internal error and
  skips the rest of its startup, as in UB-20 (measured 2026-11-15 on jc-17's release: `Index 5 out
  of bounds for length 5`, the main window up, the error log's notice; until jc-34 this said it
  "does not start", the same misreading as UB-20's), and its next settings write erases the
  custom picture lines it does not know. A settings file written by jc-18 to jc-21 with custom on
  shows its built-in tileset instead (their `CustomTileset` switch is no longer read) — and the
  other way round, jc-22's next settings write drops those `CustomTileset` lines from the shared
  file, so a computer still on jc-18 to jc-21 then falls back to its built-in tileset too.
- **Measured:** `GuiWindowTest` §5b, in a real window with two pictures chosen: the file reads
  `TilesheetNum = 5`. A pre-jc-18 build on such a file was measured in jc-18's review (FORK.md step 5).
- **Fix:** custom as a switch of its own (`CustomTileset` / `LynxCustomTileset`) beside a built-in
  `TilesheetNum`, which every build can read (jc-18). With it, jc-18's `loadForRuleset` replaces `load` at
  startup, the swap and a resize; it knows the ruleset, so its out-of-range message can name the
  key (`TilesheetNum` or `LynxTilesheetNum`), which `load`'s cannot (raised by the jc-34 and jc-36
  reviews).
- **Pinned by (jc-22 to jc-36):** `GuiWindowTest` §5b. **Checked by (jc-37 on):** `SettingsTest` §13b,
  `EngineTest` §6g, `StartupTilesetTest`, `GuiWindowTest` §2, §5b and §5c.

### UB-17 — custom pictures are saved as full paths

**Status: FIXED in `0614b86` (jc-38).** jc-18's portable storage is back, re-applied alone: both
setters (`setCustomTileset`, jc-18's, and upstream's `setCustomTilesetImages`, kept, so no setter
stores a picture inside the folder absolute) store each picture through jc-5's `toStoredPath` --
relative to the Chip's Challenge folder when it is inside it, absolute when it is not -- and
`getCustomTilesetImages` resolves what is stored through `toUsablePath`, so a relative path written
on one computer is a picture in the other computer's own folder; both setters store through one
helper (`putCustomImages`, from review). A value already stored absolute (jc-22 to jc-37 wrote them
so) still reads as it is, jc-5's rule for legacy Levelset and TWS values; and, beyond jc-18 (jc-38
review), one INSIDE this computer's folder is put in the relative form at load
(`relativizeStoredCustomImages`), so the next write -- every launch makes one -- saves it the way
the other computer can read, without custom being chosen again (otherwise, unlike Levelset and
TWS, it would stay absolute for good). It names the same file either way; a full path outside
this folder -- the other computer's, or anywhere else -- is left exactly as it is. Checked by
`SettingsTest` §13 (the pin a check: inside stored relative, read back as chosen; an outside
picture absolute beside a relative one; a relative path read in this computer's folder; legacy absolute
values read as they are, and at the next write the one inside this folder saved relative, the one
outside left alone) and §13b (`setCustomTileset` stores
relative), and `GuiWindowTest` §5b (the pin a check, the pictures read back as chosen; UB-19's
"earlier pictures still saved" now reads them through the getter, whatever their stored form). On
jc-37's jar exactly those fail: `SettingsTest` 5, `GuiWindowTest` 1 and its exit check; 7 defects
planted in `SuccPaths`, all caught (the getter handing back what is stored, or resolving only the
overlay; the setters storing the base or the overlay absolute again; no conversion at load, one
for the base only, or one that makes a path outside the folder relative anyway); corpus against jc-37: 24,217 of 24,217 identical.

- **What a user saw (jc-22 to jc-37):** a custom tileset chosen on one computer names that computer's folders
  (`C:\Users\Jeremy\...` against `C:\Users\jerem\...`), so on the other computer its pictures are
  missing — and then, with UB-20, SuperCC starts there in the default tileset with an internal
  error (jc-22 to jc-33; since jc-34 it says why). Until jc-34 this said SuperCC "does not start"
  there: UB-20's misreading.
- **Fix:** store a picture inside the Chip's Challenge folder relative to it, as Levelset and TWS
  are (jc-5's `toStoredPath`/`toUsablePath`; jc-18).
- **Pinned by (jc-22 to jc-37):** `SettingsTest` §13; `GuiWindowTest` §5b. **Checked by (jc-38 on):** the
  same, as checks, and `SettingsTest` §13b.

### UB-18 — canceling custom asks again, then complains

**Status: FIXED in `bd2b915` (jc-35).** jc-18's Cancel is back in View › Tileset › custom's
listener, re-applied alone: the second chooser is asked for only when the first returned a
picture; a Cancel at either (its Cancel button or its close box) ends the pick with nothing shown
and nothing saved; and the radio mark goes back to the tileset still drawn
(`MenuBar.markActiveTileset`, in the listener's `finally`: after a Cancel, a refused picture --
UB-19's error -- or anything else that leaves the old tileset drawn; jc-18 put it back on the
Cancel path and in the catch, and review made it one place). jc-18's chooser start folders, in the same lines, are left out: they were reverted with
the rest but never recorded, and are UB-31 now. The two pins are checks again (`GuiWindowTest`
§2: the walk's Cancel is one chooser and nothing else; the mark back on Black and White), with
new checks after a built-in tileset is drawn: each chooser canceled as a person would, by its
Cancel button and by its close box, the first (one chooser, nothing after it) and the second
(the two, nothing after them), each leaving the tileset drawn, saved and marked, and the saved custom pictures, unchanged; and
a file that is not a picture (its one error, then the same). On jc-34's jar `GuiWindowTest` fails
exactly those 11 and its exit check; 8 defects planted in `MenuBar`, all caught (the second chooser asked for after the first is canceled; Cancel complaining again; the mark never put back, or put back on Cancel only, as jc-18 split it, so a refused picture left it; `markActiveTileset` marking nothing, or custom; only the first chooser's Cancel counting; the first picture saved before the second chooser is answered); corpus against jc-34: 24,217 of 24,217 identical.

- **What a user saw (jc-22 to jc-34):** canceling the first picture's chooser still opened the
  second; canceling either showed "No tileset image provided"; and the View › Tileset mark stayed
  on custom though the old tileset was still drawn. Since jc-33 (UB-19) a picture that is refused
  left the mark on custom the same way.
- **Fix:** the first Cancel ends it, with no error, and the mark goes back to what is drawn
  (`MenuBar.markActiveTileset`; jc-18).
- **Pinned by (jc-22 to jc-34):** `GuiWindowTest` §2 (canceled with no level open: both choosers
  and the error) and the radio-mark check after it. **Checked by (jc-35 on):** the same, as
  checks, plus each chooser canceled by its Cancel button and its close box, and a refused picture,
  each putting the mark back.

### UB-19 — a custom picture is saved before it is known to load, and a bad one fails badly

**Status: FIXED in `cb55dbf` (jc-33).** jc-18's loader is back in `TileSheet`, re-applied alone:
every way a custom picture can be unusable is an `IOException` that says what is wrong and,
where there is one, names the file -- none chosen, the settings not loaded, a missing picture,
a file that is not a picture, a broken GIF, a picture cut short (the reader's own `IIOException`,
which jc-18 let through unnamed; found in jc-33's review), and any other exception the image
reader throws; a picture whose header claims more than 33,554,432 pixels (32 Mi) is refused
from the header, before it is decoded (the 46000 x 46000 file is refused, not decoded); and
both streams the reader opens are closed, so a loaded picture is never left locked. From the
jc-33 reviews, beyond jc-18: a folder where a picture should be is "not a file" rather than
"not found"; the size message states the exact limit (33,554,432 pixels); and a picture file
is read in place, with no temp-file cache and no copy held on the heap, so an unusable temp
folder no longer fails the load as the picture's fault, and a folder this user may not look in
is reported by the error from opening the file, not as "not found"; a picture the loader cannot open is said in words, naming it, not with the system's bare message; any other failure to read says "Could not read the picture", naming it, rather than blaming the picture (no test: it needs a device that fails mid-read); and a path that cannot
be a path says so. (The jar path's close, for the built-in sheets, is not covered by a check:
a leaked entry there locks no user file.) The cap bounds a picture's dimensions only: a crafted
file whose other fields ask for huge allocations (a TIFF's strip sizes, a compressed metadata
chunk) can still exhaust memory, as with every image reader. `SuccPaths.getCustomTilesetImages`
reads an empty path as "not chosen". And View › Tileset › custom LOADS both chosen pictures
(`TileSheet.loadCustom`) before it saves their paths, so a file that is not a picture brings
one error naming it and nothing is saved: the pictures chosen before stay the saved ones. Left
out, and coming back with their own fixes: the fallbacks (`TileSheet.load`, `loadForRuleset`:
UB-20, UB-21, UB-16), Cancel (UB-18) and how the choice is stored (UB-16, UB-17). The 11 pins
are checks again, with checks that each message names its file, that the 46000 x 46000 file
is refused (run only once the 50000 one has been refused from its header, since a loader that
decodes first would try to allocate the 8 GB), and that a picture just loaded can be moved. On
jc-32's jar `EngineTest` fails exactly the 18 it should (the 46000 case is skipped there,
as designed), `SettingsTest` the 3, and `GuiWindowTest` the 4 and its exit check; 18 defects
planted in `TileSheet`, `SuccPaths` and `MenuBar`, all caught (the reader's runtime exceptions
not turned into words, its own `IIOException` and plain `IOException` passed through unnamed
(jc-18's gap), ImageIO's temp-file cache back for a picture file or a built-in sheet, the
loader's own refusal wrapped a second time, the path printed twice when a picture cannot be
opened, a folder reported as not found, no size cap, the picture file left open, a missing
picture left to the error from opening it, an invalid path reported as not found, the system's
bare message for a picture that cannot be opened, a missing picture's message naming no file,
no settings reported as none chosen, upstream's getter back, half chosen read as a pair, the
listener saving before it loads, the listener loading the saved pictures instead of the chosen
ones); corpus against jc-32: 24,217 of 24,217 identical.

- **What a user saw (jc-22 to jc-32):** choosing a file that is not a picture saved it anyway, then
  failed with an internal error (`NullPointerException`) before the choice of tileset was saved. If
  custom was already the saved tileset, the next start then broke partway (UB-20); if not, only the stored
  picture paths were wrong. Other bad files failed with internal errors as well: a broken GIF
  (`IndexOutOfBoundsException`) and a picture whose header claims 50000 x 50000 pixels
  (`IllegalArgumentException`); one claiming about 46000 x 46000 made the loader try to decode about
  8 GB. A missing picture's message named no file ("Can't get input stream from URL!"). With no
  pictures chosen, custom failed with an internal error: upstream's getter returns the pair
  whenever both keys exist, and this fork's settings always have them (empty), so the empty paths
  reached the image reader as the working folder.
- **Fix:** load both pictures before saving them; make every failure an IOException that names
  the file and says what is wrong; refuse a picture over 32 million pixels from its header, before
  decoding; close both streams (jc-18).
- **Pinned by (jc-22 to jc-32):** `EngineTest` §6f (six: missing, not a picture, no settings, none
  chosen, the GIF, the 50000 x 50000 header; the 8 GB case skipped, not run); `SettingsTest` §13
  (none chosen, half chosen and a pre-jc-18 file read as a pair, not as "none"); `GuiWindowTest`
  §5b (the click on a file that is not a picture, and that it is saved). **Checked by (jc-33 on):**
  the same places, each pin a check, plus the file named in each message, the 46000 x 46000 file,
  a loaded picture left unlocked, the error shown for a file that is not a picture, and the
  earlier pictures still saved.

### UB-20 — a saved MS tileset that cannot be drawn breaks startup partway

**Status: FIXED in `9e6166a` (jc-34).** jc-18's fallback is back, re-applied alone: startup asks
`TileSheet.load` for the saved MS number, with the default tileset, Tile World (CCEdit), to fall
back to. A number this version does not have, or custom whose pictures cannot be used, draws the
default instead and says why in one message -- UB-19's reason, naming the file, then "Showing
Tile World (CCEdit) instead. Your choice is kept; pick a tileset in View > Tileset to change
it." -- leaves the saved choice alone, and startup carries on: the saved tile size, the window's
layout for it and the saved TWS notation are applied as on any start. `SuperCC.throwErrorOnce`
says a message once a session; it matters once UB-21's fallbacks are back, which meet the same
cause at every set opened. From the jc-34 review, beyond jc-18: the message is said last, over the
finished window (jc-18 said it before the layout, over the window Gui builds at 20-pixel tiles),
and a number out of range is told that the choice is kept too (jc-18 told only custom). Left out,
and coming back with their own fixes: `loadForRuleset` and custom's own switch (UB-16; startup
reads the number as upstream stores it, custom as 5), the ruleset swap and the window-size change
(UB-21), and the View › Tileset mark (UB-18, UB-21; no build marks a tileset before a set is
opened). **One startup path was left to UB-21:** a set named on the command line is opened, and
its tileset swapped, before startup reaches the tileset, so with a number out of range startup
still stopped partway there (jc-34 and jc-35, pinned under UB-21); since jc-36 the swap falls back
too, and startup finishes. Checked by a new `StartupTilesetTest`: real
starts in a child JVM, each with its own settings file, 32 x 32 tiles and the TWS notation on, its
dialogs read and closed -- two controls (a built-in tileset, and custom with pictures that load:
no message, laid out as saved) and four that cannot be drawn (99, -1, custom with its pictures
missing, custom naming a file that is not a picture: nothing thrown, the default drawn, one
message saying why, the window laid out, the tile size and notation applied, the choice still
saved; the message shown over the finished window), and in §2 three with a set on the command
line (a control, and UB-21's two pins) -- and `EngineTest` §6g, `TileSheet.load` at its edges (every number this version has, 6
and -1, custom with none chosen and with pictures that load, custom failing with custom as the
fallback) and `throwErrorOnce`. On jc-33's jar `StartupTilesetTest` fails exactly the 25 checks
it should and `EngineTest` the 13 new ones ("no TileSheet.load"); 18 defects planted in `TileSheet` and `SuperCC`, all caught (a number below zero, or the first past the end, taken as a tileset; a number out of range, or a tileset that fails, drawn with no word; the reason without the loader's own words; the tileset that failed named as the one drawn; the fallback's own failure hidden; the kept line emptied, or left off a number out of range as in jc-18; startup keeping the reason to itself, or saying it and then stopping as upstream did, or saying it before the layout as jc-18 did; "once" meaning never, every time, or once for every emulator in the JVM; the default drawn whatever was saved; the Lynx number read; MSCC as the fallback); corpus against jc-33:
24,217 of 24,217 identical.

- **Correction (2026-11-15, jc-34):** until jc-34 this entry, its summary row, README.txt,
  CHANGELOG.md and CLAUDE.md said SuperCC "does not start" here: "no window appears at all ... the
  program keeps running unseen". That was wrong. The 2026-11-09 launches used a script that waited
  only for a window titled "Error", so its "NO WINDOW" meant no error box, not no main window.
  Launched again on jc-33, and on jc-17's release for UB-16's case (every visible window of the
  process listed and captured), the main window is up every time: `Gui` builds it, already drawn in the default tileset, before
  startup reaches the tileset. The exceptions recorded below were right. UB-16 and UB-17 repeated
  the claim and are corrected too.
- **What a user saw (jc-22 to jc-33):** if `succ_settings.ini` names an MS tileset this version
  does not have, or custom whose pictures are missing or cannot be used, SuperCC comes up in the
  default tileset with nothing to say why but the error log's one notice ("SuperCC recorded an
  error", naming the log file). Startup stopped at the tileset, so the rest of it never ran: the
  saved tile size was not applied (with 32 x 32 tiles saved, the window stays 940 x 771, where a
  normal start lays it out at 1324 x 1155), nor the saved TWS notation; with custom, the game panel
  was left half set, named custom at the saved tile size while it drew the default's 20-pixel
  pictures. Setting `TilesheetNum` back to 0–4 by hand is the way out. Since jc-28 (UB-11's fix)
  custom chosen before a level set is open is saved as the MS choice too, so it can lead here the
  same way as custom chosen with an MS level open; jc-21 to jc-27 failed before saving that choice.
- **Measured (2026-11-09, launching the jc-22 candidate from a sandbox):** `TilesheetNum = 99` →
  `ArrayIndexOutOfBoundsException` in `initialiseTilesheet`; `TilesheetNum = 5` with the pictures <!-- aen-exempt: upstream method name -->
  missing → `IIOException`, then a `NullPointerException` on the missing images ("no window" was
  recorded too: see the correction above). Opening a set from the command line first shows "Could
  not load relevant tilesheet", then the same. **Again (2026-11-15, jc-33):** the same exceptions
  (custom's first is now UB-19's `IOException` naming the file); a visible main window every time,
  with the error log's notice; 940 x 771 against a normal 1324 x 1155 with 32 x 32 tiles saved.
- **Fix:** jc-18's `TileSheet.load` (and `Choice`) at startup: fall back to the default and say
  why once a session (`SuperCC.throwErrorOnce`); `loadForRuleset` follows with UB-16.
- **Pinned by (jc-22 to jc-33):** nothing -- the start needs a window and a settings file of its
  own; measured by launching. **Checked by (jc-34 on):** `StartupTilesetTest` (new) and
  `EngineTest` §6g.

### UB-21 — a tileset that cannot be drawn, met while running, leaves the screen wrong

**Status: FIXED in `e30ab77` (jc-36).** jc-18's two fallbacks are back, re-applied alone, through
`TileSheet.load` as at startup (UB-20): `Gui.swapRulesetTilesheet`, run at every set opened, the
Level menu's ruleset switch and a solution load, and `SmallGamePanel.setWindowSize`, run at every
window-size change. A number this version does not have, or custom whose pictures cannot be used,
draws the default instead, says why once a session (`SuperCC.throwErrorOnce`: the same cause met
at the next set opened is not said again), and the View › Tileset mark goes on what is drawn (the
swap sets it; the resize calls UB-18's `markActiveTileset`). Both read the number as upstream
stores it (custom as 5): jc-18 fell back to the ruleset's built-in choice for custom, through
`loadForRuleset` and custom's own switch, which come back with UB-16. Beyond jc-18: the swap says
its message last, after the window is redrawn, as startup does (jc-34 review); and a resize sets
the inventory panel up again, as the swap does -- it keeps its own reference to the board's tile
pictures, so after a fallback it went on drawing the failed tileset's keys and boots (jc-18's gap,
found in jc-36's review; checked). All callers of both
run on the event thread, which `throwErrorOnce`'s unsynchronized set relies on. `Gui` is a form
class, compiled with `formclass.ps1 -Compile` (`-Check` byte for byte green; form baseline
updated). The command-line start (UB-20's last path) now finishes: `StartupTilesetTest` §2's two
pins are checks again (nothing thrown, the default drawn, the reason said once -- by the swap, so
startup's identical message is not said again). New `GuiWindowTest` §5c, with an MS level open and
Black and White drawn: a Lynx set opened with `LynxTilesheetNum = 99` (the default drawn and
marked, the reason said, the choice kept), an MS set and then the Lynx set again (nothing said
again), custom as the Lynx tileset with its pictures missing (the default drawn and marked, the
reason naming the picture), and custom drawn, its base picture moved away, then the window size
set (the same, and the inventory drawing from the board's new pictures). On jc-35's jar `GuiWindowTest` fails exactly those 8 and its exit check, and
`StartupTilesetTest` its 3; 10 defects planted in `Gui` (each compiled with the form compiler, then restored byte for byte) and `SmallGamePanel`, all caught (the swap keeping the reason to itself, or saying it at every set opened; the swap marking the saved number, not what is drawn; the swap reading the Lynx number for MS sets too; the swap falling back to MSCC; a resize keeping the reason to itself, leaving the mark where it was, naming the failed tileset as the one drawn, drawing the default whatever was drawn, or leaving the inventory on the old pictures); corpus against jc-35: 24,217 of 24,217 identical.

- **What a user saw (jc-22 to jc-35):** opening a Lynx set whose Lynx tileset cannot be drawn shows "Could not load
  relevant tilesheet" (naming nothing) — every time such a set is opened — and keeps the previous
  ruleset's tileset; a Lynx number this version does not have fails with an internal error instead.
  Changing the window size while a custom tileset fails leaves the game area drawn at the old size.
  The swap runs at EVERY set opened, MS sets too (recorded 2026-11-15, jc-34): an MS number this
  version does not have throws there, an internal error at every set opened, and custom that
  fails says "Could not load relevant tilesheet" each time. Since jc-34 that is met after a start
  that fell back (UB-20), whose message says the choice is kept. **And a set named on the command
  line** (or dropped on the jar) is opened before startup reaches the tileset, so there a bad MS
  number still throws in the swap and stops startup partway, before UB-20's fallback is reached --
  UB-20's symptom, on this one path, until this fix; custom that fails says the swap's words
  first, then UB-20's.
- **Measured:** by reading upstream's `Gui.swapRulesetTilesheet` (catches only IOException,
  with that message) and `SmallGamePanel.setWindowSize` (swallows the IOException); the swap's
  dialog was seen in the command-line launch above. Since jc-33 (UB-19) a custom picture that
  fails is always an IOException, so at a window-size change it is now dropped silently, where
  before it usually escaped as an internal error into the error log. The command-line start was
  launched on the jc-34 candidate (2026-11-15): `TilesheetNum = 99` and a set → the swap's
  `ArrayIndexOutOfBoundsException`, the window left at 940 x 771 with 32 x 32 tiles saved, no
  message saying why; custom missing and a set → "Could not load relevant tilesheet", then
  UB-20's message, and startup finishes.
- **Fix:** both through jc-18's `loadForRuleset`, which falls back, says why once a session, and
  moves the View › Tileset mark to what is drawn (through `load` until UB-16). `SuperCC.throwErrorOnce`
  (back since jc-34) keeps its "once" in an unsynchronized set, as jc-18 did: the swap's and the
  resize's callers were checked in jc-36 and all run on the event thread.
- **Pinned by (jc-34, jc-35):** `StartupTilesetTest` §2: a number out of range with a set on the
  command line throws, and custom missing with one says the swap's words first. The swap after a
  start and the window-size change: nothing (measured as above). **Checked by (jc-36 on):**
  `StartupTilesetTest` §2, the pins as checks, and `GuiWindowTest` §5c.

### UB-31 — custom's choosers start in the user's default folder

**Status: FIXED in `ef27c8d` (jc-39). 🏁 With it, the whole custom-tileset step (`5d3f0a3`,
UB-16 to UB-21 and UB-31) is back.** jc-18's start folders, re-applied alone in `MenuBar`'s custom
listener: with no pictures chosen, the base picture's chooser opens in the Chip's Challenge folder
(the working folder) and the overlay's where the base picture was just chosen; with pictures
saved, each opens at its own picture, as upstream does. **Measured (2026-11-16, GuiWindowTest on
jc-38's jar):** with none chosen, both choosers opened in the user's Documents folder -- Java's
default folder for the user -- where the code reading said they would. Checked by
`GuiWindowTest` §2 (a new helper, `answerChoosersNoting`, notes the folder each chooser opens in
before approving: with none chosen, the first in the Chip's Challenge folder and the second in
the base picture's own folder -- a subfolder, so the two cannot be confused) and §5b (with
pictures saved in two different folders, each chooser opens where its own picture is: upstream's
behavior too, guarded here). On jc-38's jar exactly the two §2 checks fail, with the child's exit
check; 5 defects planted in `MenuBar`, all caught (either chooser opening at "" again, as
upstream; the overlay's opening in the Chip's Challenge folder rather than the base picture's;
with pictures saved, the overlay's opening at the base picture, or the base's in the Chip's
Challenge folder); corpus against jc-38: 24,217 of 24,217 identical.

- **What a user saw (jc-22 to jc-38):** with no custom pictures chosen yet, View › Tileset › custom's two file
  choosers open in Java's default folder for the user (Documents, on Windows), not the Chip's
  Challenge folder, and the second does not open where the first picture was. Once pictures are
  saved, each chooser opens at its picture, as in jc-18.
- **How it was found (jc-35):** re-applying UB-18 from `5d3f0a3`'s diff. jc-18's lines for Cancel
  also chose where the choosers start ("starting where the current ones are", the working folder
  when none are, the overlay's chooser at the base picture's folder); the revert took them out,
  but no entry recorded it. Upstream passes "" to `MenuBar.openFile` when none are chosen, whose
  `new File("")` has no parent, so `JFileChooser.setCurrentDirectory(null)` -- the user's default
  folder. Read in the code then; measured with its fix (above).
- **Fix:** jc-18's start folders (`MenuBar`'s custom listener, three lines).
- **Pinned by (jc-22 to jc-38):** nothing. **Checked by (jc-39 on):** `GuiWindowTest` §2 and §5b.

## Auto-diagonals (`d08e6ef`, ships in jc-23)

The fork's rewrite of upstream's auto-diagonals (`5f928e0`, `fb8fe25`, `da35e33`, `8c95b3e`), merged
in jc-18, was reverted: `EmulatorKeyListener` and `ControlGUI` are upstream's byte for byte
(`ControlGUI` compiled with `formclass.ps1 -Compile`), `DiagonalCombiner` is gone, and
`SuccPaths.Controls`, `getControls` and `setControls` are upstream's, inside the fork's own settings
structure (the line is written where upstream writes it, between `UpRight` and `Rewind`). All of it
comes back with `git revert d08e6ef`, which also restores `AutoDiagonalTest`, `ControlGUI`'s
compiled classes and both baselines; re-apply it first (newest first), before step 5's. **All of
it is back**: UB-28 by hand in jc-45 (`f4638be`), and UB-22 to UB-27 together in jc-46 (`275a07c`),
one rewrite, by `git revert --no-commit d08e6ef` with `SuccPaths` and the two tests it touched
merged by hand (UB-28's place for the line kept; `GuiWindowTest` §6 kept as the baseline wrote it,
its 12 pins made exact checks, with jc-18's own checks after them). **With it, no issue the
baseline introduced is open; UB-29 stays accepted.** FORK.md
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

**Status: FIXED in `275a07c` (jc-46)**, with the rest of jc-18's rewrite (`DiagonalCombiner` and its key listener, back unchanged): only the four arrows wait, and an arrow pressed and let go inside the window is kept as STRUCK and makes its move when the window closes. `GuiWindowTest` §6's three pins are exact checks again; `AutoDiagonalTest` is back.

- **What a user saw (jc-23 to jc-45):** with Auto diagonals on, a key pressed and let go within 50 ms makes no move
  at all. That is any game key, not only the arrows: a quick tap of a diagonal key, a wait,
  Backspace or Enter is lost too.
- **Measured:** `GuiWindowTest` §6, in a real window: a tapped arrow, a tapped wait key and a tapped
  diagonal key each make no move, at once or after the window.
- **Fix:** only the four arrows wait, and a tap counts (jc-18's `DiagonalCombiner`, after Tile
  World's rule: an axis nothing holds goes to its newest key tapped inside the window).
- **Checked by:** `GuiWindowTest` §6 (three; pins from jc-23 to jc-45).

### UB-23 — keys inside one window make one move between them

**Status: FIXED in `275a07c` (jc-46)**, with the rest of jc-18's rewrite: only arrows wait, any other key makes the waiting move first, each axis goes to its newest held key, and a repeat of a held arrow is a move of its own at once. `GuiWindowTest` §6's four pins are exact checks again, beside jc-18's own repeat checks (three diagonals from Up held with Right repeating; a run-then-turn that is not a diagonal).

- **What a user saw (jc-23 to jc-45):** everything pressed within the same 50 ms makes ONE move.
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
- **Checked by:** `GuiWindowTest` §6 (four; pins from jc-23 to jc-45).

### UB-24 — under MS, arrows wait too, and a key is lost

**Status: FIXED in `275a07c` (jc-46)**, with the rest of jc-18's rewrite: arrows wait only where the open level can move diagonally (`Level.supportsDiagonal()`, so Lynx); under MS they act as with the switch off. `GuiWindowTest` §6's two pins are exact checks again.

- **What a user saw (jc-23 to jc-45):** MS has no diagonal moves, but with the switch on its arrows wait like
  Lynx's. A tap makes no move (UB-22). Up held with Right pressed makes the diagonal, which
  `SuperCC.tick` reduces to its vertical half, so Chip moves Up and the Right is lost; Tile World's
  MS mode moves Right.
- **Measured:** `GuiWindowTest` §6, on an MS set: an arrow waits; Up held with Right pressed records
  only Up.
- **Fix:** no window under MS at all: arrows act as with the switch off (jc-18).
- **Checked by:** `GuiWindowTest` §6 (two; pins from jc-23 to jc-45).

### UB-25 — other input jumps ahead of a waiting move

**Status: FIXED in `275a07c` (jc-46)**, with the rest of jc-18's rewrite: a waiting arrow is made first, before any other key (`keyPressed`, and an AWT event listener that runs before the component sees the event), any mouse press, drag or wheel turn, and any loss of focus. `GuiWindowTest` §6's pin is an exact check again, beside jc-18's own: a mouse press, and focus leaving the program, each make the waiting move at once.

- **What a user saw (jc-23 to jc-45):** keys that are not game keys, and the mouse, act at once while a move waits,
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
- **Checked by:** `GuiWindowTest` §6 (one; pins from jc-23 to jc-45).

### UB-26 — the move is made on a background thread

**Status: FIXED in `275a07c` (jc-46)**, with the rest of jc-18's rewrite: one `javax.swing.Timer`, created once, fires on the event thread like every other input, and Tools › Controls reads the switch through `getAutoDiagonals()` instead of `getControls()`. `GuiWindowTest` §6's pin is an exact check again.

- **What a user saw (jc-23 to jc-45):** usually nothing. The waiting move is made from the timer's own thread, while
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
- **Checked by:** `GuiWindowTest` §6 (one; pins from jc-23 to jc-45).

### UB-27 — a key let go elsewhere stays held

**Status: FIXED in `275a07c` (jc-46)**, with the rest of jc-18's rewrite: releases are seen anywhere in the program (the AWT event listener, switch on or off), and focus leaving the program forgets what is held. `GuiWindowTest` §6's pin is an exact check again, and a new check covers the Alt+Tab case (Up held, focus away, Right tapped: up, then right; a planted defect where focus loss only flushed survived until it existed). ⚠ One case the entry lists is NOT covered by jc-18's code either: a key REMAPPED in Tools › Controls while it is physically held. Its release then carries a code that maps to no key, so the old key stays held until focus leaves the program or the key is pressed and let go again. Read in the code, not measured, and contrived (the key has to be held through the remap); recorded rather than chased.

- **What a user saw (jc-23 to jc-45):** upstream sees a key being let go only through the main window's key listener.
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
- **Checked by:** `GuiWindowTest` §6 (one; pins from jc-23 to jc-45).

### UB-28 — `AutoDiagonals = 1` is OFF, and the file can say otherwise

**Status: FIXED in `f4638be` (jc-45).** jc-18's predicate, re-applied alone in `SuccPaths` (a spliced
file): `getControls()` reads the switch through `optedIn()` (`true` in any casing, or `1`), and
`render()` writes it through the same predicate, so a value read as OFF goes back as `false` and a
`1` as `true`. The line stays where upstream writes it, between `UpRight` and `Rewind`; jc-18 had
moved it to the end of the section, which was never part of the fix. `SettingsTest` §14's two pins
are exact checks again, and three checks are new: a `1` written back as `true`, the line's place
(once, between `UpRight` and `Rewind`), and the switch reading ON after a reload. On jc-44's jar
exactly those 4 fail. 7 defects planted (the read and the write each put back alone, both together,
an exact-case read, a write of always `false`, the line left out, the line moved): all caught.
Nothing else of the auto-diagonals rewrite came back with it: `Controls.keyNames` is still
upstream's mutable array, and `getAutoDiagonals()`, which only jc-18's key listener used, waits for
UB-22 to UB-27.

- **What a user saw (jc-23 to jc-44):** only `true`, in any casing, turns the switch on. `1` is OFF, though the
  README of jc-18 to jc-22 listed it as ON. A value read as OFF is also written back as it was by
  every settings write except one that writes the Controls section itself, so the file can say
  `AutoDiagonals = 1` while the switch is off. The exceptions write `true` or `false`: closing
  Tools › Controls, and launch repairing a key code that does not parse. So only a hand edit meets
  this, and it lasts until one of those.
- **Measured:** `SettingsTest` §14 (the raw write-back, through a tileset write); the exceptions are
  from the code (`setControls` writes `String.valueOf` of the switch).
- **Fix:** read it through `optedIn()` (`true` or `1`), and write it as the value read (CLAUDE.md
  §5; jc-18), re-applied by hand in jc-45 (`f4638be`).
- **Checked by:** `SettingsTest` §14 (two pins from jc-23 to jc-44).

---

## Seen while testing — NOT introduced by the baseline

These were already in upstream, and in the fork before the baseline began. They are listed so they
are not lost.

- **Seed Search's button still says "Pause" after a search finishes by itself.** Seen in the
  2026-11-08 playtest (CCLP1 #1, the last 3,000 seeds: 3000/3000, button "Pause"). Since the search
  is no longer running, pressing it starts a new round of threads rather than pausing; what that
  round does was not checked. Upstream and jc-16 to jc-19 alike; the fork never touched that code.
- **Auto diagonals: three limits of jc-18's design, back with it in jc-46** (raised by the jc-46
  review, read in the code, none measured; Windows, the target platform, avoids the first). With
  the switch on:
  - **Off Windows a held arrow may run at half speed again.** A repeat is recognized as a second
    press with no release between, which is how Windows repeats a key. Java on Linux/X11 (and
    some macOS setups) sends each repeat as a release and a press, so repeats there would wait and
    merge as upstream's did. Upstream is no better there.
  - **A held arrow can be forgotten after a native dialog.** Focus leaving the PROGRAM (a lost
    window focus with no opposite window) forgets what is held, and Windows can report no opposite
    window for some focus moves inside one program too. The failure is a missed diagonal (held Up
    plus Right makes plain Right), the safe direction, never a stuck key.
  - **Tools › Controls is built on a background thread** (`MenuBar`'s `SwingWorker`, upstream's),
    so its read of the switch, and the window's components, are made off the event thread. jc-18
    removed the settings WRITE from that path (UB-26); moving the whole window to the event thread
    would be a new change.
  The remap case is in UB-27's entry.
- **`TWSNotate` is still read with `Boolean.parseBoolean` and written back raw** (`SuccPaths.getTWSNotation`
  and `render()`'s `[Graphics]` loop), so `TWSNotate = 1` is OFF and stays `1` in the file. Seen
  while fixing UB-28, which is the same shape. Upstream's own switch since the first import
  (`fc31de0`), never part of a fork fix; README section 6 documents it as `true` or `false`, so
  nothing promised otherwise. Moving it to `optedIn()` would be a new change, the maintainer's call.
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
- **A custom tile size of 0 fails with an internal error.** View › Tile size › custom offers sizes
  from 0 (`new ChooseTileSize(..., 0, 256)`, upstream's own call), and a width or height of 0 makes
  the tileset scaling throw `IllegalArgumentException`, which `ChooseTileSize`'s OK handler does not
  catch (it catches `IOException`): the error goes to the error log, the Choose Dimensions window
  stays open, and the size does not change. Reproduced on the jc-28 candidate by calling
  `TileSheet.getTileSheets` at 0x20 and 20x0 (the call that OK makes). In upstream v2.0.3 and every
  fork build with a level open; with none open it was reachable in jc-17 to jc-20 and again from
  jc-28, whose menu guard exempts the custom tile size (jc-21 to jc-27 failed earlier there, on the
  `getKeys()` line). Raised by the jc-28 review.
- **Closing a custom-size window without OK leaves the menu's mark on "custom".** View › Tile size
  › custom and View › Game window size › custom each open a Choose Dimensions window; its close box
  changes nothing, but the radio mark has already moved to "custom", so the menu and the screen
  disagree until another size is picked. Upstream's code, with a level open or not. Raised by the
  jc-28 review; verified in the code, not measured.
- **Tools › Controls builds its window on a background thread.** Its listener runs `new
  ControlGUI(emulator)` inside a `SwingWorker`'s `doInBackground`, off the event thread, which Swing
  does not allow; nothing has been seen to go wrong, but the window's construction can race the
  event thread's painting. Upstream's code; jc-28 only keeps it working with no level open (as
  jc-17 to jc-20 did), and `GuiWindowTest` waits up to 20 s for that window. Raised by the jc-28
  review; verified in the code.
- **The custom-size windows are hidden, never disposed.** `ChooseTileSize` and `ChooseWindowSize`
  close themselves by dispatching `WINDOW_CLOSING`, which under a `JFrame`'s default
  `HIDE_ON_CLOSE` only hides the frame, so each use of View › Tile size › custom or Game window
  size › custom leaves a hidden frame (and its OK listener) for the rest of the session. Upstream's
  code, with a level open or not. Raised by the jc-28 review; verified in the code, not measured.
- **The custom tile size opens with the height set to the WIDTH.** `MenuBar` passes only
  `getTileWidth()`, and `ChooseTileSize` starts both spinners at it, so after a custom 32x16, an
  unchanged OK makes the tiles 32x32 and saves that. Upstream's code. Raised by the jc-28 review;
  verified in the code, not measured.
- **When the repaint after a load throws, the title bar keeps the previous level's name.** The title
  is set after the repaint, inside the same `try`, so a repaint that throws (CLAUDE.md §9's jar
  replaced under a running instance) skips it. Listed under UB-14 until jc-29, but jc-17's fix never
  covered it: jc-17 to jc-20 set the title in the same place. Every build. Raised by the jc-29
  review; verified in the code.
- **A load that worked is reported as "Could not load level" when only its repaint threw -- for
  every load but an open.** jc-17's fix (back in jc-29) tells the two apart inside `openLevelset`
  only; Next, Previous, Go to..., Restart and the rest go through `loadLevel`, which reports any
  failure `tryLoadLevel` returns. Every build. Raised by the jc-29 review; verified in the code.
- **Each failed level parse leaves its file handle open until garbage collection.**
  `DatParser.parseLevel` closes its reader only on an `IOException`, so a record that fails another
  way (an `ArrayIndexOutOfBoundsException` on a damaged record) leaves the file open: right after
  such an open, renaming the .dat was refused on jc-28 and the jc-29 candidate alike, and worked
  after a forced GC. Upstream's code; jc-29's open, which tries level after level, multiplies it on
  a damaged set (see UB-14's status). Raised by the jc-29 failure-mode review; measured.
- **Playback keeps running across an open, or any other load.** The play button's thread (`Gui` →
  `SavestateManager.play`) reads `emulator.getLevel()` on each step and loads its own manager's
  states into it, so opening a set while a route plays -- or Next, Go to... or anything else that
  loads a level, since no load stops the old manager first -- loads the old level's savestate bytes
  into the new level; Open levelset is not blocked while tools run either. Upstream's code. Raised by
  the jc-29 failure-mode review (the open) and the jc-30 review (the other loads); verified in the
  code, not measured.
- **`openLevelset` swaps the level set outside the emulator's lock.** `loadLevel` and `tryLoadLevel`
  are `synchronized`; `openLevelset` is not, so a thread that ticks the emulator -- auto-diagonals'
  timer (UB-26), when the switch is on -- can run while the new set's file is in place. Upstream's
  code and every fork build. Raised by the jc-29 review; verified in the code. Likewise the getters
  (`getLevel`, `getSavestates`, `isLevelLoaded`) and `tick` are not synchronized and the two fields are not
  `volatile`, so a thread other than the event thread -- that timer, the play button's thread --
  is not guaranteed to see a load's two assignments together. Loads from the menus and tools run on
  the event thread, so the window's own painting and menus see them in order; jc-30's UB-15 fix narrows the
  gap to two adjacent writes but does not make them one step for other threads. Raised by the jc-30
  reviews; read in the code, not measured.
- **A solution loaded while its level cannot be reloaded is played on top of the current route.**
  `Solution.load` reloads the level through `loadLevel` and then replays the solution's moves,
  without asking whether the reload worked: if it did not, "Could not load level" is shown and the
  moves are then played from wherever the level was, and recorded after the moves already made, so
  Solution › Save (which writes without asking) would replace the level's saved route with the two
  run together. The reloads that keep the moves do the same with the current moves -- Restart, a
  new RNG seed or step (F1, F2, F4), Go to... with the level's own number -- so the route is played
  twice. Reachable by editing a set while SuperCC has it open, an ordinary loop for a level designer:
  the parser keeps the record positions it read when the set was opened, so a level edited before
  it no longer parses. Reproduced with headless probes on jc-20, jc-29 and the jc-30 candidate alike:
  with the set's file renamed away, a second solution's moves appended (`R` became `RDD`); with the
  file cut short in place, F2's reload doubled the moves (`DD` became `DDDD`). Upstream's code. Where
  the file is the cause, the reload fails before anything is assigned, so jc-30's UB-15 fix changes
  nothing; where the level parses and its savestates fail (planted, by the jc-30 failure-mode
  review), jc-20 and jc-30 replay onto the current level and its own savestates, while jc-29
  replayed onto a fresh level paired with the old level's savestates -- wrong both ways. The fix,
  for a later step: `Solution.load` stops when the level did not change (the identity test the
  corpus harness uses). Raised by both jc-30 reviews; measured.
- **A failed load under the other ruleset still switches the set to it.** `DatParser.parseLevel`
  records the ruleset it is asked for before it reads the level, so when F3's reload fails the
  level stays MS while the set is now Lynx, and the next level (Next, Go to...) opens under Lynx with
  no message. Reproduced with a headless probe, the set's file cut short in place, on jc-20, jc-29
  and the jc-30 candidate alike (the level MS, the set LYNX, then level 2 under LYNX). F3's handler
  also swaps the tileset whether or not the load worked (read in the code, not run in a window).
  Upstream's code; the fix would record the ruleset only once a level has parsed. Raised by the
  jc-30 failure-mode review; measured.
- **Other callers of `loadLevel` do not learn whether it worked either.** `loadLevel` returns
  nothing and reports a failure only in its message, so a caller that loads and then acts goes on
  with whatever level is loaded: Seed Search's start (`startingState` is saved from the level it
  finds, at `SeedSearch.java` line 61) and each of its search threads (a headless emulator that opens
  the set and loads the level, then searches whatever loaded); Verify tws (a row then shows the
  previous level's title and that level's verdict); and the command line's `-l` (a `.tws` set or
  tested next goes to the level the open landed on). The same family as `Solution.load` above. For a
  level that fails to parse, the previous level is what they act on in every build; for one whose
  savestates fail, jc-30 (like jc-17 to jc-20) leaves the previous level where jc-21 to jc-29 left the
  failed level half-loaded -- wrong both ways, and no real level gets there. Upstream's code; the
  fix, for a later step, is for `loadLevel` to say whether it loaded (or for each caller to use the
  identity test). Raised by the jc-30 review (third round); read in the code, not measured.
- **Go to... and Set RNG Seed refuse a number with a space before or after it.** Each box hands
  its reply to `Integer.parseInt` untrimmed, so `" 5"` or `"5 "` (a common result of pasting) shows
  "Please Enter a Whole Number", and a reply of only spaces shows it too where an empty reply does
  nothing. `Integer.parseInt` throws for all three (checked on JDK 16.0.2). Upstream's code, and
  jc-17's: UB-12's fix tests only for `null` and empty, as jc-17 did. Raised by the jc-31 review;
  verified, not run in a window.
- **Go to... with the open level's own number replays the route under seed 0.** Go to... always
  asks for seed 0, an even step and an Up slide with the moves kept (`loadLevel(n, 0, Step.EVEN,
  true, ...)`), and `tryLoadLevel` takes the keep-moves path whenever the number is the open
  level's, so the moves are replayed under those defaults, not the seed, step and slide they were
  made under (set with Set RNG Seed, F1 or F4), and can end somewhere else, with no message. Typed
  for any other level, Go to... loads it afresh. Upstream's code; UB-12's fix does not reach it.
  Raised by the jc-31 review (third round); read in the code, not run.
- **Go to... with a number past the set's last level (or below 0) reports an internal error.**
  The number goes to `DatParser.parseLevel` unchecked (only 0 and one past the last level wrap),
  so it fails with an `ArrayIndexOutOfBoundsException`: the message reads like "Could not load level:
  Index 99 out of bounds for length 3", and its stack trace goes to the error log. The level open
  stays as it was. Upstream's code. Raised by the jc-31 review (third round); the error-log entry
  measured by a planted load of level 9999 in the jc-31 mutation runs, the message read in the code.
- **Every launch rewrites `succ_settings.ini`, with the same content.** At startup SuperCC calls
  `LevelPanel.changeNotation(paths.getTWSNotation())`, which saves the TWS-notation setting back,
  and any setting saved rewrites the whole file. Nothing in it changes, but its timestamp does, so
  Dropbox syncs it after every launch, and a launch on each PC at once has two machines writing
  one file. In the code since the import of upstream (`fc31de0`), so every build. Seen in the
  jc-31 deploy (the file's hash unchanged, its time moved); traced in the code.
- **Choosing a file that is not what the item expects fails with an internal error.** Solution ›
  Open and Search for seeds turn a file that is not JSON into an `IllegalArgumentException`, and a
  JSON file that is not an object into a `ClassCastException`, neither caught, so either reaches
  the event thread and the error log and nothing is shown; the first one's message would also name
  the default route path (`getJSONPath()`), not the file chosen. Load states from disk catches only
  `IOException` and `ClassNotFoundException`, so a `.ser` holding some other object fails the same
  way (`ClassCastException`). Upstream's code; jc-12 handled only a file that is not there, and
  UB-13's fix only a canceled chooser. Raised by the jc-32 review (second round); read in the code,
  not run.
- **Saving through a chooser (`MenuBar.saveNewFile`): no overwrite question, a case-sensitive
  extension, a stream not closed on failure.** Choosing an existing file replaces it without
  asking; a name typed as `route.TWS` is saved as `route.TWS.tws`; and if the write fails part way
  (a full disk), the `FileOutputStream` is never closed, so on Windows the half-written file stays
  locked until the stream is collected. Solution › Save as, Save all states to disk and TWS ›
  Write solution to new tws all save this way. Upstream's code. Raised by the jc-32 review (second
  round); read in the code, not run.
- **Load states from disk deserializes whatever file is chosen.** It hands the file to
  `ObjectInputStream.readObject` with no filter and checks the type only afterward, so a `.ser`
  from someone else runs Java deserialization of whatever classes it names (the classic route for
  untrusted serialized data to run code, if a usable class is on the classpath) before anything is
  checked. Its mismatch message also decodes the saved title with the platform's default charset
  where the comparison above it uses Windows-1252. Upstream's code; a filter admitting only the
  savestate classes would close it. Raised by the jc-32 review (third round); read in the code,
  not run.
- **A Lynx set named on the command line is drawn in the MS tileset.** Startup opens a set named
  on the command line first (its ruleset's tileset swapped in), then draws the MS tileset over it
  -- startup reads only `TilesheetNum`. Launched 2026-11-15 with a Lynx-signature copy of CCLP1 on
  the command line: what is drawn follows `TilesheetNum` (MSCC for 3, CCEdit for 0) whatever
  `LynxTilesheetNum` says, on jc-20 (jc-18's tileset code) and the jc-34 candidate alike. Since
  jc-34 a bad MS number there says so over the Lynx level, which is true but about a tileset not
  in use. Upstream's startup order; raised by the jc-34 review (second round). Since jc-36 (UB-21) the swap
  at that open marks and, if it falls back, names the Lynx choice, and startup then draws the MS
  tileset over it, so the mark (and such a message) can name a tileset not shown (raised by the
  jc-36 review; the ordering is upstream's, kept). Since jc-37 (UB-16) the tileset startup draws there is MS's
  choice through `loadForRuleset`, custom included: with MS custom on, a Lynx level named on the
  command line is drawn in the custom pictures, and a window-size change then reloads by the Lynx
  ruleset's own setting, so the tileset can change at the resize (raised by the jc-37 review;
  jc-18 read startup the same way).
- **A saved tile size of 0 (or less) stops startup partway.** `TileWidth = 0` reaches the image
  scaling unchecked (`IllegalArgumentException: Width (0) and height (0) must be non-zero`), after
  `Gui` has put the window up, so the rest of startup -- layout, notation -- never runs, and only
  the error log's notice shows: UB-20's shape, from a different setting. Launched 2026-11-15:
  jc-20, jc-33 and the jc-34 candidate alike. Upstream's; raised by the jc-34 review (second
  round). Whether the tile-size dialog can save such a size was not checked.
- **View › Tile size has no fallback.** Its sizes and its custom box reload the tileset drawn
  through `getTileSheets` directly, so with a custom tileset whose picture has gone the raw error
  is shown (not the "kept" wording, and not once a session), the tile-size mark has moved to a size
  not applied, and the View › Tileset mark is not corrected. jc-18 did not change them (its MenuBar listener matches today's but for
  UB-11's markers, and the jc-22 revert did not touch `ChooseTileSize`), so this is upstream's and
  jc-18's alike; read in the code, raised by the jc-36 review.
- **With a fresh settings file, Level › Open levelset's chooser opens in the user's Documents
  folder.** It is handed the stored Levelset folder, "" until a set has been opened, and
  `MenuBar.openFile` turns "" into `JFileChooser.setCurrentDirectory(null)` -- Java's default
  folder for the user -- the same path UB-31 measured for custom's choosers. Upstream's, and every
  fork build's alike (no fix was reverted here); read in the code, raised by the jc-39 review. The
  root of both is `openFile`'s handling of "", which UB-31's fix works around in custom's listener
  only, as jc-18 did.
- **Seed Search from a negative start searches some seeds twice.** The RNG keeps 31 bits, so each
  negative seed in the first thread's range is a positive seed another thread also searches, and
  both attempts are counted: from -5, seeds -5 to -1 repeat the top five; from `Integer.MIN_VALUE`
  every seed is searched twice and the total reads 4,294,967,296. Upstream's, and jc-16's alike:
  UB-7's `(long)` only stops the negative start from emptying every range but the last (jc-16's
  own comment says a negative start "only repeats positive seeds"). Turning a negative start into
  its 31-bit seed, or refusing it, would change what the field accepts, so it is a decision rather
  than a fix to re-apply. Read in the code, raised by the jc-42 review. Related (jc-43 review): a
  success at a negative seed is shown as that negative number (jc-16's choice, kept: it can be typed
  into Set RNG Seed as is), though the RNG treats it as its 31-bit equivalent, so a saved solution
  stores the negative form.
- **Seed Search's Begin throws on a start or position it cannot read.** `Integer.parseInt` on the
  raw start field throws for `""`, `" -5"`, `"abc"` or `"2147483648"`, and a position without a
  comma throws `ArrayIndexOutOfBoundsException`, on the event thread: Begin does nothing visible
  (the trace goes to the error log). Upstream's code in every build. Raised by the jc-42 review, not
  reproduced in the window.
