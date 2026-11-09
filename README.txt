==============================================================================
  SuperCC  --  Jeremy Christman's fork                      build jc-25
==============================================================================

  1. What this is
  2. What you need to run it
  3. What is in this download
  4. Getting started
  5. succ_settings.ini -- how the file works
  6. succ_settings.ini -- every setting, one by one
  7. Revision history: what each build changed and what it accomplished
  8. License and credits


------------------------------------------------------------------------------
1. WHAT THIS IS
------------------------------------------------------------------------------

SuperCC is an emulator and tool-assisted-solution workbench for the original
Chip's Challenge. It plays levels from CC1 level sets (.dat and .ccl files)
under both the MS and the Lynx rulesets, records and replays solutions, reads
and writes Tile World .tws solution files, and gives you savestates, rewind,
frame-by-frame stepping, monster and slip list overlays, clone and trap
connection overlays, and a set of solution-search tools.

This download is a FORK of SuperCC, maintained by Jeremy Christman.

  Upstream project (the original this fork is built on):
      https://github.com/SicklySilverMoon/SuperCC

  This fork:
      https://github.com/JeremyChristman/SuperCC

The fork exists because I use SuperCC heavily against a large collection of
level sets, alongside Tile World, and kept running into rough edges: levels
that would not open at all, a settings file that could be destroyed by a
transient file lock, solution exports that wrote the wrong cell, and a handful
of display details I wanted changed. Every one of those is fixed or changed
here. Section 7 lists them build by build.

This fork's own changes deliberately leave the emulation engine alone: none
of them alters movement, the random number generator, or the outcome of an
existing solution. That is checked before each release by replaying every
stored solution in the maintainer's collection and comparing where each one
ends. The engine changes in this download are upstream's own; section 7 says
which build brought each one in and what it changes -- jc-19 in particular.

The deep engineering detail behind each change -- root causes, measurements,
and the traps to avoid if you edit the code -- is in FORK.md in the repository.
This file is the user-facing version.


------------------------------------------------------------------------------
2. WHAT YOU NEED TO RUN IT
------------------------------------------------------------------------------

JAVA 16 OR NEWER. That is the one hard requirement.

SuperCC.jar is compiled to Java 16 bytecode, so an older Java cannot load it.
If you try, Java stops with a message like:

    java.lang.UnsupportedClassVersionError: emulator/SuperCC has been compiled
    by a more recent version of the Java Runtime

which means "your Java is too old", not "the download is broken".

To check what you have, open a command prompt and run:

    java -version

Any version number of 16 or higher works (16, 17, 21, 24, ...). If you do not
have Java, or have an older one, install a current JDK or JRE -- for example
from https://adoptium.net/ (Eclipse Temurin) or https://www.oracle.com/java/.

No other dependency, installer, or registry entry is needed. SuperCC is a
single jar file and runs from any folder you can write to.


------------------------------------------------------------------------------
3. WHAT IS IN THIS DOWNLOAD
------------------------------------------------------------------------------

    SuperCC.jar         The program.
    succ_settings.ini   Its initialization file, with stock defaults.
    README.txt          This file.
    COPYING             The GNU General Public License, version 2.

Keep them in the same folder. Extract the zip somewhere you can write to --
your own Documents or games folder is ideal. Extracting it inside
C:\Program Files is a bad idea: Windows blocks writes there, so SuperCC would
not be able to save your settings or your solutions.

  >> ALREADY HAVE SUPERCC? READ SECTION 4's UPGRADING NOTE BEFORE YOU EXTRACT.
     Extracting on top of an existing installation will offer to replace your
     succ_settings.ini with the stock one in this zip, which would discard your
     own settings. Keep yours.


------------------------------------------------------------------------------
4. GETTING STARTED
------------------------------------------------------------------------------

  +------------------------------------------------------------------------+
  |  UPGRADING FROM jc-7 OR EARLIER? READ THIS FIRST.                       |
  |                                                                         |
  |  jc-8 renamed the initialization file from settings.ini to              |
  |  succ_settings.ini, and it does NOT read the old name or convert it.    |
  |  If you do nothing, SuperCC starts with STOCK DEFAULTS: default tile    |
  |  size and tilesheets, default control keys, no remembered level set     |
  |  folder, and succ = succsave.                                           |
  |                                                                         |
  |  TO KEEP YOUR SETTINGS: close SuperCC, then RENAME your existing        |
  |  settings.ini to succ_settings.ini, replacing the stock one from this   |
  |  zip. That is the whole procedure.                                      |
  |                                                                         |
  |  (jc-8 used to ask you to check the file's "TWS =" line as a second     |
  |  step. jc-10 undid the change that made that necessary: the tws folder  |
  |  is remembered again, so whatever value your old file carries is just   |
  |  the folder the chooser opens in first, and it updates from there.)     |
  |                                                                         |
  |  Nothing is deleted either way -- your old settings.ini is left alone,  |
  |  so this is always recoverable. The one to watch is "succ": if you had  |
  |  pointed it somewhere other than succsave, SuperCC will start a new     |
  |  empty solution folder and your saved solutions will LOOK gone. They    |
  |  are not -- they are still in the old folder named by your old file.    |
  +------------------------------------------------------------------------+

Run it either way:

    * Double-click SuperCC.jar, if .jar files are associated with Java.

    * Or open a command prompt IN THE FOLDER YOU EXTRACTED TO and run:
          java -jar SuperCC.jar

Then use Level > Open levelset and pick a .dat or .ccl file. Level sets are
not included with SuperCC; the community collection at
https://sets.bitbusters.dev/ is where most of them live.

WHICH FOLDER SUPERCC READS ITS SETTINGS FROM

succ_settings.ini is looked up in the WORKING DIRECTORY -- the folder the
program is started from, which for a double-click is the folder holding the
jar. Keep the jar and the ini together and this is simply "the folder you
extracted to", which is what you want.

It matters if you launch from a shortcut or a script: set the shortcut's
"Start in" field (or `cd` first) to the folder holding the jar, or SuperCC
will look for succ_settings.ini somewhere else, not find it, and create a
fresh one there with default settings.

If the file is missing, SuperCC creates it with defaults, so deleting it is a
safe way to start over.

A NOTE ON FOLDERS THIS FORK EXPECTS

Two settings name folders that do not exist in a fresh extraction:

    tws        where you keep Tile World .tws solution files
    succsave   where SuperCC saves its own solutions

SuperCC creates succsave itself the first time you use any Solution menu item.
Create a tws folder yourself if you want the tws file choosers to open there;
until you do, they open in SuperCC's own folder with "tws" filled into the
file-name box. Nothing breaks either way.


------------------------------------------------------------------------------
5. succ_settings.ini -- HOW THE FILE WORKS
------------------------------------------------------------------------------

It is a plain text file in the classic INI shape: three sections in square
brackets, and "Name = Value" lines under them. Edit it in Notepad or any text
editor. SuperCC reads it ONCE at launch, so any hand edit takes effect the
NEXT TIME YOU START SUPERCC.

