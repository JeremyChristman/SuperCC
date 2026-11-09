# SuperCC — agent brief

You are working in **Jeremy Christman's fork of SuperCC**, a Java 16 Swing desktop application:
an emulator and tool-assisted-solution workbench for the original *Chip's Challenge* (CC1).

Upstream is [SicklySilverMoon/SuperCC](https://github.com/SicklySilverMoon/SuperCC); this fork is
public at <https://github.com/JeremyChristman/SuperCC> and is **GPLv2-or-later**. Builds are tagged
`jc-N` and published as GitHub releases that real people download.

Read this file completely before you change anything. Everything below is load-bearing.

---

## 🔴 1. The one thing that will break your build: this is a SPLICE build

**Do not run a full `javac` rebuild of this project. It produces a jar that compiles cleanly, passes
a smoke check, and then dies on launch.**

SuperCC's GUI is built with the **IntelliJ GUI Designer**. Fifteen classes have a sibling `.form`
file (`java/graphics/Gui.form` and the fourteen `java/tools/*.form`). IntelliJ's *form compiler* —
not `javac` — generates a hidden `$$$setupUI$$$()` method into those classes that constructs their
Swing components. `javac` has no idea the `.form` files exist. Recompile a form class with `javac`
and its components are never created, so the app throws `NullPointerException: playButton is null`
at startup. Under `javaw` (double-click) stderr goes nowhere, so the symptom a user reports is
**"nothing happens."**

The repo therefore works like this:

| What | Where | Role |
|---|---|---|
| IntelliJ-built `.class` baseline | `emulator/ game/ graphics/ io/ tools/ util/` at the repo **root** | **Committed on purpose.** The authoritative bytecode for the form classes. |
| Java source | `java/**` | Authoritative for the *non-form* classes only. Also shipped inside the jar, as upstream does. |
| Third-party libs | `com/`, `org/` | Committed bytecode only (IntelliJ annotations, `org.json`). No package manager, no manifest. |

`build.ps1` recompiles **only** the files in the `$SPLICE_MODIFIED` list — which lives in
`build-config.ps1`, not in `build.ps1` — and splices the results over that committed baseline.

> **If you modify a source file that is not already in `$SPLICE_MODIFIED`, add it to that list in
> `build-config.ps1`, or your edit silently does nothing.** The build will succeed and ship the old
> behavior. Before adding it, confirm the file has no sibling `.form` — if it does, it must NEVER go
> in that list (javac would ship it without components). A form class is compiled separately,
> `.java` or `.form` edit alike: `formclass.ps1 -Compile <file>`, which runs JetBrains' own form
> compiler (ADR 0011; `formclass.ps1 -DownloadCompiler` once per machine). IntelliJ is not needed.

**You do not have to take this on trust — run the check:**

```powershell
powershell -ExecutionPolicy Bypass -File verify-splice.ps1
```

It covers the two ways an edit can fail to ship, by two different mechanisms:

- **Unspliced ordinary files** (63 of them since jc-17) are recompiled and compared against the bytecode the
  jar would actually contain. An edit the build ignores fails and names the file.
- **Form-based files** — the 15 `.form` files *and their sibling `.java`* — ship as committed
  bytecode the build never recompiles, so they are hashed against `docs/form-baseline.sha256`.
  `graphics/Gui.java` is ordinary hand-written Java and is the likeliest of these to be edited; an
  edit there is flagged. Compile the class with `formclass.ps1 -Compile <file>` (either edit),
  commit the new `.class` files, and re-run with `-UpdateFormBaseline`.

**The form classes have a second check of their own.** `formclass.ps1 -Check` compiles all fifteen
from their committed `.java` **and `.form`** with javac plus JetBrains' own form compiler, and
requires the committed classes **byte for byte** — so a `.java` or `.form` edit whose class was
never compiled fails, even after someone has run `-UpdateFormBaseline`. Byte for byte because that
was measured: from unchanged sources the compiler reproduces all fifteen IntelliJ-built classes
exactly (ADR 0011). CI and the release workflow run it after the build with `-RequireCompiler`, so
a missing compiler is a failure; locally, without the compiler, it falls back to the older
transplant comparison and says so loudly — that one cannot see a `.form` edit. It needs JDK 16.0.2
exactly. **And a byte match is not a working window:** `test/FormClassTest.java` runs the real
constructor of every form window that can run headless, and `GuiWindowTest` builds the main one.
After any `-Compile`, those tests and a launch of the program are what prove the window still
builds. `-Compile` also runs the ADR 0010 transplant over the same javac output as a LINT, because
the real compiler reproduces IntelliJ's known misplacements of the setup call faithfully; how those
were found is in `docs/adr/0010`.

Run it after touching anything under `java/**`, before calling the change done. CI runs it too.
**Run it on JDK 16.0.2, like CI.** On a newer `javac` it reports false edits: on JDK 18 (measured
2026-11-02), `graphics\SmallGamePanel.java` and `tools\tsp\SimulatedAnnealing.java`, both untouched,
compile to different bytecode and fail as "EDITED but not spliced". A machine without 16.0.2 can
point `JAVA_HOME` at an unpacked Temurin 16.0.2+7 zip for the one command (see §4, *Coverage*).
Its header lists the narrow cases it still cannot see (annotations, a literal swapped between two
concatenations, line-number-only edits) — read that before trusting a green run absolutely.

**Corollary that shapes every test in this repo: the built jar is the only authoritative artifact.**
The source tree can disagree with what ships. Tests run against `SuperCC.jar`, never against
freshly compiled sources. See §4.

---

## 2. Commands

Everything is PowerShell. There is no Maven, no Gradle, no `package.json`, no dependency manifest.
Requires a **JDK 16 or newer** (`--release 16` keeps bytecode compatible with the baseline) to build
and test — but `verify-splice.ps1` and the coverage baseline need **16.0.2 itself**, as CI uses (§1, §4).

```powershell
powershell -ExecutionPolicy Bypass -File verify-splice.ps1         # will my edit actually ship?
powershell -ExecutionPolicy Bypass -File build.ps1                 # -> SuperCC.jar
powershell -ExecutionPolicy Bypass -File build.ps1 -Package        # -> also dist\SuperCC-<tag>.zip
powershell -ExecutionPolicy Bypass -File run-tests.ps1             # builds, then runs test\
powershell -ExecutionPolicy Bypass -File run-tests.ps1 -NoBuild    # reuse the existing jar
powershell -ExecutionPolicy Bypass -File run-tests.ps1 -ResultsPath test-results   # JUnit XML + JSON
powershell -ExecutionPolicy Bypass -File run-tests.ps1 -Isolated   # private temp jar (see §9)
powershell -ExecutionPolicy Bypass -File coverage.ps1 -Download     # JaCoCo, first run only
powershell -ExecutionPolicy Bypass -File coverage.ps1               # branch coverage (see §4)
powershell -ExecutionPolicy Bypass -File coverage.ps1 -CheckBaseline  # do the numbers below still hold?
powershell -ExecutionPolicy Bypass -File coverage.ps1 -UpdateBaseline # after deliberately moving them
powershell -ExecutionPolicy Bypass -File verify-splice.ps1 -UpdateFormBaseline     # after rebuilding a form class
powershell -ExecutionPolicy Bypass -File formclass.ps1 -DownloadCompiler   # JetBrains' form compiler, once per machine
powershell -ExecutionPolicy Bypass -File formclass.ps1 -Compile tools\SeedSearch.java  # a form .java or .form edit (JDK 16.0.2)
powershell -ExecutionPolicy Bypass -File formclass.ps1 -Check       # every committed form class is byte for byte its .java + .form
powershell -ExecutionPolicy Bypass -File formclass.ps1 -SelfTest    # the rebuild tool still refuses what it must
powershell -ExecutionPolicy Bypass -File verify-splice.ps1 -UpdateVendorBaseline   # after a deliberate library update
```

Extra switches worth knowing:

- `build.ps1 -ExpectTag jc-N` fails unless `BUILD_TAG` matches (the release workflow passes the
  pushed git tag), and `-Manifest <path>` writes a JSON record of the build — tag, jar SHA-256,
  size, entry count, and the exact compiler used.
- `verify-splice.ps1 -UpdateFormBaseline` regenerates `docs/form-baseline.sha256`. It is the *only*
  correct response to a flagged form file, and only after the class has genuinely been compiled
  (`formclass.ps1 -Compile`) and committed. Running it to silence a warning no longer defeats the
  `.form` check outright — `formclass.ps1 -Check`'s byte comparison would still fail — but only
  where the compiler runs, which CI guarantees and a local machine may not.
- `formclass.ps1` builds a private jar to compile against unless given `-Jar <path>`; CI passes the
  jar its Build step just made. It refuses to run on anything but `javac 16.0.2`.
- `-Isolated` and `-NoBuild` are mutually exclusive and the script rejects the pair: `-Isolated`
  names a jar it is about to build, so there is nothing for `-NoBuild` to reuse.

`build.ps1` resolves a real JDK `bin` by itself — the Oracle `javapath` directory that lands on
`PATH` exposes `javac` and `java` but **not** `jar`, so `$env:JAVA_HOME` or a real JDK under
`C:\Program Files\Java` is what it looks for.

### Running it

```powershell
java -jar SuperCC.jar
```

Always from a console, **never** `javaw` or a double-click — those swallow the stack trace you need,
and a GUI failure then presents as "nothing happens". `emulator.SuperCC.main` hands its arguments to
`ArgumentParser`, so a level set path can be passed on the command line. Playtesting is a required
gate before any release, so being able to launch it matters.

**A clean launch prints nothing to stderr.** That is new in jc-11: a `getSavestates()` NPE used to
fire on every startup (harmless, and invisible because `javaw` discarded it). If you see stderr
output on startup now, it is real — investigate it.

**Errors also land in a file now.** jc-11 tees `System.err` to `succ_error-<MACHINE>.log` next to
`succ_settings.ini`, created lazily, capped at 512 KB with one rotation. It is gitignored, and CI
fails if one is ever committed — it contains local paths. See
`docs/adr/0009-the-error-log-is-not-a-setting.md` for why it is always on and has no setting.

**Target platform is Windows.** Both of Jeremy's machines run **Windows PowerShell 5.1**, and CI
deliberately runs 5.1 too rather than PowerShell 7, so that CI fails the same way his machine does.
See `docs/adr/0006-ci-runs-windows-powershell-5-1.md`. Do not introduce PowerShell 7 syntax — no
`&&`, `||`, ternary, or `??`.

If you edit a workflow: GitHub Actions wraps every `run:` with `$ErrorActionPreference='stop'` and
appends `exit $LASTEXITCODE`, so **the last native command in a step decides whether the step
passes**. Never end one with `robocopy`, `git diff`, or `findstr`.

---

## 3. Repo map

```
CLAUDE.md              this file
AGENTS.md              the same essentials for non-Claude agents
README.txt             ships in the release zip; section 7 is the user-facing revision history
FORK.md                deep engineering notes, per change, with the reasoning
BASELINE-ISSUES.md     every problem the upstream baseline brought back (UB-n), to fix later (§6)
CHANGELOG.md           Keep a Changelog view of the same history
COPYING                GPLv2 — ships with the binary, as the license requires
build-config.ps1       THE splice list, the JDK finder, the robocopy wrapper — one source of truth
build.ps1              the splice build + the release packager (-Package)
verify-splice.ps1      proves an edit will actually ship (§1)
formclass.ps1          compiles / checks the FORM classes with JetBrains' form compiler (§1, ADR 0011)
formclass/**           FormCompile.java (drives that compiler as the IDE does) and FormTransplant.java
                       (ADR 0010's transplant, now the lint on every compile; JDK-internal ASM)
run-tests.ps1          builds, then compiles and runs everything in test\
coverage.ps1           JaCoCo branch coverage, scoped to game\** + io\** (§4)
docs/coverage-baseline.tsv  what the §4 table claims; the release gate checks it
docs/vendor-baseline.sha256 hashes of the 23 vendored third-party class files (check 5)
trace.ps1              differential trace against Tile World, local only (§4)
corpus.ps1             replays EVERY stored solution through a jar and compares two runs (§4)
corpus/**              its harness (CorpusCheck.java) and self-test fixture (CorpusFixture.java)
java/**                source (authoritative for non-form classes)
emulator/ game/ graphics/ io/ tools/ util/    committed .class baseline — DO NOT DELETE
com/ org/              third-party bytecode (inventory: docs/THIRD_PARTY.md)
resources/             tilesets, icons, sounds
test/**                the regression suite (see §4)
docs/adr/**            Architecture Decision Records — read before "fixing" anything odd
docs/THIRD_PARTY.md    what is vendored, and under which license
docs/form-baseline.sha256   hashes of the 15 .form files and their sibling .java (see §1)
.claude/settings.json  agent command allowlist — a prompt-reducer, NOT a security boundary
.github/workflows/**   CI, release, CodeQL
.github/dependabot.yml keeps the SHA-pinned actions current
.github/CONTRIBUTING.md  the contributor loop
.github/RELEASING.md     the full release checklist
.github/PULL_REQUEST_TEMPLATE.md, .github/ISSUE_TEMPLATE/
```

Key source files, by what they own:

| File | Owns |
|---|---|
| `java/emulator/SuperCC.java` | app entry, `BUILD_TAG`, window title, `openLevelset`, `startingRuleset()` |
| `java/io/SuccPaths.java` | the whole `succ_settings.ini` contract — parsing, defaults, atomic write, opt-in switches |
| `java/io/DatParser.java` | CC1 `.dat` reading. A file reader; it must not learn about settings. |
| `java/io/LevelFactory.java` | turns parsed fields into a `Level`; owns the MS and Lynx monster lists |
| `java/io/TWSWriter.java`, `TWSReader.java` | Tile World solution files |
| `java/game/**` | the emulator itself — MS and Lynx rulesets, `RNG`, `Level`, creatures |
| `java/graphics/**` | Swing UI. `Gui.java` is form-based; the others are not. |

---

## 4. Tests

`run-tests.ps1` compiles every `test\*.java` against the **built jar** and runs each class that has
a `main`. Adding a test file is all it takes — there is no registry to update.

```
test/Harness.java          shared PASS/FAIL/SKIP counters + JUnit-XML and JSON emitters
test/DatBuilder.java       synthesizes a valid CC1 .dat in a temp dir — no level set needed
test/SettingsTest.java     the settings contract (SuccPaths)
test/EngineTest.java       .dat parsing and headless emulator behavior
test/ErrorLogTest.java     the jc-11 error log
test/MonsterListTest.java  creature-list ORDER under both rulesets — the desync surface
test/ConnectionTest.java   trap/clone wiring (fields 4 and 5) — the stride arithmetic, and the trap→button map
test/RngTest.java          the shared TW generator — and that it is NOT MSCC's
test/ButtonTest.java       the four button types: what a press does, and what it must NOT touch
test/CreatureMoveTest.java move ORDER per creature, transcribed from TW's choosecreaturemove()
test/CanEnterTest.java     which tiles admit whom, from which direction — TW's movelaws[] table
test/SlideAndLeaveTest.java  where a slide sends you, and whether you may leave a square
test/LynxCreatureMoveTest.java  the LYNX preference table, from lxlogic.c -- a different oracle
test/LynxCanEnterTest.java   the LYNX movelaws[] entry table, all three entity columns
test/LynxCreatureListTest.java  LYNX list ORDER (backward, both phases), claims, clones
test/LynxTickTest.java       LYNX start/continue/end movement -- speed table + arrival effects
test/TwsRoundTripTest.java   the .tws solution format, written and read back
test/TwsClickTest.java       exporting a CLICK to .tws -- the jc-2 fix, via the real emulator
test/MsTryEnterTest.java     MS ARRIVAL effects -- what a tile does to whoever steps on it
test/MsCreatureListTest.java MS list per TICK -- FORWARD order, the teeth clock, what MS refuses
test/MsCloneTrapTest.java    MS clone machines and beartraps -- the dispatch, and traps that START occupied
test/WindowTitleTest.java    the window title -- all 8 combinations of the three jc-14 switches
test/LinkTest.java           every call between the fork's classes resolves IN THE BUILT JAR (§1's splice hazard)
test/InputDiscardTest.java   which keys Level.tick reports as DISCARDED, and the WAIT the move list records
test/SeedSearchTest.java     Seed Search through its REAL constructor, headless: stops at the top seed, threads, Pause
test/FormClassTest.java      every form window's REAL constructor builds its components (14 of 15; Gui is the JFrame)
test/GuiWindowTest.java      the MAIN window (Gui) built for real in a NON-headless child JVM; no-level-open guards
```

**Testing a form-based window headless.** Its constructor builds every component
(`$$$setupUI$$$`), then throws `HeadlessException` at its `JFrame`. So reaching that exception means
setup ran: `FormClassTest` checks exactly that for every form window, plus a spy `PanelUI` count.
A rebuilt class with no setup call, a final bound field, or the call on one branch fails it with
`NullPointerException` / `IllegalAccessError` instead. To drive a window's behavior,
`SeedSearchTest` goes further: a spy `ButtonUI` installed through `UIManager` catches the button,
whose listener is the constructor's own lambda, holding the instance.

**The main window cannot be tested that way** — `Gui` IS the `JFrame`, so headless its `super()`
throws first. `GuiWindowTest` launches a child JVM with `-Djava.awt.headless=false` in a temp
folder, builds the real window through `new SuperCC()`, and reads back PASS/FAIL lines; without a
desktop it skips. On a desktop, a SuperCC window appears for a few seconds during the suite. After
any `formclass.ps1 -Compile graphics\Gui.java`, this is the test that matters. Its helpers that
close modal dialogs are started and stopped around each action, so neither can close a later one.
Where a check COUNTS dialogs, use `closeEveryDialog()`, not `closeNextDialog()`: the second stops
after one, so a second dialog hangs the child until the 90-second kill instead of failing the count.

`InputDiscardTest` is about the move list, which the corpus cannot see: a discarded key changes no
final state, only what SuperCC records, and so what a saved `.json` or an exported `.tws` holds.
Only Lynx discards. If a change ever makes MS return `Level.MASK_DISCARD_INPUT`, `InputDiscardTest` §4 shows the cost —
an MS key on a force floor is an override, and recorded as a wait the route ends somewhere else; even
an MS key on ice, whose move is refused, changes the savestate. Its `inert()` check is the property
everything else rests on: on a tick the engine reports discarded, every key from the same savestate
must give the same savestate. Run it on any new discard rule before trusting the rule.

`LinkTest` exists because a splice build can ship a stale call. Only the files in `$SPLICE_MODIFIED`
are recompiled; if one of them removes or re-signs a method, a baseline `.class` that still calls the
old one goes into the jar untouched, and throws `NoSuchMethodError` only when that line runs.
`verify-splice.ps1` cannot see it for the fifteen form classes, which it hashes rather than
recompiles. The upstream merge removes methods routinely (the trap rework removed four), so any
merge step that goes green here has at least proved its API changes did not strand a caller.

### Mutation testing — and the trap that makes it lie

Every engine test here was checked by planting the defect it claims to catch. That is the only way
to know an assertion tests anything: four separate tests in this suite looked airtight, passed, and
turned out to catch nothing when the bug was actually planted.

**🔴 The splice build makes naive mutation testing silently useless.** `build.ps1` recompiles only
the files in `$SPLICE_MODIFIED` (§1). Plant a defect in anything else — `game\MS\MSCreatureList.java`,
`game\Lynx\LynxCreature.java`, `game\Position.java` — and the build overlays nothing: the jar keeps its committed
baseline bytecode, the suite runs against unmutated code, and **every mutation "passes"**. You
conclude the test is weak, or that the code is fine, and both conclusions are wrong.

This is the same silent-failure class `verify-splice.ps1` exists to prevent, in the one direction it
cannot help with — here you *want* a temporary edit to ship.

The procedure:

```powershell
# 1. add the file you are about to mutate to $SPLICE_MODIFIED in build-config.ps1 -- CHECK FIRST:
#    since the upstream merge, MSCreature, MSLevel, LynxLevel, Level and BrownButton are already
#    there for good, and must not be added twice or removed in step 4
# 2. plant the defect, then:
powershell -ExecutionPolicy Bypass -File build.ps1
powershell -ExecutionPolicy Bypass -File run-tests.ps1 -NoBuild
# 3. restore the source AND rebuild, or the next run measures the mutated jar
# 4. put build-config.ps1 back (git diff build-config.ps1 must be empty), rebuild, and confirm
#    verify-splice.ps1 is green
```

Three things that have actually gone wrong doing this:

- **Forgetting step 3's rebuild.** Restoring the source without rebuilding leaves the mutated jar in
  place, so the *next* measurement — often the "clean baseline" — is taken against mutated code. It
  shows up as a baseline that inexplicably fails.
- **Leaving `build-config.ps1` widened.** `verify-splice.ps1` will not complain: a spliced file is
  compiled from source, which is legitimate. The check that catches it is counting the entries.
- **Believing a survivor.** A surviving mutation means one of three things, and they need different
  responses: the test is weak (fix the test), the mutant is *equivalent* (record why, as
  `MsCloneTrapTest` does for `springTrappedCreature`), or the splice list was never widened (nothing
  was tested at all). Check the third first — it is the cheapest to rule out and the easiest to miss.

### Coverage — what the suite actually reaches

`coverage.ps1` runs the suite under JaCoCo and reports **branch** coverage. Branch, not line: an
emulator is mostly conditionals, and a line count flatters a `switch` whose arms are never all
taken. JaCoCo is a test-time tool cached under `%LOCALAPPDATA%\jacoco\<version>\` — it is **not a
dependency**, never committed, never shipped. Output goes to `coverage-report\` (gitignored);
`coverage.csv` is the evidence behind every percentage below.

As of jc-20, with all 2,362 assertions passing (24 of them `upstreamBug` pins, §6) and 4 skipped —
counts for a machine with 4 or more CPUs. `SeedSearchTest` §3 makes one pin per search thread, so a
3-CPU run has one pass and pin fewer and a 2-CPU run two; at 1 CPU §3 and §2's pin are skipped
(measured: 7 passes and 6 pins fewer, 2 skips more) — **measured on JDK 16.0.2** (16.0.2+7-67 on the desktop; Temurin 16.0.2+7, what CI pins, gives the
same figures):

| scope | branch | line | branches |
|---|---|---|---|
| **`game\**` + `io\**` — THE TARGET** | **56.6%** | 71.2% | 1514/2676 |
| &nbsp;&nbsp;`game\**` (the emulator) | 53.2% | 67.5% | 1191/2239 |
| &nbsp;&nbsp;&nbsp;&nbsp;`game\MS\**` | 46.8% | 61.9% | 545/1164 |
| &nbsp;&nbsp;&nbsp;&nbsp;`game\Lynx\**` | 62.8% | 70.6% | 460/732 |
| &nbsp;&nbsp;&nbsp;&nbsp;`game\*` + `button\**` (shared) | 54.2% | 73.0% | 186/343 |
| &nbsp;&nbsp;`io\**` (file formats) | 73.9% | 81.8% | 323/437 |
| `emulator\**` | 36.0% | 37.9% | 191/530 |
| `tools\**` + `graphics\**` — *not a target* | 4.3% | 9.6% | 86/2023 |

**Read this correctly.** Whole-project branch coverage is 30.7% (1903/6195), and that number is
meaningless: 15 form-based classes can be rebuilt here only by copying IntelliJ's output (ADR 0010), `tools\**`
is 35 files of upstream code this fork does not modify, `graphics\**` is Swing with no headless test
story, and the rest is vendored `org.json` / `com.intellij`. What this fork can break is the engine
and the file formats, so that is what is scoped and measured.

**The number is JDK-dependent, by construction — so measure the baseline on JDK 16.0.2.** Every
spliced class is compiled by your `javac`, and `build-config.ps1` already notes that javac 17 does
not emit identical bytecode to javac 16. That was once only the five `io\**` classes (`ErrorLog`,
`LevelFactory`, `SuccPaths`, `TWSReader`, `TWSWriter`, 382 branches at jc-15). Since the upstream
trap merge (2026-11-02) it is also `MSCreature`, `MSLevel` and `LynxLevel` (plus `Level` and
`BrownButton`, which have none): another 1,154 branches — 1,554 of the target's 2,676 in all. Only the rest comes from the committed
baseline and is byte-identical on every machine. A figure that differs slightly on another JDK is not
a bug, but it is not the baseline either: `docs/coverage-baseline.tsv` and the table above are
measured on 16.0.2, what CI and the release workflow pin. A machine without it does not need an
install: jc-15's baseline was taken on the laptop (JDK 18) from Temurin 16.0.2+7's zip — the build CI
uses, checked against Adoptium's published SHA-256 — unpacked to a scratch folder, with `JAVA_HOME`
pointed at it for the one command. `build-config.ps1` lets `JAVA_HOME` win.

**56.6% is a floor to build on, not a passing grade**, and the split says where the work is:

- **Lynx is ahead now — MS 46.8%, Lynx 62.8% — and both took deliberate work.**
  Four Lynx files built that side (the move-preference table `LynxCreatureMoveTest`, the entry rules
  `LynxCanEnterTest`, the creature list `LynxCreatureListTest`, the movement engine `LynxTickTest`,
  all against `lxlogic.c`); three MS files closed the gap (`MsTryEnterTest`, `MsCreatureListTest`,
  `MsCloneTrapTest`). **`MSCreature` (43.9%, 348/793) is now the single largest pool of uncovered
  engine branches in the repo** — over a third of `game\**` lives in that one class — and MS is
  the ruleset the maintainer's own solutions replay against, so it is the obvious next target.
  `game.MS.MSLevel` (48.6%, 118/243) is the next engine class down. (Both fell a little at jc-19:
  upstream's trap code has fewer branches, and a trap that no longer opens at the start takes the
  tests' Chip and blocks down fewer paths.)
  ⚠ Note that `lxlogic.c` carries **no** `MOD (Jeremy)` comments, unlike `mslogic.c`'s 79 — so it
  is unmodified upstream, and a Lynx divergence would never have been looked for by the (MS-only)
  desync project. A failure in a Lynx test is a finding, not a bug in the test.
- The `io.TWS*` classes were once a flat 0%; `TwsRoundTripTest` and `TwsClickTest` took `io\**` to
  72%, including the **click-conversion path** — the one jc-2 fixed, where 13 of 19 click-bearing
  solutions exported the wrong cell. What is left there is mostly `TWSReader.twsInputStream`
  (28.9%, 13/45), the byte-level reader.
- Still at a flat **0%**: `game.Cheats` (30 branches), `game.Ruleset`, `game.Savestate`,
  `game.Lynx.LynxSavestate`, and in `emulator\**` the `EmulatorKeyListener` (43), `ArgumentParser`
  (26) and `SavestateCompressor` (12). `ArgumentParser` is the one a user can reach without a GUI.
- The suite is strongest exactly where the desyncs were: MS movement, `canEnter`, slide and leave.
  That is not an accident, and it was the right order to do it in.

### The numbers above are enforced — at release time only

A percentage in a document is a claim, and it stops being true the moment somebody adds a test or
adds untested code. `docs/coverage-baseline.tsv` records what this table claims;
`coverage.ps1 -CheckBaseline` fails when the measurement has moved away from it, and
`-UpdateBaseline` rewrites it (the same shape as `verify-splice.ps1 -UpdateFormBaseline`). It also
fails if `CLAUDE.md` no longer mentions the headline figure at all, because agreeing with the
baseline does not prove this table was ever updated.

**If you move the number, the fix is two steps and the error message says so:** run
`coverage.ps1 -UpdateBaseline`, edit the table above to match, and commit both.

`.github/workflows/release.yml` runs the check, so a release cannot ship documentation that no
longer describes the code. **CI does not run it, deliberately** — it asserts nothing about
correctness, it re-runs the whole suite a second time under the agent, and adding a test is
*supposed* to move the number. Gating every push on a stale doc trains people to ignore a red X.

There is likewise **no coverage THRESHOLD gate** anywhere. A minimum percentage on a number this
young gets gamed before it is met; the useful signal today is the `least-covered engine classes`
list the script prints at the end of every run.

### The differential trace — the one oracle that is not a transcription

Everything in `test/` compares SuperCC against a *transcription* of Tile World's rules.
`trace.ps1` + `trace/TraceLevel.java` compare the two **engines**, replaying one real solution and
pinning the first tick where they disagree. It is the instrument that took the desync count from
135 to 0.

```powershell
powershell -File trace.ps1 -Dat <set>.dat -Level <n> -Solution <succsave>.json
powershell -File trace.ps1 -Dat ... -Level ... -Solution ... -TWTrace tw.txt   # and diff
powershell -File trace.ps1 -Compare -SccTrace scc.txt -TWTrace tw.txt -Level <n>
```

It needs a level set and a solution, neither of which may live here, so the *comparison* is **local
and on-demand**. `trace.ps1 -SelfTest` checks the alignment on synthetic traces with no level set at
all, and CI runs that on every push. The Tile World half is in `mslogic.c` behind `-DTRACE_DESYNC`,
selected with `TW_TRACE_LEVEL`; build it with
`cmake -DCMAKE_C_FLAGS=-DTRACE_DESYNC` and run it in BATCH VERIFY mode -- `tworld2.exe -b -r -q
<set>.dac 2> tw.txt`, from a directory with no spaces in its path -- and capture stderr.

⚠ **This harness shipped broken and was fixed on 2026-09-01, when it was verified end to end for
the first time.** It had never been run, because running it needs data that cannot be committed. It
reported a divergence at tick 0 on a level where the two engines agree perfectly. Three defects: the
trace was written as UTF-16 so ordinary tools could not read it; the documented Tile World command
(`-r -p`) opens the GUI instead of replaying; and the aligner compared equal tick numbers when the
two engines do not share a clock -- Tile World emits four ticks per MS move, SuperCC one per move on
a half-move counter, plus a pre-move line with no counterpart. It now aligns by state-change
sequence, and the self-test guards exactly that. Both sides emit the same tab-separated lines:

```
T <lvl> <tick> <rng> chip=<x>,<y>,<slip>  C:<letter>,<x>,<y>,<dir> ...  B:<x>,<y> ...
Q <lvl> <tick> Q:<letter>,<x>,<y>,<dir> ...
```

🔴 **The format is a contract with `mslogic.c` — do not change one side of it.** Positions are
grid `x,y` and directions are `N/W/S/E` because the engines encode both differently; without that,
the diff is blind to a creature in the right place facing the wrong way. The `Q` line exists
because `T` shows only positions, and block-versus-block divergences on random force floors turn
on *which* block the slip pass reaches first.

### The corpus replay — the widest oracle, and the gate for any engine change

The trace compares two engines on ONE solution. `corpus.ps1` + `corpus/CorpusCheck.java` replay
**every** stored solution in a Chip's Challenge folder through one jar and write the final state of
each — outcome, tick, timer, chips, Chip's cell, facing and sliding, keys and boots, RNG, and CRC32s
of both layers, the creature list and the slip list — so two builds can be compared line for line.
That is everything the line names, not every bit of engine state (per-creature timers, the idle
counter and pending clicks are not in it), and it checks where each route ENDS: a divergence that
fully re-converges is invisible. `trace.ps1` is the tool for where one goes.

```powershell
# From the repo root. Outputs go in corpus-results\ (gitignored): they list a personal collection
# and must never land where `git add -A` would commit them.
# BEFORE: the jar you trust, named explicitly (deployed / previous release) -- NOT the repo's
# SuperCC.jar, which build.ps1 overwrites with whatever you just built.
powershell -File corpus.ps1 -CCDir "<CC>" -Jar <trusted.jar> -Out corpus-results\before.txt
# AFTER: build the change; the default jar is the repo's fresh build.
powershell -File corpus.ps1 -CCDir "<CC>" -Out corpus-results\after.txt
powershell -File corpus.ps1 -Compare -Baseline corpus-results\before.txt -Candidate corpus-results\after.txt
powershell -File corpus.ps1 -CCDir "<CC>" -CrossCheck      # after touching Solution.java
powershell -File corpus.ps1 -SelfTest                      # no level set; CI runs it
```

Exit codes: **0** identical / clean run, **1** differences found, **2** an error or a refused
compare, **3** `-CrossCheck` found the harness disagreeing with SuperCC's own replay.

**Run it for any change to `game\**`, `io\**` or `emulator\**` (where `tick`, `loadLevel` and
`Solution` live), before and after, and treat any difference as a behavior change on a real level
until proven otherwise.** The compare classifies each one: STATUS
(it no longer loads or replays at all — marked "(was solved)" when it had reached the exit — or,
already failing, fails differently: "detail changed"), LOST
THE EXIT (it replays, and a solved route no longer solves), GAINED THE EXIT, and FINAL STATE ONLY
(the outcome survived, the end state did not), then totals "solved routes lost in all" across every
category. Differences in the COLLECTION are listed but never counted there: INPUT CHANGED (every
line ends with `src=<save>:<level>`, CRC32s of the solution file and the level record its filename
points at, so a save re-recorded or a pack updated between the two runs is caught as such — decided
before STATUS, since the engine cannot be blamed for it), and a save present on only one side
(CorpusCheck writes a line for every file whatever the jar does, so that file was added or removed).
🔴 **The fingerprint must be a function of the INPUTS ONLY** — the set's bytes and the save's
filename — never of what the jar did. Its first version used the whole-set CRC for BADJSON/OPENFAIL
lines and the record CRC otherwise, so a jar that broke parsing moved the fingerprint and its
regression read as INPUT CHANGED, out of the headline. One function (`locate`) now finds each save's
level once, and every line's inputs and the level replayed come from it, on every path. `pos=N` is
input identity too, so it follows `src=`. The self-test pins all of this. A solved route lost where
the input also changed cannot be attributed and is counted on its own line, with the remedy (re-run
the baseline jar on the current collection). STATUS is decided first on purpose: a solved level that will not open is a load-path bug,
and filing it under LOST THE EXIT sends the triage looking at movement.

🔴 **Why "the tests pass" is not enough, measured.** On 2026-11-01 upstream's trap rework
(`858dbd0`) passed all 1,878 assertions in `test\` while changing 13 of 24,204 real solutions: five
levels would not open at all (four of them solved), six more solved routes stopped reaching the
exit, and two unsolved routes ended differently — ten solved routes lost in all — because no fixture
starts a block or Chip on a wired trap. The corpus caught it and pinned the cause: the `MSLevel`
constructor presses start-occupied traps before its trap index exists. With that one ordering fixed,
the same rework was byte-identical across the whole collection. That is the shape of result to
expect: the test suite sees the arrangements someone built; the corpus sees the ones level designers
built. (The rework was merged on 2026-11-02 with that fix and three more; `MsCloneTrapTest` §6–§7 now
carry the fixtures that were missing, and each fails on the build it guards against. On 2026-11-08
the four fixes were reverted for the upstream baseline (§6), and those fixtures became
`upstreamBug` pins; the corpus against jc-18 shows the same 13 differences again, on 24,212.)

The corpus compares FINAL STATES, so it is blind to a change in what gets *recorded*. Upstream's
input discarding (step 2) is the worked example: identical on all 24,204 solutions, yet 40 Lynx move
lists record differently once loaded (a `-` in place of each ignored key). A change to `SuperCC.tick`, `SavestateManager` or anything else
on the recording path needs that measured separately — FORK.md's step 2 says how it was done (a
scratch census comparing the recorded list with jc-14's, then a save-reload-replay round trip).

Things that are load-bearing in `CorpusCheck` and `corpus.ps1`:

- **`openLevelset` swallows a read failure too** — it reports it and returns with no set loaded — so
  the set is pre-parsed with `DatParser` for the reason and then checked with `lastLevelNumber()`;
  either failure is `OPENFAIL` for every save in that pack. Before this, one unreadable `.dat`
  aborted the whole run with an NPE (reproduced). The self-test pins it with a fixture set that is
  not a level set.
- **A compare of a jar against ITSELF is refused (exit 2).** Every output opens with
  `#jar <sha256> <path>` — the jar the JVM actually loaded, read by `CorpusCheck` from its own class
  source, not what the script believes it passed — and `-Compare` refuses a pair with the same jar
  unless `-SameJar` says that is the point. A before/after pair that quietly replayed one jar twice
  compares IDENTICAL and proves nothing: the one failure this tool must never hide. The check fails
  CLOSED: a run whose jar is unknown (no header, an unhashable one, or one an empty line pushed off
  line 0) is refused the same way. Every run also prints the jar and its hash. **The header names
  the harness version too** (`harness=<sha256>` of `CorpusCheck.java`, CRs removed so both PCs'
  checkouts agree), and a pair written by two versions, or an unknown one, is refused unless
  `-MixedHarness`: a harness change shows on every line it touches (counting the fatal move moved
  23,095) and would all be blamed on the engine. After a harness change, re-run both sides. The JDK
  is recorded as well (`java=`); a mismatch is only a NOTE, since no end state depends on it.
- **Every run ends with `#end <count>`, and `-Compare` refuses one without it** (or with a wrong
  count). A run killed part-way otherwise compares as a short collection: measured, a killed run's
  435 lines gave "solved routes lost in all: 0" and blamed 22,605 unreplayed solved routes on the
  collection. The header also records `pack=` (a one-pack run against a whole one compares that
  pack alone), and the harness hash is of the source COPY that was compiled, not the repo's file.
- **Keep the collection still during a run.** Each set is fingerprinted from one read, but the engine
  re-reads the file at every load; if a set changes mid-pack (Dropbox syncing an edit from the other
  PC), the run stops with no `#end`, so it is refused rather than compared.
- **The jar is replayed from a PRIVATE COPY** in the run's temp folder (`Get-JarCopy`), and the
  hash recorded is the copy's: `build.ps1` can overwrite a jar a live JVM has open, and late-loaded
  classes then read as garbage (section 9). Never point `Get-CorpusArgs` at the caller's jar. The
  header still SHOWS the path the user named (`-Dcorpus.jarpath`): the copy's temp folder is gone
  by the time anyone reads a kept baseline.
- **Paths lose a trailing backslash** (`Resolve-UserPath`): PowerShell 5.1 quotes an argument with a
  space, and the `\` before that closing quote escapes it for java — `-CCDir "...\Chip's
  Challenge\"` swallowed the next argument. The self-test's fixture folders have spaces on purpose.
- **An explicit `-Jar` resolves against the CURRENT directory**, like every other path in it; only
  the default is the repo's own `SuperCC.jar`. That deliberately differs from `trace.ps1` and
  `run-tests.ps1`, which take `-Jar` from the repo root: this is the one script whose `-Jar` is
  routinely a jar OUTSIDE the repo, and "`-Jar SuperCC.jar`" typed in a release folder must not
  silently mean the repo's fresh build.
- **A save belongs to the level carrying its number, which is not always the level at that
  position.** SuperCC names a solution file after `level.getLevelNumber()` — the number stored in
  the level's record — but loads levels by position. They agree in every set measured so far (a
  `-CrossCheck` run of all 24,204 saves skipped none for this), but the format allows any number,
  and several levels may carry the same one; replaying by position there would test the moves on
  the wrong level and print an ordinary result under BOTH jars. So each set is indexed once by
  record number, a save goes to the level carrying its number (`pos=N` when that is not its
  position), and where several do, the title SuperCC wrote into the filename picks one — or the
  save is `AMBIGUOUS`, never a guess. The title is ONLY a tie-break: saves are copied between packs
  under their source's name, so requiring it everywhere would flag correct saves.
  `loadLevel` also WRAPS (0 is the last level, one past the end is level 1), so out-of-range
  positions are never passed to it.
