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
| jc-4, jc-5 | **Disk behavior** — how the settings file is read and written. See *Settings-file resilience* and *Portable paths*. |
| jc-7 | **Level loading** — three levels that could not be opened at all now open. See *Off-map monster-list entries*. |
| jc-8 | **Settings file + release packaging** — renamed to `succ_settings.ini`, the TWS folder stops drifting, the build tag defaults to off, and the release ships as a documented zip. See *The settings file (jc-8)*. |
| jc-9 | **Opening ruleset** — an opt-in setting forces every set to open under MS. See *Always open under MS (jc-9)*. |
| jc-10 | **Settings behavior** — the TWS folder remembers the last folder used again, reversing that part of jc-8. See *The TWS folder remembers again (jc-10)*. |

> **`settings.ini` is `succ_settings.ini` from jc-8 on.** Older sections below are left in their
> original wording where they describe historical work; read "settings.ini" in those as the same
> file under its old name.

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
so it is switched by a setting rather than by a rebuild. In the CC folder's `succ_settings.ini`:

```ini
[Graphics]
ShowBuildTag = true
```

**`true` (any casing) or `1` shows the tag. Everything else hides it, including the key being
absent and the settings file not existing at all.** Settings are parsed once at launch, so a change
applies the **next time SuperCC starts**.

> ⚠️ **The default was INVERTED in jc-8, and must stay inverted.** Up to jc-7 the rule was the
> opposite — "anything but an explicit `false`/`0` is ON" — which meant every fresh download showed
> a build number in its title bar until the user found this key. Jeremy hands the GitHub link to
> other people and does not want them seeing one, so the tag is now strictly opt-in. This is the
> same requirement Tile World has, and there it needed the same one-time source flip. Don't
> "restore" the lenient reading; a release that shows `[jc-N]` on a clean install is a regression.
> The acceptance test is a clean-room one: **no settings file at all → launch → no tag**.

> **Don't put a trailing `;` comment on that line.** `parseSettings()` only honors a `;` at the
> *start* of a line; everything after `=` is the value. `ShowBuildTag = true ; shows it` yields the
> value `true ; shows it`, which is not `true`, so the tag stays **off**.

Three more ways to get this wrong by hand — all of which now fail toward *off*:

- **The space before `=` is load-bearing.** `parseSettings()` reads the key as
  `substring(0, indexOf('=') - 1)`, so `ShowBuildTag=true` parses as the key `ShowBuildTa` and is
  ignored — leaving the tag off.
- **The section matters.** Keys are stored as `<section>:<name>`, so a `ShowBuildTag` line under
  `[Paths]` is a different key that nothing ever reads. It must be inside `[Graphics]`.
- **Don't edit while SuperCC is running.** It keeps the whole settings file in memory and rewrites
  every line of it whenever any setting changes, so a running instance restores its own stale value
  over the edit.

