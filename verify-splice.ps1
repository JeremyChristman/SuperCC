<#
Verifies the splice-build invariants. Run it before calling any change done, and it runs in CI.

WHY THIS SCRIPT EXISTS
----------------------
build.ps1 recompiles ONLY the files listed in $SPLICE_MODIFIED and overlays them on the committed
IntelliJ .class baseline (docs\adr\0001). Edit a source file that is not in that list and the build
still succeeds, the test suite still passes, and the jar still contains the OLD bytecode. There is
no error, no warning, and no runtime signal. Documentation cannot fix a silent failure -- an agent
or a contributor reads the rule, believes they complied, and is wrong.

So this checks it mechanically: every unspliced source file is recompiled and compared against the
class the build would actually ship. A difference means an edit that is not going to ship.

Measured on the current tree: 70 of 71 unspliced files recompile to byte-identical javap output,
zero differ. The comparison is quiet, so any noise it makes is real.

  powershell -ExecutionPolicy Bypass -File verify-splice.ps1
  powershell -ExecutionPolicy Bypass -File verify-splice.ps1 -Verbose
  powershell -ExecutionPolicy Bypass -File verify-splice.ps1 -UpdateFormBaseline

WHAT THIS CANNOT SEE
--------------------
Stated so nobody mistakes a green run for a proof. The bytecode comparison ignores:

  * RUNTIME-VISIBLE ANNOTATIONS. Adding or removing @Deprecated changes only the annotation
    attribute, which javap -c does not print and whose type name is a Utf8, not a String constant.
  * SWAPPING two string literals BETWEEN concat call sites. javac numbers BootstrapMethods entries
    in code order, so the disassembly is unchanged, and sorting the string set -- which is what buys
    independence from the compiler's pool ordering -- also erases a pure swap.
  * Line-number-only edits. Harmless, and correctly ignored.

Both real holes need an actual edit to an unspliced file to matter at all, and both are far less
likely than the ordinary case this does catch. Do not extend the ignore list without measuring.

Exit code is 0 only if every invariant holds.
#>
[CmdletBinding()]
param([switch]$UpdateFormBaseline)

# Native tools write notes and warnings to stderr; under "Stop" PowerShell 5.1 turns those into
# terminating NativeCommandErrors even when the tool succeeded. Exit codes are checked explicitly.
$ErrorActionPreference = "Continue"
$root = $PSScriptRoot
. (Join-Path $root "build-config.ps1")

$jdkBin = Find-JdkBin -Requires "javap.exe"
$javac  = Join-Path $jdkBin "javac.exe"
$javap  = Join-Path $jdkBin "javap.exe"
$srcRoot = (Get-Item -LiteralPath (Join-Path $root "java")).FullName.TrimEnd([char]92)

$problems = New-Object System.Collections.ArrayList
$checked = 0; $skipped = 0

function Add-Problem { param([string]$Text) [void]$problems.Add($Text); Write-Host "  FAIL  $Text" }
function Report-Ok   { param([string]$Text) Write-Host "  PASS  $Text" }

function Get-FormSibling { param([string]$JavaPath) return [IO.Path]::ChangeExtension($JavaPath, ".form") }

<#
Hashes a TEXT file with line endings normalized, so the result does not depend on how git happened
to check the file out.

This is not tidiness. .gitattributes declares `* text=auto eol=lf`, so a fresh clone -- CI, or any
new contributor -- gets LF, while this working tree holds CRLF for blobs committed before that
attribute existed. Measured on graphics\Gui.form: 11,330 bytes here, 11,116 on checkout. A raw-byte
hash therefore flags all 15 form files as EDITED on every machine except the one that generated the
baseline, which is exactly what CI's first run did.

CR bytes are stripped rather than the text being decoded, which keeps this binary-safe and
independent of encoding and BOM.
#>
function Get-TextHash {
    param([string]$Path)

    $bytes = [IO.File]::ReadAllBytes($Path)
    $out = New-Object byte[] $bytes.Length
    $n = 0
    foreach ($b in $bytes) { if ($b -ne 13) { $out[$n] = $b; $n++ } }
    $sha = [Security.Cryptography.SHA256]::Create()
    try { return [BitConverter]::ToString($sha.ComputeHash($out, 0, $n)).Replace('-', '') }
    finally { $sha.Dispose() }
}