- **That index is read from the file's BYTES, never through the engine under test.** Which level a
  save belongs to is a fact about the file and must come out the same under both jars. Built with
  `DatParser.parseLevel` it ran the candidate's level constructor, in the set's ruleset, so a jar
  whose `MSLevel` constructor throws (858dbd0) erased those levels' numbers and their Lynx saves
  read `LOADFAIL` unloaded — reproduced, then fixed. The byte reader agrees with SuperCC's parser
  on the number and exact title of all 23,476 levels in the collection; keep it that way if
  `DatParser`'s format handling ever changes.
- **Only the FIRST line is the `#jar` header.** Every other line is data whatever it starts with —
  a pack folder may be named `#anything`, and skipping every `#` line would drop that pack from both
  sides of a compare. An empty line is refused, not skipped: skipping one would push the header
  into the data.
- **An exception is recorded without a source line number** — class, message and SuperCC method
  only — **and without a helpful NPE's local slot** (`<local4>` becomes `<local>`; the spliced
  classes are compiled without `-g`). Both move when anything above them is edited, so a release
  that touches `DatParser` or `Solution` would otherwise make every unchanged failure "differ". For
  the same reason the CC folder's path becomes `<CC>` (it is a different user folder on each PC) and
  an identity hash `@<hash>`. Catch narrowly around state probes: the slip-list probe catches only
  the `UnsupportedOperationException` Lynx throws, so an MS crash there is `STATEEXC`, not `slip=-`.
