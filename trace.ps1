<#
Differential trace: run one solution through SuperCC and diff it against Tile World, tick by tick.

WHY THIS IS COMMITTED
---------------------
Every test in test\ compares SuperCC against a TRANSCRIPTION of Tile World's rules. This compares
the two ENGINES on a real level and pins the first tick where they disagree. It is the instrument
that took the desync count from 135 to 0.

The previous version of it lived in a scratch folder and was lost. Tile World's half survived only
because it sits inside mslogic.c behind -DTRACE_DESYNC. This half now lives in the repo so that
cannot happen again.

WHAT IT NEEDS THAT THE REPO DOES NOT HAVE
-----------------------------------------
A level set and a solution, neither of which may be committed here (docs\adr\0007). So this is a
LOCAL, ON-DEMAND tool -- it is not part of CI and never will be. It follows the same
skip-when-absent shape as run-tests.ps1's -Collection switch.

  # SuperCC side only -- always available
  powershell -File trace.ps1 -Dat "<CC>\data\CCLP5.dat" -Level 138 -Solution "<CC>\succsave\CCLP5\138_x-MS.json"

  # both engines, and diff them
  powershell -File trace.ps1 -Dat ... -Level ... -Solution ... -TWTrace "<tw-stderr>.txt"

  # compare two existing traces without re-running anything
  powershell -File trace.ps1 -Compare -SccTrace scc.txt -TWTrace tw.txt -Level 138

PRODUCING THE TILE WORLD SIDE
-----------------------------
Tile World must be built with -DTRACE_DESYNC, then run over the SET with the level selected by an
environment variable, and its STDERR captured:

  $env:TW_TRACE_LEVEL = "138"        # comma-separated list also accepted
  $env:TW_TRACE_TICK_LO = "600"      # optional window
  $env:TW_TRACE_TICK_HI = "900"
  .\tworld2.exe -r -p -S "<CC>\save" CCLP5.dat-ms.dac  2> tw.txt

Both engines emit the same two line types, tab separated:

  T <lvl> <tick> <rng> chip=<x>,<y>,<slip>  C:<letter>,<x>,<y>,<dir> ...  B:<x>,<y> ...
  Q <lvl> <tick> Q:<letter>,<x>,<y>,<dir> ...

Positions are grid x,y and directions are N/W/S/E on purpose: the engines encode both differently,
and without normalizing them the diff is blind to a creature standing in the right place facing the
wrong way.
#>
param(
    [string]$Dat,
    [int]$Level,
    [string]$Solution,
    [string]$SccTrace,
    [string]$TWTrace,
    [switch]$Compare,
    [string]$Jar = "SuperCC.jar",
    [int]$Context = 3
)

# Native tools write notes to stderr; under "Stop" PowerShell 5.1 turns those into terminating
# NativeCommandErrors even on success. Exit codes are checked explicitly instead.
$ErrorActionPreference = "Continue"
$root = $PSScriptRoot
. (Join-Path $root "build-config.ps1")

function Fail($msg) { Write-Host "  ERROR  $msg"; exit 1 }

