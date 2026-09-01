<#
Runs the regression tests in test\ against a BUILT SuperCC.jar.

Builds the jar first unless -NoBuild is given. The tests exercise the shipped artifact rather than
the source tree on purpose: build.ps1 is a splice build (recompiled classes overlaid on a prebuilt
class baseline), so the jar is the only thing whose behavior is authoritative. See
docs\adr\0003-test-through-the-built-jar.md.

Every test\*.java is compiled together, and every class with a main() is run in its own JVM. There
is no registry to update -- dropping a new file into test\ is all it takes.

  powershell -ExecutionPolicy Bypass -File run-tests.ps1
  powershell -ExecutionPolicy Bypass -File run-tests.ps1 -NoBuild
  powershell -ExecutionPolicy Bypass -File run-tests.ps1 -ResultsPath test-results
  powershell -ExecutionPolicy Bypass -File run-tests.ps1 -Isolated
  powershell -ExecutionPolicy Bypass -File run-tests.ps1 -Mo3 "C:\path\to\MO3.dat"
  powershell -ExecutionPolicy Bypass -File run-tests.ps1 -Collection "C:\path\to\level sets"

  -ResultsPath  writes <dir>\<TestClass>.xml (JUnit XML, which CI renders as annotations) and
                <dir>\<TestClass>.json for every test class.
  -Isolated     builds to a private temp jar instead of .\SuperCC.jar. Use this when another agent
                or shell may be building at the same time -- the default output is a single shared
                file and concurrent runs race on it.
  -Mo3          MO3.dat, the Lynx-signature set behind jc-9's AlwaysOpenInMS.
  -Collection   a folder of .dat level sets, for the wide open-every-level check.

Checks that need files this repo does not contain SKIP rather than fail -- level sets are
third-party content and are not ours to redistribute.

Exit code is 0 only if every assertion in every test class passed.
#>
param(
    [switch]$NoBuild,
    [switch]$Isolated,
    [string]$Mo3,
    [string]$Collection,
    [string]$ResultsPath,
    [string]$Jar = "SuperCC.jar",
    # Extra JVM arguments, prepended to every test class's JVM. coverage.ps1 uses this to attach
    # the JaCoCo agent; nothing else does. Kept as a general passthrough rather than a
    # coverage-specific switch so this script needs no knowledge of the profiler.
    [string[]]$JvmArgs = @()
)

# Native tools write notes to stderr; under "Stop" PowerShell 5.1 turns those into terminating
# NativeCommandErrors even on success. Exit codes are checked explicitly instead.
$ErrorActionPreference = "Continue"
$root = $PSScriptRoot
. (Join-Path $root "build-config.ps1")

$jdkBin = Find-JdkBin -Requires "javac.exe"

# -Isolated names a jar that this script is about to create, so there is nothing for -NoBuild to
# reuse. Left to run, it would fail later with "no jar at ...SuperCC-<guid>.jar", naming a file the
# user never chose. Reject the combination where it is still explainable.
if ($Isolated -and $NoBuild) {
    throw "-Isolated builds a private jar, so it cannot be combined with -NoBuild. Use -Jar to point at an existing jar."
}

# -Isolated only changes where the build LANDS. The default stays .\SuperCC.jar so that the
# familiar "run the tests, then deploy the jar they just tested" flow is unchanged.
#
# $ownTempJar, not $Isolated, is what authorizes the delete in the finally block: with an explicit
# -Jar the path belongs to the caller, and deleting it would destroy a jar they asked us to test.
$ownTempJar = $null
if ($Isolated -and -not $PSBoundParameters.ContainsKey('Jar')) {
    $Jar = Join-Path $env:TEMP ("SuperCC-" + [Guid]::NewGuid().ToString("N") + ".jar")
    $ownTempJar = $Jar
}

$failedClasses = @()
$testClasses = @()
$out = $null