- **In a set cut short, a single readable candidate is not trusted on its own** — a record whose
  number cannot be read might carry the same one — so the filename title must confirm it, or the
  save is `AMBIGUOUS`.
- **A truncated output is an error, not a run**: `PrintStream` swallows `IOException`s, so the
  harness asks `checkError()` and exits 2. A missing JDK is exit 2 too, never 1 ("differences").
- **`loadLevel` swallows its own exceptions**, so failure is detected by IDENTITY: the level object
  unchanged means the previous level is still loaded; the savestate manager unchanged means the
  level parsed but `SavestateManager`'s constructor threw (a level with no title did this — MO3
  #12 and #14 — until jc-17 named such a level `""`, as Tile World does). Either is `LOADFAIL`.
  Reporting a stale level as a result is the failure to avoid. From jc-17 a set opens at its first
  level that loads, and a set where NONE loads leaves no set open on a fresh emulator, so its saves
  read `OPENFAIL` (an NPE from `lastLevelNumber()`) instead; no set in the collection does either.
- **The set may be `<pack>.dat` or `<pack>.ccl`** — SuperCC names the solution folder after the set
  with any extension removed. Both present is reported, not guessed.
- **The replay loop mirrors `Solution.tickBasicMoves`** exactly, like `TraceLevel`'s, because that
  method runs a whole solution in one call and cannot be observed. `-CrossCheck` replays every
  solution a second time through the real `Solution.load` and fails (exit 3, `MIRRORDIFF` lines) on
  any final state that differs — including a replay that THROWS, which must throw alike on both
  paths — and accounts for every replayed save as checked or skipped. So "kept in step" is measured
  rather than promised; run it after anything touches `Solution.java`. Exposing a hook in `Solution`
  instead would change shipped bytecode and need a release, which a tooling change should not.
  ⚠ **Known SuperCC defect it works around:** `Solution.load` reloads by `getLevelNumber()` used as
  a POSITION, so for a level whose number is not its position SuperCC itself replays the solution
  on the wrong level (GUI included). Those saves are skipped and reported in a `NOTE` line; none
  exist in the collection today. Fixing it is an engine-side change for a release, not this tool.
  The trace harness's `TraceLevel` holds a second copy of the same loop that `-CrossCheck` does not
  cover — a known follow-up (a shared helper for both harnesses).
