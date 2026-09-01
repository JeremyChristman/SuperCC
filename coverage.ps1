<#
Measures test coverage with JaCoCo, and reports the number that actually matters.

WHY THE NUMBER IS SCOPED RATHER THAN GLOBAL
-------------------------------------------
A single whole-project percentage would be actively misleading here, and chasing it would push
effort in exactly the wrong direction:

  * 15 of the 94 source files are form-based and CANNOT be compiled from this repo at all
    (docs\adr\0001). Coverage of them is unreachable by construction, not merely absent.
  * tools\** is 35 files -- the variation interpreter, the TSP solver, seed search. It is upstream
    code this fork does not modify. Testing it would be testing somebody else's work.
  * graphics\** is the Swing UI, which has no headless test story.

What this fork can break, and therefore what is worth measuring, is the ENGINE and the FILE
FORMATS: game\** and io\**. Those get a real number; everything else is reported separately as
context and is explicitly not a target.

THE GOVERNING RULE: NEVER PRINT A NUMBER THIS RUN DID NOT EARN
--------------------------------------------------------------
A coverage tool that lies is worse than no coverage tool, because the lie is quantitative and
therefore believed. Three review passes found four separate ways this script could print a
plausible, well-formatted, WRONG percentage and still exit 0 -- a stale CSV left behind by a failed
wipe, execution data appended onto a previous run, a red test suite whose exit code was discarded,
and a jar/exec class mismatch that JaCoCo reports as 0% rather than as an error. Every guard below
exists for one of those. When a guard cannot establish freshness this script FAILS rather than
reporting; that is deliberate, and it is why several checks look redundant.

NO DEPENDENCY IS ADDED TO THE REPO
----------------------------------
This repo has no package manager and a standing rule against adding dependencies. JaCoCo is a
TEST-TIME tool, is never shipped, and is never committed: it is cached per machine under
%LOCALAPPDATA%\jacoco\<version>\. Pass -Download once to fetch it, or -JacocoDir to point at a
copy you already have.

  powershell -ExecutionPolicy Bypass -File coverage.ps1 -Download    # first run
  powershell -ExecutionPolicy Bypass -File coverage.ps1              # thereafter
  powershell -ExecutionPolicy Bypass -File coverage.ps1 -Html        # also write an HTML report
  powershell -ExecutionPolicy Bypass -File coverage.ps1 -NoBuild     # reuse the jar already there

Output goes to coverage-report\ (gitignored): jacoco.exec, coverage.csv, and with -Html an
html\index.html. The CSV is the evidence behind every percentage printed.

Coverage is measured against the BUILT JAR, like every test here (docs\adr\0003) -- the jar is the
only artifact whose behavior is authoritative under a splice build. Note that 299 of the target
scope's branches live in the four spliced io\** classes, which YOUR javac compiles; the other 2355
come from the committed baseline and are identical everywhere. A different JDK can therefore move
the io\** figure slightly without anything being wrong. The numbers documented in CLAUDE.md were
measured on JDK 16.0.2, which is what CI pins.
#>
param(
    [switch]$Download,
    [switch]$Html,
    [switch]$NoBuild,
    [string]$JacocoDir,
    [string]$Version = "0.8.15"
)

# Native tools write notes to stderr; under "Stop" PowerShell 5.1 turns those into terminating
# NativeCommandErrors even on success. Exit codes are checked explicitly instead.
#
# The cost of that choice is that EVERY cmdlet failure below is non-terminating and must be checked
# by hand. A Remove-Item that silently fails is what lets stale data reach the report, so the
# cleanup block does not trust its own delete -- it re-tests afterward.
$ErrorActionPreference = "Continue"
$root = $PSScriptRoot

function Fail($msg) { Write-Host "  ERROR  $msg"; exit 1 }

<# The dot-source below is also the repo marker, and it is fatal on purpose.

   This script deletes files under its own directory. Run a stray copy of it from a Desktop or a
   dist\ folder and, without this check, it would delete THAT folder's coverage-report -- a real
   deletion of unrelated data, confirmed in review. Under $ErrorActionPreference = "Continue" a
   failed dot-source is only a printed error, and execution would otherwise reach the delete fifty
   lines later regardless. Refusing to run outside the repo is the guard. #>
$configPath = Join-Path $root "build-config.ps1"
if (-not (Test-Path $configPath)) {
    Fail "no build-config.ps1 beside this script. coverage.ps1 must run from inside the SuperCC repo -- it deletes files under its own directory, so it refuses to run anywhere else."
}
. $configPath

