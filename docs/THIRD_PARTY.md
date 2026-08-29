# Third-party code

This project has **no package manager and no dependency manifest**. Its third-party code is
committed as bytecode (see `docs/adr/0002-commit-the-intellij-bytecode-baseline.md`), which means
Dependabot cannot see it, CodeQL does not analyze it, and nothing else will tell you what is in
here. This page is that inventory.

It matters beyond curiosity: these classes are **redistributed** inside `SuperCC.jar`, which ships
in every release zip under GPLv2-or-later.

## What is vendored

| Path | Project | Classes | License | Role |
|---|---|---|---|---|
| `com/intellij/uiDesigner/core/` | IntelliJ GUI Designer runtime (`forms_rt`), JetBrains | 11 | Apache-2.0 | Layout classes (`GridLayoutManager`, `GridConstraints`, `Spacer`, …) that the generated `$$$setupUI$$$()` methods call at runtime. Required by every `.form`-based screen. |
| `org/json/simple/` | JSON.simple, Yidong Fang | 12 | Apache-2.0 | Reads and writes the solution JSON files under `succsave/`. |

Both are inherited from upstream [SicklySilverMoon/SuperCC](https://github.com/SicklySilverMoon/SuperCC)
rather than chosen here.

## Versions

**Exact versions are not recorded upstream and cannot be recovered from the bytecode with
confidence.** Neither tree carries a `META-INF` with version metadata; both were extracted from the
upstream fat jar. Best-effort identification from the class set:

- `com.intellij.uiDesigner.core` — the `forms_rt` runtime as bundled with the IntelliJ IDEA release
  used to build upstream's baseline. The class set (`AbstractLayout`, `DimensionInfo`,
  `LayoutState`, `SupportCode$TextWithMnemonic`, `Util`, `HorizontalInfo`, `VerticalInfo`) has been
  stable across many IDEA versions, so it does not pin a release.
- `org.json.simple` — JSON.simple 1.1.x. The presence of `ItemList`, `JSONStreamAware` and the
  `parser` package with `Yylex`/`Yytoken` matches the 1.1 line; 1.1.1 is the most likely.

Do not state a version more precisely than this without evidence. If a version ever has to be
pinned exactly, the honest route is to obtain candidate jars and compare class hashes.

## Rules

- **Do not add a dependency.** There is no mechanism to fetch, update or audit one, and adding a
  build tool to get one is a decision that needs an ADR, not a commit.
- **Do not upgrade these in place** without checking the GUI still launches. The `uiDesigner` runtime
  is called by IntelliJ-generated code compiled against a specific API; a mismatch fails at runtime,
  not at build time, and `javaw` will swallow the stack trace.
- **License text ships with the binary.** `COPYING` (GPLv2) is included in every release zip by
  `build.ps1 -Package`. Both vendored libraries are Apache-2.0, which is compatible with GPLv2+
  distribution.

## Runtime requirement

Java **16 or newer**. Below that the jar fails with `UnsupportedClassVersionError`, whose message
does not mention that the Java installation is too old — `README.txt` section 2 spells that out for
downloaders.
