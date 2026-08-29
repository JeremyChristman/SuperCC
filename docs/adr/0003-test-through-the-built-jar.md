# 0003 — Tests run against the built jar, not the source tree

**Status:** Accepted (2026-08-14) · **Applies to:** `run-tests.ps1`, everything in `test/`

## Context

Because the build splices (ADR 0001), the source tree and the shipped bytecode can disagree. A test
that compiles `SuccPaths.java` and asserts on the result would be testing code that may not be in
the jar — and the failure mode this guards against is exactly that: a modified file left out of
`$MODIFIED`, where the source is correct, the test is correct, and the program still ships the old
behavior.

There is a second, sharper version of the same problem. Reflection can prove that a *method* exists
or is gone. It proves nothing about its *callers*, which live in classes the splice may or may not
have recompiled, and both directions fail silently:

- a stale caller of a removed method throws `NoSuchMethodError` on a menu click, in production;
- a stale class that never received a *new* call simply does nothing at all, with no error anywhere.

The second one is worse and has no runtime signal whatsoever.

## Decision

`run-tests.ps1` builds the jar first (unless given `-NoBuild`), then compiles every `test\*.java`
**against that jar** and runs it with the jar on the classpath. No test compiles application source.

Tests may additionally **scan every class in the jar** for references to a method by name, and
assert on the result. `SettingsTest` section 10 does this: it asserted that nothing still called the
removed `setTWSPath` in jc-8, and — with the same assertion inverted — that `MenuBar` genuinely does
call it again in jc-10.

## Consequences

- The suite is slower: every run builds a jar unless told not to.
- Tests can only reach public API. That is an acceptable price; the public surface is what ships.
- **The jar-scan assertion must be inverted, not deleted, when a method comes back.** Deleting it
  removes the only check that can catch a stale splice.
- `-NoBuild` exists for iterating on tests. Anything reporting a result to a human or to CI should
  run the full build first, or it is reporting on an unknown artifact.
- Concurrency: the build writes `SuperCC.jar` at the repo root, so parallel runs race. Use
  `-NoBuild` or `build.ps1 -Out` with a private path.
