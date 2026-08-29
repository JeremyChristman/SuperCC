## What this changes

<!-- What it does and what that accomplishes, in plain English. If it fixes something, say what
     broke and how it showed up. -->

## Checks

- [ ] `verify-splice.ps1` is green — **required if any `java/**` file changed.** It is the only
      thing that catches an edit the splice build would silently discard.
- [ ] Every edited source file is in `$SPLICE_MODIFIED` (`build-config.ps1`), or the change is not
      in `java/**`.
- [ ] `run-tests.ps1` is green. Paste the summary line:
      <!-- e.g. 90 passed, 0 failed, 1 skipped / 32 passed, 0 failed, 1 skipped -->
- [ ] New behavior has a test. Fixtures are synthesized with `test/DatBuilder.java` — no level set
      is committed.
- [ ] No PowerShell 7 syntax (`&&`, `||`, ternary, `??`); the target is Windows PowerShell 5.1.
- [ ] American English throughout.

## If this changes shipped behavior

- [ ] `BUILD_TAG` bumped in `java/emulator/SuperCC.java`
- [ ] `README.txt` header names the new build, and section 7 has an entry
- [ ] Any new or changed **setting** is documented in `README.txt` section 6
- [ ] `CHANGELOG.md` and `FORK.md` updated
- [ ] Launched the jar **from the packaged zip** and used it

See [`RELEASING.md`](RELEASING.md) for the full sequence.

## If this changes something that looks like a bug but was deliberate

Say which ADR you read (`docs/adr/`) and why the decision no longer holds. Supersede the record
rather than deleting it — the history of a reversal is what stops it being re-reversed.
