# 0001 — Build by splicing recompiled classes over a prebuilt baseline

**Status:** Accepted (2026-07-25) · **Applies to:** `build.ps1`

## Context

SuperCC's Swing UI is built with the **IntelliJ GUI Designer**. Fifteen classes have a sibling
`.form` file: `java/graphics/Gui.form` and the fourteen `java/tools/*.form`. At build time IntelliJ's
*form compiler* generates a hidden `$$$setupUI$$$()` method into each of those classes, and that
method is what actually constructs their Swing components.

`javac` knows nothing about `.form` files. A full `javac` rebuild therefore produces classes with no
`$$$setupUI$$$()`, so no components are created. This was tried. The result:

- The build succeeds with no warnings.
- The jar launches and immediately throws `NullPointerException: playButton is null`.
- Under `javaw` — which is what a double-click uses — stderr goes nowhere, so the user-visible
  symptom is **"nothing happens."**

Reproducing IntelliJ's form compilation outside IntelliJ would mean adding a build tool and the
`forms_rt` compiler to a project that has no package manager at all, to solve a problem that only
affects fifteen files nobody modifies.

## Decision

`build.ps1` recompiles **only** the hand-edited, non-form source files listed in its `$MODIFIED`
array, with `javac --release 16`, and splices the resulting `.class` files over a committed baseline
of IntelliJ-built bytecode (ADR 0002). It never compiles a form-based class.

`-implicit:none` and an empty `-sourcepath` are part of the mechanism, not decoration: they stop
`javac` from helpfully recompiling a form class it finds referenced from a file being built.

## Consequences

- **Modifying a file not in `$MODIFIED` silently does nothing.** The build succeeds and ships the
  old behavior with no error. This is the single most likely way to waste an afternoon here, which
  is why it leads `CLAUDE.md`.
- **A file with a sibling `.form` cannot be modified from this repo at all** without IntelliJ.
- **The built jar, not the source tree, is authoritative** — which is why the tests are written the
  way they are (ADR 0003).
- Adding a real build tool later would supersede this record. It would have to reproduce the form
  compilation and be verified by launching the GUI, not by a green compile.
