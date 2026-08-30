# Architecture Decision Records

Short records of the decisions in this repo that are **counter-intuitive on purpose**. Each one
looks like a bug, an oversight, or a thing to tidy up, and each one is load-bearing.

They exist because an agent or contributor arriving with no context will otherwise "fix" them, and
several of these fixes are silent — the build succeeds, the tests pass, and the shipped program is
wrong. Read the relevant record before changing the behavior it describes.

| # | Decision | Status |
|---|---|---|
| [0001](0001-splice-build-not-full-recompile.md) | Build by splicing recompiled classes over a prebuilt baseline | Accepted |
| [0002](0002-commit-the-intellij-bytecode-baseline.md) | Commit IntelliJ-built `.class` files to version control | Accepted |
| [0003](0003-test-through-the-built-jar.md) | Tests run against the built jar, not the source tree | Accepted |
| [0004](0004-store-settings-paths-relative.md) | Store settings paths relative to the Chip's Challenge folder | Accepted |
| [0005](0005-switches-default-off-for-downloaders.md) | Every opt-in switch defaults to off | Accepted |
| [0006](0006-ci-runs-windows-powershell-5-1.md) | CI runs Windows PowerShell 5.1, not PowerShell 7 | Accepted |
| [0007](0007-synthesize-dat-fixtures.md) | Engine tests synthesize `.dat` fixtures instead of committing level sets | Accepted |
| [0008](0008-tws-folder-remembers-again.md) | The TWS folder remembers the last folder used (reverses jc-8) | Accepted |
| [0009](0009-the-error-log-is-not-a-setting.md) | The error log is always on, and is not a setting (carve-out from 0005) | Accepted |

## Writing a new one

Copy the shape of an existing record: **Context** (the forces, with the evidence), **Decision**
(what we do, stated plainly), **Consequences** (what this costs and what it forbids). Number it
sequentially, add it to the table, and link it from `CLAUDE.md` §6 if it is something a newcomer
would otherwise try to fix.

Supersede rather than delete. ADR 0008 reverses a decision made in jc-8 and says so; the history of
a reversal is exactly what stops it from being re-reversed by the next person who finds the original
reasoning persuasive.
