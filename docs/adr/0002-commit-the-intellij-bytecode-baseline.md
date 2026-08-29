# 0002 — Commit IntelliJ-built `.class` files to version control

**Status:** Accepted (2026-07-25) · **Applies to:** `emulator/ game/ graphics/ io/ tools/ util/` at the repo root

## Context

ADR 0001 establishes that fifteen GUI classes can only be compiled by IntelliJ's form compiler. That
bytecode has to come from somewhere on a machine that does not have IntelliJ — including a CI
runner, and including any contributor's clone.

The alternatives were:

1. **Require IntelliJ to build.** Kills CI, and kills any contribution from someone without it.
2. **Keep the baseline in a release asset or a submodule.** Adds a fetch step and a second thing
   that can be out of date, for content that changes roughly never.
3. **Commit the bytecode.** Ugly by convention; makes the repo self-contained.

Upstream already ships its `.java` sources *inside* the jar, so the project's own precedent is that
source and bytecode travel together.

## Decision

Commit the IntelliJ-built `.class` baseline under `emulator/`, `game/`, `graphics/`, `io/`,
`tools/`, and `util/` at the repo root, alongside the `java/**` source tree. Third-party libraries
(`com/intellij` annotations, `org/json`) are committed as bytecode for the same reason: there is no
package manager here to fetch them.

`.gitignore` excludes only the *built* `SuperCC.jar` and `dist/`.

## Consequences

- **The repo builds anywhere with just a JDK.** CI needs `actions/setup-java` and nothing else.
- **These directories are not build output and must never be deleted or ignored.** Removing them
  breaks the build in a way whose error message does not mention them.
- Diffs touching the baseline are unreviewable by eye. They should only appear when the baseline is
  genuinely regenerated from IntelliJ, and that deserves its own commit with an explanation.
- The source tree is authoritative for non-form classes; the baseline is authoritative for form
  classes. They can disagree, and the jar is what settles it (ADR 0003).
- Merging upstream changes means rebuilding the baseline in IntelliJ, not just merging source.