- **The data goes straight to a UTF-8 file from Java**, never through PowerShell's `>` (UTF-16 in
  5.1 — the trace harness's original defect).
- **Lynx has no background layer** and throws for it; the line carries `bg=-`.
- It needs data that may not be committed, so the real run is local only. A full collection of
  ~24,000 solutions takes 3–4 minutes per jar on the laptop (5 with `-CrossCheck`). Each JVM may
  take 2 GB: on the 10 GB laptop run jars ONE AT A TIME — four at once were killed for low memory.

**`MonsterListTest` is written against the SPEC, not against this code, and that distinction is the
whole point.** Creature order drives MS behavior, so two engines that agree on every tile but
disagree on list order will desync — which is why this fork exists. An assertion written by
observing what SuperCC currently prints would freeze today's behavior and report green forever.
Every expectation there is derived from Tile World's `readpos()`/`mslogic.c` rules instead. Hold any
new emulator test to the same standard: **cite the reference behavior, never the observed output.**
Proven to bite — restoring the pre-jc-7 aliasing, or dropping Lynx's Chip-to-slot-0 swap, each fail
it immediately.

A class counts as a test if it declares a `main()`, so `Harness` and `DatBuilder` are helpers
without needing a naming convention. Two checks skip when their optional local files are absent:
`-Mo3` (the Lynx-signature set behind jc-9) and `-Collection` (a folder of real level sets, for the
wide open-every-level check that synthesized fixtures cannot replace).