<#
Returns a directory's canonical full path, for use as a substring base.

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
#>
function Get-ClassKey {
    param([string]$ClassDir, [string]$Fqcn)

    # javap's exit code is checked because this whole script is a silent-failure detector and
    # ignoring it would put one inside: if javap failed on BOTH sides -- a corrupt or unreadable
    # class -- the two keys would both be the same empty string and the file would pass.
    $code = (& $javap -c -p -constants -cp $ClassDir $Fqcn) -join "`n"
    if ($LASTEXITCODE -ne 0) { throw "javap could not read $Fqcn from $ClassDir (exit $LASTEXITCODE)" }
    $verbose = & $javap -v -p -cp $ClassDir $Fqcn
    if ($LASTEXITCODE -ne 0) { throw "javap -v could not read $Fqcn from $ClassDir (exit $LASTEXITCODE)" }
    $strings = @($verbose |
                 Select-String -Pattern '=\s+String\s+#\d+\s+//\s*(.*)$' |
                 ForEach-Object { $_.Matches[0].Groups[1].Value } |
                 Sort-Object)
    return ($code.Trim() + "`n--STRINGS--`n" + ($strings -join "`n"))
}

Write-Host "`n== 1. every file in the splice list exists and is compilable by javac =="
foreach ($m in $SPLICE_MODIFIED) {
    $src = Join-Path $srcRoot $m
    if (-not (Test-Path $src)) { Add-Problem "$m is in the splice list but does not exist"; continue }
    # A .form sibling means IntelliJ's form compiler generates $$$setupUI$$$() for this class and
    # javac cannot reproduce it. Splicing a javac build of it ships a GUI that dies at launch.
    if (Test-Path (Get-FormSibling $src)) {
        Add-Problem "$m is in the splice list but has a sibling .form -- javac cannot build it (ADR 0001)"
    }
}
if ($problems.Count -eq 0) { Report-Ok "$($SPLICE_MODIFIED.Count) spliced files, none form-based" }

Write-Host "`n== 2. the .form inventory still matches the committed baseline =="
# A NEW .form file means a new class javac cannot build. It has to be compiled by IntelliJ and its
# .class committed, or the build silently ships nothing for it.
$formProblems = $problems.Count
$forms = @(Get-ChildItem $srcRoot -Recurse -Filter *.form | Sort-Object FullName)
foreach ($f in $forms) {
    $rel = $f.FullName.Substring($srcRoot.Length + 1)
    $cls = Join-Path $root ([IO.Path]::ChangeExtension($rel, ".class"))
    if (-not (Test-Path $cls)) {
        Add-Problem "$rel has no committed .class baseline -- it must be built in IntelliJ and committed (ADR 0002)"
    }
}

