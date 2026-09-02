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
> behavior. Before adding it, confirm the file has no sibling `.form` — if it does, you cannot
> modify it from here at all without IntelliJ.

**You do not have to take this on trust — run the check:**

```powershell
powershell -ExecutionPolicy Bypass -File verify-splice.ps1
```

It covers the two ways an edit can fail to ship, by two different mechanisms:

- **Unspliced ordinary files** (70 of them) are recompiled and compared against the bytecode the
  jar would actually contain. An edit the build ignores fails and names the file.
- **Form-based files** — the 15 `.form` files *and their sibling `.java`* — cannot be recompiled
  here at all, so they are hashed against `docs/form-baseline.sha256`. `graphics/Gui.java` is
  ordinary hand-written Java and is the likeliest of these to be edited; an edit there is flagged,
  and the only correct response is to rebuild it in IntelliJ, commit the new `.class`, and re-run
  with `-UpdateFormBaseline`.

Run it after touching anything under `java/**`, before calling the change done. CI runs it too.
Its header lists the narrow cases it still cannot see (annotations, a literal swapped between two
concatenations, line-number-only edits) — read that before trusting a green run absolutely.

**Corollary that shapes every test in this repo: the built jar is the only authoritative artifact.**
The source tree can disagree with what ships. Tests run against `SuperCC.jar`, never against
freshly compiled sources. See §4.

---

## 2. Commands

Everything is PowerShell. There is no Maven, no Gradle, no `package.json`, no dependency manifest.
Requires a **JDK 16 or newer** (`--release 16` keeps bytecode compatible with the baseline).

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
powershell -ExecutionPolicy Bypass -File verify-splice.ps1 -UpdateFormBaseline     # after an IntelliJ rebuild
```

Extra switches worth knowing:

- `build.ps1 -ExpectTag jc-N` fails unless `BUILD_TAG` matches (the release workflow passes the
  pushed git tag), and `-Manifest <path>` writes a JSON record of the build — tag, jar SHA-256,
  size, entry count, and the exact compiler used.
- `verify-splice.ps1 -UpdateFormBaseline` regenerates `docs/form-baseline.sha256`. It is the *only*
  correct response to a flagged form file, and only after the class has genuinely been rebuilt in
  IntelliJ and committed — running it to silence a warning defeats the check entirely.
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
CHANGELOG.md           Keep a Changelog view of the same history
COPYING                GPLv2 — ships with the binary, as the license requires
build-config.ps1       THE splice list, the JDK finder, the robocopy wrapper — one source of truth
build.ps1              the splice build + the release packager (-Package)
verify-splice.ps1      proves an edit will actually ship (§1)
run-tests.ps1          builds, then compiles and runs everything in test\
coverage.ps1           JaCoCo branch coverage, scoped to game\** + io\** (§4)
docs/coverage-baseline.tsv  what the §4 table claims; the release gate checks it
trace.ps1              differential trace against Tile World, local only (§4)
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
test/MonsterListTest.java  creature-list ORDER under both rulesets — the desync surface
test/ConnectionTest.java   trap/clone wiring (fields 4 and 5) — the stride arithmetic
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
```

### Coverage — what the suite actually reaches