Rules that are not negotiable here:

- **Test through the jar.** Never add a test that compiles a source file and asserts on it — that
  tests code which may not be what ships. `run-tests.ps1` puts the jar on the classpath for you.
- **No level set may ever be committed.** `.dat` and `.ccl` files are third-party content. Engine
  tests synthesize their own fixtures with `DatBuilder`. Anything needing a real set must **skip**,
  not fail, when the file is absent — see the `-Mo3` switch for the established pattern.
- **A jar-wide class scan is a real test.** Reflection proves a *method* exists or is gone; it
  proves nothing about *callers*, which live in classes the splice may or may not have recompiled.
  Both directions fail silently — a stale caller of a removed method throws `NoSuchMethodError` on
  a menu click, and a stale class that never got a *new* call simply does nothing at all, with no
  error. `SettingsTest` section 10 scans every class in the jar for exactly this. Keep that habit.
- **Machine-readable output exists — use it.** `-ResultsPath <dir>` writes `<Class>.xml` (JUnit XML,
  which CI turns into annotations) and `<Class>.json` (easier to diff between runs) per test class.
  The exit code is 0 only if every assertion passed.
- **Assert through `Harness`**, not with your own counters, or your results never reach the report.
  `Harness.run(name, Body)` also catches a thrown exception, records it as a failure, and still
  writes the report — the run that dies halfway is exactly when you most want to know how far it
  got.

