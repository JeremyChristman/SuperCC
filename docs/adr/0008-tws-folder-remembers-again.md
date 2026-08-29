# 0008 — The TWS folder remembers the last folder used

**Status:** Accepted (jc-10, 2026-08-21) · **Supersedes** the jc-8 decision to pin it · **Applies to:** `SuccPaths.setTWSPath()`, `java/graphics/MenuBar.java`

## Context

The `TWS` setting records where Tile World solution files are read and written.

**In jc-8 it was pinned.** Every TWS file operation had been calling `setTWSPath()` with the folder
just used, so the stored value drifted as you worked; with 411 subfolders in play, the maintainer's
stored value had wandered to `tws\Walls_of_CCLP2-MS`. The reasoning for pinning was that landing in
the parent folder is fewer clicks than landing wherever you happened to be last. `setTWSPath()` was
deleted outright — not merely left uncalled — so nothing could reintroduce the drift.

**Then he used it, and it was worse.** His words: *"I now realize I made a mistake and I want that
behavior back… it used to save which tws folder you were in and use that the next time."*

This is a preference decided by living with both, and the losing option is the one with the tidier
argument. That is precisely the kind of decision that gets re-reversed by whoever next reads the
jc-8 reasoning and finds it persuasive.

## Decision

Restore `setTWSPath()`. Both TWS actions in `MenuBar` call it with the **parent folder of the file
actually used**, so the setting tracks where you are working.

**Do not restore the jc-8 pinning.** It was tried, shipped, and rejected on use.

The jc-8 hardening survives the reversal and must stay:

- `saveNewFile()` returns `null` on a canceled dialog, and `getParent()` is `null` for a parentless
  file. Both callers guard.
- `setTWSPath()` itself ignores a null or blank argument, so a future caller bug cannot blank a good
  stored value.
- The value is stored **relative** via `toStoredPath()` (ADR 0004), so it stays portable across the
  two machines.

## Consequences

- The stored value drifts again, by design.
- A folder outside the Chip's Challenge directory is necessarily stored absolute and will not
  resolve on the other machine until it is used there. This degrades gracefully and is documented in
  both `FORK.md` and `README.txt`.
- **The jar-scan assertion was inverted, not deleted.** `SettingsTest` section 10 asserted in jc-8
  that no compiled class still called `setTWSPath`; it now asserts that `MenuBar` *does*. That
  inversion is the only check that catches the splice-build trap here — a stale `MenuBar` would
  silently never call it, with no error at all (ADR 0003).