`set_supercc_buildtag.ps1` (in Jeremy's `Dropbox\Claude\CC Audit Scripts\`) mirrors the parser on all
three: it scopes to `[Graphics]`, reports the state SuperCC would actually see (so a no-space line
reads as *off*, and gets repaired rather than reported as working), takes the last of any duplicates,
writes atomically via a temp file, and verifies by reading back. It targets `succ_settings.ini` from
jc-8 on.

Switching the tag off does **not** make a build unidentifiable — `BUILD_TAG` is still a string
constant in the jar, so `unzip`/`javap` on `emulator/SuperCC.class` still names the release.

## The settings file (jc-8)

### Renamed to `succ_settings.ini`

`SuccPaths.SETTINGS_FILE_NAME` is the one place the name lives; `SuperCC`'s constructor reads it
from there instead of repeating a literal. SuperCC and Tile World share the Chip's Challenge folder
here and Tile World is getting an initialization file of its own, so neither may claim the generic
name `settings.ini`.

**There is deliberately no migration path.** An old `settings.ini` is simply not read — it is not
renamed, merged, or deleted, and a fresh `succ_settings.ini` is created next to it. Reading a
legacy file "just once" would mean shipping a compatibility path forever, in the one class whose
whole jc-4 story is about not touching files it does not own; renaming one file by hand, once, is
cheaper and safer. Anyone upgrading renames theirs and keeps their settings.

### The TWS folder was made a fixed starting point (jc-8) — ⚠ REVERSED BY jc-10

> **Superseded.** Read this section as history. jc-10 put the remembering back at Jeremy's
> request; see *The TWS folder remembers again (jc-10)* below for what the code does now. The
> only part of this section still live is the NPE fix in the last bullet.

`MenuBar`'s two TWS actions (open, and write-solution-to-new) used to call `setTWSPath(...)` with
whatever folder the chooser landed in, so the stored value drifted to whichever set was touched
last — it was sitting at `tws\Walls_of_CCLP2-MS`. With one subfolder per set (411 here), opening in
the parent every time seemed both more predictable and fewer clicks than opening in an arbitrary
sibling.

- `DEFAULT_TWS_PATH = "tws"`, and `getTWSPath()` falls back to it when the key is absent **or
  blank**, so clearing the line is a supported way to reset it. *(Still true in jc-10.)*
- A hand-written value was honored, and was the only way the value ever changed. *(No longer true
  — jc-10 writes it again.)*
- `setTWSPath()` was removed, not just left uncalled. *(jc-10 restored it deliberately.)*
- Removing the caller also retired a live NPE: `saveNewFile()` returns `null` when the save dialog
  is canceled, and the old line called `tws.getParent()` on it unconditionally. **This fix
  survives jc-10** — the restored callers guard for it.

### The TWS folder remembers again (jc-10)

Jeremy asked for the pre-jc-8 behavior back (2026-08-21): with one set open for a stretch of work,
having to re-navigate the chooser every single time costs more clicks than landing in the parent
saves. The jc-8 reasoning above was sound in the abstract and wrong in practice, and this is a
deliberate reversal, not a regression.

- **`setTWSPath()` is restored**, and both `MenuBar` TWS actions call it with the *parent folder*
  of the file actually used. `getTWSPath()` and its fallbacks are unchanged from jc-8.
- **The callers guard against null, and so does the setter.** `saveNewFile()` returns `null` on a
  canceled dialog and `getParent()` is `null` for a parentless file; either would have been the
  jc-8-era crash. `setTWSPath()` additionally ignores a null or blank argument rather than blanking
  a good stored value — a caller bug must not destroy the setting.
- **The value is stored relative to the CC folder** via `toStoredPath()`, exactly like `Levelset`,
  so a settings file shared between two machines stays valid on both.
- ⚠ **Choosing a folder OUTSIDE the CC folder stores an absolute path** (that is what
  `toStoredPath()` does, and it is right — there is no portable way to express it). On a
  Dropbox-shared settings file that path may not exist on the other machine. It degrades
  gracefully: `JFileChooser` falls back to a default directory rather than throwing, and the next
  use on that machine overwrites it. Restoring the memory makes this path materially more likely
  to be hit than it was under jc-8, which is why it is called out here.
- Tested by `SettingsTest` section 4 (round trip inside and outside the CC folder, plus the
  null/blank guard) and section 10 (the built jar really does call it from `MenuBar`), and by a
  real GUI run: the chooser opened at the stored folder, selecting a file elsewhere rewrote the
  setting to that file's folder, and reopening the chooser landed there.

### The release is a packaged zip

`build.ps1 -Package` produces `dist\SuperCC-<tag>.zip` containing `SuperCC.jar`, a stock
`succ_settings.ini` and `README.txt`, where:

- the **tag comes from `BUILD_TAG`** in the source, so the zip cannot be named for a build it does
  not contain;
- the **`.ini` is generated by calling `SuccPaths.createSettingsFile()` in the jar that was just
  built**, never hand-written, so the shipped defaults cannot drift from the program's;
- the build **fails** if `README.txt` does not name the tag being packaged — a download whose README
  describes a different build is worse than no README;
- `README.txt` is converted to **CRLF** on the way in (Notepad), while `succ_settings.ini` keeps its
  mixed layout untouched, because matching what SuperCC writes byte for byte is what stops the first
  settings change from rewriting the whole file.

`README.txt` is the user-facing document — what SuperCC is, that it needs Java 16+, every setting
explained, and the revision history. **It is updated with every release**, including a plain-English
entry for whatever changed and an entry for any new setting.

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

(That `TWS` value is what jc-5 produced, back when the program still rewrote it; since jc-8 it stays
`tws` unless you edit it yourself.)

- **Callers see no change** — the getters still return absolute, usable paths. Only the stored form moved.
- A path **outside** the CC folder stays absolute (nothing to anchor it to); so does a different drive.
- An existing absolute value still resolves and converts the next time that folder is set — no migration step.
- **`getSuccPath()` is deliberately excluded.** It's the one data-bearing path (it decides where
  solution JSONs are written), it's already relative, and its callers resolve it against the working
  directory. Changing what it returns would move where solutions are saved.

`Path.startsWith` compares *name elements*, not characters, so a sibling folder sharing a name prefix
(`<cc>Extra\data`) is correctly treated as outside. Don't "simplify" it to a string prefix test.

## Always open under MS (jc-9)

A CC1 `.dat` declares its intended ruleset in its 4-byte signature — `0x0002AAAC` / `0x0003AAAC`
mean MS, `0x0102AAAC` means Lynx (`DatParser`). `MO3.dat` carries the Lynx signature, so it opens
under Lynx. Correct by the format; a nuisance when working through a collection under MS.

```ini
[Emulation]
AlwaysOpenInMS = true
```

Same strictly opt-in rule as `ShowBuildTag`, and now literally the same predicate: `optedIn()` is
the single definition of "this switch is on", read by both getters and used by `render()`. Any
future switch goes through it rather than growing a second, subtly different rule.

**Where the override is applied, and why there.** In `SuperCC.startingRuleset()`, not in
`DatParser`:

- `DatParser` is a file reader; giving it a dependency on the settings file would be backwards.
- `DatParser.getRuleset()` has exactly one consumer — `openLevelset()` — so this is a genuine choke
  point, not one of several.
- `parseLevel()` records whatever ruleset it is handed (`if (rules != Ruleset.CURRENT) this.rules =
  rules`), so passing MS in at open time makes every later load of that set MS too. The override
  propagates without a second special case.
- The `paths == null` guard is load-bearing: the headless `SuperCC(boolean)` constructor never
  builds a `SuccPaths`, and `openLevelset()` is reachable from it.

**Scope is deliberately narrow — it decides only what a set OPENS in.** `Level > Change ruleset`
(F3) still switches freely, and loading a solution still switches to that solution's own ruleset.
Forcing MS there would break every Lynx replay, which is a far worse outcome than the convenience
this buys.

⚠️ **It does move where solutions are filed, and that is inherent, not a bug.** `getJSONPath()`
names each solution `<n>_<title>-<ruleset>.json`, so a set opened under forced MS saves and looks
for `-MS.json` while anything recorded earlier under Lynx sits in `-LYNX.json`. `Solution > Open`
stops preselecting the old files and `Solution > Save` starts a parallel set beside them. Nothing is
deleted or overwritten, and turning the setting off (or pressing F3) restores the old target — but
it is the consequence most likely to be mistaken for lost work, so it is called out in README.txt's
entry for the setting. The same mismatch makes `--testtws` prompt when a **Lynx-recorded** tws meets
a forced-MS set; for an MS corpus the flag removes a mismatch that used to be there.

Verified end to end by building a real `SuperCC` against a temporary settings file and reading back
`getLevel().getRuleset()`: MO3 gives `LYNX` with the flag absent or false and `MS` with it true,
while CCLP5 (MS signature) gives `MS` either way.

`[Emulation]` is a new section. A jc-8 settings file has no such section; `seedDefaults()` supplies
the key and it appears the next time anything is written — exactly how `ShowBuildTag` arrived in
jc-3, and covered by `test\SettingsTest.java`.

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
is byte-identical before and after, except the 3 levels above.

**The new rule is exactly the reference engine's.** Tile World decodes each entry as

```c
#define readpos(x, y)  (*(x) < CXGRID ? *(x) + CYGRID * *(y) : POS_INVALID)   /* encoding.c */
                                            /* POS_INVALID = CXGRID*(CYGRID+1) = 1056 */
if (pos < 0 || pos >= CXGRID * CYGRID) continue;                              /* mslogic.c */
```

so it discards an entry iff `x >= 32` (flagged immediately — it never aliases onto another cell) or
`y >= 32` (`pos` lands at 1024 or beyond). That is precisely `Position.isValid()`. It keeps no slot
and does not clamp, so creature *order* agrees with SuperCC on every off-map entry, unconditionally.

**Tile World never had this bug** — it opens all three levels and just logs a warning, e.g.
`level 146: invalid creature location (0 33)` (33 = `POS_INVALID`/32). Only SuperCC needed the fix.

MS only — the Lynx loader never reads this list, so those levels always opened under Lynx.

## Building

Requires a **JDK 16+** (upstream targets Java 16). From this folder:

```powershell
powershell -ExecutionPolicy Bypass -File build.ps1              # -> SuperCC.jar
powershell -ExecutionPolicy Bypass -File build.ps1 -Package     # -> also dist\SuperCC-<tag>.zip
powershell -ExecutionPolicy Bypass -File run-tests.ps1          # builds, then runs test\
```

## Tests

`test\SettingsTest.java`, run by `run-tests.ps1`, covers `SuccPaths`: the settings file's name,
its byte layout, the defaults table, path handling, and both opt-in switches — 90 assertions
including the jc-4 protections (an unreadable file is never overwritten, `createSettingsFile()`
refuses to clobber, a BOM is stripped) and the jc-5 relative-path round trip.

Two properties it deserves a note for:

- **It tests the BUILT JAR, not the source tree.** This is a splice build, so what ships is
  recompiled classes overlaid on a prebuilt baseline — the jar is the only artifact whose behavior
  is authoritative. `run-tests.ps1` builds first unless given `-NoBuild`.
- **It scans every class in the jar for references to a method by name.** Reflection on one class
  proves a method exists or not; it cannot prove anything about the *callers*, which live in
  classes the splice may or may not have recompiled. Either direction fails silently: a stale
  caller of a removed method throws `NoSuchMethodError` on a menu click, and a stale class that
  never got the *new* call simply does nothing, with no error at all. That scan is why
  `setTWSPath`'s removal could be trusted in jc-8 — and, with the assertion inverted, why its
  restoration can be trusted in jc-10.

Two checks skip (rather than fail) without files this repo does not contain: the jar scan without
`-Dsupercc.jar`, and the MO3 signature check without `-Mo3` (MO3 is a third-party level set, not
ours to redistribute).

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