Coverage today is honest about its gaps: the settings contract is thorough, engine coverage is a
foundation rather than a full suite, and the Swing UI is untested. Widening engine coverage is the
highest-value test work available.

---

## 5. `succ_settings.ini` — a hand-rolled parser with four sharp edges

Every one of these has drawn blood. `SuccPaths.parseSettings()` is not an INI library.

1. **The space before `=` is load-bearing.** The key is read as `substring(0, indexOf('=') - 1)`,
   so `ShowBuildTag=false` parses as the key `ShowBuildTa` and is silently ignored. Always write
   `Key = value`.
2. **`;` only starts a comment at the beginning of a line.** `ShowBuildTag = false ; hides it`
   has the *value* `false ; hides it`, which is not `false`.
3. **Sections are scoped.** Keys are stored `<section>:<name>`. A key under the wrong `[Section]`
   is a different key that nothing reads. Duplicates: the last one wins.
4. **The whole file is rewritten from a fixed template on any settings change**, so a key the
   template does not know about is erased the next time anything changes. Add a new setting to the
   template, not just to a getter.

Also: the file is written **atomically** (render → unique sibling temp → `ATOMIC_MOVE`, retried).
The staging temp **must stay a sibling** of the target — same directory means same volume means the
atomic path is available; the fallback is copy-then-delete, which truncates the destination and
reintroduces the torn-file bug this replaced. Never "tidy" it into `%TEMP%`.

