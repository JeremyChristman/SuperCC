<#
Runs the regression tests in test\ against a BUILT SuperCC.jar.

Builds the jar first unless -NoBuild is given. The tests exercise the shipped artifact rather than
the source tree on purpose: build.ps1 is a splice build (recompiled classes overlaid on a prebuilt
class baseline), so the jar is the only thing whose behavior is authoritative.

  powershell -ExecutionPolicy Bypass -File run-tests.ps1
  powershell -ExecutionPolicy Bypass -File run-tests.ps1 -NoBuild
  powershell -ExecutionPolicy Bypass -File run-tests.ps1 -Mo3 "C:\path\to\MO3.dat"

Two checks need files this repo does not contain and SKIP without them: the jar scan (supplied
automatically here) and the MO3.dat signature check (-Mo3, optional -- MO3 is a third-party level
set, not ours to redistribute).

Exit code is 0 only if every assertion passed.
#>
param([switch]$NoBuild, [string]$Mo3, [string]$Jar = "SuperCC.jar")
$ErrorActionPreference = "Stop"
$root = $PSScriptRoot

function Find-JdkBin {
    if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME "bin\javac.exe"))) { return (Join-Path $env:JAVA_HOME "bin") }
    $cand = Get-ChildItem "C:\Program Files\Java" -Directory -ErrorAction SilentlyContinue |
            Where-Object { Test-Path (Join-Path $_.FullName "bin\javac.exe") } |
            Sort-Object Name -Descending | Select-Object -First 1
    if ($cand) { return (Join-Path $cand.FullName "bin") }
    throw "No JDK found. Set JAVA_HOME to a JDK 16+."
}
$jdkBin = Find-JdkBin

if (-not $NoBuild) {
    & powershell -ExecutionPolicy Bypass -File (Join-Path $root "build.ps1")
    if ($LASTEXITCODE -ne 0) { throw "build failed (exit $LASTEXITCODE)" }
}
$jarPath = if ([IO.Path]::IsPathRooted($Jar)) { $Jar } else { Join-Path $root $Jar }
if (-not (Test-Path $jarPath)) { throw "no jar at $jarPath -- build first, or pass -Jar." }

$out = Join-Path $env:TEMP ("scc-test-" + [Guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Force -Path $out | Out-Null
try {
    & (Join-Path $jdkBin "javac.exe") -encoding UTF-8 -cp $jarPath -d $out (Join-Path $root "test\SettingsTest.java")
    if ($LASTEXITCODE -ne 0) { throw "test compile failed (exit $LASTEXITCODE)" }

    $jvmArgs = @("-Dsupercc.jar=$jarPath")
    if ($Mo3) { $jvmArgs += "-Dsupercc.mo3=$Mo3" }
    & (Join-Path $jdkBin "java.exe") @jvmArgs -cp "$jarPath;$out" SettingsTest
    $code = $LASTEXITCODE
} finally {
    [IO.Directory]::Delete($out, $true)
}
if ($code -ne 0) { throw "TESTS FAILED (exit $code)" }