# The release this was developed against, verified only when you did not ask for a different one.
# Warning-and-continuing on a hash mismatch is documentation, not a control: the NOTE scrolls past
# and the script runs on for minutes afterward. So the pinned version hard-fails, and any other
# -Version skips the check entirely rather than crying wolf.
$PINNED_VERSION    = "0.8.15"
$PINNED_ZIP_SHA256 = "5B3F6DDB724E761D25C937D68B0189A3A23F3E220E3282575EE0B53359E8110E"

# ------------------------------------------------------------------ locate JaCoCo
if (-not $JacocoDir) { $JacocoDir = Join-Path $env:LOCALAPPDATA "jacoco\$Version" }
$agent = Join-Path $JacocoDir "lib\jacocoagent.jar"
$cli   = Join-Path $JacocoDir "lib\jacococli.jar"

if (-not (Test-Path $agent) -or -not (Test-Path $cli)) {
    if (-not $Download) {
        Write-Host "JaCoCo $Version is not cached at $JacocoDir"
        Write-Host "Run once with -Download to fetch it, or pass -JacocoDir <path> to an existing copy."
        Write-Host "It is a test-time tool: never committed, never shipped, cached per machine."
        exit 1
    }
    New-Item -ItemType Directory -Force $JacocoDir | Out-Null
    $zip = Join-Path $JacocoDir "jacoco-$Version.zip"
    Write-Host "downloading JaCoCo $Version ..."
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    <# -ErrorAction Stop inside a try. Without it the web failure is non-terminating, execution
       falls through to the hash check and Expand-Archive, and the user is shown an empty-SHA
       "stop and check" supply-chain warning when the actual problem is that the network is down.
       That points the reader at entirely the wrong thing. #>
    try {
        Invoke-WebRequest -Uri "https://github.com/jacoco/jacoco/releases/download/v$Version/jacoco-$Version.zip" `
                          -OutFile $zip -UseBasicParsing -ErrorAction Stop
    } catch {
        Fail ("could not download JaCoCo {0}: {1}`n         Check your network or proxy, or download the zip yourself and pass -JacocoDir <path>." -f $Version, $_.Exception.Message)
    }
    if ($Version -eq $PINNED_VERSION) {
        $sha = (Get-FileHash $zip -Algorithm SHA256).Hash
        if ($sha -ne $PINNED_ZIP_SHA256) {
            Remove-Item -LiteralPath $zip -Force -ErrorAction SilentlyContinue
            Fail ("JaCoCo {0} archive SHA-256 is {1}, expected {2}. The download was truncated or the asset changed; the file has been deleted. Retry, or pass -JacocoDir with a copy you trust." -f $Version, $sha, $PINNED_ZIP_SHA256)
        }
    }
    Expand-Archive $zip -DestinationPath $JacocoDir -Force
}
# BOTH jars, not just the agent. A partial extract that yields the agent without the CLI otherwise
# runs the entire suite and then dies with "jacococli produced no CSV" -- which blames the report
# step for a file that was never extracted.
if (-not (Test-Path $agent)) { Fail "no jacocoagent.jar under $JacocoDir -- the extract was incomplete. Delete that folder and re-run with -Download." }
if (-not (Test-Path $cli))   { Fail "no jacococli.jar under $JacocoDir -- the extract was incomplete. Delete that folder and re-run with -Download." }

# ------------------------------------------------------------------ build
<# Builds first, like run-tests.ps1 does. Coverage is measured against the jar (docs\adr\0003), so
   measuring a stale one reports a percentage for code that is no longer in the tree -- a wrong
   number that looks exactly like a right one. -NoBuild is there for a jar you built deliberately. #>
$jarPath = Join-Path $root "SuperCC.jar"
if (-not $NoBuild) {
    & powershell -ExecutionPolicy Bypass -File (Join-Path $root "build.ps1") | Out-Null
    if ($LASTEXITCODE -ne 0) { Fail "build failed (exit $LASTEXITCODE)" }
}
if (-not (Test-Path $jarPath)) { Fail "no SuperCC.jar -- run build.ps1 first, or drop -NoBuild." }
$jarStamp = (Get-Item $jarPath).LastWriteTime
Write-Host ("measuring {0}  (built {1})" -f $jarPath, $jarStamp)

# ------------------------------------------------------------------ a guaranteed-clean output dir
$work    = Join-Path $root "coverage-report"
$exec    = Join-Path $work "jacoco.exec"
$csv     = Join-Path $work "coverage.csv"
$htmlDir = Join-Path $work "html"

