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

This section used to say the versions "cannot be recovered from the bytecode with confidence", and
suggested that the honest route would be to obtain candidate jars and compare class hashes. That was
done on 2026-09-03, and it settled one of the two.

### `org.json.simple` — **JSON.simple 1.1.1**, established by byte identity

All twelve vendored classes are **byte-for-byte identical** to
`com.googlecode.json-simple:json-simple:1.1.1` from Maven Central, and identical to none of the
classes in 1.1. This is not an inference from the class set; it is a SHA-256 match on every file:

| | 1.1 | 1.1.1 |
|---|---|---|
| all 12 classes | no match | **exact match** |

To repeat the check, fetch
`https://repo1.maven.org/maven2/com/googlecode/json-simple/json-simple/1.1.1/json-simple-1.1.1.jar`,
unpack it, and hash `org/**/*.class` against this tree.

**Security position, checked 2026-09-03:** the OSV database — which aggregates CVE and GHSA —
reports **no known vulnerabilities** for this version. The library is nonetheless unmaintained
upstream, so the answer is "clean today", not "clean permanently".

```
curl -s -X POST https://api.osv.dev/v1/query -d \
  '{"package":{"ecosystem":"Maven","name":"com.googlecode.json-simple:json-simple"},"version":"1.1.1"}'
```

That one command is the whole vulnerability process for this dependency. Run it when something
prompts you to; there is no automation that can, for the reason below.

### `com.intellij.uiDesigner.core` — **not a published artifact**

Compared against every `forms_rt` release on Maven Central — `com.intellij` 4.5.4, 5.0, 5.1, 6.0.3,
6.0.5, 7.0.3, and `com.github.adedayo.intellij.sdk` 142.1 — and `GridConstraints.class` matches
**none** of them. That is consistent with the tree having been extracted from an IntelliJ IDEA
installation rather than from Maven: the IDE ships this runtime inside its own jars, versioned by
IDE build rather than by artifact version.

So this one genuinely cannot be pinned to a published release, and saying "it is forms_rt from some
IDEA build" is as precise as the evidence allows. It is JetBrains' own Apache-2.0 layout code, it
has no network or file surface, and it is only reached by generated `$$$setupUI$$$()` methods.

## Why Dependabot does not cover any of this

`.github/dependabot.yml` declares `github-actions` and nothing else, and that is not an oversight
that can be fixed by adding an ecosystem. Dependabot resolves dependencies from a manifest — a
`pom.xml`, a `build.gradle`, a lockfile. This repo deliberately has none (ADR 0002): the third-party
code is committed bytecode, so there is no manifest to read and no ecosystem to declare.

What replaces it, and what does not:

| | |
|---|---|
| **Tamper-evidence** | `verify-splice.ps1` check 5 hashes all 23 vendored class files against `docs/vendor-baseline.sha256` and fails on any change. It runs in CI on every push. Regenerate with `-UpdateVendorBaseline` when a library is deliberately updated, and record the update here. |
| **Vulnerability alerting** | **None, and none is possible without a manifest.** The OSV query above is manual. This is an accepted, recorded risk rather than an omission — the alternative is adopting a build system, which ADR 0002 rejects for reasons that still hold. |

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