# Existence is not enough, and neither is checking only the .form.
#
# EDITING a .form -- moving a button, renaming a field, adding a component -- changes the
# $$$setupUI$$$() that IntelliJ generates, and this build cannot regenerate it, so the jar keeps
# shipping the OLD layout with no error anywhere.
#
# The SIBLING .java is the bigger hole and the more likely edit. graphics\Gui.java is 219 lines of
# ordinary hand-written Java -- constructor, listeners, real methods -- and check 3 skips it
# entirely because it is form-based. So an edit there is invisible twice over: the build will not
# recompile it (it cannot, without IntelliJ) and, until now, nothing said so.
#
# Both are hashed. A member-level bytecode comparison that ignores the generated $$$ members would
# be more precise, but a hash cannot produce a false green, and for a file nobody can rebuild here
# anyway the only correct response to either flag is identical: rebuild it in IntelliJ, commit the
# new .class, and re-run with -UpdateFormBaseline.
$baselineFile = Join-Path $root "docs\form-baseline.sha256"
$formTracked = @()
foreach ($f in $forms) {
    $formTracked += $f.FullName
    $sibling = [IO.Path]::ChangeExtension($f.FullName, ".java")
    if (Test-Path $sibling) { $formTracked += $sibling }
}
if ($UpdateFormBaseline) {
    $lines = foreach ($p in $formTracked) {
        "{0}  {1}" -f (Get-TextHash $p),
                      $p.Substring($srcRoot.Length + 1)
    }
    [IO.File]::WriteAllText($baselineFile, (($lines -join "`n") + "`n"), (New-Object Text.UTF8Encoding $false))
    Write-Host "  WROTE $baselineFile ($($formTracked.Count) files) -- commit this alongside the rebuilt .class files"
}
elseif (-not (Test-Path $baselineFile)) {
    Add-Problem "docs\form-baseline.sha256 is missing -- regenerate it with -UpdateFormBaseline"
}
else {
    $expected = @{}
    foreach ($line in (Get-Content $baselineFile)) {
        if ($line -match '^([0-9A-Fa-f]{64})\s\s(.+)$') { $expected[$Matches[2]] = $Matches[1].ToUpper() }
    }
    foreach ($p in $formTracked) {
        $rel = $p.Substring($srcRoot.Length + 1)
        $got = Get-TextHash $p
        if (-not $expected.ContainsKey($rel)) {
            Add-Problem "$rel is NEW and form-based -- build it in IntelliJ, commit its .class, then re-run with -UpdateFormBaseline"
        }
        elseif ($expected[$rel] -ne $got) {
            Add-Problem ("$rel has been EDITED, and it is form-based, so THIS BUILD CANNOT RECOMPILE IT -- the jar " +
                         "would ship the old class. Rebuild it in IntelliJ, commit the new .class, then re-run " +
                         "with -UpdateFormBaseline.")
        }
    }
    foreach ($rel in $expected.Keys) {
        if (-not (Test-Path (Join-Path $srcRoot $rel))) {
            Add-Problem "$rel is in the form baseline but no longer exists -- re-run with -UpdateFormBaseline"
        }
    }
}
if ($problems.Count -eq $formProblems) {
    Report-Ok "$($forms.Count) .form files and their sibling sources are unchanged, each with a baseline class"
}