<# Deletes the three things this script writes, BY NAME, and then proves each one is gone.

   The previous shape -- Remove-Item -Recurse -Force on the whole directory -- was wrong twice
   over. It silently destroyed anything a user or another agent had parked in coverage-report\,
   and it silently destroyed a plain FILE of that name. Worse, it was only advisory: under
   "Continue" a locked file (Excel holding coverage.csv open is the everyday case, and this script
   prints that path at the end, inviting exactly that) left the stale file in place, after which
   New-Item -Force succeeded as a no-op and the run reported the PREVIOUS run's numbers under this
   run's jar timestamp.

   Deleting by name also makes a failure specific: if jacoco.exec cannot be removed, that is
   precisely the condition that must abort, and we can say which file is stuck. #>
if ((Test-Path $work) -and -not (Test-Path $work -PathType Container)) {
    Fail "$work exists and is a FILE, not a directory. Move or delete it; this script will not remove it for you."
}
New-Item -ItemType Directory -Force $work | Out-Null
if (-not (Test-Path $work -PathType Container)) { Fail "could not create $work" }
foreach ($stale in @($exec, $csv, $htmlDir)) {
    if (Test-Path $stale) {
        Remove-Item -LiteralPath $stale -Recurse -Force -ErrorAction SilentlyContinue
        if (Test-Path $stale) {
            Fail "could not delete $stale -- close whatever has it open (Excel holding the CSV is the usual cause). Refusing to report a number that could be mixed with stale data."
        }
    }
}

# ------------------------------------------------------------------ run the suite instrumented
# append=true because run-tests.ps1 gives every test class its OWN JVM -- without it each one would
# overwrite the last and the report would describe a single test file. That makes the delete above
# load-bearing: appending onto a surviving exec file silently UNIONS two runs, and the percentage
# moves up, which is the direction nobody audits.
$agentArg = "-javaagent:$agent=destfile=$exec,append=true"

Write-Host "running the suite under JaCoCo ..."
<# Called IN-PROCESS, not via `powershell -Command "..."`.

   The old form built a command string and interpolated paths into single quotes inside it. A user
   name containing an apostrophe closed the literal early: best case a parse error blamed on the
   agent, worst case the remainder ran as code, reachable from the -JacocoDir parameter. Calling
   the script directly passes the array through the parameter binder, so quoting never enters into
   it -- verified with a path containing both an apostrophe and a space.

   *>&1 folds Write-Host (the information stream in PS 5.1) and the tests' stderr into the pipeline
   so the summary can be filtered out of it. ErrorLogTest deliberately writes kilobytes of filler
   to System.err to exercise log rotation; unfiltered, that scrolls the real result off the screen.
   Everything is buffered rather than streamed so that a failure can show its tail. #>
$runner = Join-Path $root "run-tests.ps1"
$suiteOutput = @(& $runner -NoBuild -Jar $jarPath -JvmArgs @($agentArg) *>&1 | ForEach-Object { $_.ToString() })
$suiteExit = $LASTEXITCODE

$suiteOutput |
    Where-Object { $_ -match 'passed,|all green|FAILED|test class\(es\) run' } |
    ForEach-Object { Write-Host ("  " + $_.Trim()) }

<# The suite's exit code was previously discarded, which meant a red suite still printed a full
   coverage table and exited 0. That is the worst possible framing of a broken build: the classes
   that did run still wrote execution data, so the number merely looked LOWER, and a genuine test
   failure would be read as "coverage regressed" rather than "the tests are broken." #>
if ($suiteExit -ne 0) {
    Write-Host ""
    Write-Host "  ---- last 25 lines of the run ----"
    $suiteOutput | Select-Object -Last 25 | ForEach-Object { Write-Host ("  " + $_) }
    Write-Host ""
    Fail "the test suite failed (exit $suiteExit). Coverage measured from a failed run describes nothing -- fix the tests first."
}

if (-not (Test-Path $exec)) { Fail "JaCoCo wrote no execution data -- did the agent attach? Check that $agent exists and is readable." }
# Freshness, not mere existence. The delete above should make this unreachable; it is here because
# "the file is present" was the exact assumption that let a previous run's data be reported as this
# run's, and the check costs nothing.
if ((Get-Item $exec).LastWriteTime -lt $jarStamp.AddHours(-1)) {
    Fail "$exec looks stale (older than the jar by more than an hour). Delete coverage-report\ and re-run."
}

