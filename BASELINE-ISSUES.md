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

---

## Seen while testing — NOT introduced by the baseline

These were already in upstream and in every fork build. They are listed so they are not lost.

- **Seed Search's button still says "Pause" after a search finishes by itself.** Seen in the
  2026-11-08 playtest (CCLP1 #1, the last 3,000 seeds: 3000/3000, button "Pause"). Since the search
  is no longer running, pressing it starts a new round of threads rather than pausing; what that
  round does was not checked. Upstream and jc-16 to jc-19 alike; the fork never touched that code.
