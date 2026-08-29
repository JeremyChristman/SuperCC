# Contributing

Written for humans and coding agents alike. **Read [`CLAUDE.md`](../CLAUDE.md) first** — it carries
the traps, and the biggest one will silently discard your change if you do not know about it.

## Setup

Windows and a **JDK 16 or newer**. That is the whole setup. There is no package manager, no build
tool, and nothing to install — third-party libraries are committed as bytecode
(`docs/THIRD_PARTY.md`).

`build.ps1` finds a JDK by itself: `$JAVA_HOME` if set, otherwise the newest one under
`C:\Program Files\Java`. It looks for `jar.exe` specifically, because the Oracle `javapath` shim on
`PATH` provides `javac` and `java` but not `jar`.

## The loop

```powershell
powershell -ExecutionPolicy Bypass -File verify-splice.ps1    # will my edit actually ship?
powershell -ExecutionPolicy Bypass -File build.ps1            # -> SuperCC.jar
powershell -ExecutionPolicy Bypass -File run-tests.ps1        # builds, then runs test\
```

Run `verify-splice.ps1` **first** after editing any `java/**` file. It is the only thing that
catches the repo's signature failure: `build.ps1` recompiles only the files listed in
`$SPLICE_MODIFIED` (in `build-config.ps1`), and editing anything else leaves the build green, the
tests passing, and the old bytecode in the jar. There is no error and no runtime signal.

It works two ways. Unspliced ordinary files are recompiled and their bytecode compared against what
would actually ship. The 15 form-based files — each `.form` **and its sibling `.java`** — cannot be
recompiled here at all, so they are hashed against `docs/form-baseline.sha256`.

- If it reports an ordinary file, add it to `$SPLICE_MODIFIED` — after confirming it has no
  sibling `.form`.
- If it reports a form-based file, the edit cannot ship from this repo. Rebuild that class in
  IntelliJ, commit the new `.class`, and re-run with `-UpdateFormBaseline`. Never run
  `-UpdateFormBaseline` just to silence the warning; that removes the only check there is.

## Running it

```powershell
java -jar SuperCC.jar
```

Always from a console, never `javaw` or a double-click: those swallow stderr, and a GUI failure then
looks like "nothing happens". A benign `getSavestates()` startup NPE on stderr is pre-existing and
harmless. `emulator.SuperCC.main` parses command-line arguments through `ArgumentParser`, so a level
set path can be passed directly.

## Tests

Everything in `test\` is compiled against the **built jar** and every class with a `main()` runs.
Adding a file to `test\` is all it takes; there is no registry.

```powershell
powershell -ExecutionPolicy Bypass -File run-tests.ps1 -ResultsPath test-results   # JUnit XML + JSON
powershell -ExecutionPolicy Bypass -File run-tests.ps1 -NoBuild                    # reuse the jar
powershell -ExecutionPolicy Bypass -File run-tests.ps1 -Isolated                   # private temp jar
```

Conventions that are not negotiable:

- **Test through the jar**, never against freshly compiled sources (`docs/adr/0003`).
- **Never commit a level set.** `.dat`, `.tws`, `.ccl` and `.dac` are third-party content, are
  gitignored, and CI fails on any tracked one. Synthesize fixtures with `test/DatBuilder.java`.
- **Skip, do not fail**, when an optional local file is absent — see `-Mo3` and `-Collection`.
- Assertions go through `Harness`, so results land in the machine-readable report.

## Working alongside other agents

- `build.ps1` writes `.\SuperCC.jar` and `-Package` **wipes all of `dist/`**. Those are shared
  mutable paths: two agents building in one checkout will produce confusing, non-reproducible
  failures. Use `run-tests.ps1 -Isolated`, or give each agent its own `git worktree`:
  ```powershell
  git worktree add ..\SuperCC-agent-a -b feature/agent-a
  ```
- **One agent owns `BUILD_TAG` per release.** A duplicate published tag cannot be undone.
- Announce your file set. `SuccPaths.java`, `build.ps1` and `README.txt` conflict with almost
  everything.
- Leave no stray `java.exe` running — a live JVM holds the jar open and the next build's delete
  fails with a lock error that reads like a permissions problem.

### `.claude/settings.json` is a convenience, not a security boundary

That file is committed, so it applies to anyone who runs a coding agent in a clone of this repo.
Two things follow.

**It reduces prompts; it does not contain an agent.** The `allow` entries are deliberately exact
rather than wildcarded, because a trailing `:*` would permit arbitrary extra arguments — and these
scripts have arguments that matter: `run-tests.ps1 -Jar <path>` puts any jar on the classpath of a
JVM that then runs, and `build.ps1 -Out`/`-Manifest` write to any path. The `deny` list is a
typo-catcher for the obvious forms and nothing more; it is literal prefix matching, so
`git push origin main --force` and `git push origin +main` sail straight past it. **The real
protection for "two jars must never report the same build tag" is a GitHub ruleset on
`refs/tags/jc-*` blocking deletion and non-fast-forward** — enforced server-side, where no
client-side pattern can be talked around.

**A pull request that edits `.claude/settings.json` is a privilege-escalation attempt against your
own agent.** Review diffs to it the way you review code, not the way you skim config.

## Pull requests

CI runs on `windows-latest` under **Windows PowerShell 5.1** (`docs/adr/0006`) and does: the
committed-level-set check, `verify-splice.ps1`, `build.ps1`, `run-tests.ps1`, and CodeQL. Please run
the tests locally and say so in the PR.

Do not introduce PowerShell 7 syntax (`&&`, `||`, ternary, `??`) — it fails on 5.1, which is what
the maintainer runs.

## Style

- **American English** in code, comments, identifiers, strings and docs.
- Comment *why*, not *what*. Mark fork changes `/* MOD (Jeremy, jc-N): ... */` and name the trap
  that motivated them.
- SOLID and GoF patterns only where they genuinely make the code cleaner.
- No `\u` escapes in Java comments — the lexer decodes them inside comments, so a Windows path in a
  comment is a compile error.
- When you change behavior a comment explains, update the comment in the same edit.

## Before you "fix" something odd

Check `CLAUDE.md` §6 and `docs/adr/`. A dozen things here look like bugs and are deliberate: the
committed bytecode, the hand-built zip entries, the off-by-default build tag, the relative stored
paths, and the jc-10 reversal of jc-8's TWS pinning. In this repo the surprising choice is usually
the considered one.