# ------------------------------------------------------------------ report
$jdkBin = Find-JdkBin -Requires "java.exe"
$reportArgs = @("--classfiles", $jarPath, "--sourcefiles", (Join-Path $root "java"), "--csv", $csv)
if ($Html) { $reportArgs += @("--html", $htmlDir) }

<# Not piped to Out-Null any more.

   jacococli prints "Classes in bundle 'X' do not match with execution data" to STDOUT when the jar
   it is reporting against is not the jar the tests ran against. That is not an error to JaCoCo --
   it reports the mismatched classes as 0% covered and exits 0. Swallowing that warning turns a
   stale-jar mixup into a full table of understated percentages that looks entirely credible, and
   understated coverage is the direction people act on ("we need more tests"). #>
$reportOutput = @(& (Join-Path $jdkBin "java.exe") -jar $cli report $exec @reportArgs 2>&1 | ForEach-Object { $_.ToString() })
$reportExit = $LASTEXITCODE
if ($reportExit -ne 0) {
    $reportOutput | ForEach-Object { Write-Host ("  " + $_) }
    Fail "jacococli report failed (exit $reportExit)"
}
$mismatch = @($reportOutput | Where-Object { $_ -match 'do not match|\[WARN\]' })
if ($mismatch.Count -gt 0) {
    $mismatch | ForEach-Object { Write-Host ("  JACOCO: " + $_.Trim()) }
    Fail "jacococli reported a class/execution-data mismatch. The jar being reported on is not the jar the tests ran against, so every mismatched class would read as 0% covered. Delete coverage-report\ and re-run without -NoBuild."
}
if (-not (Test-Path $csv)) { Fail "jacococli produced no CSV at $csv" }

<#
Sums the CSV by package group.

JaCoCo's CSV is one row per CLASS with covered/missed counters. The numbers that matter here are
BRANCH coverage -- an emulator is mostly conditionals, and line coverage would flatter a switch
whose arms are never all taken.
#>
$rows = @(Import-Csv $csv)
if ($rows.Count -eq 0) { Fail "$csv has no rows -- jacococli wrote a header and nothing else." }

# InvariantCulture on every formatted percentage. The -f operator uses the CURRENT culture, so on a
# machine with a comma decimal separator the console would read "19,5%" while CLAUDE.md documents
# "19.5%" -- a cosmetic mismatch that reads as a discrepancy. The arithmetic itself is already
# culture-proof (Import-Csv takes a literal comma without -UseCulture, and PowerShell's string-to-
# number coercion is invariant); only the display needed pinning.
$inv = [cultureinfo]::InvariantCulture

function Sum-Group {
    param([string]$Label, [scriptblock]$Filter)
    $g = @($rows | Where-Object $Filter)
    # Same property set on both paths, including Branches. A missing property formats as an empty
    # string, so an empty group used to print a BLANK branches column -- reading as "not measured"
    # rather than "no classes matched."
    if ($g.Count -eq 0) {
        return [pscustomobject]@{ Label=$Label; Classes=0; BranchPct="  n/a"; LinePct="  n/a"; Branches="n/a" }
    }
    $bc = ($g | Measure-Object BRANCH_COVERED -Sum).Sum
    $bm = ($g | Measure-Object BRANCH_MISSED  -Sum).Sum
    $lc = ($g | Measure-Object LINE_COVERED   -Sum).Sum
    $lm = ($g | Measure-Object LINE_MISSED    -Sum).Sum
    $bp = if (($bc + $bm) -gt 0) { [string]::Format($inv, "{0,5:N1}%", (100.0 * $bc / ($bc + $bm))) } else { "  n/a" }
    $lp = if (($lc + $lm) -gt 0) { [string]::Format($inv, "{0,5:N1}%", (100.0 * $lc / ($lc + $lm))) } else { "  n/a" }
    return [pscustomobject]@{ Label=$Label; Classes=$g.Count; BranchPct=$bp; LinePct=$lp;
                              Branches=("{0}/{1}" -f $bc, ($bc+$bm)) }
}

<# game\** is split by ruleset because the aggregate hides the thing worth knowing. The two
   rulesets are separate implementations of the same game, and a single game\** figure lets a
   well-tested MS engine and an untested Lynx one average into a number that describes neither. #>
