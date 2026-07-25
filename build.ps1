<#
Builds SuperCC.jar from the source in this repo.

Requires a JDK 16+ on PATH (javac + jar). Recompiles all of SuperCC's own
source under java/, keeps the bundled third-party libs (com/, org/) and the
resources/ assets as-is, and packages everything with the manifest — matching
the structure of the upstream fat jar.

Usage:  powershell -ExecutionPolicy Bypass -File build.ps1 [-Out SuperCC.jar]
#>
param([string]$Out = "SuperCC.jar")
$ErrorActionPreference = "Stop"
$root  = $PSScriptRoot

# Resolve a JDK bin that actually contains jar.exe (the Oracle "javapath"
# symlink dir on PATH exposes javac/java but NOT jar). Machine-agnostic:
# JAVA_HOME first, then the newest jdk-* under Program Files\Java.
function Find-JdkBin {
    if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME "bin\jar.exe"))) {
        return (Join-Path $env:JAVA_HOME "bin")
    }
    $cand = Get-ChildItem "C:\Program Files\Java" -Directory -ErrorAction SilentlyContinue |
            Where-Object { Test-Path (Join-Path $_.FullName "bin\jar.exe") } |
            Sort-Object Name -Descending | Select-Object -First 1
    if ($cand) { return (Join-Path $cand.FullName "bin") }
    throw "No JDK with jar.exe found. Set JAVA_HOME to a JDK 16+."
}
$jdkBin = Find-JdkBin
$javac  = Join-Path $jdkBin "javac.exe"
$jar    = Join-Path $jdkBin "jar.exe"

$stage = Join-Path $env:TEMP ("scc-build-" + [Guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Force -Path $stage | Out-Null
try {
    # Stage the packaged (non-compiled) content.
    foreach ($d in "com","org","resources","java","META-INF") {
        robocopy (Join-Path $root $d) (Join-Path $stage $d) /E /NFL /NDL /NJH /NJS /NP | Out-Null
    }
    # Compile all SuperCC source into the stage (classpath = stage, for the bundled libs).
    $srcList = Join-Path $env:TEMP ("scc-srcs-" + [Guid]::NewGuid().ToString("N") + ".txt")
    Get-ChildItem (Join-Path $root "java") -Recurse -Filter *.java |
        ForEach-Object { $_.FullName } | Set-Content $srcList -Encoding ASCII
    & $javac --release 16 -encoding UTF-8 -cp $stage -d $stage "@$srcList"
    if ($LASTEXITCODE -ne 0) { throw "javac failed (exit $LASTEXITCODE)" }
    [System.IO.File]::Delete($srcList)
    # Package. The staged META-INF/MANIFEST.MF (Main-Class: emulator.SuperCC) becomes the jar manifest.
    $outPath = Join-Path $root $Out
    if ([System.IO.File]::Exists($outPath)) { [System.IO.File]::Delete($outPath) }
    & $jar cfm $outPath (Join-Path $stage "META-INF\MANIFEST.MF") -C $stage .
    if ($LASTEXITCODE -ne 0) { throw "jar failed (exit $LASTEXITCODE)" }
    Write-Host ("Built {0} ({1:N0} bytes)" -f $outPath, (Get-Item $outPath).Length)
} finally {
    [System.IO.Directory]::Delete($stage, $true)
}
