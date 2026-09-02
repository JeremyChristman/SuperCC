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
    "emulator\SuperCC.java",
    "graphics\LevelPanel.java",
    "graphics\MenuBar.java",
    "graphics\GamePanel.java",
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