Five rules to know before you edit it. The first two bite people.

  (1) THE SPACE BEFORE THE "=" IS REQUIRED.
      The parser reads the key name as everything up to one character before
      the "=", so a line with no space loses its last letter:

          ShowBuildTag = true      <-- correct, and works
          ShowBuildTag=true        <-- read as the key "ShowBuildTa", ignored

      The space AFTER the "=" is optional, and leading and trailing blanks
      around the value are trimmed.

  (2) A ";" ONLY STARTS A COMMENT AT THE BEGINNING OF A LINE.
      There are no end-of-line comments. Everything after the "=" is part of
      the value, semicolon and all:

          ; this whole line is a comment, and is fine
          ShowBuildTag = true ; turn the tag on   <-- the VALUE is
                                                      "true ; turn the tag on"
                                                      which is not "true"

  (3) SECTIONS MATTER. Keys are identified by section plus name, so a setting
      written under the wrong heading is a different setting that nothing
      reads. ShowBuildTag under [Paths] does nothing at all.

  (4) SUPERCC REWRITES THE WHOLE FILE whenever any setting changes -- from the
      value it holds in memory, using a fixed template. Two consequences:
      comments and blank lines you add are erased the first time that happens,
      as is any key SuperCC does not recognize. If you want notes, keep them
      somewhere else. Your VALUES are preserved and only your formatting is
      lost -- with one exception: the three title switches (ShowBuildTag,
      ShowLevelPack and ShowLevelName) are normalized to true or false, so a
      hand-written "1" comes back as "true" (same meaning, different text) and
      anything SuperCC does not recognize comes back as whatever it actually
      read that value as. That is on purpose: the file can then never say one
      thing while the program does another.

  (5) DO NOT EDIT IT WHILE SUPERCC IS RUNNING. A running SuperCC holds the
      whole file in memory and will write its own version back over your edit.
      Close SuperCC, edit, then start it again.

If a line is missing or damaged, SuperCC falls back to that setting's default
rather than failing. If the whole file cannot be read -- most often because
another program has it locked for a moment -- SuperCC tells you so, runs on
defaults for that session, and leaves your file strictly alone rather than
overwriting it. In that state nothing you change is saved, and once you open a
level the title bar ends in "[settings read-only]".


THE ERROR LOG  (new in jc-11)
-----------------------------

If SuperCC ever hits an internal error, it writes the details to a file next to
succ_settings.ini:

    succ_error-YOURPCNAME.log

You will usually never see this file. It is created only when there is
something to record -- a clean session leaves no log at all -- so if it exists,
something went wrong, even if the program carried on working.

Why it exists: double-clicking a .jar runs it under javaw, which throws away
everything a program prints to its error stream. Before jc-11 an error simply
vanished, and "it just closed" was all anyone could report. Now there is a file
to look at, and to quote in a bug report.

  * It is capped at about 512 KB. When it fills up, the old one is renamed to
    succ_error-YOURPCNAME.prev.log and a fresh one is started, so it can never
    grow without limit.
  * The name includes your PC name on purpose. The Chip's Challenge folder is
    often kept in Dropbox and shared between two computers, and a single shared
    log would collide and turn into a "conflicted copy".
  * The first time anything is written, SuperCC tells you once, and names the
    file. It will not nag you again in that session.
  * PASTE ITS CONTENTS, DON'T ATTACH THE FILE. The file name has your PC's
    name in it, and attaching preserves that; pasting does not. The contents
    hold full paths from your own machine -- which means your Windows user
    name, and the folders your level sets live in. Read through it before
    posting it anywhere public.
  * The one failure it CANNOT record is Java being too old to start the program
    at all -- see section 2. That error happens before SuperCC runs, so if the
    program will not start and there is no log, check your Java version first.

You can delete it whenever you like; it is re-created when needed.


------------------------------------------------------------------------------
6. succ_settings.ini -- EVERY SETTING, ONE BY ONE
------------------------------------------------------------------------------

This is the complete stock file. Every key SuperCC knows is here:

    [Paths]
    Levelset =
    TWS = tws
    succ = succsave

    [Controls]
    Up = 38
    Left = 37
    Down = 40
    Right = 39
    HalfWait = 32
    FullWait = 27
    UpLeft = 85
    DownLeft = 74
    DownRight = 75
    UpRight = 73
    AutoDiagonals = false
    Rewind = 8
    Play = 10

    [Graphics]
    TilesheetNum = 0
    LynxTilesheetNum = 0
    TileWidth = 20
    TileHeight = 20
    TWSNotate = false
    CustomBaseImage =
    CustomOverlayImage =
    ShowBuildTag = false
    ShowLevelPack = false
    ShowLevelName = true

    [Emulation]
    AlwaysOpenInMS = false


[Paths]
---------

Levelset        The folder the "Open levelset" chooser starts in.
                Values:  a folder path, or empty for none.
                Default: empty.
                SuperCC UPDATES THIS ITSELF -- it remembers the folder you last
                opened a level set from, so you normally never touch it.
                A folder inside SuperCC's own folder is stored relative to it
                (for example "sets"), which keeps the file portable between
                machines; anywhere else is stored as a full path.

TWS             The folder the two .tws choosers start in -- "TWS > Open tws"
                and "TWS > Write solution to new tws".
                Values:  a folder path, relative to SuperCC's folder or full.
                Default: tws
                SUPERCC UPDATES THIS ITSELF -- every time you open or write
                a .tws it stores the folder you used, so the next chooser
                opens right back where you were. You normally never touch
                this line. Set it by hand to choose where the first chooser
                of a fresh install lands; after that SuperCC keeps it
                current. Clearing the value makes SuperCC read it as "tws"
                again, and it starts tracking again from there.
                A folder inside SuperCC's own folder is stored relative to it
                (for example "tws\CCLP5-MS"), which keeps the file portable
                between machines; anywhere else is stored as a full path.
                jc-8 and jc-9 pinned this value instead of tracking it; that
                is what jc-10 reversed. See section 7.

succ            Where SuperCC saves its own solutions -- one .json file per
                level per ruleset, in a subfolder named after the level set.
                Values:  a folder path.
                Default: succsave
                This one is DATA, not just a chooser's starting point. Change
                it and previously saved solutions are still in the old folder,
                not the new one. The folder is created when it is first needed.


[Controls]
------------

Twelve keys. Each value is a Java virtual-key code -- a NUMBER, not a letter.
Change them the easy way with Tools > Controls in the program, which writes
this section for you. The numbers exist for hand editing.

    Up          Move up.                            Default 38  (arrow up)
    Left        Move left.                          Default 37  (arrow left)
    Down        Move down.                          Default 40  (arrow down)
    Right       Move right.                         Default 39  (arrow right)
    UpLeft      Diagonal. Lynx only -- under MS a   Default 85  (U)
    DownLeft    diagonal is reduced to its          Default 74  (J)
    DownRight   vertical half.                      Default 75  (K)
    UpRight                                         Default 73  (I)
    HalfWait    Wait ONE TICK: half a move under    Default 32  (space)
                MS, a quarter of one under Lynx.
    FullWait    Wait a WHOLE MOVE: 2 ticks under    Default 27  (escape)
                MS, 4 under Lynx.
    Rewind      Step BACK one recorded move.        Default 8   (backspace)
    Play        Step FORWARD one recorded move,     Default 10  (enter)
                back through what you rewound.

Note on FullWait's default of 27: the Controls dialog uses escape as its
"unbound" marker, so it displays that key as "Disabled" rather than as escape.

Useful codes if you are editing by hand: A-Z are 65-90 (A=65 ... Z=90), the
number row 0-9 is 48-57, space 32, enter 10, escape 27, backspace 8, tab 9,
shift 16, control 17, arrows 37 left / 38 up / 39 right / 40 down, and the
numeric keypad 0-9 is 96-105. These are java.awt.event.KeyEvent's VK_ values;
any table of those works.

