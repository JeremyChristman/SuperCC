==============================================================================
  SuperCC  --  Jeremy Christman's fork                      build jc-11
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

The emulation engine is deliberately NOT modified. No change in this fork
alters movement, the random number generator, or the outcome of an existing
solution. That is verified before each release by replaying solutions and
confirming they produce identical tick-by-tick results.

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
      lost -- with one exception: ShowBuildTag is normalized to true or false,
      so a hand-written "1" comes back as "true" (same meaning, different text).

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
    Rewind = 8
    Play = 10

    [Graphics]
    TilesheetNum = 0
    LynxTilesheetNum = 0
    TileWidth = 20
    TileHeight = 20
    TWSNotate = false
    ShowBuildTag = false

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


[Graphics]
------------

TilesheetNum    Which tile graphics to use under the MS ruleset.
                Values:  0 = Tile World (CCEdit)
                         1 = Tile World
                         2 = MSCC (CCEdit)
                         3 = MSCC
                         4 = MSCC (Black and White - Editor)
                Default: 0
                Set it in the program with View > Tileset, which writes this
                line for you. A value that is not a number falls back to 0,
                but STAY INSIDE 0-4 when editing by hand: a number outside
                that range is used as a list index and stops SuperCC from
                starting up properly. (Inherited from upstream.)

LynxTilesheetNum
                The same, for the Lynx ruleset. SuperCC keeps a separate
                choice per ruleset and swaps when you open a Lynx set.
                Values and default: as above.

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
