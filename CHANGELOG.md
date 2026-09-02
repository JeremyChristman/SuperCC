# Changelog

Every release of this fork, newest first, in the spirit of [Keep a Changelog](https://keepachangelog.com/).

Versions are build tags, `jc-N`, not semantic versions — the fork tracks upstream SuperCC rather
than versioning independently. Dates are the tagged commit's date.

**Two jars must never report the same build tag.** jc-6 exists only because a one-line fix landed
after jc-5 was already deployed and reusing the number was not an option.

This file is a summary. `README.txt` section 7 is the user-facing history that ships in the download,
and `FORK.md` is the engineering record: why each change exists, what broke first, and what was
measured. All three are updated together — see [`.github/RELEASING.md`](.github/RELEASING.md).

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
- **Cancelling a file chooser threw an uncaught NullPointerException.** `MenuBar.openFile` returns
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