`coverage.ps1` runs the suite under JaCoCo and reports **branch** coverage. Branch, not line: an
emulator is mostly conditionals, and a line count flatters a `switch` whose arms are never all
taken. JaCoCo is a test-time tool cached under `%LOCALAPPDATA%\jacoco\<version>\` — it is **not a
dependency**, never committed, never shipped. Output goes to `coverage-report\` (gitignored);
`coverage.csv` is the evidence behind every percentage below.

As of jc-13, with all 1,715 assertions passing, **measured on JDK 16.0.2** (what CI pins):

| scope | branch | line | branches |
|---|---|---|---|
| **`game\**` + `io\**` — THE TARGET** | **42.2%** | 58.1% | 1121/2658 |
| &nbsp;&nbsp;`game\**` (the emulator) | 36.8% | 51.4% | 827/2249 |
| &nbsp;&nbsp;&nbsp;&nbsp;`game\MS\**` | 25.6% | 38.2% | 300/1174 |
| &nbsp;&nbsp;&nbsp;&nbsp;`game\Lynx\**` | 51.5% | 55.2% | 377/732 |
| &nbsp;&nbsp;&nbsp;&nbsp;`game\*` + `button\**` (shared) | 43.7% | 68.3% | 150/343 |
| &nbsp;&nbsp;`io\**` (file formats) | 71.9% | 77.9% | 294/409 |
| `emulator\**` | 24.5% | 24.6% | 93/380 |
| `tools\**` + `graphics\**` — *not a target* | 0.0% | 0.0% | 0/1887 |

**Read this correctly.** Whole-project branch coverage is 20.8% (1223/5891), and that number is
meaningless: 15 form-based classes cannot be compiled from this repo at all (ADR 0001), `tools\**`
is 35 files of upstream code this fork does not modify, `graphics\**` is Swing with no headless test
story, and the rest is vendored `org.json` / `com.intellij`. What this fork can break is the engine
and the file formats, so that is what is scoped and measured.

**The number is mildly JDK-dependent, by construction.** 299 of the 2654 target branches live in
the four spliced `io\**` classes (`ErrorLog`, `LevelFactory`, `SuccPaths`, `TWSWriter`), which your
`javac` compiles; `build-config.ps1` already notes that javac 17 does not emit identical bytecode to
javac 16. The other 2355 branches — including all 2249 of `game\**` — come from the committed
baseline and are byte-identical on every machine. So an `io\**` figure that differs slightly on
another JDK is not a bug.

**42.2% is a floor to build on, not a passing grade**, and the split says where the work is:

- **`game\MS\**` at 17.0% is now the least-covered ruleset by a long way** — Lynx is at 51.5%,
  three times it. Four Lynx files did that: the move-preference table (`LynxCreatureMoveTest`),
  the entry rules (`LynxCanEnterTest`), the creature list (`LynxCreatureListTest`) and the movement
  engine (`LynxTickTest`), all against `lxlogic.c`. The MS suite is dense exactly where the desyncs
  were and thin everywhere else. **`MSLevel` (12.4%) and `MSCreatureList` (10.7%) are now the
  weakest engine files in the repo**, and MS is the ruleset the maintainer's own solutions replay
  against — so it is the obvious next target.
  ⚠ Note that `lxlogic.c` carries **no** `MOD (Jeremy)` comments, unlike `mslogic.c`'s 79 — so it
  is unmodified upstream, and a Lynx divergence would never have been looked for by the (MS-only)
  desync project. A failure in a Lynx test is a finding, not a bug in the test.
- The `io.TWS*` classes were the other 0%, and `TwsRoundTripTest` took them to 65.2% for `io\**`
  overall. What is still uncovered there is the **click-conversion path** — the one jc-2 fixed,
  where 13 of 19 click-bearing solutions exported the wrong cell. It needs an MS level plus a real
  `SavestateManager`, whose constructor is package-private in `emulator`, so the round-trip test
  uses Lynx (which has no clicks) and says so.
- `game.Cheats`, `game.CreatureList`, `game.SavestateReader` and both `io.TWS*` stream classes sit
  at a flat **0%**. TWS read/write is the format behind every exported solution, which makes it the
  highest-value uncovered code in the repo.
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
test/ErrorLogTest.java     the jc-11 error log
```

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

Opt-in switches (`ShowBuildTag`, `AlwaysOpenInMS`) all go through the single `optedIn()` predicate.
Put any new switch through it too. **Every switch defaults OFF for downloaders** — see §6.

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
   `powershell -File coverage.ps1 -CheckBaseline` — all three green. The release workflow runs the
   coverage check too, so skipping it here just moves the failure to after the tag is public.
   The splice check is not optional here: it is what proves the change you are shipping is the
   change that will actually be in the jar.
5. `powershell -File build.ps1 -Package -ExpectTag jc-N`, then actually launch the jar **from the
   built zip**, not the one in the repo root.
6. Commit, push `main`, create and push the tag `jc-N`.
7. The tag push runs the release workflow, which re-verifies, re-packages, and creates a **draft**
   release with the zip attached. **It does not publish.** Download that asset, launch it once, and
   publish by hand — the playtest gate is a human act and CI cannot perform it.

⚠ `BUILD_TAG`, `TITLE` and `SETTINGS_FILE_NAME` are `static final String`s, so javac **inlines them
at every use site**. A class that is not recompiled keeps the old value baked in permanently. Today
only `SuperCC.java` and `SuccPaths.java` reference them and both are spliced, but if a form-based
class ever reads one, it would report a stale build tag forever — which is the "two jars must never
report the same tag" invariant broken from the inside.

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
- Do not commit `SuperCC.jar` or `dist/` — both are ignored, deliberately.

---

## 10. When you are stuck

`FORK.md` is the engineering log: every fork change, why it exists, what broke first, and what was
measured. `docs/adr/` holds the decisions. `README.txt` section 7 is the plain-English history.
If a behavior looks wrong, check those three before changing it — in this repo, the surprising
choice is usually the deliberate one.