$target  = Sum-Group "game\** + io\**  (THE TARGET)" { $_.PACKAGE -like 'game*' -or $_.PACKAGE -eq 'io' }
$game    = Sum-Group "  game\**  (the emulator)"     { $_.PACKAGE -like 'game*' }
$ms      = Sum-Group "    game\MS\**  (MS ruleset)"  { $_.PACKAGE -eq 'game.MS' }
$lynx    = Sum-Group "    game\Lynx\**  (Lynx ruleset)" { $_.PACKAGE -eq 'game.Lynx' }
$shared  = Sum-Group "    game\* + button\**  (shared)" { $_.PACKAGE -eq 'game' -or $_.PACKAGE -eq 'game.button' }
$io      = Sum-Group "  io\**  (file formats)"       { $_.PACKAGE -eq 'io' }
$emu     = Sum-Group "emulator\**"                   { $_.PACKAGE -eq 'emulator' }
$notTgt  = Sum-Group "tools\** + graphics\**  (not a target)" { $_.PACKAGE -like 'tools*' -or $_.PACKAGE -like 'graphics*' }

# A total measurement failure -- a JaCoCo whose CSV column names differ, or a future release
# emitting VM-form package names (game/MS) instead of dot form -- otherwise renders a full table of
# "n/a" and exits 0. Zero classes in the target scope is never a legitimate result here.
if ($target.Classes -eq 0) {
    Fail "no classes matched the target scope in $csv. The CSV's PACKAGE column probably changed shape (this expects dot-separated names such as 'game.MS'). The report is not trustworthy; do not use these numbers."
}

Write-Host ""
Write-Host "########## coverage ##########"
Write-Host ""
Write-Host ("  {0,-34} {1,8} {2,10} {3,10}   {4}" -f "scope", "classes", "branch", "line", "branches")
Write-Host ("  {0,-34} {1,8} {2,10} {3,10}   {4}" -f ("-"*34), "-------", "------", "----", "--------")
foreach ($r in @($target, $game, $ms, $lynx, $shared, $io, $emu, $notTgt)) {
    Write-Host ("  {0,-34} {1,8} {2,10} {3,10}   {4}" -f $r.Label, $r.Classes, $r.BranchPct, $r.LinePct, $r.Branches)
}

# The three sub-rows are meant to partition game\**. That is true today, but it is not
# self-maintaining: add java\game\tile\ and package game.tile lands in game\** and in none of the
# three, so the sub-rows quietly stop adding up to the row above them with no visible hint.
if ($game.Classes -ne ($ms.Classes + $lynx.Classes + $shared.Classes)) {
    Write-Host ""
    Write-Host ("  WARNING  the game\** sub-rows cover {0} of {1} classes -- a new game subpackage is missing from the split." -f `
                ($ms.Classes + $lynx.Classes + $shared.Classes), $game.Classes)
    Write-Host "           The game\** and target totals are still correct; the breakdown is not."
}

Write-Host ""
Write-Host "  Branch coverage is the honest metric for an emulator: it is mostly conditionals, and"
Write-Host "  line coverage flatters a switch whose arms are never all taken."
Write-Host ""
Write-Host "  tools\** and graphics\** are NOT targets -- upstream code this fork does not modify,"
Write-Host "  and 15 form-based classes that cannot be compiled from this repo at all (ADR 0001)."
if ($Html) { Write-Host ("  HTML report: {0}" -f (Join-Path $htmlDir 'index.html')) }
Write-Host ""

# The worst-covered engine classes, which is the actionable half of any coverage run.
$worst = @($rows | Where-Object { ($_.PACKAGE -like 'game*' -or $_.PACKAGE -eq 'io') -and
                                 ([int]$_.BRANCH_COVERED + [int]$_.BRANCH_MISSED) -ge 20 } |
          Sort-Object { [double][int]$_.BRANCH_COVERED / ([int]$_.BRANCH_COVERED + [int]$_.BRANCH_MISSED) } |
          Select-Object -First 8)
if ($worst.Count -gt 0) {
    Write-Host "  least-covered engine classes with 20+ branches:"
    foreach ($c in $worst) {
        $tot = [int]$c.BRANCH_COVERED + [int]$c.BRANCH_MISSED
        Write-Host ([string]::Format($inv, "    {0,-28} {1,5:N1}%   {2}/{3} branches",
                    "$($c.PACKAGE).$($c.CLASS)", (100.0 * [int]$c.BRANCH_COVERED / $tot), $c.BRANCH_COVERED, $tot))
    }
    Write-Host ""
}

Write-Host ("  evidence: {0}" -f $csv)
Write-Host ""
exit 0