A value that is not a number is replaced by that key's default at launch.

AutoDiagonals   Whether two arrow keys pressed together make a diagonal move,
                so the four diagonal keys are not needed.
                Values:  true (any casing) turns it ON. ANYTHING ELSE IS
                         OFF -- 1 included -- and is kept as it was, so the
                         file can say 1 while it is off, until Tools >
                         Controls next saves this section (as true/false).
                Default: false
                Set it in the program with Tools > Controls > Auto diagonals,
                which writes this line when the Controls window is closed
                (the diagonal keys' boxes are grayed out while it is on; to
                change one, turn this off, change it, and turn this back on).
                How it works -- upstream's own design, kept exactly since
                jc-23: EVERY game key (arrows, diagonal keys, both waits,
                rewind and play) waits a twentieth of a second. Then ONE move
                is made from the keys still held: the diagonal if two arrows
                of a pair are held, otherwise one held key. In play:
                  - a quick tap, let go within that twentieth, makes NO move;
                  - a held key moves at about half speed;
                  - keys pressed together make one move between them, so a
                    wait pressed with two arrows is lost;
                  - other keys (saving and loading states, macros) and the
                    mouse act at once, AHEAD of a move still waiting;
                  - MS waits too: holding Up and pressing Right moves only
                    Up, since MS has no diagonal moves;
                  - a key let go while another window has the keyboard still
                    counts as held, until you press and let go of it again
                    in the main window.
                These are UB-22 to UB-28 in BASELINE-ISSUES.md in the
                repository, to be fixed again later. With this off, every key
                acts at once, as it always has.
                A build older than jc-18 does not know this line and erases
                it the next time it saves a setting.


[Graphics]
------------

