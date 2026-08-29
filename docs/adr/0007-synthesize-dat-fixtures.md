# 0007 — Engine tests synthesize `.dat` fixtures instead of committing level sets

**Status:** Accepted (2026-08-28) · **Applies to:** `test/DatBuilder.java`, `test/EngineTest.java`

## Context

Until now the suite covered only the settings contract. Everything that makes this an emulator —
`.dat` parsing, `LevelFactory`, the MS and Lynx rulesets — had no automated coverage at all, and the
fork's most serious shipped bug (jc-7: three levels that would not open) lived exactly there.

The obvious fixture is a real level set. That is not available:

- CC1 level sets are **third-party content** and are not ours to redistribute. `MO3.dat`, the set
  that motivated jc-9, is referenced by the suite through an optional `-Mo3` switch precisely
  because it cannot be committed.
- A test that requires a file the repo does not contain either fails for everyone or skips for
  everyone, and a suite of permanent skips is not coverage.

The objection to synthesizing fixtures is real and worth stating: `DatBuilder` encodes the same
`.dat` format knowledge that `DatParser` already has, so a naive round trip proves only that the two
agree with each other. A test whose fixture is built from its own assumptions cannot find a bug in
those assumptions.

## Decision

Ship `test/DatBuilder.java`, which writes a valid CC1 `.dat` into a temp directory: signature, level
count, RLE-compressed layers, and TLV optional fields (title, password, hint, author, monster list).
Engine tests build the fixture they need and open it through the built jar.

To keep it from becoming a self-agreeing round trip, `DatBuilder` is written **against the format
specification** (`seasip.info/ccfile.html`), not against `DatParser`, and its value is concentrated
in fixtures that encode a *known-bad* input rather than a well-formed one:

- **byte-level constants are asserted directly** against the spec (`0x0002AAAC` is MS, `0x0102AAAC`
  is Lynx), so the test fails if the parser's notion of a signature drifts;
- **the jc-7 regression is expressible**: an optional field 10 entry whose `x >= 32` is off the
  32×32 map. That input caused either an `ArrayIndexOutOfBoundsException` or — worse — a silent
  disagreement between the counting and storing loops, producing a trailing `null` and an NPE. A
  synthesized fixture reproduces both classes without any level set;
- **the RLE encoder is exercised in both forms** (literal runs and `0xFF` count/code runs), which is
  parser input that a hand-picked real level might never contain.

## Consequences

- Engine tests run anywhere, including CI, with no external file and no license question.
- **`DatBuilder` must keep being written from the spec.** The moment someone "fixes" it by copying
  `DatParser`'s logic, the tests stop being able to find a parser bug. Any change to it should cite
  the format, not the parser.
- A synthesized fixture cannot prove behavior against the corpus of real sets. Wide checks — the
  kind that fingerprinted the MS monster list of all 21,838 levels across 273 sets for jc-7 — remain
  a manual, local activity against the maintainer's own collection.
- Fixtures needing a real set still use the skip-when-absent pattern (`-Mo3`). Skips are reported
  distinctly from passes so they can never be mistaken for coverage.
