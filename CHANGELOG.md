# Changelog

Every release of this fork, newest first, in the spirit of [Keep a Changelog](https://keepachangelog.com/).

Versions are build tags, `jc-N`, not semantic versions — the fork tracks upstream SuperCC rather
than versioning independently. Dates are the tagged commit's date.

**Two jars must never report the same build tag.** jc-6 exists only because a one-line fix landed
after jc-5 was already deployed and reusing the number was not an option.

This file is a summary. `README.txt` section 7 is the user-facing history that ships in the download,
and `FORK.md` is the engineering record: why each change exists, what broke first, and what was
measured. All three are updated together — see [`.github/RELEASING.md`](.github/RELEASING.md).

## Unreleased (upstream merge, step 1)

### Changed
- **Upstream's trap rework (`858dbd0`) is merged — with four fixes of the fork's own, and no
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
  **For the next release's README:** a `.ser` saved with *Save all states to disk* by jc-14 or
  earlier loads correctly wherever each trap has one wire; on a trap with two or more wires, its
  state is read from its first wire's bit alone, so it can restore shut when it was open (392 of
  23,476 levels in the maintainer's collection have such a trap). A `.ser` saved now loads correctly
  in older builds. Lynx states and solution files are unaffected. Coverage rose (target 50.3% →
  51.2% on the laptop's JDK 18, before §10 was added) and must be re-baselined on JDK 16.0.2 before
  the release: the newly spliced engine classes are now JDK-dependent too.

### Added
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

## Unreleased (tooling)

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
