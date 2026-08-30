# Changelog

Every release of this fork, newest first, in the spirit of [Keep a Changelog](https://keepachangelog.com/).

Versions are build tags, `jc-N`, not semantic versions — the fork tracks upstream SuperCC rather
than versioning independently. Dates are the tagged commit's date.

**Two jars must never report the same build tag.** jc-6 exists only because a one-line fix landed
after jc-5 was already deployed and reusing the number was not an option.

This file is a summary. `README.txt` section 7 is the user-facing history that ships in the download,
and `FORK.md` is the engineering record: why each change exists, what broke first, and what was
measured. All three are updated together — see [`.github/RELEASING.md`](.github/RELEASING.md).

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
