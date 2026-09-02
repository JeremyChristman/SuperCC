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

PRODUCING THE TILE WORLD SIDE -- the recipe below is VERIFIED, the previous one was not
---------------------------------------------------------------------------------------
  cmake -S <tworld> -B <build> -G Ninja -DCMAKE_BUILD_TYPE=Release "-DCMAKE_C_FLAGS=-DTRACE_DESYNC"
  cmake --build <build>

Then run it in BATCH VERIFY mode with the level selected by an environment variable, capturing
stderr:

  $env:TW_TRACE_LEVEL = "138"        # comma-separated list also accepted
  $env:TW_TRACE_TICK_LO = "600"      # optional window
  $env:TW_TRACE_TICK_HI = "900"
  .\tworld2.exe -b -r -q CCLP5.dat-ms.dac  2> tw.txt

-b is BATCH VERIFY and is the flag that makes it replay without a window. This file previously
documented `-r -p`, which is read-only plus a password toggle: it opens the GUI and sits there, so
the trace never appears. Run it from a directory containing data\, sets\ and save\ -- pointing -D
and -R at a path with a SPACE in it truncated the argument and made it scan the wrong sets folder,
so a scratch directory with no spaces is the reliable arrangement, and it also keeps Tile World
from writing anything near the real save files.

  powershell -File trace.ps1 -SelfTest      # checks the aligner with no level set at all

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
    [switch]$SelfTest,
    [string]$Jar = "SuperCC.jar",
    [int]$Context = 3
)

# Native tools write notes to stderr; under "Stop" PowerShell 5.1 turns those into terminating
# NativeCommandErrors even on success. Exit codes are checked explicitly instead.
$ErrorActionPreference = "Continue"
$root = $PSScriptRoot
. (Join-Path $root "build-config.ps1")

function Fail($msg) { Write-Host "  ERROR  $msg"; exit 1 }

<# ---------------------------------------------------------------- self-test

   The alignment logic shipped BROKEN for its whole life, and the reason is simply that nothing ever
   ran it: it needs a level set and a solution, neither of which may live in this repo, so it was
   committed and never exercised. Run against a level where the two engines agree perfectly it
   reported a divergence at tick 0.

   This builds both sides of a tiny synthetic trace -- no level set, no Tile World, no jar -- and
   checks the two outcomes that matter: that agreement reads as agreement, and that a one-creature
   difference is still pinned to the right place. It costs a second and it runs in CI, so the
   comparison cannot rot unnoticed again.

   It deliberately reproduces the exact shape that broke the old aligner: Tile World emitting four
   ticks per state, SuperCC emitting one per move on a half-move clock, and SuperCC carrying an
   extra pre-move line at the front. #>
