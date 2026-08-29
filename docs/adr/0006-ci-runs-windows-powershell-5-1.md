# 0006 — CI runs Windows PowerShell 5.1, not PowerShell 7

**Status:** Accepted (2026-08-28) · **Applies to:** `.github/workflows/*.yml`

## Context

Every script here is PowerShell, and the maintainer's two machines both run **Windows PowerShell
5.1**. GitHub's `windows-latest` runner provides both 5.1 (`shell: powershell`) and PowerShell 7
(`shell: pwsh`, the default for `run:` on Windows runners).

The two are not interchangeable for this codebase. Behavior that 5.1 gets wrong, and that this repo
has already had to work around, includes:

- `Compress-Archive` and `ZipFile::CreateFromDirectory` both emit **backslash** zip entry names on
  5.1 — measured here. `build.ps1` builds entries by hand because of it. PowerShell 7 does not have
  this bug, so a 7-only CI would validate a code path the maintainer never runs and would go quiet
  on the one he does.
- `ZipArchiveMode` needs both `System.IO.Compression` and `System.IO.Compression.FileSystem`
  loaded on 5.1.
- 5.1's `-Encoding utf8` writes a BOM, and `javac` rejects a BOM. `build.ps1` writes its generated
  source as ASCII specifically to dodge this.
- 5.1 has no `&&`/`||` pipeline chain operators, no ternary, and no null-coalescing. A script that
  is only ever exercised on 7 will grow syntax that fails on the maintainer's desktop.

The point of CI here is to fail the way his machine fails, before his machine does.

## Decision

Every workflow step that runs project PowerShell declares `shell: powershell` (Windows PowerShell
5.1) explicitly. Steps are invoked as `powershell -ExecutionPolicy Bypass -File <script>` so that
the script runs the same way it is documented and the same way it is run by hand.

## Consequences

- CI exercises the real target runtime. A 5.1-only failure is caught in a pull request.
- **PowerShell 7 syntax must not be introduced into these scripts**, even though CI would otherwise
  tolerate it locally on a developer's machine.
- Workflows are pinned to Windows. There is no Linux or macOS job, and there should not be: the
  build shells out to `robocopy`, and the product is a Windows-targeted desktop app.
- `robocopy` returns exit codes 0–7 for *success*. `build.ps1` pipes it to `Out-Null` and does not
  test `$LASTEXITCODE`, which is correct — but any new step that shells out must not naively treat
  a nonzero robocopy exit as a failure, and must not leave a nonzero `$LASTEXITCODE` sitting at the
  end of a step, because the Actions shell wrapper checks it and fails the step.
