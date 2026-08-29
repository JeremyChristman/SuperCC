# 0004 — Store settings paths relative to the Chip's Challenge folder

**Status:** Accepted (jc-5, 2026-08-09) · **Applies to:** `java/io/SuccPaths.java`

## Context

`succ_settings.ini` lives in the Chip's Challenge folder, which is inside Dropbox and therefore
synced between two machines whose user directories differ: `C:\Users\Jeremy` on the desktop and
`C:\Users\jerem` on the laptop.

Paths were stored absolute. Since any settings change rewrites the **whole** file, the last machine
used overwrote the other's paths. The live file was measurably a blend — `Levelset` written by one
machine, `TWS` by the other — pointing at a folder that did not exist on either. The one key that
never broke was `succ = succsave`, which had always been relative.

## Decision

Store `Levelset` and `TWS` **relative to the Chip's Challenge folder**. Getters still return
absolute paths, so callers saw no change; only the stored form moved. A path outside the folder (or
on another drive) necessarily stays absolute and degrades gracefully — it will not resolve on the
other machine until it is used there.

Legacy absolute values still resolve and convert on the next write, so there is no migration step.

`getSuccPath()` is deliberately **not** converted. It is the one data-bearing path — where solution
JSON files are written — it was already relative, and its callers resolve it against the working
directory.

## Consequences

- The synced settings file works on both machines. `[Paths]` contains no usernames.
- **`Path.startsWith` compares name elements, not characters.** That is why `<cc>Extra\data` is
  correctly treated as outside the folder. Never "simplify" the containment test to a string prefix
  comparison.
- `toStoredPath()` and its inverse must stay inverses. jc-6 was a release spent on exactly this: the
  outside-the-folder branch returned the caller's original string instead of the absolute path it
  had just computed, so a relative input resolving outside the folder was stored verbatim and then
  re-anchored on the way out.
- This is a *mitigation* for a synced settings file, not a fix. Two machines writing the file at
  once is still last-writer-wins; nothing at this layer can make that safe.
