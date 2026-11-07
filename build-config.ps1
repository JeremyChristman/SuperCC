<#
Shared build configuration. Dot-sourced by build.ps1 and verify-splice.ps1.

This file exists so the splice list has exactly ONE definition. build.ps1 uses it to decide what to
recompile; verify-splice.ps1 uses it to decide what must NOT have been edited. Two copies of the
list would drift, and the failure that causes is silent: an edited file missing from the build's
list ships the old behavior, and a verifier working from a stale copy would not notice.

See docs\adr\0001-splice-build-not-full-recompile.md.
#>

# Hand-edited source files that build.ps1 recompiles and splices over the committed baseline.
# Paths are relative to java\. NONE of these may have a sibling .form file -- verify-splice.ps1
# asserts that, because javac cannot reproduce a form class's generated $$$setupUI$$$() method.
#
#   --> Modifying a source file NOT in this list silently does nothing: the build succeeds and
#       ships the old bytecode. Add it here, then run verify-splice.ps1.
$SPLICE_MODIFIED = @(
    "emulator\DiagonalCombiner.java",
    "emulator\EmulatorKeyListener.java",
    "emulator\SuperCC.java",
    "game\Level.java",
    "game\Lynx\LynxLevel.java",
    "game\MS\MSCreature.java",
    "game\MS\MSLevel.java",
    "game\button\BrownButton.java",
    "graphics\LevelPanel.java",
    "graphics\MenuBar.java",
    "graphics\GamePanel.java",
    "graphics\SmallGamePanel.java",
    "graphics\TileSheet.java",
    "io\ErrorLog.java",
    "io\LevelFactory.java",
    "io\SuccPaths.java",
    "io\TWSWriter.java",
    "io\TWSReader.java"
)

# Everything staged into the jar: the committed IntelliJ .class baseline, the third-party bytecode,
# the resources, the bundled source tree, and the manifest.
$SPLICE_STAGE_DIRS = @(
    "com","org","emulator","game","graphics","io","tools","util","resources","java","META-INF"
)

# Source files with no compiled class in the baseline, and none expected.
# FullscreenGamePanel is shipped as source inside the jar (upstream does that for every file) but
# was never compiled into the build -- verified against both the baseline tree and the jar's own
# entry list. It is dead source, not a missing class, so the verifier skips it rather than failing.
$SPLICE_SOURCE_ONLY = @(
    "graphics\FullscreenGamePanel.java"
)

# javac settings. --release 16 keeps bytecode compatible with the IntelliJ-built baseline even when
# a newer JDK is doing the compiling; -encoding UTF-8 is required because SuperCC.java contains
# Unicode arrow characters.
$SPLICE_RELEASE  = "16"
$SPLICE_ENCODING = "UTF-8"

# Debug flag used by verify-splice.ps1 ONLY, to match how the committed baseline was compiled.
#
# IntelliJ builds with full debug info (-g) by default, and that is not cosmetic: with -g, javac
# keeps `final int NORTH = 1, WEST = 2, ...` alive as real locals so they can appear in the
# LocalVariableTable, while without -g it constant-folds them away and emits no stores at all.
# TWSReader$twsInputStream.readFormat4 declares exactly that pattern, so a verifier compiling
# without -g reports a 16-instruction difference that is purely a flag mismatch -- measured here,
# and it is the only file in the tree where the two disagree.
#
# The build itself deliberately does NOT pass -g: the spliced classes ship with javac's default
# debug info, which is what every release so far has contained. Changing that would alter the
# shipped bytecode of all seven spliced files for no functional gain.
$SPLICE_VERIFY_DEBUG = "-g"

<#
Resolves a real JDK bin directory.

The Oracle "javapath" shim that lands on PATH exposes javac.exe and java.exe but NOT jar.exe, so
"is javac on PATH" is not a usable test. $JAVA_HOME wins when it is set and complete.

The directory-scan fallback sorts by PARSED VERSION, not by name: a plain string sort puts jdk-9
above jdk-17, which is the wrong JDK and a confusing failure. Anything that does not parse sorts
last rather than being discarded, so an oddly named JDK is still usable if it is the only one.

The version pattern needs the `*`, not a `+`: requiring at least one dot would fail to match the
dotless directory names Temurin and Adoptium actually use (`jdk-21`, `jdk-17`), leaving them parsed
as 0.0 and sorted BELOW `jdk-9.0.4` -- reintroducing the exact bug this function exists to avoid.
A bare major version is normalized to `<major>.0` because [version] rejects a single number.
#>
function Find-JdkBin {
    param([string]$Requires = "jar.exe")

    if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME "bin\$Requires"))) {
        return (Join-Path $env:JAVA_HOME "bin")
    }
    $cand = Get-ChildItem "C:\Program Files\Java" -Directory -ErrorAction SilentlyContinue |
            Where-Object { Test-Path (Join-Path $_.FullName "bin\$Requires") } |
            Sort-Object -Descending {
                $v = [version]"0.0"
                if ($_.Name -match '(\d+(\.\d+)*)') {
                    $text = $Matches[1]
                    if ($text -notmatch '\.') { $text = "$text.0" }
                    [void][version]::TryParse($text, [ref]$v)
                }
                $v
            } | Select-Object -First 1
    if ($cand) { return (Join-Path $cand.FullName "bin") }
    throw "No JDK with $Requires found. Set JAVA_HOME to a JDK 16 or newer."
}