Write-Host "`n== 3. no unspliced source file has been edited away from its shipped bytecode =="
$spliceProblems = $problems.Count
$stage = Join-Path $env:TEMP ("scc-verify-" + [Guid]::NewGuid().ToString("N"))
$out   = Join-Path $env:TEMP ("scc-verifyout-" + [Guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Force -Path $stage | Out-Null
New-Item -ItemType Directory -Force -Path $out | Out-Null
try {
    # Stage the same baseline the build would use, so the recompile resolves references identically.
    foreach ($d in $SPLICE_STAGE_DIRS) {
        $src = Join-Path $root $d
        if (Test-Path $src) { Invoke-Robocopy -Source $src -Destination (Join-Path $stage $d) }
    }
    $empty = Join-Path $stage "__empty"
    New-Item -ItemType Directory -Force -Path $empty | Out-Null

    foreach ($f in (Get-ChildItem $srcRoot -Recurse -Filter *.java | Sort-Object FullName)) {
        $rel = $f.FullName.Substring($srcRoot.Length + 1)
        if ($SPLICE_MODIFIED -contains $rel)    { continue }   # the build recompiles these anyway
        if ($SPLICE_SOURCE_ONLY -contains $rel) { $skipped++; continue }
        if (Test-Path (Get-FormSibling $f.FullName)) { $skipped++; continue }  # IntelliJ owns these

        $pkgDir = Split-Path $rel
        $base   = [IO.Path]::GetFileNameWithoutExtension($rel)
        $cls    = if ($pkgDir) { Join-Path $pkgDir "$base.class" } else { "$base.class" }
        if (-not (Test-Path (Join-Path $stage $cls))) {
            Add-Problem ("$rel has no baseline class, so nothing it contains ships. If it is a NEW file you " +
                         "want built, add it to `$SPLICE_MODIFIED; if it is source that is deliberately never " +
                         "compiled, add it to `$SPLICE_SOURCE_ONLY. Both are in build-config.ps1.")
            continue
        }

        $fresh = Join-Path $out ([Guid]::NewGuid().ToString("N"))
        New-Item -ItemType Directory -Force -Path $fresh | Out-Null
        # Canonical form before it is used as a substring base: the runner's TEMP is an 8.3 short
        # path while Get-ChildItem reports FullName in long form, and mixing the two cuts the
        # relative path in the wrong place.
        $fresh = Get-CanonicalDir $fresh
        & $javac $SPLICE_VERIFY_DEBUG --release $SPLICE_RELEASE -encoding $SPLICE_ENCODING -cp $stage `
                 -sourcepath $empty -implicit:none -d $fresh $f.FullName 2>&1 | Out-Null
        if ($LASTEXITCODE -ne 0) {
            Add-Problem "$rel does not compile -- it cannot be verified, and the build would not ship the fix"
            continue
        }

        # Compare EVERY class the file produces, not just the top-level one. Inner and anonymous
        # classes carry real code -- an edit confined to one of them is exactly as unshipped, and a
        # top-level-only check reports a clean tree while the jar holds the old bytecode.
        $edited = $false
        foreach ($p in (Get-ChildItem $fresh -Recurse -Filter *.class | Sort-Object FullName)) {
            $relCls = $p.FullName.Substring($fresh.Length + 1)
            if (-not (Test-Path (Join-Path $stage $relCls))) {
                Add-Problem "$rel produces $relCls, which has no baseline class -- it would not ship"
                $edited = $true
                continue
            }
            $fqcn = ($relCls -replace '\.class$', '').Replace('\', '.')
            if ((Get-ClassKey $fresh $fqcn) -ne (Get-ClassKey $stage $fqcn)) { $edited = $true }
        }
        $checked++
        if ($edited) {
            Add-Problem ("$rel is EDITED but not spliced -- the jar would ship the old bytecode. " +
                         "Add `"$rel`" to `$SPLICE_MODIFIED in build-config.ps1.")
        }
        # Write-Verbose is already a no-op when verbosity is off, and $PSBoundParameters['Verbose']
        # misses a caller who set $VerbosePreference instead of passing the switch.
        Write-Verbose "  ok  $rel"
    }
    if ($problems.Count -eq $spliceProblems) {
        Report-Ok "$checked unspliced files match their shipped bytecode ($skipped form-based or source-only, skipped)"
    }

    Write-Host "`n== 4. every shipped class still has source behind it =="
    # The mirror of check 3, and it needs to exist because check 3 iterates over SOURCE files:
    # delete java\graphics\Foo.java and there is simply no iteration for it, while graphics\Foo.class
    # stays staged and stays in the jar. Verifier green, jar shipping a class nobody can read the
    # source of. Walking the baseline side is the only way to see that.
    $orphanProblems = $problems.Count
    $orphans = 0
    foreach ($d in @("emulator","game","graphics","io","tools","util")) {
        $dir = Join-Path $root $d
        if (-not (Test-Path $dir)) { continue }
        foreach ($c in (Get-ChildItem $dir -Recurse -File -Filter *.class)) {
            $relCls = $c.FullName.Substring($root.Length + 1)
            # Foo$1.class and Foo$Bar.class both belong to Foo.java.
            $owner = ($relCls -replace '\.class$', '') -replace '\$.*$', ''
            if (-not (Test-Path (Join-Path $srcRoot "$owner.java"))) {
                Add-Problem "$relCls ships in the jar but java\$owner.java does not exist -- stale class, or a deleted source file"
                $orphans++
            }
        }
    }
    if ($problems.Count -eq $orphanProblems) { Report-Ok "no shipped class is missing its source" }
}
finally {
    if (Test-Path $stage) { [IO.Directory]::Delete($stage, $true) }
    if (Test-Path $out)   { [IO.Directory]::Delete($out, $true) }
}

Write-Host "`n== results =="
if ($problems.Count -gt 0) {
    Write-Host "  $($problems.Count) splice problem(s):"
    foreach ($p in $problems) { Write-Host "    - $p" }
    Write-Host ""
    exit 1
}
Write-Host "  splice invariants hold`n"
exit 0