if ($SelfTest) {
    $td = Join-Path $env:TEMP ("scc-tracetest-" + [Guid]::NewGuid().ToString("N"))
    New-Item -ItemType Directory -Force $td | Out-Null
    try {
        $states = @(
            "chip=10,14,0`tC:U,5,5,N`tB:",
            "chip=10,15,0`tC:U,5,6,N`tB:",
            "chip=11,15,0`tC:U,6,6,E`tB:"
        )
        # Tile World: one line per engine tick, four ticks per state, numbering from 0.
        $tw = New-Object System.Collections.ArrayList
        $tick = 0
        foreach ($st in $states) {
            for ($k = 0; $k -lt 4; $k++) {
                [void]$tw.Add("T`t7`t$tick`t999`t$st")
                [void]$tw.Add("Q`t7`t$tick`tQ:")
                $tick++
            }
        }
        # SuperCC: a pre-move line, then one per move on a half-move clock.
        $scc = New-Object System.Collections.ArrayList
        [void]$scc.Add("T`t7`t0`t999`tchip=10,13,0`tC:U,5,5,N`tB:")
        [void]$scc.Add("Q`t7`t0`tQ:")
        $t2 = 2
        foreach ($st in $states) {
            [void]$scc.Add("T`t7`t$t2`t999`t$st")
            [void]$scc.Add("Q`t7`t$t2`tQ:")
            $t2 += 2
        }
        $twFile  = Join-Path $td "tw.txt"
        $sccFile = Join-Path $td "scc.txt"
        $badFile = Join-Path $td "scc-bad.txt"
        $enc = New-Object Text.UTF8Encoding($false)
        [IO.File]::WriteAllLines($twFile,  $tw,  $enc)
        [IO.File]::WriteAllLines($sccFile, $scc, $enc)
        # One creature moved one square, in the middle state.
        $bad = @($scc | ForEach-Object { $_ })
        for ($i = 0; $i -lt $bad.Count; $i++) {
            if ($bad[$i] -like "T`t7`t4`t*") { $bad[$i] = $bad[$i].Replace("U,5,6,N", "U,5,7,N") }
        }
        [IO.File]::WriteAllLines($badFile, $bad, $enc)

        $self = $PSCommandPath
        $ok = $true

        & powershell -ExecutionPolicy Bypass -File $self -Compare -SccTrace $sccFile -TWTrace $twFile -Level 7 | Out-Null
        if ($LASTEXITCODE -ne 0) { Write-Host "  FAIL  agreeing traces were reported as a divergence"; $ok = $false }
        else { Write-Host "  PASS  agreeing traces align despite different clocks" }

        $out = @(& powershell -ExecutionPolicy Bypass -File $self -Compare -SccTrace $badFile -TWTrace $twFile -Level 7)
        if ($LASTEXITCODE -eq 0) { Write-Host "  FAIL  an injected divergence was NOT detected"; $ok = $false }
        elseif (-not ($out -match 'FIRST DIVERGENCE at state change 1\b')) {
            Write-Host "  FAIL  the divergence was found at the wrong place:"
            $out | Where-Object { $_ -match 'DIVERGENCE' } | ForEach-Object { Write-Host "        $_" }
            $ok = $false
        }
        else { Write-Host "  PASS  a one-creature divergence is pinned to the right state change" }

        if ($ok) { Write-Host "  trace alignment self-test: all green"; exit 0 }
        exit 1
    } finally {
        if (Test-Path $td) { Remove-Item -LiteralPath $td -Recurse -Force -ErrorAction SilentlyContinue }
    }
}

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
        <# Captured and written explicitly rather than redirected with `>`.

           PowerShell 5.1's `>` is Out-File, which re-encodes to UTF-16LE with a BOM. TraceLevel.java
           deliberately builds a UTF-8 PrintStream, and the redirect threw that away: the trace came
           out as UTF-16 and ordinary tools -- grep, diff, an editor doing a byte compare -- could not
           read it, while Tile World's half is written as plain bytes by fprintf. A diagnostic file
           that only this script can read is most of the way to useless. #>
        $traceLines = @(& (Join-Path $jdkBin "java.exe") "-Djava.awt.headless=true" -cp "$jarPath;$out" `
                          TraceLevel $Dat $Level $Solution)
        if ($LASTEXITCODE -ne 0) { Fail "TraceLevel failed (exit $LASTEXITCODE)" }
        [IO.File]::WriteAllLines($SccTrace, $traceLines, (New-Object Text.UTF8Encoding($false)))
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
        Write-Host ("  `$env:TW_TRACE_LEVEL = `"{0}`"; .\tworld2.exe -b -r -q <set>.dac 2> tw.txt" -f $Level)
        Write-Host "  -b is BATCH VERIFY, which is what replays without a window. -r is read-only,"
        Write-Host "  -q silences the sound init. Run it from a directory holding data\ sets\ save\."
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
ALIGNMENT: BY STATE CHANGE, NOT BY TICK NUMBER.

This is the part that was wrong, and it was wrong in the way that matters most: run against a level
where the two engines agree perfectly, the old aligner reported a divergence at tick 0.

The two halves do not share a clock.

  * Tile World emits once per ENGINE tick, numbering 0,1,2,3..., and an MS move spans four of them,
    so each state appears four times running.
  * TraceLevel emits once per MOVE, and SuperCC's MS tick number counts HALF-moves, so it produces
    0,2,4,6... -- with an occasional step of 1 where a move resolves in a single half-tick, which
    makes even the ratio non-constant.
  * TraceLevel additionally emits the PRE-MOVE state first. mslogic.c's trace point is at the END of
    advancegame(), so that opening line has no counterpart at all.

Measured on CCLP1 #6: SuperCC 114 lines, Tile World 429, and `SCC tick 2` holds the same state as
`TW tick 0`. Comparing equal tick numbers therefore lines the pre-move state up against the
post-first-tick state and declares a divergence immediately.

What the two DO share is the sequence of distinct states. Collapse each side's repeats, drop
SuperCC's pre-move line, and the sequences correspond one to one -- 109 and 109 on that level, every
one identical. That needs no per-ruleset tick ratio, so it works for Lynx (four ticks per move) as
well as MS.

The cost is that a divergence is reported at the first differing STATE CHANGE rather than the first
differing tick. Both engines' own tick numbers are printed with it, which is what you need to go
back into either trace by hand.
#>
function Read-States {
    param([string]$Path, [int]$LevelNumber, [string]$Kind)

    $states = New-Object System.Collections.ArrayList
    $prev = $null
    foreach ($line in [IO.File]::ReadLines((Resolve-Path $Path))) {
        if (-not $line.StartsWith($Kind + "`t")) { continue }
        $f = $line -split "`t"
        if ($f.Count -lt 3) { continue }
        if ([int]$f[1] -ne $LevelNumber) { continue }

        # Field 3 is the RNG on a T line and part of the payload on a Q line. The RNG is compared
        # separately, so it is not part of the state key.
        $from = if ($Kind -eq "T") { 4 } else { 3 }
        if ($f.Count -le $from) { continue }
        $state = ($f[$from..($f.Count - 1)] -join "`t").TrimEnd()
        $rng = if ($Kind -eq "T" -and $f.Count -gt 3) { $f[3] } else { "" }

        # Collapse runs. Tile World repeats a state for every tick of a move; SuperCC repeats one
        # whenever a move resolves in a single half-tick.
        if ($state -ne $prev) {
            [void]$states.Add([pscustomobject]@{ Tick = [int]$f[2]; State = $state; Rng = $rng })
            $prev = $state
        }
    }
    return $states
}