Opt-in switches (`ShowBuildTag`, `ShowLevelPack`, `AlwaysOpenInMS`) all go through the single
`optedIn()` predicate — ON only for `true`/`1`, so a typo cannot switch one on. **A switch whose
default is OFF must use it.**

`ShowLevelName` (jc-14) is the one exception and it proves the rule: its default is ON, so the same
reasoning inverts and it goes through the mirror `shownUnlessOptedOut()` — OFF only for
`false`/`0`. **Match the predicate to the default, and never share one between switches whose
defaults differ.** Be accurate about why: an absent key is filled by `seedDefaults()` before either
predicate sees it, so the two differ *only* on an unrecognized value.

### Adding a setting: three places, and missing one fails quietly

This is the most likely change anyone is asked to make here, so the procedure is worth spelling out.
All three are in `java/io/SuccPaths.java`:

1. **`buildDefaults()`** — add the key to the `DEFAULTS` map, in file order. Skip this and a boolean
   still works (because `render()` calls `optedIn()` regardless), but a String or int key renders
   **empty**, via `DEFAULTS.getOrDefault(key, "")`. No test currently catches that.
2. **`render()`** — add the line to the template. ⚠ `render()` has **two different mechanisms**, and
   picking the wrong one is silent: the `[Graphics]` block loops over a `String[]` of key names and
   appends the **raw map value**, while `ShowBuildTag` is a separate hand-written line routed through
   `showBuildTagOf()`. A new switch dropped into the loop array **bypasses `optedIn()`**, so the file
   would echo back `ShowFrameRate = yes` while the getter reads OFF — breaking the invariant that the
   file and the getter can never disagree. Copy `ShowBuildTag`'s hand-written form, not the loop.
3. **The getter** — route it through `optedIn()`.

Then: add it to `README.txt` section 6 (a shipped setting nobody can find out about is not shipped;
nothing enforces this mechanically), and add a test — `SettingsTest` section 11 is the pattern,
covering on, off, the wrong section, and the no-space typo.

---

## 6. Do not "fix" these — they are deliberate

Each of these looks like a bug or an oversight and is not. The ADRs carry the full reasoning.

- **The committed `.class` baseline.** It is not build output that escaped `.gitignore`. See §1.
- **Upstream's code is kept exactly — bugs included — on purpose (2026-11-08 on).** Jeremy is
  returning the fork to upstream one deviation at a time before layering fixes back on top (FORK.md,
  *The upstream baseline*, which lists every step). Reverted so far: the trap code (`79b0032`),
  `SuperCC.tick`'s recorder (`923d07c`: upstream writes a wait on ANY discarded tick, even one that
  also ran automatic ticks; no real level returns both bits, and `InputDiscardTest` §5 pins it
  through a wrapped level) and Seed Search (`397c7bb`). **Every problem a revert brings back goes
  into `BASELINE-ISSUES.md` in the same push** (Jeremy's standing request, 2026-11-08) — and a fix
  closes its entry there rather than deleting it. ⚠ That makes "no tick returns both bits" load-bearing with nothing
  guarding it: `LynxLevel.tick` CAN return `MASK_TICK_MULTI | discard`, and only the reasoning in
  FORK.md step 2 keeps it from happening. **Any change to `LynxLevel.tick`, `selectChipMove` or
  `moveChip` must be checked for both bits** — if a real tick ever returns both, upstream's
  recorder saves that route ticks short. For the traps: `MSLevel`'s constructor presses a trap that starts occupied before its
  trap index exists, so 5 levels in his collection do not open, 6 solved routes no longer solve and
  2 unsolved routes end differently; the corpus shows exactly those 13 differences against jc-18, and that is the expected result, not
  a regression. Do not fix it in passing: the fixes come back as their own step, by `git revert`
  of the revert commit, and then this bullet, FORK.md's table, the coverage baseline (§4:
  `-UpdateBaseline`, the table, the pin count) are updated to match — FORK.md lists the steps. The tests that pin the bug use `Harness.upstreamBug` — each passes only on
  upstream's own measured answer, fails "the correct answer came back" on the correct one, and
  fails as a regression on anything else. "The correct answer came back" is the cue to turn the
  pin back into a `check()` (if a fix was re-applied) or to find what moved (if not, e.g. hash
  order in `MsCloneTrapTest` §7) — never to restore the bug.
- **Every menu button does nothing until a level is loaded — on purpose.** `MenuBar.requireALevel()`
  wraps their action listeners at the end of the constructor (jc-17; 46 of 56 threw on jc-16 with no
  level open). A new menu item is covered automatically. If it genuinely works with no level set
  open, mark it `worksWithoutALevel(...)` where you create it; do not add a per-item
  `isLevelLoaded()` check. `GuiWindowTest` clicks every button in the bar with no level open.
- **`formclass/FormTransplant.java` imports `jdk.internal.org.objectweb.asm`.** That is the JDK's own
  internal ASM, chosen so this repo takes on no dependency (ADR 0002); the tool is pinned to JDK
  16.0.2, where it exists. Do not "modernize" it to a downloaded ASM or to a newer JDK's class-file
  API without re-running the fifteen-class reproduction in ADR 0010.
- **`formclass.ps1 -DownloadCompiler` fetches JetBrains' form compiler instead of the repo
  committing it, and CI downloads it fresh every run instead of caching it.** It is a tool, not a
  dependency of the program (ADR 0011), pinned by SHA-256 and copied into each run's own work
  folder, where the copies are hashed and used (so nothing can swap a jar between check and use); caching it
  would mean pinning another third-party action into the workflow that produces the published bytes.
  And `-Check -RequireCompiler` in CI is deliberately strict: without the compiler it FAILS rather
  than falling back, because the fallback cannot see a `.form` edit. Do not drop the flag to make a
  network blip go green; re-run the job.
- **`formclass.ps1 -Compile` still runs the ADR 0010 transplant, and refuses what it refuses.** The
  real compiler reproduces IntelliJ's own misplacements of the setup call (a second setup in a
  `this(...)` constructor, a bound field read first, a try or loop that can skip it), and the
  transplant's refusals are the only thing that sees them. Removing the "redundant" second tool
  brings those back silently.
- **`build.ps1` builds zip entries by hand instead of using `Compress-Archive`.** On PowerShell 5.1
  both `Compress-Archive` and `ZipFile::CreateFromDirectory` emit **backslash** entry names —
  measured here, not assumed. Explorer tolerates it; Info-ZIP `unzip` and Java's `ZipInputStream`
  do not, and produce files with literal backslashes in their names. This is a public download, so
  it has to open correctly off Windows.
- **`ShowBuildTag` defaults OFF.** Downloaders should not see a private build number. Jeremy turns
  it on locally. Absent key, absent file, typo, and garbage all mean OFF.
- **The TWS folder remembers the last folder used.** jc-8 pinned it to a fixed `tws`; jc-10
  deliberately **reversed that** at Jeremy's explicit request after he lived with it. Do not
  restore the jc-8 behavior. The jc-8 null guards survive and must stay.
- **Stored paths are relative to the Chip's Challenge folder**, not absolute. The settings file is
  Dropbox-synced between two machines whose user directories differ (`C:\Users\Jeremy` and
  `C:\Users\jerem`); absolute paths made the last machine used win. `Path.startsWith` compares name
  elements, not characters — never "simplify" it to a string prefix test.
- **`getSuccPath()` is deliberately not converted to relative.** It is the one data-bearing path
  and its callers resolve it against the working directory.
- **`AlwaysOpenInMS` is scoped to opening only.** F3 still switches ruleset, and loading a solution
  still follows the *solution's* ruleset — forcing MS there would break every Lynx replay.
- **`startingRuleset()` lives in `SuperCC`, not `DatParser`.** A file reader must not grow a
  dependency on the settings file.
- **The `paths == null` guards are not decoration.** The headless `SuperCC(boolean)` constructor
  never builds a `SuccPaths`, and both `openLevelset()` and the window title are reachable from it.
  Tile World's equivalent re-title crashed every headless batch run for exactly this reason.

