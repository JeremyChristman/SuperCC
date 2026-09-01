# Releasing

Every behavior change here ships publicly. The GitHub link is handed to people who have never met
the maintainer, so a release is a jar **plus** the files needed to run, configure and understand it.

## The invariant

> **Two jars must never report the same build tag.**

`BUILD_TAG` in `java/emulator/SuperCC.java` is baked into the jar and shown in the window title.
Once a tag is published, that tag describes those exact bytes forever. jc-6 exists solely because a
one-line fix landed after jc-5 had already been deployed and reusing the number was not an option.

The release workflow enforces the tag half mechanically: `build.ps1 -ExpectTag` fails unless the git
tag and `BUILD_TAG` agree. Without it, pushing `jc-11` while `BUILD_TAG` still reads `[jc-10]`
passes every other gate and publishes a release whose jar calls itself `jc-10`.

⚠ **`BUILD_TAG` is a `static final String`, so javac inlines it at every use site.** A class that
does not get recompiled keeps the old tag baked in permanently. Today only `SuperCC.java` and
`SuccPaths.java` reference `BUILD_TAG`/`TITLE`/`SETTINGS_FILE_NAME`, and both are spliced every
build — but if a form-based class ever reads one of them, it would report a stale tag forever, and
`verify-splice.ps1` would flag it only if the source itself changed. Keep these constants out of
form-based classes.

## Checklist

1. **Bump `BUILD_TAG`** in `java/emulator/SuperCC.java` to `[jc-N]`.

2. **Update `README.txt`.** It ships in the zip and is a per-release deliverable, not a one-time
   write:
   - the **header line** must name the new build — `build.ps1 -Package` **fails the build** if it
     does not, and the check is anchored to the header specifically, so a revision-history entry
     mentioning the tag will not satisfy it;
   - add a section 7 entry saying what changed **and what that accomplished**, in plain English;
   - if the release adds or changes a **setting**, document it in section 6 — what it does, valid
     values, default, whether the program rewrites it, and the GUI path that sets it. A shipped
     setting nobody can find out about is not shipped. Say what a changed default used to be.

3. **Update `FORK.md`** with the engineering detail — the reasoning, what broke first, what was
   measured — and **`CHANGELOG.md`** with the summary entry.

4. **Verify.**
   ```powershell
   powershell -ExecutionPolicy Bypass -File verify-splice.ps1
   powershell -ExecutionPolicy Bypass -File run-tests.ps1
   powershell -ExecutionPolicy Bypass -File coverage.ps1 -CheckBaseline
   ```
   All three must be green. If you edited a `java/**` file, `verify-splice.ps1` is what proves the
   edit is actually going to ship.

   **Run the coverage check HERE, not for the first time from CI.** The release workflow runs it
   too, so a drift you skip now fails *after* the tag is public and the recovery is delete-tag /
   fix / re-push. If it reports drift: run `coverage.ps1 -UpdateBaseline`, update the table in
   `CLAUDE.md` section 4 to match, and commit both — the numbers in that table are a promise to
   whoever reads the repo next, and a release is when it should be true.

5. **Package and playtest.**
   ```powershell
   powershell -ExecutionPolicy Bypass -File build.ps1 -Package -ExpectTag jc-N
   ```
   Then extract `dist\SuperCC-jc-N.zip` somewhere clean and **launch the jar from the zip** — not
   the one in the repo root. Open a level set, change a setting, confirm the title bar. Reviews
   audit artifacts; this audits reality.

6. **Commit, push, tag.**
   ```powershell
   git add -A
   git commit -m "jc-N: <what changed and what it accomplished>"
   git push origin main
   git tag jc-N
   git push origin jc-N
   ```

7. **Publish.** The tag push runs the release workflow, which re-verifies, re-packages, and creates
   a **draft** release with the zip attached. Download that asset, launch it once, then publish.

   The draft is deliberate: CI builds on a different machine than the maintainer's, and the playtest
   gate is a human act that CI cannot perform. `gh release create` errors rather than overwriting if
   the release already exists, so a re-run can never silently replace a published zip.

8. **Send the link:** `https://github.com/JeremyChristman/SuperCC/releases/tag/jc-N`

## What ships

`build.ps1 -Package` produces `dist\SuperCC-<tag>.zip` containing exactly four files:

| File | Rule |
|---|---|
| `SuperCC.jar` | the build being released |
| `succ_settings.ini` | **generated** by calling `SuccPaths.createSettingsFile()` in the jar just built — never hand-written, so the shipped defaults cannot drift from the program's |
| `README.txt` | re-updated every release (step 2) |
| `COPYING` | GPLv2 travels with the binary, as the license requires |

One zip rather than loose assets, so nobody downloads the jar without the other three.

The packager verifies the **archive**, not the folder it was built from: entry names must contain no
backslash (PowerShell 5.1 writes them by default, and Info-ZIP and Java cannot read those as
separators — this is a public download that has to open off Windows), the file set must be exactly
those four, and every entry must hash-match its source.

## Build provenance

```powershell
powershell -ExecutionPolicy Bypass -File build.ps1 -Manifest dist/build-manifest.json
```

Records the tag, the jar's SHA-256, its size and entry count, and the **exact compiler** and
`--release` used. The compiler field matters: `--release 16` on a JDK 17 compiles fine but does not
emit the same bytecode as a JDK 16 compiler, and nothing in the jar itself reveals which one ran.
When the build tag is switched off — its default for downloaders — the jar's SHA-256 is how you
confirm which build is which.