try {
    if (-not $NoBuild) {
        & powershell -ExecutionPolicy Bypass -File (Join-Path $root "build.ps1") -Out $Jar
        if ($LASTEXITCODE -ne 0) { throw "build failed (exit $LASTEXITCODE)" }
    }
    $jarPath = if ([IO.Path]::IsPathRooted($Jar)) { $Jar } else { Join-Path $root $Jar }
    if (-not (Test-Path $jarPath)) { throw "no jar at $jarPath -- build first, or pass -Jar." }

    $testSrc = Get-ChildItem (Join-Path $root "test") -Filter *.java -File | Sort-Object Name
    if (-not $testSrc) { throw "no tests found in test\" }

    $out = Join-Path $env:TEMP ("scc-test-" + [Guid]::NewGuid().ToString("N"))
    New-Item -ItemType Directory -Force -Path $out | Out-Null

    # Compile the whole test folder in one pass so tests can share helpers (Harness, DatBuilder).
    & (Join-Path $jdkBin "javac.exe") -encoding UTF-8 -cp $jarPath -d $out ($testSrc | ForEach-Object { $_.FullName })
    if ($LASTEXITCODE -ne 0) { throw "test compile failed (exit $LASTEXITCODE)" }

    # A class is a test if it was compiled from a top-level test file AND declares a main(). That
    # excludes helpers like Harness and DatBuilder without needing a naming convention.
    # javap failing is treated as fatal rather than "not a test": silently skipping a class here
    # would drop a whole suite from the run while still reporting "all green".
    $candidates = $testSrc | ForEach-Object { [IO.Path]::GetFileNameWithoutExtension($_.Name) }
    foreach ($c in $candidates) {
        $dump = & (Join-Path $jdkBin "javap.exe") -cp $out $c
        if ($LASTEXITCODE -ne 0) { throw "javap could not read compiled test class $c (exit $LASTEXITCODE)" }
        if (($dump -join "`n") -match 'public static void main\(java\.lang\.String\[\]\)') { $testClasses += $c }
    }
    if (-not $testClasses) { throw "no test class in test\ declares a main()" }

    if ($ResultsPath) {
        $resultsDir = if ([IO.Path]::IsPathRooted($ResultsPath)) { $ResultsPath } else { Join-Path $root $ResultsPath }
        # Cleared, not merely created: a class that dies before Harness reports writes nothing, so
        # last run's green XML would otherwise survive a run in which that class crashed.
        if (Test-Path $resultsDir) { Remove-Item -LiteralPath $resultsDir -Recurse -Force }
        New-Item -ItemType Directory -Force -Path $resultsDir | Out-Null
    }

    $counts = @{}
    foreach ($class in $testClasses) {
        Write-Host "`n########## $class ##########"
        # headless=true because the GUI-less emulator has a null window: anything that reaches
        # Swing from a test would otherwise fail differently on a machine with a display than on
        # a CI runner without one.
        $summaryFile = Join-Path $out "$class.summary"
        <# Named $jvmLine, NOT $jvmArgs. PowerShell variable names are CASE-INSENSITIVE, so a local
           $jvmArgs IS the $JvmArgs parameter -- assigning @() to it silently discarded the caller's
           argument, and coverage.ps1 ran the whole suite with no agent attached and no error. #>
        $jvmLine = @()
        if ($JvmArgs.Count -gt 0) { $jvmLine += $JvmArgs }
        $jvmLine += @("-Djava.awt.headless=true", "-Dsupercc.jar=$jarPath", "-Dsupercc.summary=$summaryFile")
        if ($Mo3)        { $jvmLine += "-Dsupercc.mo3=$Mo3" }
        if ($Collection) { $jvmLine += "-Dsupercc.collection=$Collection" }
        if ($ResultsPath) { $jvmLine += ("-Dsupercc.results=" + (Join-Path $resultsDir $class)) }

        & (Join-Path $jdkBin "java.exe") @jvmLine -cp "$jarPath;$out" $class
        # Covers both a failed assertion and a class that died before Harness could report -- a
        # crashed JVM exits nonzero with no output at all, and must not read as a pass.
        if ($LASTEXITCODE -ne 0) { $failedClasses += $class }

        # No summary file means the class never reached the end of Harness.run -- it crashed, or it
        # called System.exit itself, either of which bypasses reporting entirely.
        if (Test-Path $summaryFile) {
            $parts = (Get-Content $summaryFile -Raw).Trim() -split '\s+'
            if ($parts.Count -ge 4) {
                $counts[$class] = "$($parts[1]) passed, $($parts[2]) failed, $($parts[3]) skipped"
            } else {
                # A clean exit with a TRUNCATED summary. Without this branch the class recorded no
                # count and was not added to $failedClasses, so the run printed "no count reported"
                # and then "all green" and exited 0 -- the same silently-green shape as a missing
                # summary, which the else below already handles correctly.
                $counts[$class] = "MALFORMED RESULT ($($parts.Count) field(s); expected 4)"
                if ($failedClasses -notcontains $class) { $failedClasses += $class }
            }
        } else {
            $counts[$class] = "NO RESULT REPORTED (crashed, or called System.exit)"
            if ($failedClasses -notcontains $class) { $failedClasses += $class }
        }
    }
} finally {
    if ($out -and (Test-Path $out)) { [IO.Directory]::Delete($out, $true) }
    # Only ever delete a jar THIS script created. With an explicit -Jar the path belongs to the
    # caller, and removing it would destroy the very jar they asked to have tested.
    if ($ownTempJar -and (Test-Path $ownTempJar)) { Remove-Item -LiteralPath $ownTempJar -Force }
}

Write-Host "`n########## summary ##########"
Write-Host ("  {0} test class(es) run" -f $testClasses.Count)
# Per-class counts, not just names: a suite that silently shrinks is otherwise invisible here.
foreach ($class in $testClasses) {
    $detail = if ($counts.ContainsKey($class)) { $counts[$class] } else { "no count reported" }
    Write-Host ("    {0,-16} {1}" -f $class, $detail)
}
if ($failedClasses.Count -gt 0) {
    Write-Host ("  FAILED: {0}" -f ($failedClasses -join ", "))
    Write-Host ""
    exit 1
}
Write-Host "  all green`n"
exit 0