<#
Runs robocopy and throws on a real failure.

robocopy's exit code is a bit field where 0-7 all mean SUCCESS (1 = files copied, 2 = extra files,
4 = mismatched files, and combinations). Only 8 and above are genuine failures. Testing it the
ordinary way would fail every successful build; ignoring it entirely -- which this build did until
now -- means a partial copy produces a jar with classes missing that still builds and still passes
the tests. Both directions are wrong, so the threshold is explicit.

$LASTEXITCODE is reset afterwards because GitHub Actions appends `exit $LASTEXITCODE` to every
step: a leftover robocopy 1 would fail an otherwise successful step.
#>
function Invoke-Robocopy {
    param([Parameter(Mandatory)][string]$Source, [Parameter(Mandatory)][string]$Destination)

    robocopy $Source $Destination /E /NFL /NDL /NJH /NJS /NP | Out-Null
    $code = $LASTEXITCODE
    $global:LASTEXITCODE = 0
    if ($code -ge 8) { throw "robocopy failed copying $Source -> $Destination (exit $code)" }
}

<#
Returns a directory's canonical full path, for use as a substring base. Shared by verify-splice.ps1
and formclass.ps1.

GitHub's Windows runners set TEMP to an 8.3 short path (C:\Users\RUNNER~1\AppData\Local\Temp), while
Get-ChildItem reports FullName in long form. Subtracting the short length from a long path cuts in
the wrong place -- CI reported paths like "87\emulator\ArgumentParser.class", the tail of a GUID
directory name leaking into what should have been a package-relative path.
#>
function Get-CanonicalDir {
    param([string]$Path)
    return (Get-Item -LiteralPath $Path).FullName.TrimEnd('\')
}

<#
Builds the comparison key for one class: its disassembly, plus the SORTED SET of String constants.
Shared by verify-splice.ps1 (ordinary files) and formclass.ps1 (form classes), so "the same
bytecode" means one thing in both.

Neither half is sufficient on its own, and both failure modes were measured on this tree:

  * `javap -c -p -constants` alone is stable across compilers (70 of 70 files matched) but BLIND to
    string literals. Java 9+ lowers string concatenation to an invokedynamic whose literal lives in
    the BootstrapMethods attribute, which -c does not print -- so changing a message string inside
    a concatenation produced an identical disassembly and slipped straight through.
  * Raw class bytes catch everything but are useless here: 68 of 70 files differ on constant-pool
    ordering alone, because the baseline came from IntelliJ and the comparison build comes from
    javac.

Sorting the String entries drops pool ORDER, which is the part that legitimately varies between
compilers, and keeps the string CONTENT, which is the part an edit changes.

-IgnorePoolIndexes (formclass.ps1) also drops the `#N` an instruction names, and the column padding
that shifts with its width. IntelliJ numbered the pool differently in the three forms that use
$$$getFont$$$, though their code is identical. javap prints what each index resolves to in a
trailing `//` comment, and that comment is kept, as is any operand after the index. (Like the key
without the switch, it never compared stack-map frames, which javap -c does not print.)

Uses the caller's $javap (both scripts define it before calling).
#>
function Get-ClassKey {
    param([string]$ClassDir, [string]$Fqcn, [switch]$IgnorePoolIndexes)

    # javap's exit code is checked because this whole script is a silent-failure detector and
    # ignoring it would put one inside: if javap failed on BOTH sides -- a corrupt or unreadable
    # class -- the two keys would both be the same empty string and the file would pass.
    $lines = & $javap -c -p -constants -cp $ClassDir $Fqcn
    if ($LASTEXITCODE -ne 0) { throw "javap could not read $Fqcn from $ClassDir (exit $LASTEXITCODE)" }
    if ($IgnorePoolIndexes) {
        # Only the `#N` itself. What may follow it -- `invokeinterface #N,  1`, `multianewarray #N,  2`
        # -- is kept: multianewarray's dimension count is NOT in the `//` comment.
        $lines = $lines | ForEach-Object { ($_ -replace '#\d+(?=(,\s*\d+)?\s+//)', '#') -replace '\s+', ' ' }
    }
    $code = $lines -join "`n"
    $verbose = & $javap -v -p -cp $ClassDir $Fqcn
    if ($LASTEXITCODE -ne 0) { throw "javap -v could not read $Fqcn from $ClassDir (exit $LASTEXITCODE)" }
    $strings = @($verbose |
                 Select-String -Pattern '=\s+String\s+#\d+\s+//\s*(.*)$' |
                 ForEach-Object { $_.Matches[0].Groups[1].Value } |
                 Sort-Object)
    return ($code.Trim() + "`n--STRINGS--`n" + ($strings -join "`n"))
}