TilesheetNum    Which tile graphics to use under the MS ruleset.
                Values:  0 = Tile World (CCEdit)
                         1 = Tile World
                         2 = MSCC (CCEdit)
                         3 = MSCC
                         4 = MSCC (Black and White - Editor)
                         5 = custom (your own two pictures, CustomBaseImage
                             below)
                Default: 0
                Set it in the program with View > Tileset, which writes this
                line for you. A value that is not a number falls back to 0.
                ⚠ Since jc-22 (upstream's code, on purpose): if this names a
                tileset that cannot be drawn -- a number this version does
                not have, or 5 with its pictures missing or unreadable --
                SUPERCC DOES NOT START: no window appears. Set the line back
                to 0 to 4 by hand. And 5 cannot be read by builds older than
                jc-18 (they do not start either), so on a folder shared
                between computers, keep them on the same build.

LynxTilesheetNum
                The same, for the Lynx ruleset. SuperCC keeps a separate
                choice per ruleset and swaps when you open a Lynx set.
                Values and default: as above. A Lynx choice that cannot be
                drawn does not stop the start; it fails each time a Lynx set
                is opened instead, and the previous tileset stays on screen.

TileWidth       The size of one tile in pixels, and so the size of the game
TileHeight      window. Both are usually the same number.
                Values:  0 to 256. The View > Tile size menu offers 16, 20, 24
                         and 32; View > Tile size > custom takes any value in
                         that range, and width and height can differ.
                Default: 20 and 20.
                Non-numeric falls back to 20x20.

TWSNotate       How the fractional part of the clock is displayed.
                Values:  true or false.
                Default: false
                false shows the time the way the game does. true adds Tile
                World's tws notation next to it -- the fraction counted DOWN
                from the top of the second, as "142 (-.4)" under MS or
                "142 (-.45)" under Lynx (MS shows one fraction digit, Lynx
                two) -- which is what you want when you are comparing against
                tws timings. The
                toggle button "Switch Decimal Notation" in the View menu
                writes this line for you.

CustomBaseImage The two pictures of the custom tileset (TilesheetNum or
CustomOverlayImage  LynxTilesheetNum = 5).
                Values:  a path to an image file -- PNG is what the chooser
                         offers -- or empty for none.
                Default: empty.
                SUPERCC WRITES THESE ITSELF: View > Tileset > custom asks for
                the base image, then the overlay image, and saves both as
                full paths -- before it knows they load. Canceling a chooser
                still opens the next one, and then shows an error.
                A full path names one computer's folders, so if you share
                this folder between computers, a custom tileset chosen on
                one is missing on the other -- and then SuperCC does not
                start there (TilesheetNum, above).
                There is one custom pair, shared by MS and Lynx: choosing
                new images while a Lynx level is open changes the custom
                tileset for MS too.
                Each picture is a sheet in the same layout as the built-in
                ones: 7 tiles across and 16 down, in the same order. The base
                image holds the tiles; the overlay holds what is drawn over
                them, with transparency. Any size works -- SuperCC scales the
                sheet to the tile size in use, so draw it at a multiple of
                7 x 16 tiles to keep it sharp.
                If a picture is missing, very large, or not a picture SuperCC
                can read, choosing it fails with an error that may not name
                the file -- or with an internal error -- after its path was
                already saved; if custom was already your tileset, the next
                start then fails too (TilesheetNum, above). jc-18 to jc-21
                checked all of this first; the checks come back in a later
                release.

ShowBuildTag    Whether the window title shows which build of this fork you
                are running.
                Values:  true or 1 turns it ON. ANYTHING ELSE IS OFF.
                Default: false -- off, including when the key is absent
                         entirely or the file does not exist.
                On:   SuperCC [jc-10] - CCLP5 - Lesson Zero
                Off:  SuperCC - CCLP5 - Lesson Zero
                This is opt-in on purpose: a version number in the title bar
                is useful while the fork is being worked on and just noise
                otherwise. Only the exact values "true" (any capitalization)
                and "1" switch it on, so a typo leaves it off rather than
                turning it on by accident. Remember rule (1) in section 5:
                "ShowBuildTag=true" with no space is silently ignored.
                Turning it off does not hide which build you have -- the tag
                is still a string inside SuperCC.jar.

ShowLevelPack   Whether the window title names the level SET you have open.
                Values:  true or 1 turns it ON. ANYTHING ELSE IS OFF.
                Default: false -- off, including when the key is absent
                         entirely or the file does not exist.
                On:   SuperCC - CCLP5 - Lesson Zero
                Off:  SuperCC - Lesson Zero
                New in jc-14. Off is the way SuperCC has always looked to
                everyone downloading it, so that is what it still does; turn
                it on if you keep several sets open at once and want to tell
                the windows apart at a glance. Like ShowBuildTag it is strictly
                opt-in, so a typo leaves it off.

ShowLevelName   Whether the window title names the LEVEL you are playing.
                Values:  false or 0 turns it OFF. ANYTHING ELSE IS ON.
                Default: true -- on, including when the key is absent
                         entirely or the file does not exist.
                On:   SuperCC - Lesson Zero
                Off:  SuperCC
                New in jc-14. Note that this one reads the OPPOSITE way round
                from every other switch in this file: the others are off unless
                you turn them on, and this one is on unless you turn it off.
                That is deliberate -- it has always been on, so leaving a typo
                or a blank value showing the level name is the safe answer.

                The three switches above are independent, and the title is
                assembled from whichever ones are on -- there is never a
                leftover " - " for a part you switched off:

                    all three on   SuperCC [jc-14] - CCLP5 - Lesson Zero
                    the default    SuperCC - Lesson Zero
                    pack only      SuperCC - CCLP5
                    all three off  SuperCC

                One thing to know if you script against the window title:
                with the stock settings it no longer names the level set, so
                anything matching on "SuperCC - <set name>" needs
                ShowLevelPack = true.


[Emulation]
------------

AlwaysOpenInMS  Whether every level set opens under the MS ruleset, no matter
                what the set itself asks for.
                Values:  true or 1 turns it on. ANYTHING ELSE IS OFF.
                Default: false -- off, including when the key is absent.

                A CC1 .dat states its intended ruleset in the first four bytes
                of the file, and SuperCC honors it: most sets say MS, but some
                say Lynx and therefore open under Lynx. MO3.dat is one of them.
                That is correct behavior, and it is a nuisance if you work
                through sets under MS and have to switch every time.

                Turn this on and every set opens under MS instead. It changes
                only what a set OPENS in:

                  * Level > Change ruleset (F3) still switches freely, both
                    ways, exactly as before.
                  * Loading a solution still switches to whatever ruleset that
                    solution was recorded under. Solutions carry their own, and
                    overriding that would break every Lynx replay.
                  * Sets that already ask for MS are unaffected.

                ONE CONSEQUENCE WORTH KNOWING, because it is easy to mistake
                for lost work: SuperCC files its own solutions one per level
                PER RULESET, named "<number>_<title>-<ruleset>.json". A level
                you open under forced MS therefore saves and looks for
                "...-MS.json", while anything you recorded for that set under
                Lynx is in "...-LYNX.json". Solution > Open will no longer
                preselect those older files, and Solution > Save starts a
                second, parallel set beside them. Nothing is deleted or
                overwritten -- the Lynx files are still there, and turning this
                setting back off (or pressing F3) makes them the default target
                again.

                Related: if you replay a Lynx-recorded .tws against a set that
                forced MS, SuperCC asks whether to switch rulesets, exactly as
                it does for any other mismatch.

                Leave it off if you want each set played the way its author
                intended -- a Lynx set opened under MS is a different game, and
                may not even be solvable.


------------------------------------------------------------------------------
7. REVISION HISTORY
------------------------------------------------------------------------------

Releases from jc-2 on carry a jc-N tag in the repository. The title bar, when
ShowBuildTag is on, shows the build tag compiled into the jar, which matches
the tag for that release. Newest first.


jc-25  --  A trap's state no longer depends on hash order
-------------------------------------------------------------

  * THE SECOND FIX ON TOP OF UPSTREAM. An empty trap is now open at the
    end of a move whenever a brown button wired to it is held. Upstream's
    code could leave it marked shut when it had two or more buttons,
    depending on the order in which a hash map happened to list the
    wires, and always did when one held button was wired to it twice. (A
    button wired to several traps still holds only the first of them, as
    in every SuperCC.) Nothing you play changes: a creature stepping into
    a trap checks its buttons again anyway, so every one of the 24,212
    stored solutions replays exactly as on jc-24. Only the trap state kept
    in savestates, in memory and on disk, could differ.

  * Still there, on purpose until their own fixes come back: two trap
    problems from jc-19 (UB-4 and UB-5 in BASELINE-ISSUES.md in the
    repository).


jc-24  --  Levels with a trap that starts occupied work again
---------------------------------------------------------------

  * THE FIRST FIX ON TOP OF UPSTREAM. jc-19 to jc-23 brought this fork
    back to upstream SuperCC 2.0.3 exactly, the latest official release,
    bugs and all. That baseline is done, and fixes now go back on top of
    it one at a time, so every difference from upstream can be explained.

  * MS LEVELS WITH A TRAP THAT STARTS WITH CHIP, A BLOCK OR AN ICE BLOCK ON
    IT WORK AGAIN. Since jc-19, if such a trap had two or more brown
    buttons wired to it the level would not open at all, and with one
    button the trap started shut. (MSCC and Tile World start such a trap
    open with Chip or a block on it; the MS game has no ice block, and
    SuperCC treats one like a block.) In the maintainer's collection that
    meant:
      - 5 levels open again: EEfor5subs #16, IsaacD1-Old #116, JRB_CCLP1
        #18, TCCLP #110 and TomP3 #66;
      - 6 solved routes reach the exit again: C1059-1 #47, EEfor5subs #26,
        geodave1 #52, geodave4 #7, IsaacD1-Old #121 and TLFC3 #89;
      - 2 unsolved routes end where they did before jc-19 (MO1 #61,
        TCCLPpgchip #2).
    Every other one of the 24,212 stored solutions replays exactly as on
    jc-23. The other three trap problems from jc-19 are still there, and
    are listed in BASELINE-ISSUES.md in the repository (UB-3 to UB-5).


jc-23  --  Auto-diagonals exactly as upstream wrote them, bugs and all
----------------------------------------------------------------------

  * AUTO-DIAGONALS NOW WORK EXACTLY AS IN UPSTREAM SUPERCC v2.0.3 --
    INCLUDING ITS BUGS, ON PURPOSE. This is the last of the five
    places where the fork had changed the upstream code it merged
    (jc-19 says why). jc-18 fixed these; the fixes come back in a
    later release. NOTHING CHANGES UNLESS Tools > Controls > Auto
    diagonals IS ON -- it is off by default, and with it off every key
    acts at once, as it always has. With it on:
      - EVERY GAME KEY WAITS A TWENTIETH OF A SECOND (arrows, diagonal
        keys, both waits, rewind and play), and then ONE move is made
        from the keys still held. So a quick tap makes NO move, a held
        key moves at about half speed, and keys pressed together make
        one move between them: a wait pressed with two arrows is lost,
        and with three arrows held upstream's table order wins, not the
        newest key.
      - Other keys and the mouse act at once, AHEAD of a move still
        waiting: a state saved with Shift+digit is saved without it.
      - MS waits too, though MS has no diagonal moves: holding Up and
        pressing Right moves only Up.
      - A key let go while another window has the keyboard still
        counts as held, until you press and let go of it again in
        SuperCC's main window.
      - The waiting move is made from a background thread, beside the
        one that draws the screen. No problem from that was seen in
        testing.
      - In succ_settings.ini only AutoDiagonals = true turns it on;
        1 is now OFF (jc-18 to jc-22 said 1 was ON).
    Section 6 (AutoDiagonals) describes the setting as it now behaves.

  * Nothing else changes. Every one of the 24,212 stored solutions in
    the maintainer's collection replays exactly as it did on jc-22. The
    rest of this release is in the test suite and the documentation,
    and every problem these upstream changes bring back is listed in
    BASELINE-ISSUES.md in the repository.


jc-22  --  Custom tilesets exactly as upstream wrote them, bugs and all
-----------------------------------------------------------------------

  * CUSTOM TILESETS NOW WORK EXACTLY AS IN UPSTREAM SUPERCC v2.0.3 --
    INCLUDING ITS BUGS, ON PURPOSE. This is the next piece of bringing
    the fork back to upstream's code exactly (jc-19 says why). jc-18
    fixed these; the fixes come back in a later release. Your tileset
    does not change by itself -- all of this starts with choosing
    View > Tileset > custom, or with a settings file that names a
    tileset that cannot be drawn:
      - IF THE SAVED TILESET CANNOT BE DRAWN, SUPERCC DOES NOT START:
        no window appears. That happens when succ_settings.ini names a
        tileset number this version does not have, or the custom
        tileset with its pictures missing. Set TilesheetNum back to a
        number from 0 to 4 by hand (section 6).
      - Choosing custom saves it as tileset number 5, which builds
        older than jc-18 cannot start with, and saves its pictures as
        full paths -- so on a folder shared between computers, a
        custom tileset chosen on one is missing on the other, and
        SuperCC then does not start there.
      - Canceling the first picture still asks for the second, then
        shows an error, and the menu's mark stays on custom.
      - A file that is not a picture is saved before SuperCC finds
        out, and then fails with an internal error; if custom was
        already your tileset, the next start fails too. Other bad
        pictures fail with messages that may not name the file.
      - A Lynx tileset that cannot be drawn shows an error every time
        a Lynx level set is opened, and the previous tileset stays.
    Section 6 (TilesheetNum, CustomBaseImage) describes the settings
    as they now behave.

  * Nothing else changes. Every one of the 24,212 stored solutions in
    the maintainer's collection replays exactly as it did on jc-21. The
    rest of this release is in the test suite and the documentation,
    and every problem these upstream changes bring back is listed in
    BASELINE-ISSUES.md in the repository.


jc-21  --  Before a level set is open: exactly as upstream, bugs and all
------------------------------------------------------------------------

  * THE PROGRAM BEFORE A LEVEL SET IS OPEN, AND A FEW CANCEL BUTTONS,
    NOW WORK EXACTLY AS IN UPSTREAM SUPERCC v2.0.3 -- INCLUDING ITS
    BUGS, ON PURPOSE. This is the next piece of bringing the fork back
    to upstream's code exactly (jc-19 says why). jc-17 fixed these; the
    fixes come back in a later release. Until then:
      - SuperCC starts with no level set open, and until you open one,
        many menu items fail with an internal error instead of doing
        nothing: most of the Solution and TWS menus (Ctrl+S included),
        View > Tileset (the tileset is shown but not remembered for the
        next launch) and Tile size > custom, Tools > Variation testing
        and TSP Solver, the Cheats, and Level > Change stepping value.
        Nothing else happens; each one is written to the error log.
        Open a level set first and they all work as usual.
      - Pressing Cancel in Level > Go to... or Level > Set RNG Seed fails
        the same way, and so does canceling the file chooser of
        Solution > Open, Search for seeds or Load states from disk.
      - A level set whose first level cannot be loaded opens badly:
        after its error, either no level is open, or the level set you
        had open before stays on screen. None of the 331 level sets the
        maintainer has is like that.

  * Nothing else changes. Every one of the 24,212 stored solutions in
    the maintainer's collection replays exactly as it did on jc-20. The
    rest of this release is in the test suite and the documentation,
    and every problem these upstream changes bring back is listed in
    BASELINE-ISSUES.md in the repository.


jc-20  --  Seed Search exactly as upstream wrote it, bugs and all
-----------------------------------------------------------------

  * SEED SEARCH NOW WORKS EXACTLY AS IN UPSTREAM SUPERCC v2.0.3 --
    INCLUDING ITS BUGS, ON PURPOSE. This is the next piece of bringing
    the fork back to upstream's code exactly (jc-19 says why). jc-16
    fixed four problems in Solution > Search for seeds; they are taken
    out again here, and will come back in a later release:
      - A negative starting seed no longer shares the work: one thread
        searches billions of seeds -- far more than were asked for --
        while the others do nothing. That is several times slower with
        a few threads, and dozens of times slower with many.
      - As a search starts, an early thread can finish and mark the
        whole search stopped while the others still run. In that moment
        the button acts as Begin instead of Pause, and pressing it
        starts a second search over the same seeds; the main window's
        controls that a search locks (Restart, Next, Go to..., the move
        keys and others) unlock too. A Pause pressed as a search begins
        can also be ignored. This mostly affects very short searches.
      - A success at a negative seed is counted but never shown as the
        "Example seed".
      - The fourth fix, making sure every search thread sees Pause, has
        no effect you can see.
    A search that starts at 0 or above, is not very short, and is not
    paused in its first instant behaves as before.

  * One more piece goes back to upstream with no effect you can see:
    how a move is recorded when the game both ignores your key and
    moves Chip anyway. No level does that, and every one of the 24,212
    stored solutions in the maintainer's collection records and replays
    exactly as it did on jc-19.

  * The rest of this release is in the test suite and the documentation.
    Every problem these upstream changes bring back is now listed in
    BASELINE-ISSUES.md in the repository, so that each one can be fixed
    later.


jc-19  --  Traps exactly as upstream wrote them, bugs and all
-------------------------------------------------------------

  * TRAPS NOW WORK EXACTLY AS IN UPSTREAM SUPERCC v2.0.3 -- INCLUDING ITS
    BUGS, ON PURPOSE. This fork is going back to upstream's code exactly,
    one piece at a time, so that each fix it makes can then be added back
    on top, separately and visibly. This is the first piece. When jc-15
    brought in upstream's new trap code, this fork fixed four problems in
    it; those four fixes are taken out again here, and will come back in
    a later release.

    What that changes, under the MS ruleset only: a level where Chip, a
    block or an ice block starts on top of a trap.
      - If that trap is wired to two or more brown buttons, the level no
        longer opens: SuperCC says "Could not load level", and Next and
        Previous cannot get past it -- use Level > Go to... to skip over
        it. In the maintainer's collection that is 5 levels:
        EEfor5subs 16, IsaacD1-Old 116, JRB_CCLP1 18, TCCLP 110 and
        TomP3 66.
      - If it is wired to one, the trap starts shut, where MSCC and Tile
        World start it open. A solution that counts on it being open no
        longer reaches the exit: 6 in the collection, on C1059-1 47,
        EEfor5subs 26, geodave1 52, geodave4 7, IsaacD1-Old 121 and
        TLFC3 89. Two more, which never reached it (MO1 61 and
        TCCLPpgchip 2), now end somewhere else.
    A savestate file (.ser) saved by an earlier build may load with a
    trap wrongly open or shut: one saved by jc-15 to jc-18, on a level
    with more than one trap; one saved by jc-14 or earlier, on such a
    level or wherever a trap has two or more brown buttons wired to it.

  * Nothing else changes. The Lynx ruleset is not affected, and every
    other one of the 24,212 stored solutions in the maintainer's
    collection replays exactly as it did on jc-18. The rest of this
    release is in the test suite and the documentation, and changes
    nothing you can see.


jc-18  --  Custom tilesets and auto-diagonals: level with upstream at last
------------------------------------------------------------------------

  * CUSTOM TILESETS. View > Tileset > custom asks for two pictures of
    your own -- the tiles, and the overlay drawn over them -- and draws
    the game with them. Canceling either chooser changes nothing, a file
    that is not a picture is refused with a message instead of being
    saved, and the choice is remembered in a way that works on every
    computer sharing this folder, as long as the pictures are kept inside
    it. Going back to an older build is safe: it starts normally, with
    the tileset you had before choosing custom. Section 6, CustomTileset
    and CustomBaseImage, has the details. This is upstream's feature with
    this fork's fixes.

  * A TILESET THAT CANNOT BE SHOWN NO LONGER STOPS ANYTHING. If the custom
    pictures have been moved or deleted, or succ_settings.ini names a
    tileset this version does not have (which used to stop SuperCC from
    starting properly), the standard Tile World (CCEdit) tileset is shown
    instead, with one message saying why. Your choice is kept, so it comes
    back when the pictures do. Fixed in this fork only.

  * AUTO-DIAGONALS, for Lynx. Tools > Controls > Auto diagonals lets two
    arrow keys pressed together make a diagonal move, instead of needing
    the four diagonal keys. A quick tap counts, holding one arrow and
    pressing another works, and anything else you do -- another key, a
    click, switching windows -- makes the waiting move first, so your
    moves are always recorded in the order you made them. Under MS, which
    has no diagonal moves, arrows behave exactly as before. Off unless
    you turn it on. This is upstream's feature, with its key handling
    rewritten in this fork: as it came, it could record moves out of
    order, lose quick taps, and swallow keys under MS.

  * Nothing else changes how the program behaves. Every one of the 24,212
    stored solutions in the maintainer's collection replays exactly as it
    did on jc-17. With this release the fork contains every change of
    upstream SuperCC v2.0.3, its latest. Behind the scenes, the windows
    built with IntelliJ's designer are now compiled with JetBrains' own
    tool, so the Controls window's new checkbox could be added without
    IntelliJ; that changes nothing you can see.


jc-17  --  No errors from the controls before a level is open
--------------------------------------------------------

  * NOTHING BREAKS BEFORE A LEVEL IS OPEN. SuperCC starts with no level
    set open, and until one was, most of the menus and several controls
    failed with an internal error (since jc-11 each one was written to the
    succ_error log): most of the Level, Solution, TWS and Cheats menus,
    View > Tileset, Tools > Variation testing and TSP Solver, keyboard
    shortcuts such as Ctrl+S, the game keys, the play button and the speed
    slider. On jc-16 that was 46 of the 56 controls in the menus alone.
    Now anything that needs a level simply does nothing until one is
    open, and the things that make sense without one -- opening a level
    set or a tws file, the tile and window sizes, the View toggles and
    Tools > Controls -- work.

    Upstream fixed part of this -- the Level menu, the keys, the play
    button and the slider -- in the change this release brings in, the
    fourth step of bringing its newer commits into this fork. This fork
    extends it to every menu.

  * CANCELING "GO TO..." OR "SET RNG SEED" NO LONGER FAILS. Pressing
    Cancel (or closing the box) in either dialog also hit an internal
    error. Fixed in this fork only.

  * LEVELS WITH NO TITLE OPEN. A level's title is optional in the file
    format, and SuperCC could not open a level without one -- MO3's levels
    12 and 14 are two. They now open with a blank title, which is what
    Tile World shows for them. Every level of all 331 level sets in the
    maintainer's collection now opens. Fixed in this fork only.

  * THE BLACK AND WHITE TILESET WORKS. View > Tileset > "MSCC (Black and
    White - Editor)" has failed with an internal error in every release of
    this fork: one of its two image files was stored under a name that
    differed from the one the program asks for in capital letters only,
    which Windows ignores but a jar does not. Upstream's fix, renaming the
    file, is brought in here.

  * VIEW > TILESET WORKS BEFORE A LEVEL IS OPEN. With no level open, the
    menu used to move its mark to the tileset you picked without drawing
    or saving it. It now applies it, and saves it as the tileset for MS
    levels, which is the one shown before a level is open.

  * A LEVEL THAT CANNOT BE LOADED NO LONGER LEAVES THINGS HALF-LOADED. A
    level whose data is damaged still cannot be opened (none in the
    maintainer's collection is). Going to one used to show the error and
    then leave the program stuck between two levels, so later actions
    could fail or use the wrong level's data. Now you stay on the level you
    were on. And a level set whose FIRST level is like that now opens at
    its first level that works, after one message saying which levels were
    skipped, instead of leaving half of the old set behind. Fixed in this
    fork only.

  * Nothing else changes how the program behaves. Every one of the 24,212
    stored solutions in the maintainer's collection replays exactly as it
    did on jc-16. The main window is now built from this fork's own copy
    of its code, using the tool jc-16 added, and a new test opens the real
    main window and clicks every menu control to prove each one behaves.


jc-16  --  Seed search stops where it should
--------------------------------------------------------

  * SEARCH FOR SEEDS NOW ENDS. Solution > Search for seeds tries every RNG
    seed from the starting seed up to the last one, 2,147,483,647. Until
    now it did not stop there: the counting ran past the top and wrapped
    around to negative numbers, so the search went on forever, retrying
    seeds it had already tried (the game treats a negative seed as one of
    the positive ones) and adding them to the totals. Asked for the last
    3,000 seeds, jc-15 had made 12 million attempts eight seconds later.
    jc-16 makes exactly 3,000 and stops. The success and attempt totals
    also count correctly past 2,147,483,647, where a long search used to
    show them going negative. This is upstream's fix, and the third step
    of bringing its 13 newer commits into this fork.

    Two more fixes, made in this fork only:

    - A NEGATIVE starting seed used to hand all of the work to one thread
      while the others did nothing. The threads now share the work evenly,
      and a seed that works is shown as the example even when it is
      negative (it used to be counted but never shown). Searching from 0
      already tries every seed the game can use.

    - Seed Search could think it had stopped while threads were still
      searching -- whenever one thread finished before the next had even
      started, which a short search does. Pressing Pause at that moment
      started the search a second time over the same seeds, counting them
      twice. Now every thread is ready before any starts, and Pause always
      pauses.

  * Nothing else changes how the program behaves. The emulator itself is
    untouched -- the game engine in this jar is byte-for-byte jc-15's.
    Since Seed Search's window was built with IntelliJ's GUI designer,
    this fork could not rebuild it until now; it gained a tool that can,
    which several of upstream's remaining changes also need. The test
    suite now builds each of the 14 tool windows to prove each one still
    puts itself together, and grew to 2,109 assertions across 24 classes.


jc-15  --  Newer upstream code for traps and Lynx keys, checked
--------------------------------------------------------

  * THE FIRST BUILD WITH NEWER UPSTREAM CODE. This fork started from
    upstream SuperCC as it stood in September 2021, and upstream has 13
    commits since. jc-15 brings in two of those changes, one at a time, and
    each was checked by replaying all 24,204 solutions in the maintainer's
    collection: every one of them ends exactly as it did under jc-14.

  * TRAPS. Upstream changed how SuperCC keeps track of which traps are open.
    As upstream wrote it, the change broke levels where Chip or a block
    starts the level standing on a trap: some would not open at all, and
    others had the trap stuck shut. This build has the change with that bug
    and three smaller ones fixed, so those levels play exactly as in jc-14.

    One thing to know if you use "Save all states to disk" (.ser files): a
    .ser file saved by jc-14 or earlier still loads correctly on any level
    where each trap has a single button wired to it. On a trap with two or
    more buttons wired to it, the trap's open/shut state comes back from the
    first button's record only, so it can come back shut when it was saved
    open. A .ser file saved by jc-15 loads correctly in older builds.
    Solution files (.json and .tws) are not affected: they store no state.

  * LYNX: A KEY THE GAME IGNORES IS RECORDED AS A WAIT. Under Lynx rules,
    a key does nothing while Chip is between two tiles, while ice is
    carrying him, just after a teleport, or on the force floor that first
    grabs him (from the next force floor of the same slide on, he may step
    off). Tile World never records a key like that. SuperCC used to record it anyway; now the move
    list holds a wait there instead. A solution plays exactly as before --
    the game ignored that key before too -- but the move list and a copied
    or saved solution now show what actually happened, and an exported .tws
    file can be smaller. Opening an older Lynx solution re-records it this
    way, and saving it then writes the new form. In the maintainer's
    collection that is 40 of 1,085 Lynx solutions, and every one of them,
    saved again and replayed, still ends exactly the same.

    The one place this can show: a MACRO, or moves copied out of the move
    list, now holds those waits too. Played back from the same spot, they
    do exactly what they did. Played back somewhere a key WOULD have counted
    -- say, on plain floor instead of ice -- they wait there instead of
    moving, where jc-14 would have moved.

    MS is unchanged: every MS key is still recorded as typed, because under
    MS even a key that cannot move Chip still counts for the game.

  * Also since jc-14, and none of it changes how the program behaves: the
    test suite grew to 2,017 assertions across 22 classes, including a check
    that every call between the program's own classes still connects in the
    finished jar, and the replay of the whole collection that checked this
    release is now part of the repository.


jc-14  --  You choose what the window title says
--------------------------------------------------------

  * THE WINDOW TITLE IS NOW YOURS TO SET. It has always been the same three
    things in the same order -- the program name, the level set, the level --
    and the only part you could switch off was the build tag. Two new settings
    in succ_settings.ini finish the job:

        [Graphics]
        ShowLevelPack = false     <-- names the level SET
        ShowLevelName = true      <-- names the LEVEL

    All three switches are independent, and the separators follow whatever is
    left rather than being baked into the format, so you never get a dangling
    " - " for a part you turned off:

        all three on   SuperCC [jc-14] - CCLP5 - Lesson Zero
        the default    SuperCC - Lesson Zero
        pack only      SuperCC - CCLP5
        all three off  SuperCC

    Section 6 documents both settings in full. Note the defaults: the level
    name stays ON, which is how SuperCC has always looked, and the level set
    is OFF -- a change you WILL see, because the stock title no longer names
    the set. Turn ShowLevelPack on to get the old look back. If you script
    against the window title, that is the line to add.

    Upgrading keeps working: a settings file from jc-13 has neither key, and
    SuperCC fills both in with these defaults the next time it writes the file.

  * Also since jc-13, and none of it changes how the program behaves: the test
    suite grew to 1,912 assertions across 20 classes, the vendored third-party
    libraries became tamper-evident, and the developer documentation gained the
    procedure for verifying a test actually catches the bug it claims to.


jc-13  --  A damaged solution file is refused instead of misread
--------------------------------------------------------

  * A .tws SOLUTION FILE THAT HAS BEEN CUT SHORT IS NOW REJECTED. Only the
    four-byte signature was really being checked. Everything after it was
    read past the end of the file, where Java answers -1 rather than
    complaining, so a file holding nothing but its signature was accepted
    and its header length came out as 7 instead of a real number. SuperCC
    then looked for solutions at an offset that meant nothing. The failure
    that produces is "the wrong solution plays back", not an error message,
    which is the worst way for something reading your saved work to fail.

  * A HEADER THAT CLAIMS TO BE LONGER THAN THE FILE is refused too. Every
    field can be present and well-formed and the file still be truncated
    after them; only comparing the two catches it.

  * Both messages say what is wrong in words and give the numbers, instead
    of naming a Java exception.

  * Nothing that was readable before has stopped being readable: all 23,967
    .tws files in the maintainer's own collection were re-opened with the
    new check and every one was accepted.


jc-12  --  Canceling a file dialog no longer breaks the program
--------------------------------------------------------

  * CANCELING "OPEN" NO LONGER THROWS. Solution > Open, Search for seeds,
    and Load states all put up a file chooser. If you pressed Cancel or
    Escape instead of picking a file, SuperCC threw a NullPointerException
    on the event thread. Nothing visibly crashed, but the action silently
    died and the error went into the log. Canceling is now what you would
    expect: nothing happens.

  * ASKING FOR A SOLUTION THAT ISN'T THERE NOW SAYS SO IN WORDS. The file
    chooser fills in the solution filename for the level you are on. If no
    solution has been saved for that level yet, opening it produced a
    message box containing nothing but the full path, and dumped a fifty-
    line Java stack trace into the error log. It now says "There is no
    file:" and names it, and writes nothing to the log -- because being
    asked for a solution you have not recorded yet is ordinary, not a
    fault.

  * These were both found by the error log added in jc-11, which had
    recorded the second one three times across two sessions without anyone
    noticing at the time. That is the log doing exactly the job it was
    added for.


jc-11  --  Errors stop disappearing

--------------------------------------------------------

  * WHEN SOMETHING GOES WRONG, THERE IS NOW A FILE THAT SAYS SO. SuperCC
    writes the details to succ_error-YOURPCNAME.log next to succ_settings.ini,
    and tells you once, naming the file. Section 5 explains it in full.

    What this fixes is not a bug so much as a blind spot. Double-clicking a
    .jar runs it under javaw, and javaw discards everything the program prints
    to its error stream -- so every internal error this program has ever
    reported went nowhere at all. "It just closed" was the most anyone could
    say, and there was nothing to send to anybody. Now there is.

    The file is created only when there is something to write, so a normal
    session leaves nothing behind. It is capped at about 512 KB and rotates
    once, and it carries your PC name so that two computers sharing a Dropbox
    folder cannot collide over it.

  * A HARMLESS ERROR ON EVERY SINGLE LAUNCH IS GONE. Starting SuperCC without
    a level open had always thrown a NullPointerException internally, in the
    code that redraws the window. Nothing broke -- the window had already been
    drawn, and all that was skipped was the play-button icon refresh and one
    panel redraw after it -- and nobody ever saw it, because javaw was throwing
    the message away.

    It had to go before the log could be useful: an error recorded every time
    you started the program would have taught everybody to ignore the log, and
    a log everybody ignores is not worth having.

  * OPENING THE WRONG FILE IS TIDIER. Picking a file that is not a level set
    told you so clearly and then threw an internal error immediately behind the
    message. Now it just tells you, and -- if you already had a set open --
    leaves you where you were instead of jumping you back to its level 1.
    Passing a bad file on the command line no longer leaves the window
    half-built either.

  * THE EMULATOR IS UNTOUCHED, and this was proved rather than assumed. Every
    level of all 286 sets was re-read and every one of the 23,322 saved
    solutions was re-played under jc-11, and the results were compared against
    the same run under jc-10: 45,641 recorded outcomes, identical to the byte.
    Separately, every compiled class in the jar was compared between the two
    builds -- not one file under game/ differs.


jc-10  --  The tws folder remembers where you were again
--------------------------------------------------------

  * THE TWS FOLDER TRACKS THE LAST FOLDER YOU USED, once more. Opening a .tws
    or writing a solution to a new one stores that folder in succ_settings.ini,
    so the next chooser opens right where you left off instead of at the top of
    the tws tree.
    Accomplishes: this puts back the behavior SuperCC had up to jc-7. jc-8 had
    pinned the folder to a fixed starting point on purpose -- with one
    subfolder per level set, always landing in the parent seemed the more
    choice. In practice it was not: when you work in one set for a stretch, the
    chooser having to be re-navigated every single time costs more clicks than
    it saves. So this is a deliberate reversal of the jc-8 change, not a
    regression.
    WHAT THIS MEANS FOR YOUR SETTINGS FILE: the "TWS =" line is now maintained
    by SuperCC and will change as you work. If you had hand-set it to a folder
    you wanted to keep, it will no longer stay put. Nothing else about the file
    changes, and the value is still stored relative to SuperCC's own folder
    when it can be, so a settings file shared between two machines stays valid
    on both.
    The crash jc-8 fixed along the way STAYS fixed: exporting a solution and
    then canceling the save dialog does not throw, and neither does writing to
    a folder that has no parent.


jc-9  --  Open everything under MS, if you want to
--------------------------------------------------

  * NEW SETTING: [Emulation] AlwaysOpenInMS. A .dat declares its intended
    ruleset in its first four bytes, and some sets -- MO3 among them -- declare
    Lynx, so they open under Lynx. Set AlwaysOpenInMS = true and every set opens
    under MS instead. Off by default, so nothing changes unless you ask.
    Accomplishes: no more switching the ruleset by hand on every Lynx-flagged
    set when you are working through a collection under MS. Scoped to the
    ruleset a set OPENS in: F3 still switches, and loading a solution still
    follows that solution's own ruleset, so Lynx replays are untouched.
    This is also the first setting in the new [Emulation] section -- an older
    settings file simply gains that section the next time anything is saved.


jc-8  --  Its own initialization file, a fixed tws folder, and a quiet title
--------------------------------------------------------------------------

  * THE SETTINGS FILE IS NOW CALLED succ_settings.ini, not settings.ini.
    Accomplishes: SuperCC and Tile World live in the same folder here, and
    Tile World is getting an initialization file of its own -- two programs
    sharing one directory must not both claim the generic name "settings.ini".
    UPGRADING FROM jc-7 OR EARLIER: your old settings.ini is NOT read and NOT
    migrated. Rename it to succ_settings.ini to keep your settings, or delete
    it and let SuperCC write a fresh one. If you rename it, fix its "TWS ="
    line too -- see the upgrading note in section 4.

  * THE TWS FOLDER NO LONGER DRIFTS. Opening or exporting a .tws used to write
    that folder back into the settings file, so the value wandered to whatever
    set was touched last and the chooser opened somewhere different every
    session. Now the setting is a fixed starting point that only a hand edit
    changes; it defaults to "tws".
    (SUPERSEDED BY jc-10, which put the remembering back at my request. The
    crash fix noted below is the part that survived.)
    Accomplishes: with one subfolder per level set -- 411 of them in my
    collection -- always landing in the parent is both predictable and fewer
    clicks than landing in an arbitrary sibling. It also retired a real crash:
    exporting a solution and then CANCELING the save dialog used to throw a
    NullPointerException, because the canceled chooser's null result was used
    to compute the folder to remember.

  * THE BUILD TAG IS OFF BY DEFAULT. Up to jc-7 the rule was "anything but an
    explicit off means on", so a fresh download showed "[jc-N]" in its title
    bar until the user found the setting. Now only ShowBuildTag = true (or 1)
    shows it, and absent means off.
    Accomplishes: people I hand this to never see a build number they have no
    use for, while I can still switch it on in my own copy to keep track of
    which build I am running.

  * THIS README, shipped in the download alongside the jar, a stock
    succ_settings.ini and the license text, and updated with every release.


jc-7  --  Three levels that would not open at all
-------------------------------------------------

  Fixed: geodave1 #146 "Ooops! Chip can't swim without flippers!", pi #9
  "bugs", and Rock-Alpha #21 "Mustache" threw an ArrayIndexOutOfBoundsException
  on load and could not be opened under the MS ruleset.

  Cause: their monster-movement list (CC1 optional field 10) contains junk
  entries pointing off the 32x32 map. The loader has two loops over that list,
  one counting and one storing; the storing loop discarded off-map entries
  correctly, but the counting loop used an accessor with no bounds check. A
  second, quieter form of the same bug could mis-count without any exception at
  all and leave a gap that crashed the level constructor.

  Accomplishes: every level of all 273 sets in my collection now opens. The new
  rule is exactly Tile World's -- verified by fingerprinting the monster list
  of all 21,838 levels before and after, where only those three lines changed.


jc-6  --  A settings-path round trip that was not a round trip
--------------------------------------------------------------

  The function that converts a stored path back to a usable one was not the
  exact inverse of the function that stores it: a relative input pointing
  outside SuperCC's folder was stored verbatim and then re-anchored to
  SuperCC's folder on the way out, i.e. resolved to a different directory than
  the caller meant. Not reachable from the shipped program, closed anyway.


jc-5  --  Portable paths
------------------------

  Levelset and TWS were stored as full paths, so a settings file shared
  between two machines with different user names pointed at a folder that did
  not exist on one of them. Folders inside SuperCC's own folder are now stored
  relative to it, the way the "succ" setting always was. Anything outside stays
  a full path, old full paths still work, and there is no migration step.
  Accomplishes: one settings file can be shared across machines.


jc-4  --  The settings file stops being destroyable
---------------------------------------------------

  Three inherited defects, each reproduced before being fixed:

    * ANY read error was treated as "the file does not exist", which triggered
      writing a fresh default file over it. File locks on Windows are
      mandatory, so a settings file held for an instant by a sync client or
      antivirus was genuinely unreadable -- and was destroyed, behind the
      reassuring message "Could not find settings.ini file, creating".
      Now: absent and unreadable are distinguished, a locked file is retried,
      and if it still cannot be read SuperCC runs on defaults for the session
      WITHOUT writing anything, so the real file survives.

    * The write truncated the file at open and could never report a failure,
      so an error mid-write left a zero-byte settings file. Now the new
      contents are written to a temporary file and moved into place, so the
      settings file is only ever replaced by a complete one -- and a failure
      is reported instead of silently swallowed.

    * Keys the file did not mention were saved back as the literal text
      "null", which never repaired itself. Now every key has a known default,
      missing keys are filled in, and an existing "null" is repaired.

  Accomplishes: the settings file survives sync clients, antivirus, a full
  disk, and being killed mid-write.


jc-3  --  The build tag became switchable
-----------------------------------------

  Added the ShowBuildTag setting so the "[jc-N]" tag in the title bar could be
  turned on and off without rebuilding. (In jc-8 its default flipped to off.)


jc-2  --  Exported .tws click solutions pointed at the wrong square
------------------------------------------------------------------

  When a solution containing mouse clicks was exported to .tws, every click
  was encoded relative to Chip's position at the END of the solution instead
  of where he stood when that click happened, because rewinding for the export
  moved an internal cursor without applying the state back to the level.
  Measured: 13 of the 19 click-bearing solutions in my collection exported the
  wrong target cell. Accomplishes: exported click solutions replay correctly.


jc-1  --  The three display mods this fork started as   (untagged: these
--------------------------------------------------------  shipped as commits,
                                                           before tagging began)

  * The level's HINT is shown in the level panel, under "Author", wrapped in
    full however long it is, and hidden on levels that have none.
  * The window title shows the LEVEL SET and the CURRENT LEVEL together
    (SuperCC - CCLP5 - Lesson Zero), updating as you move through the set.
    Upstream showed only the level name.
  * The CLONE and TRAP connection overlays are on by default, matching the
    monster list and slip list overlays, and the "Show Clone Connections" menu
    label's capitalization was fixed.

  All three are display only.


------------------------------------------------------------------------------
8. LICENSE AND CREDITS
------------------------------------------------------------------------------

SuperCC is free software under the GNU General Public License, version 2 or
later. The full text is in COPYING, included in this download. That applies to
this fork exactly as it does to the original: you may use, study, share, and
modify it, and any version you pass on must carry the same freedoms and its
source.

Credit for SuperCC itself belongs to its upstream authors at
https://github.com/SicklySilverMoon/SuperCC -- this fork is a small set of
changes on top of their work. Chip's Challenge is the property of its
respective owners; no game data is included in this download.

Bug reports and questions about THIS FORK belong at
https://github.com/JeremyChristman/SuperCC/issues -- please do not take them
upstream, since the changes above are not theirs.
