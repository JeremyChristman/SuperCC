# Jeremy's SuperCC fork — notes

A personal fork of **[SicklySilverMoon/SuperCC](https://github.com/SicklySilverMoon/SuperCC)**
(the Chip's Challenge / Tile World TASing emulator), with a few display-only mods and a
self-contained build. Upstream is GPLv2+; see `COPYING`.

## What's changed vs upstream

Each mod is its own commit on top of the pristine import, so `git log`/`git diff` show exactly
what's mine.

**The emulation engine is untouched** — no mod changes movement, RNG, or the outcome of an existing
solution, verified by a headless replay reproducing the exact same tick-by-tick result before and
after. What the fork *does* change, by release:

| | Scope |
|---|---|
| Mods 1–3 | **Display-only** — window title, hint panel, connection overlays. |
| jc-4, jc-5 | **Disk behavior** — how `settings.ini` is read and written. See *Settings-file resilience* and *Portable paths*. |
| jc-7 | **Level loading** — three levels that could not be opened at all now open. See *Off-map monster-list entries*. |

1. **Window title = build tag + pack + current level** (`java/emulator/SuperCC.java`).
   Title reads `SuperCC [jc-N] - <pack> - <level>` (bump `BUILD_TAG` per production deploy so the
   running build is identifiable). Upstream showed only the level name; an earlier mod showed only the
   pack — this shows the version, pack, and current level together, updating as you change levels.
   **The `[jc-N]` half is toggleable — see below.**
2. **Hint shown in the level panel** (`java/graphics/LevelPanel.java`). The level's hint text is drawn
   under "Author:" (uncapped width so even the longest hint wraps fully); hidden on hintless levels.
3. **Clone/Trap connections on by default** (`java/graphics/MenuBar.java`, `java/graphics/GamePanel.java`).
   The Clone- and Trap-connection overlays render by default (matching Monster/Slip list), and the
   "Show Clone Connections" menu label casing is fixed.

## Toggling the build tag (no rebuild)

The tag is handy while the fork is under active modification and just noise the rest of the time,
so it is switched by a setting rather than by a rebuild. In the CC folder's `settings.ini`:

```ini
[Graphics]
ShowBuildTag = false
```

`true` shows the tag, `false` (or `0`) hides it, and the key being **absent means ON** — anything
other than `false`/`0` counts as on, so a typo or a `settings.ini` predating the key keeps the tag
rather than silently dropping it. Settings are parsed once at launch, so a change applies the
**next time SuperCC starts**.

> **Don't put a trailing `;` comment on that line.** `parseSettings()` only honors a `;` at the
> *start* of a line; everything after `=` is the value. `ShowBuildTag = false ; hides it` yields the
> value `false ; hides it`, which is not `false`, so the tag stays **on**.

Three more ways to get this wrong by hand:

- **The space before `=` is load-bearing.** `parseSettings()` reads the key as
  `substring(0, indexOf('=') - 1)`, so `ShowBuildTag=false` parses as the key `ShowBuildTa` and is
  ignored — leaving the tag on.
- **The section matters.** Keys are stored as `<section>:<name>`, so a `ShowBuildTag` line under
  `[Paths]` is a different key that nothing ever reads. It must be inside `[Graphics]`.
- **Don't edit while SuperCC is running.** It keeps the whole settings file in memory and rewrites
  every line of it whenever any setting changes, so a running instance restores its own stale value
  over the edit.

`set_supercc_buildtag.ps1` (in Jeremy's `Dropbox\Claude\CC Audit Scripts\`) mirrors the parser on all
three: it scopes to `[Graphics]`, reports the state SuperCC would actually see (so a no-space line
reads as *on*, and gets repaired rather than reported as working), takes the last of any duplicates,
writes atomically via a temp file, and verifies by reading back.

Switching the tag off does **not** make a build unidentifiable — `BUILD_TAG` is still a string
constant in the jar, so `unzip`/`javap` on `emulator/SuperCC.class` still names the release.

## Settings-file resilience (jc-4)

`java/io/SuccPaths.java` had three inherited defects that could destroy `settings.ini`. All three
were reproduced before being fixed:

| | Was | Now |
|---|---|---|
| **Read failure** | ANY `IOException` was treated as "file missing" → `createSettingsFile()` truncated it and wrote defaults. Windows locks are *mandatory*, so a settings.ini briefly held by Dropbox or antivirus was genuinely unreadable — and destroyed. | `SuccPaths.load()` distinguishes absent from unreadable, retries a locked file, and otherwise runs on in-memory defaults **without writing**. |
| **Write** | `PrintWriter` straight onto the destination — truncates at open, and never throws (errors go to a flag only `checkError()` reads, which nothing called). A failure left a zero-byte file, silently. | Rendered to a String, staged to a uniquely named sibling temp file, moved into place, **retried**, and reported. |
| **Absent keys** | Persisted as the literal text `null`, which never self-healed (`"null"` is a non-null String). `createSettingsFile()` omitted seven keys, which is how they got there. | A `DEFAULTS` table seeds missing keys at load, repairs an existing `null`, and renders the complete set. |

Two things to preserve if you touch this code:

- **The staging file must stay a sibling of the target.** Same directory means same volume, which
  keeps `Files.move` on the `ATOMIC_MOVE` path. The fallback is copy-then-delete, which truncates the
  destination and would reintroduce exactly the torn file this design prevents.
- **The output layout is byte-for-byte compatible** with the old `PrintWriter` — platform separator
  after section headers, bare `\n` after key lines, no trailing newline. Verified against a real
  settings.ini: 449 bytes in, 449 identical bytes out. Don't "normalize" it.

## Portable paths (jc-5)

`settings.ini` is Dropbox-synced between two PCs with different usernames, and `Levelset`/`TWS` held
absolute paths — so each machine overwrote the other's and left it pointing at a folder that didn't
exist. `succ = succsave` never had this problem because it was always stored relative, so those two
keys now work the same way:

```ini
[Paths]
Levelset = data
TWS = tws\JacquesOld-MS
succ = succsave
```

- **Callers see no change** — the getters still return absolute, usable paths. Only the stored form moved.
- A path **outside** the CC folder stays absolute (nothing to anchor it to); so does a different drive.
- An existing absolute value still resolves and converts the next time that folder is set — no migration step.
- **`getSuccPath()` is deliberately excluded.** It's the one data-bearing path (it decides where
  solution JSONs are written), it's already relative, and its callers resolve it against the working
  directory. Changing what it returns would move where solutions are saved.

`Path.startsWith` compares *name elements*, not characters, so a sibling folder sharing a name prefix
(`<cc>Extra\data`) is correctly treated as outside. Don't "simplify" it to a string prefix test.

## Off-map monster-list entries (jc-7)

Three levels could not be opened **at all** — `ArrayIndexOutOfBoundsException` on load:

| Set | Level | Title | Bad entry |
|---|---|---|---|
| `geodave1` | 146 | Ooops! Chip can't swim without flippers! | (160, 160) |
| `pi` | 9 | bugs | (24, 34) and (31, 32) |
| `Rock-Alpha` | 21 | Mustache | (88, 88) |

Their CC1 monster-movement list (optional field 10) carries junk entries pointing off the 32×32 map.
`LevelFactory.getMSMonsterList()` has two loops over that array — one counts, one stores. The storing
loop was already correct: it builds a `Position`, and `Layer.get(Position)` returns `Tile.WALL` for an
off-map one, which is not a monster, so the entry is skipped. The **counting** loop used the raw
`int` overload, which has no bounds check. The fix is to count through the same accessor.

Two failure modes existed, and the second is the better argument for the fix:

- `32*y + x >= 1024` → the exception above; the level never opened.
- `x >= 32` but `32*y + x < 1024` → **no exception**. The multiply aliased onto an unrelated in-bounds
  cell; had it held a monster, the counting loop would have over-allocated and left a trailing `null`
  that NPEs in `MSLevel`'s constructor. 10 such entries exist across 8 levels here — all alias onto
  Wall/Water/Dirt/Floor, so none has bitten yet.

**Verified safe across the whole collection**: the MS monster list of all **21,838 levels in 273 sets**
is byte-identical before and after, except the 3 levels above. **And it matches the reference engine** —
Tile World's loader skips an out-of-range entry outright (`if (pos < 0 || pos >= CXGRID*CYGRID) continue;`)
and then skips anything that is not a creature. It keeps no slot and does not clamp, so creature *order*
agrees with SuperCC on every one of the 14 off-map entries present. MS only — the Lynx loader never
reads this list, so those levels always opened under Lynx.

## Building

Requires a **JDK 16+** (upstream targets Java 16). From this folder:

```powershell
powershell -ExecutionPolicy Bypass -File build.ps1     # -> SuperCC.jar
```

`build.ps1` recompiles **only the hand-edited, non-form source files** (listed in `$MODIFIED`) with
`javac --release 16` and splices them over the committed baseline of compiled classes, then repackages
with the libs (`com/intellij` annotations, `org/json`), `resources/`, and the manifest — reproducing
the upstream fat jar (571 entries, 193 classes). It resolves a real JDK bin automatically (the Oracle
`javapath` dir on PATH exposes `javac` but not `jar`).

**Why not a full `javac` rebuild?** SuperCC's GUI is built with the IntelliJ GUI Designer — the main
`graphics/Gui` window and the `tools/*` dialogs each have a `.form` file, and IntelliJ's *form compiler*
generates a hidden `$$$setupUI$$$()` method at build time that creates their Swing components. Plain
`javac` doesn't do that, so a full rebuild yields a jar whose GUI dies on launch (`... playButton is
null`). So the IntelliJ-built `.class` files are committed as the authoritative baseline (under
`emulator/ game/ graphics/ io/ tools/ util/`), and only the non-form modified classes are recompiled.
**Adding a mod to a new file?** Add it to `$MODIFIED` in `build.ps1` — and make sure it has no sibling
`.form`.

## Note on history / source vs. bytecode

Upstream ships its `.java` source *inside* the jar under `java/`. The prior hand-built mods were
spliced in as recompiled `.class` files only — the bundled source stayed pristine. This repo
reconstructs the mods **into the source** (from the documented changes) so the source is authoritative
for the non-form classes, while the IntelliJ-built `.class` baseline is committed for the form classes
that `javac` can't reproduce (see the build note above).