# ---------------------------------------------------------------- produce the SuperCC trace
if (-not $Compare) {
    if (-not $Dat)      { Fail "-Dat is required (a .dat level set; not in this repo)" }
    if (-not $Solution) { Fail "-Solution is required (a succsave .json)" }
    if (-not (Test-Path $Dat))      { Fail "no level set at $Dat" }
    if (-not (Test-Path $Solution)) { Fail "no solution at $Solution" }

    $jdkBin = Find-JdkBin -Requires "javac.exe"
    $jarPath = if ([IO.Path]::IsPathRooted($Jar)) { $Jar } else { Join-Path $root $Jar }
    if (-not (Test-Path $jarPath)) { Fail "no jar at $jarPath -- run build.ps1 first" }

    $out = Join-Path $env:TEMP ("scc-trace-" + [Guid]::NewGuid().ToString("N"))
    New-Item -ItemType Directory -Force -Path $out | Out-Null
    try {
        & (Join-Path $jdkBin "javac.exe") -encoding UTF-8 -cp $jarPath -d $out `
            (Join-Path $root "trace\TraceLevel.java")
        if ($LASTEXITCODE -ne 0) { Fail "could not compile TraceLevel (exit $LASTEXITCODE)" }

        if (-not $SccTrace) { $SccTrace = Join-Path $root ("trace-scc-{0}.txt" -f $Level) }
        & (Join-Path $jdkBin "java.exe") "-Djava.awt.headless=true" -cp "$jarPath;$out" `
            TraceLevel $Dat $Level $Solution > $SccTrace
        if ($LASTEXITCODE -ne 0) { Fail "TraceLevel failed (exit $LASTEXITCODE)" }
    } finally {
        if (Test-Path $out) { [IO.Directory]::Delete($out, $true) }
    }

    $lines = @(Get-Content $SccTrace)
    Write-Host ("SuperCC trace: {0}  ({1} lines, {2} ticks)" -f $SccTrace, $lines.Count,
                (@($lines | Where-Object { $_ -like "T`t*" })).Count)
    if (-not $TWTrace) {
        Write-Host ""
        Write-Host "No -TWTrace given, so nothing to diff against. To produce the other side:"
        Write-Host "  build Tile World with -DTRACE_DESYNC, then"
        Write-Host ("  `$env:TW_TRACE_LEVEL = `"{0}`"; .\tworld2.exe -r -p -S <save> <set>.dac 2> tw.txt" -f $Level)
        exit 0
    }
}

# ---------------------------------------------------------------- align and diff
if ($Compare) {
    if (-not $SccTrace) { Fail "-SccTrace is required with -Compare" }
}
if (-not (Test-Path $SccTrace)) { Fail "no SuperCC trace at $SccTrace" }
if (-not (Test-Path $TWTrace))  { Fail "no Tile World trace at $TWTrace" }

<#
Keeps only this level's lines and indexes them by tick.

Tile World's stderr from a batch run holds every level it replayed plus whatever else it printed,
so the trace has to be split out rather than read whole. Indexing by TICK rather than by line
number matters: the two engines do not necessarily emit the same NUMBER of lines -- one may stop
early -- and comparing line N to line N would then report every tick after the first difference as
different too, burying the one that matters.
#>
function Read-Trace {
    param([string]$Path, [int]$LevelNumber, [string]$Kind)

    $byTick = @{}
    foreach ($line in [IO.File]::ReadLines((Resolve-Path $Path))) {
        if (-not $line.StartsWith($Kind + "`t")) { continue }
        $f = $line -split "`t"
        if ($f.Count -lt 3) { continue }
        if ([int]$f[1] -ne $LevelNumber) { continue }
        $tick = [int]$f[2]
        # A level replayed more than once in one run: keep the FIRST pass, which is the one the
        # other engine's single pass lines up with.
        if (-not $byTick.ContainsKey($tick)) { $byTick[$tick] = $line }
    }
    return $byTick
}

$sccT = Read-Trace -Path $SccTrace -LevelNumber $Level -Kind "T"
$twT  = Read-Trace -Path $TWTrace  -LevelNumber $Level -Kind "T"
$sccQ = Read-Trace -Path $SccTrace -LevelNumber $Level -Kind "Q"
$twQ  = Read-Trace -Path $TWTrace  -LevelNumber $Level -Kind "Q"

if ($sccT.Count -eq 0) { Fail "no T lines for level $Level in $SccTrace" }
if ($twT.Count -eq 0)  { Fail "no T lines for level $Level in $TWTrace -- was TW_TRACE_LEVEL set to $Level, and was it built with -DTRACE_DESYNC?" }

Write-Host ""
Write-Host ("level {0}:  SuperCC {1} ticks, Tile World {2} ticks" -f $Level, $sccT.Count, $twT.Count)

# The RNG column is informational: the engines share one generator, so a divergence in it is a
# SYMPTOM of a different draw count rather than a cause. Compared separately so a creature
# difference is never buried under it.
function Split-Line { param([string]$Line) $f = $Line -split "`t"; return $f }

$ticks = @($sccT.Keys) + @($twT.Keys) | Sort-Object -Unique
$firstDiff = $null
$rngFirstDiff = $null
foreach ($tick in $ticks) {
    $a = $sccT[$tick]; $b = $twT[$tick]
    if ($null -eq $a) { $firstDiff = @{ tick = $tick; why = "SuperCC has no tick $tick (its trace ends earlier)" }; break }
    if ($null -eq $b) { $firstDiff = @{ tick = $tick; why = "Tile World has no tick $tick (its trace ends earlier)" }; break }

    $fa = Split-Line $a; $fb = Split-Line $b
    # Compare everything except the RNG value first.
    $aState = ($fa[4..($fa.Count-1)] -join "`t").TrimEnd()
    $bState = ($fb[4..($fb.Count-1)] -join "`t").TrimEnd()
    if ($null -eq $rngFirstDiff -and $fa[3] -ne $fb[3]) { $rngFirstDiff = $tick }
    if ($aState -ne $bState) { $firstDiff = @{ tick = $tick; why = "creature or block state differs" }; break }

    $qa = $sccQ[$tick]; $qb = $twQ[$tick]
    if ($qa -and $qb) {
        $qaS = ((Split-Line $qa)[3..99] -join "`t").TrimEnd()
        $qbS = ((Split-Line $qb)[3..99] -join "`t").TrimEnd()
        if ($qaS -ne $qbS) { $firstDiff = @{ tick = $tick; why = "SLIP LIST differs (same positions, different order or direction)" }; break }
    }
}

if ($null -eq $firstDiff) {
    Write-Host "  IDENTICAL across every shared tick"
    if ($rngFirstDiff -ne $null) {
        Write-Host ("  note: the RNG value first differs at tick {0} even though state matches" -f $rngFirstDiff)
    }
    Write-Host ""
    exit 0
}

$t = $firstDiff.tick
Write-Host ""
Write-Host ("  FIRST DIVERGENCE at tick {0} -- {1}" -f $t, $firstDiff.why)
if ($rngFirstDiff -ne $null -and $rngFirstDiff -lt $t) {
    Write-Host ("  the RNG had already diverged at tick {0}, so look for a different DRAW COUNT before this tick" -f $rngFirstDiff)
}
Write-Host ""
foreach ($tick in ($ticks | Where-Object { $_ -ge ($t - $Context) -and $_ -le ($t + $Context) })) {
    $mark = if ($tick -eq $t) { ">>" } else { "  " }
    Write-Host ("{0} tick {1}" -f $mark, $tick)
    if ($sccT[$tick]) { Write-Host ("     SCC {0}" -f (($sccT[$tick] -split "`t")[3..99] -join "  ")) }
    if ($twT[$tick])  { Write-Host ("     TW  {0}" -f (($twT[$tick]  -split "`t")[3..99] -join "  ")) }
    if ($sccQ[$tick] -and $twQ[$tick]) {
        $qa = (($sccQ[$tick] -split "`t")[3..99] -join "  ")
        $qb = (($twQ[$tick]  -split "`t")[3..99] -join "  ")
        if ($qa -ne $qb) {
            Write-Host ("     SCC {0}" -f $qa)
            Write-Host ("     TW  {0}" -f $qb)
        }
    }
}
Write-Host ""
exit 1