$sccT = @(Read-States -Path $SccTrace -LevelNumber $Level -Kind "T")
$twT  = @(Read-States -Path $TWTrace  -LevelNumber $Level -Kind "T")

if ($sccT.Count -eq 0) { Fail "no T lines for level $Level in $SccTrace" }
if ($twT.Count -eq 0)  { Fail "no T lines for level $Level in $TWTrace -- was TW_TRACE_LEVEL set to $Level, and was it built with -DTRACE_DESYNC? Batch replay is -b, not -r -p." }

<# TraceLevel's opening line is the position BEFORE the first move; mslogic.c has no such sample.
   Dropped by position rather than by comparing it away, so a genuine divergence on the very first
   move is still reported rather than silently absorbed. #>
$droppedPreMove = $false
if ($sccT.Count -gt 1) {
    $sccT = $sccT[1..($sccT.Count - 1)]
    $droppedPreMove = $true
}

Write-Host ""
Write-Host ("level {0}:  SuperCC {1} state changes, Tile World {2}" -f $Level, $sccT.Count, $twT.Count)
if ($droppedPreMove) { Write-Host "  (SuperCC's pre-move opening state dropped -- Tile World does not emit one)" }

$sccQ = @(Read-States -Path $SccTrace -LevelNumber $Level -Kind "Q")
$twQ  = @(Read-States -Path $TWTrace  -LevelNumber $Level -Kind "Q")

$n = [Math]::Min($sccT.Count, $twT.Count)
$bad = -1
$rngFirst = -1
for ($i = 0; $i -lt $n; $i++) {
    if ($rngFirst -lt 0 -and $sccT[$i].Rng -ne $twT[$i].Rng) { $rngFirst = $i }
    if ($sccT[$i].State -ne $twT[$i].State) { $bad = $i; break }
}

if ($bad -lt 0 -and $sccT.Count -ne $twT.Count) {
    Write-Host ""
    Write-Host ("  the states agree for all {0} shared changes, but the traces are different LENGTHS" -f $n)
    Write-Host ("  SuperCC has {0}, Tile World {1} -- one engine ended the level earlier." -f $sccT.Count, $twT.Count)
    Write-Host ""
    exit 1
}

if ($bad -lt 0) {
    Write-Host ("  IDENTICAL across all {0} state changes" -f $n)
    if ($rngFirst -ge 0) {
        Write-Host ("  note: the RNG first differs at change {0} (SCC tick {1} / TW tick {2}) even though state matches" -f `
                    $rngFirst, $sccT[$rngFirst].Tick, $twT[$rngFirst].Tick)
    }
    Write-Host ""
    exit 0
}

Write-Host ""
Write-Host ("  FIRST DIVERGENCE at state change {0}:  SuperCC tick {1}  /  Tile World tick {2}" -f `
            $bad, $sccT[$bad].Tick, $twT[$bad].Tick)
if ($rngFirst -ge 0 -and $rngFirst -lt $bad) {
    Write-Host ("  the RNG had already diverged at change {0}, so look for a different DRAW COUNT before this point" -f $rngFirst)
}
Write-Host ""
$lo = [Math]::Max(0, $bad - $Context)
$hi = [Math]::Min($n - 1, $bad + $Context)
for ($i = $lo; $i -le $hi; $i++) {
    $mark = if ($i -eq $bad) { ">>" } else { "  " }
    Write-Host ("{0} change {1}   SCC tick {2} / TW tick {3}" -f $mark, $i, $sccT[$i].Tick, $twT[$i].Tick)
    Write-Host ("     SCC {0}" -f ($sccT[$i].State -replace "`t", "  "))
    Write-Host ("     TW  {0}" -f ($twT[$i].State  -replace "`t", "  "))
    if ($i -lt $sccQ.Count -and $i -lt $twQ.Count -and $sccQ[$i].State -ne $twQ[$i].State) {
        Write-Host ("     SCC slip {0}" -f ($sccQ[$i].State -replace "`t", "  "))
        Write-Host ("     TW  slip {0}" -f ($twQ[$i].State  -replace "`t", "  "))
    }
}
Write-Host ""
exit 1