Two language-level traps:

- **No `\u` sequences in Java comments.** The lexer decodes unicode escapes *inside comments*, so a
  Windows path in a comment is a compile error. This has cost a build here before.
- **`-encoding UTF-8` is required** — `SuperCC.java` contains Unicode arrow characters.

---

## 7. Conventions

- **Design:** SOLID and GoF patterns where they genuinely make the code cleaner, never for
  vocabulary's sake. Small functions, one composable guard helper over a special case, delete a
  special case rather than handle it. A pattern that is not paying for itself is a defect.
- **Comments:** this codebase comments *why*, not *what*, and fork-specific edits are marked
  `/* MOD (Jeremy, jc-N): ... */` with the reasoning and the trap that motivated them. Match that.
  A fork fix to code brought in from upstream is marked `MOD (Jeremy, upstream merge)` instead, so
  every departure from an upstream commit can be found with one search; FORK.md's merge section
  says which release each step shipped in.
  When you change behavior that a comment explains, update the comment in the same edit.
- **American English** in code, comments, identifiers, strings, and documentation: `color`,
  `behavior`, `gray`, `analyze`, `center`, `-ize`.
- **Line endings:** `.gitattributes` normalizes text to LF, except `README.txt`, which is CRLF
  because it is read in Notepad. `build.ps1` re-normalizes it at package time anyway, because
  `eol=crlf` only applies at checkout.
- **Never edit `succ_settings.ini` while SuperCC is running.** It holds the file in memory and
  rewrites it on any settings change, silently reverting your edit.

---

## 8. Shipping a release

Assume any behavior change ships publicly. The full checklist is `.github/RELEASING.md`; in short:

1. Bump `BUILD_TAG` in `java/emulator/SuperCC.java`. **Two jars must never report the same tag.**
2. Add the entry to `README.txt` section 7 *and* update its header line to name the new build —
   `build.ps1 -Package` **fails** if the header does not name the tag being packaged. Add any new
   or changed setting to README section 6; a shipped setting nobody can find out about is not
   shipped.
3. Update `CHANGELOG.md` and `FORK.md`.
4. `powershell -File verify-splice.ps1`, `powershell -File run-tests.ps1`, and
   `powershell -File coverage.ps1 -CheckBaseline` — all three green, **on JDK 16.0.2** (`JAVA_HOME`). The release workflow runs the
   coverage check too, so skipping it here just moves the failure to after the tag is public.
   The splice check is not optional here: it is what proves the change you are shipping is the
   change that will actually be in the jar. If the release touches a form class, add
   `powershell -File formclass.ps1 -Check -RequireCompiler` — the release workflow runs it as well.
5. `powershell -File build.ps1 -Package -ExpectTag jc-N`, then actually launch the jar **from the
   built zip**, not the one in the repo root.
6. Commit, push `main`, create and push the tag `jc-N`.
7. The tag push runs the release workflow, which re-verifies, re-packages, and creates a **draft**
   release with the zip attached. **It does not publish.** Download that asset, launch it once, and
   publish by hand — the playtest gate is a human act and CI cannot perform it.

⚠ `BUILD_TAG`, `TITLE` and `SETTINGS_FILE_NAME` are `static final String`s, so javac **inlines them
at every use site**. A class that is not recompiled keeps the old value baked in permanently. Today
only `SuperCC.java`, `SuccPaths.java` and `ErrorLog.java` (its log-session header) reference them,
and all three are spliced, but if a form-based
class ever reads one, it would report a stale build tag forever — which is the "two jars must never
report the same tag" invariant broken from the inside. (`formclass.ps1 -Check` would now catch it —
it compiles against the new jar, so the inlined tag would differ from the committed class — but
keeping these constants out of form classes is still the rule.)

The release zip is exactly four files: `SuperCC.jar`, a **generated** stock `succ_settings.ini`
(produced by calling `SuccPaths.createSettingsFile()` in the jar just built — never hand-written,
so the shipped defaults cannot drift from the program's), `README.txt`, and `COPYING`.

---

## 9. Working alongside other agents

- **Announce your file set.** Editing `SuccPaths.java`, `build.ps1`, or `README.txt` conflicts with
  almost everything; those are the contended files.
- **One agent owns `BUILD_TAG` per release.** It is a single line that every other change depends
  on, and a duplicate tag is unrecoverable once published.
- **`build.ps1` writes `.\SuperCC.jar`, and `-Package` wipes all of `dist/`.** Those are shared
  mutable paths, and two agents in one checkout will produce confusing, non-reproducible failures.
  Either use `run-tests.ps1 -Isolated` (builds to a private temp jar and deletes it afterward), or
  give each agent its own checkout:
  ```powershell
  git status --short          # 🔴 COMMIT OR STASH FIRST -- see below
  git worktree add ..\SuperCC-agent-a -b feature/agent-a
  ```
  🔴 **`git worktree add` branches from HEAD, not from your working tree.** Uncommitted work does
  not come with it, so an agent handed a worktree while this scaffolding was uncommitted would get
  a checkout with no `CLAUDE.md`, no `build-config.ps1`, no `verify-splice.ps1`, and an older
  `build.ps1` carrying its own inline splice list — two agents running two different build systems
  with two divergent lists, which is precisely the drift `build-config.ps1` exists to prevent.
  Commit or stash before creating one.

  A worktree is the cleaner answer when an agent needs to build, commit, or package.
- **`-Isolated` covers `run-tests.ps1` only.** `verify-splice.ps1` and a bare `build.ps1` still
  write shared paths, and the mandatory splice check is the common case, not the exotic one. A
  worktree is the real fix for concurrent agents.
- **`-ResultsPath` is not isolated either.** Two agents in one checkout both write
  `test-results\EngineTest.xml` and the last writer wins. Give each a distinct directory.
- **Never leave a stray `java.exe` running.** A live JVM holds the jar open, and the next build's
  delete fails with a lock error that reads like a permissions problem.

  🔴 **And the failure is not always a lock error — that is the dangerous half.** Windows lets you
  *overwrite* the jar a running JVM has open. The JVM keeps the central directory it cached at
  startup, so `getResource()` still hands back a URL, but the entry OFFSET behind it now points into
  a different file. Anything the program had not yet loaded comes back as garbage. Measured
  2026-09-03 by replacing jc-13's jar under a live jc-13 instance and re-reading one icon:

  ```
  BEFORE swap: url=ok   ImageIO.read -> image ok
  AFTER  swap: url=ok   ImageIO.read -> NULL
  ```

  In the real instance that surfaced as 18 of these in `succ_error-<MACHINE>.log`, one per mouse
  movement, hours after the swap:

  ```
  java.lang.NullPointerException: Cannot invoke "java.awt.Image.getProperty(...)" because "image" is null
      at javax.swing.ImageIcon.<init>(ImageIcon.java:240)
      at graphics.Gui.changePlayButton(Gui.java:121)
      at graphics.Gui.repaint(Gui.java:137)
  ```

  **If you see that trace, the jar was replaced under a running instance — the jar itself is fine.**
  Check for a live `java`/`javaw` on the jar BEFORE deploying, and never deploy over a running one.

  It is loud here because `changePlayButton` re-reads and re-decodes `play.gif`/`pause.gif` from the
  jar on **every** `repaint()`, and `GamePanel.mouseMoved` repaints — so it is two zip reads and two
  image decodes per mouse motion event. Caching the two icons in fields would remove the per-repaint
  I/O and make the app immune to this after startup. `Gui.java` is form-based (§1), but this is a
  `formclass.ps1 -Compile graphics\Gui.java` ships it without IntelliJ (ADR 0011). Worth doing
  next time someone is in there.
- Do not commit `SuperCC.jar` or `dist/` — both are ignored, deliberately.

---

## 10. When you are stuck

`FORK.md` is the engineering log: every fork change, why it exists, what broke first, and what was
measured. `docs/adr/` holds the decisions. `README.txt` section 7 is the plain-English history.
If a behavior looks wrong, check those three before changing it — in this repo, the surprising
choice is usually the deliberate one.
