# 0011 — Compile form classes with JetBrains' own form compiler, and check them byte for byte

**Status:** Accepted (2026-11-07) · **Amends:** 0002, 0010 · **Applies to:** `formclass.ps1`, `formclass/FormCompile.java`, `.github/workflows/ci.yml`, `.github/workflows/release.yml`

## Context

ADR 0010 made a `.java`-only edit to a form class possible without IntelliJ, by transplanting the
generated members from the committed class. It said plainly what it could not do: **a `.form` edit
still needed IntelliJ**, because `$$$setupUI$$$` is generated from the `.form` and the transplant can
only copy it. Upstream's auto-diagonals change (`5f928e0`) edits `tools/ControlGUI.form` — it adds a
checkbox row to the Controls window — so it hit exactly that wall.

The maintainer was offered four ways through it (2026-11-07): install IntelliJ, port IntelliJ's code
generator by hand, add the checkbox in plain Java and leave the `.form` stale, or download
**JetBrains' own form compiler**, hash-pinned, as a test-time tool. He chose the last.

JetBrains publishes the compiler of every IDE build in its own repository
(`cache-redirector.jetbrains.com/intellij-repository/releases`), as
`com.jetbrains.intellij.java:java-gui-forms-compiler`. The committed classes came from the
211.7628.21 line (ADR 0010 read `FormConstructorVisitor` at that tag).

## Decision

**`formclass.ps1 -Compile <file>`** compiles a form class the way IntelliJ's build does: javac 16.0.2
(`-g --release 16`) on the `.java`, then JetBrains' compiler 211.7628.21 reading the `.form`, driven by
`formclass/FormCompile.java`, which repeats the IDE's own calls (`FormsInstrumenter.java` lines
143-205 at `idea/211.7628.21`, in the ui-designer JPS plugin). Three details there decide the output
bytes and each is kept: the class writer is built *from* the reader, so ASM keeps javac's constant
pool; frames are recomputed for the whole class (`COMPUTE_FRAMES`), so the finder must see the JDK's
classes (its jrt image, first) and the program's; and the generator runs with the IDE's defaults
(`ignoreCustomCreation = false`, `useDynamicBundles = false`).

**Measured, before anything else was built on it:** from their unchanged sources, **all fifteen
committed IntelliJ-built form classes and their 22 inner classes come out byte-for-byte identical**
(against the class tree at `331a9bd`, where all fifteen were still IntelliJ's own). The transplant
had matched 4 of 15 byte for byte; the other 11 differed in frames or pool numbering. The three
classes the transplant had since rebuilt (SeedSearch in jc-16, Gui and ChooseTileSize in jc-17)
matched the compiler in code and strings but not in frames, and were recompiled with it: all fifteen
committed classes are now exactly what the compiler makes.

**`formclass.ps1 -Check` adds a byte comparison:** javac + the compiler, from the committed `.java`
**and `.form`**, must reproduce every committed class exactly. This closes the hole ADR 0010
documented — the transplant comparison copies the generated members from the committed class, so a
`.form` edit compiled by nobody passed it, and `verify-splice.ps1 -UpdateFormBaseline` would then
silence the `.form` hash too. **CI and the release workflow pass `-RequireCompiler`**, so a missing
compiler fails the step instead of quietly falling back to the weaker check. Locally, without the
compiler, `-Check` runs the transplant comparison and warns loudly that the byte check was skipped.

**The transplant stays, as a lint.** The real compiler reproduces IntelliJ's placement of the
`$$$setupUI$$$` call faithfully — *including* the shapes ADR 0010 found IntelliJ gets wrong (a
`new <ThisClass>(…)` before the call taken for `this(…)`, a second setup in a delegating
constructor, a try or loop that can skip it). So every `-Compile`, and every class `-Check` compiles,
also runs FormTransplant over the same javac output, with the generated members taken from the fresh
compile; it must accept, and must place the call where the compiler did. `-SelfTest` proves this
bites: the compiler builds the `new ChangeTimer(0)` specimen, and the lint refuses it.

`-Rebuild` is now an alias for `-Compile`. A transplant-built class keeps javac's frames, so it
would fail the byte check the moment it was committed.

**The download.** Six jars — the compiler, its runtime (`java-gui-forms-rt`), the instrumentation
utilities and their JDK-9+ loader (`…-java8`, without which the finder cannot see `java.*`),
`asm-all` 9.1 and `jdom` 2.0.6 — each pinned by SHA-256 (taken from a download whose published SHA-1
matched) and re-hashed on every use. The compiler's POM also names `org.jetbrains:annotations`
(compile-time only) and `com.jgoodies:forms` (needed only for a form laid out with JGoodies'
FormLayout, which none of these is; such a form would fail loudly, not compile wrongly); both were
measured unnecessary and are not fetched. Cached per machine in
`%LOCALAPPDATA%\supercc-formsc\211.7628.21\`, never committed, never shipped.

## Consequences

- **A `.form` edit no longer needs IntelliJ.** Edit it, run `formclass.ps1 -Compile`, then
  `verify-splice.ps1 -UpdateFormBaseline`, and commit the source, the classes and the baseline.
- **ADR 0002's "no package manager" rule bends exactly as far as JaCoCo already bent it:** a pinned,
  hash-checked download of a *tool*, never a dependency of the program. The program's own vendored
  runtime, `com/intellij/uiDesigner/core`, turned out to be byte-identical to the
  `java-gui-forms-rt` of this same build (`docs/THIRD_PARTY.md`), so compiler and runtime are known
  to be one version.
- **CI now depends on JetBrains' repository being reachable.** Deliberately not cached: caching
  means pinning another third-party action into the workflow that produces the published bytes,
  the trade `release.yml` already declines for JaCoCo, and the download is about 1 MB. The download
  retries three times.
- **The JDK pin tightens rather than loosens.** The byte check needs javac 16.0.2, as everything in
  this folder already does. It was measured on Oracle's 16.0.2 build; CI runs Temurin 16.0.2+7, and
  CI's own `-Check -RequireCompiler` is the measurement that the two agree.
- **What still proves a window WORKS** is unchanged: `test/FormClassTest.java` constructs every
  headless-constructible form window, and `test/GuiWindowTest.java` builds the main one. A byte
  comparison proves the class is what the sources make, not that the sources are right.
