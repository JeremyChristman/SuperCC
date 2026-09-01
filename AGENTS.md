# AGENTS.md

Instructions for any coding agent working in this repository, in the cross-tool
[AGENTS.md](https://agents.md) convention.

**The full brief is [`CLAUDE.md`](CLAUDE.md). Read it before making changes.** This file is the
subset that will keep you out of trouble if you read nothing else. It is short on purpose; every
line of it is a mistake somebody already made here.

## What this is

Jeremy Christman's public fork of [SuperCC](https://github.com/SicklySilverMoon/SuperCC) — a Java 16
Swing emulator and tool-assisted-solution workbench for *Chip's Challenge* (CC1). GPLv2-or-later.
Builds are tagged `jc-N` and published as GitHub releases that real people download.

## Setup

- **Windows and a JDK 16+.** No Maven, no Gradle, no package manager, no dependency manifest.
  Third-party libraries are committed as bytecode under `com/` and `org/`.
- All tooling is PowerShell, and CI runs **Windows PowerShell 5.1** on purpose.

## Build, test, package

```powershell
powershell -ExecutionPolicy Bypass -File verify-splice.ps1  # will my edit actually ship? (rule 1)
powershell -ExecutionPolicy Bypass -File build.ps1          # -> SuperCC.jar
powershell -ExecutionPolicy Bypass -File run-tests.ps1      # builds, then runs test\
powershell -ExecutionPolicy Bypass -File build.ps1 -Package # -> dist\SuperCC-<tag>.zip
java -jar SuperCC.jar                                       # run it (never javaw -- it eats stderr)
```

Coverage numbers in CLAUDE.md are enforced at RELEASE time by `coverage.ps1 -CheckBaseline` against
`docs/coverage-baseline.tsv`. If you move the number: run `coverage.ps1 -UpdateBaseline`, update the
CLAUDE.md section 4 table, commit both. Not gated in CI.

Coverage: `coverage.ps1` (JaCoCo; test-time only, never committed, not a dependency). Branch
coverage of `game\**` + `io\**` is **20.6%** — a floor, not a grade. `game\Lynx\**` (4.8%) and the
`io.TWS*` streams (0%) are the biggest gaps. No CI gate; see CLAUDE.md §4.

Machine-readable results: `run-tests.ps1 -ResultsPath test-results` writes JUnit XML and JSON per
test class. Exit code is 0 only if every assertion passed. Use `-Isolated` if another agent may be
building at the same time — `SuperCC.jar` and `dist/` are shared mutable paths.

## The five rules

1. **Never run a full `javac` rebuild.** This is a *splice* build. Fifteen GUI classes come from
   IntelliJ GUI Designer `.form` files whose `$$$setupUI$$$()` method `javac` cannot generate; a
   full rebuild yields a jar that dies at launch with `playButton is null`. The committed `.class`
   files at the repo root (`emulator/ game/ graphics/ io/ tools/ util/`) are the authoritative
   baseline. They are **not** stray build output — do not delete or "gitignore" them.

2. **If you edit a source file, make sure it is in `$SPLICE_MODIFIED` in `build-config.ps1`.** Only
   files in that list are recompiled. Edit anything else and the build succeeds while shipping the
   old behavior, with no error. Confirm the file has no sibling `.form` first. **`verify-splice.ps1`
   checks this for you** — run it after touching `java/**`. It recompiles the unspliced files and
   compares bytecode, and hashes the 15 form-based files (`.form` *and* their sibling `.java`,
   which cannot be recompiled here at all) against `docs/form-baseline.sha256`. Its header lists
   the narrow cases it still cannot see.

3. **Test through the built jar, never against freshly compiled sources.** The jar is the only
   artifact whose behavior is authoritative. `run-tests.ps1` puts it on the classpath for you.

4. **Never commit a level set.** `.dat` and `.ccl` files are third-party content. Tests synthesize
   their own fixtures with `test/DatBuilder.java`. A test needing a real set must **skip**, not
   fail, when it is absent.

5. **Check `CLAUDE.md` §6 before "fixing" anything that looks wrong.** A dozen things in this repo
   look like bugs and are deliberate, load-bearing decisions with ADRs behind them — the committed
   bytecode, the hand-built zip entries, the off-by-default build tag, the relative stored paths,
   and the jc-10 reversal of jc-8's TWS pinning among them.

## Style

- **American English** everywhere: `color`, `behavior`, `gray`, `analyze`, `center`, `-ize`.
- Comment *why*, not *what*. Mark fork-specific edits `/* MOD (Jeremy, jc-N): ... */` and say what
  trap motivated them.
- SOLID and GoF patterns only where they genuinely make the code cleaner.
- No `\u` escape sequences in Java comments — the lexer decodes them inside comments, so a Windows
  path in a comment is a compile error.

## Pull requests

Run `run-tests.ps1` and include the result. CI runs build + tests + CodeQL on `windows-latest`.
See `.github/CONTRIBUTING.md` for the full workflow and `.github/RELEASING.md` for shipping.
