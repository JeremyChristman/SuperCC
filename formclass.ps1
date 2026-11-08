<#
Compiles -- or checks -- the committed bytecode of a FORM-BASED class, .form edits included.
(Until jc-18 it could only rebuild a class whose .form was unchanged; docs\adr\0011 replaced that.)

WHY THIS EXISTS
---------------
Fifteen classes are laid out in IntelliJ's GUI Designer: graphics\Gui and fourteen tools\* windows.
IntelliJ's form compiler adds a generated $$$setupUI$$$() to each one, javac cannot, and so the
build ships their committed IntelliJ-built .class files untouched (docs\adr\0001, 0002). Until this
script, editing one of their .java files meant opening IntelliJ.

Upstream's later commits edit four of them (Gui, SeedSearch, ControlGUI, ChooseTileSize), and for
all but one of those edits the .form itself does not change. Then the generated members cannot
change either: they depend only on the .form and on the bound fields' names and types. So the class
can be rebuilt exactly: javac compiles the edited .java, and formclass\FormTransplant.java copies the
generated members out of the committed class and inserts the constructor call at the point
IntelliJ's own instrumenter chooses. docs\adr\0010 records the decision and how it was proven.

  powershell -ExecutionPolicy Bypass -File formclass.ps1 -Rebuild tools\SeedSearch.java
  powershell -ExecutionPolicy Bypass -File formclass.ps1 -Check
  powershell -ExecutionPolicy Bypass -File formclass.ps1 -Check -Jar SuperCC.jar   # reuse a build

-Rebuild <file>[,<file>...]
    Since jc-18, the same as -Compile (below). It used to build the class by the transplant, which
    keeps javac's stack-map frames where IntelliJ recomputes them, so its classes would now fail
    -Check's byte comparison. docs\adr\0011.

-Check
    Two comparisons for each of the fifteen. First the transplant's: javac on the committed .java,
    the generated members copied from the committed class, compared as verify-splice compares
    ordinary files (disassembly plus sorted string constants, constant-pool numbers removed) -- a
    .java edit whose class was never compiled fails here. Then the BYTE check: javac plus JetBrains'
    compiler, from the committed .java AND .form, must reproduce every committed class exactly --
    which is the only thing here that can see a .form edit. The byte check needs the compiler; with
    -RequireCompiler (CI, the release workflow) its absence is a failure, without it a loud warning.
    Neither comparison proves a window still WORKS: test\FormClassTest.java runs the real
    constructor of every form window that can run headless, and test\GuiWindowTest.java builds the
    main one.

-SelfTest
    Eighteen checks on seventeen specimens built from tools\SeedSearch, ChangeTimer and ChangeInventory:
    each refusal that guards a window that would break when opened, and the placements that differ
    from a naive rule. With the compiler, four more: it reproduces a committed class byte for byte,
    the byte check catches an uncompiled .form edit and a transplant-built class, and -Compile's
    lint refuses a constructor IntelliJ's own placement gets wrong.

-DownloadCompiler
    Fetches JetBrains' own form compiler -- java-gui-forms-compiler 211.7628.21, the IntelliJ line
    that built the committed classes, and the five jars it runs with -- into
    %LOCALAPPDATA%\supercc-formsc\211.7628.21\, checking every jar against a SHA-256 pinned below.
    A test-time tool like JaCoCo: never committed, never shipped. Every run COPIES the jars into its
    own work folder, hashes the copies and uses only those, so a jar changed on disk is refused, and
    one changed during a run is never the one run. docs\adr\0011.

-Compile <file>[,<file>...]
    javac compiles the .java, JetBrains' compiler reads the .form and adds the generated members --
    exactly what IntelliJ's build does: from unchanged sources it reproduces all fifteen
    IntelliJ-built classes byte for byte (formclass\FormCompile.java says how). Then the
    transplant's refusals run over the same javac output as a LINT, because the real compiler
    reproduces IntelliJ's known misplacements faithfully (docs\adr\0010), and the two must agree on
    where the call goes. Overwrites the committed <Name>.class and every <Name>$*.class with the new
    set, deleting any committed inner class the source no longer produces (a leftover one would ship
    unseen). Then run verify-splice.ps1 -UpdateFormBaseline and commit the source, the classes and
    the baseline together. Needs -DownloadCompiler once per machine.

Measured on the tree this was written against (jc-15): -Check passes all fifteen, and from
unchanged source the rebuild is BYTE-IDENTICAL to IntelliJ's output for ChangeTimer,
ChooseWindowSize, GameGifRecorder and SeedSearch.

REQUIRES JDK 16.0.2 (JAVA_HOME), and refuses anything else. Two reasons. javac's output varies by
version, and the committed classes came from 16.0.2 -- on JDK 18, verify-splice already sees false
edits in untouched files. And FormTransplant uses the JDK's internal copy of ASM, which keeps this
repo free of a new dependency but is not a public API. See CLAUDE.md section 1 for the portable
Temurin 16.0.2+7 that works on a machine without it installed.

-Jar <path> compiles against an existing SuperCC.jar instead of building a private one. The classpath
is a BUILT jar, not the baseline tree, because that is what the rebuilt class will link against at
run time: a form class that calls a method the fork changed has to be compiled against the fork's
version of it.

Exit code: 0 only if everything rebuilt or checked cleanly.
#>
[CmdletBinding()]
param([string[]]$Rebuild, [string[]]$Compile, [switch]$Check, [switch]$SelfTest, [switch]$DownloadCompiler,
      [switch]$RequireCompiler, [string]$Jar)

# Native tools write notes to stderr; under "Stop" PowerShell 5.1 turns those into terminating
# errors even on success. Exit codes are checked explicitly instead.
$ErrorActionPreference = "Continue"
$root = $PSScriptRoot
. (Join-Path $root "build-config.ps1")

# Each term parenthesized: PowerShell binds the comma tighter than -gt, so `$a.Count -gt 0, $b`
# compares against an ARRAY, fails, and under "Continue" carries on as if no mode had been given.
$modeCount = [int]($Rebuild.Count -gt 0) + [int]($Compile.Count -gt 0) + [int][bool]$Check + [int][bool]$SelfTest + [int][bool]$DownloadCompiler
if ($modeCount -ne 1) { throw "Pass exactly one of -Rebuild <file>, -Compile <file>, -Check, -SelfTest or -DownloadCompiler." }

<# JetBrains' form compiler (docs\adr\0011). The version is the IntelliJ line that built the committed
   classes (ADR 0010 read FormConstructorVisitor at the same tag). Six jars are what it runs with
   here; its POM also names org.jetbrains:annotations (runtime scope there, but measured unneeded) and com.jgoodies:forms
   (needed only by a form laid out with JGoodies' FormLayout, which none of the fifteen is -- such a
   form would fail loudly with NoClassDefFoundError, not compile wrongly). Each SHA-256 was taken
   from a download whose SHA-1 matched the one the repository publishes beside it. #>
$FORMSC_VERSION = "211.7628.21"
# Without LOCALAPPDATA there is no per-machine cache: Join-Path on $null once made -DownloadCompiler
# write into the CURRENT folder and report success (review). $null here refuses instead, below.
$FORMSC_DIR = if ($env:LOCALAPPDATA) { Join-Path $env:LOCALAPPDATA "supercc-formsc\$FORMSC_VERSION" } else { $null }
$jbJava = "https://cache-redirector.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/java"
$jbDeps = "https://cache-redirector.jetbrains.com/intellij-dependencies/org/jetbrains/intellij/deps"
$FORMSC_JARS = @(
    @{ Name = "java-gui-forms-compiler-$FORMSC_VERSION.jar"; Url = "$jbJava/java-gui-forms-compiler/$FORMSC_VERSION/java-gui-forms-compiler-$FORMSC_VERSION.jar";
       Sha256 = "23CCB811A9A49D0F5622AB9CAF47D34C020E933A042AA4A2FBC964A8D7099C60" },
    @{ Name = "java-gui-forms-rt-$FORMSC_VERSION.jar"; Url = "$jbJava/java-gui-forms-rt/$FORMSC_VERSION/java-gui-forms-rt-$FORMSC_VERSION.jar";
       Sha256 = "9A38EF9996B1EA6E76CAD688E9389363120AAFAB27D0892E8225365CD61EB279" },
    @{ Name = "java-compiler-instrumentation-util-$FORMSC_VERSION.jar"; Url = "$jbJava/java-compiler-instrumentation-util/$FORMSC_VERSION/java-compiler-instrumentation-util-$FORMSC_VERSION.jar";
       Sha256 = "927B1817F4EEFEDC0A2E545E2DB8F5356E6B73B2F0174F5BBAFF8BDD0C550489" },
    # JrtLoader: without it the finder cannot see the JDK's own classes and every form fails.
    @{ Name = "java-compiler-instrumentation-util-java8-$FORMSC_VERSION.jar"; Url = "$jbJava/java-compiler-instrumentation-util-java8/$FORMSC_VERSION/java-compiler-instrumentation-util-java8-$FORMSC_VERSION.jar";
       Sha256 = "E3A3A0464C2278C278543A27666276FFE9347ADD3AE8D3638948F942A09272A0" },
    @{ Name = "asm-all-9.1.jar"; Url = "$jbDeps/asm-all/9.1/asm-all-9.1.jar";
       Sha256 = "C9DCB554F5E410A76D3D0DFC0B06CD6156AFDDCB31A9B3831446F4927B22CBC3" },
    @{ Name = "jdom-2.0.6.jar"; Url = "$jbDeps/jdom/2.0.6/jdom-2.0.6.jar";
       Sha256 = "BA6A807A6AA9FD2D034DB2B048C32DC55254172BEF3085D683516DE9E853FBD5" }
)

# The compiler's classpath, or $null with the reason in $script:formscProblem.
# Each cached jar is COPIED into $Into -- this run's private work folder -- and the COPY is hashed
# and used. Hashing the cache and then running the cache left the whole run between the check and
# the use: a jar swapped in after the hash ran fifteen times, and with a planted FormCompile.class
# inside it, -Check passed an uncompiled .form edit (review, reproduced). This closes that window
# on the shared cache. It cannot stop a process running as the same user from writing this run's own
# work folder under %TEMP% -- nothing a script does can -- and so it does not claim to.
function Get-FormsCompilerClasspath {
    param([Parameter(Mandatory)][string]$Into)
    if (-not $FORMSC_DIR) {
        $script:formscProblem = "LOCALAPPDATA is not set, so there is no compiler cache to read"
        return $null
    }
    New-Item -ItemType Directory -Force -Path $Into | Out-Null
    $paths = @()
    foreach ($j in $FORMSC_JARS) {
        $p = Join-Path $FORMSC_DIR $j.Name
        if (-not (Test-Path -LiteralPath $p -PathType Leaf)) {
            $script:formscProblem = "$($j.Name) is not cached in $FORMSC_DIR -- run formclass.ps1 -DownloadCompiler"
            return $null
        }
        $copy = Join-Path $Into $j.Name
        try { [IO.File]::Copy($p, $copy, $true) }
        catch { $script:formscProblem = "could not copy $p to $copy -- $($_.Exception.Message)"; return $null }
        $sha = (Get-FileHash -LiteralPath $copy -Algorithm SHA256).Hash
        if ($sha -ne $j.Sha256) {
            $script:formscProblem = "$p has SHA-256 $sha, expected $($j.Sha256) -- delete it and run formclass.ps1 -DownloadCompiler"
            return $null
        }
        $paths += $copy
    }
    return ($paths -join ";")
}

if ($DownloadCompiler) {
    if (-not $FORMSC_DIR) { throw "LOCALAPPDATA is not set: there is nowhere to cache the form compiler." }
    New-Item -ItemType Directory -Force -Path $FORMSC_DIR | Out-Null
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    $savedProgress = $ProgressPreference
    $ProgressPreference = 'SilentlyContinue'   # Invoke-WebRequest's progress bar is slow non-interactively
    $failed = 0
    foreach ($j in $FORMSC_JARS) {
        $dest = Join-Path $FORMSC_DIR $j.Name
        if ((Test-Path -LiteralPath $dest) -and (Get-FileHash -LiteralPath $dest -Algorithm SHA256).Hash -eq $j.Sha256) {
            Write-Host "  ok      $($j.Name) (cached)"; continue
        }
        # Downloaded beside the target under a temporary name, checked, THEN moved into place, so a
        # truncated or substituted file is never left where the compiler would find it.
        $tmp = "$dest.part"
        $got = $false; $lastError = ""
        foreach ($attempt in 1..3) {   # retried: this sits on the CI and release critical paths
            try {
                Invoke-WebRequest -Uri $j.Url -OutFile $tmp -UseBasicParsing -ErrorAction Stop
                $got = $true; break
            } catch {
                $lastError = $_.Exception.Message
                if ($attempt -lt 3) { Start-Sleep -Seconds (3 * $attempt) }
            }
        }
        if (-not $got) { Write-Host "  FAIL    $($j.Name): could not download after 3 attempts: $lastError"; $failed++; continue }
        $sha = (Get-FileHash -LiteralPath $tmp -Algorithm SHA256).Hash
        if ($sha -ne $j.Sha256) {
            Remove-Item -LiteralPath $tmp -Force -ErrorAction SilentlyContinue
            Write-Host "  FAIL    $($j.Name): SHA-256 $sha, expected $($j.Sha256) -- deleted, not used"
            $failed++; continue
        }
        # [IO.File] calls THROW; Move-Item under "Continue" failed quietly and this still said "ok"
        # (review). And "ok" is said only for what is really in place, hashed again where it landed.
        try {
            if (Test-Path -LiteralPath $dest) { [IO.File]::Delete($dest) }
            [IO.File]::Move($tmp, $dest)
        } catch {
            Write-Host "  FAIL    $($j.Name): could not move it into $FORMSC_DIR -- $($_.Exception.Message)"
            $failed++; continue
        }
        if ((Get-FileHash -LiteralPath $dest -Algorithm SHA256).Hash -ne $j.Sha256) {
            Write-Host "  FAIL    $($j.Name): not intact after the move"; $failed++; continue
        }
        Write-Host "  ok      $($j.Name) (downloaded, SHA-256 verified)"
    }
    $ProgressPreference = $savedProgress
    $global:LASTEXITCODE = 0
    if ($failed -gt 0) { Write-Host "`n$failed jar(s) missing: the form compiler is NOT usable."; exit 1 }
    Write-Host "  JetBrains form compiler $FORMSC_VERSION ready in $FORMSC_DIR"
    exit 0
}

<# -Rebuild is -Compile now (docs\adr\0011). The transplant it used to run keeps javac's stack-map
   frames, while JetBrains' compiler -- and so -Check's byte comparison -- recomputes them as IntelliJ
   did, so a transplanted class would fail CI the moment it was committed. FormTransplant still runs on
   every compile, as a LINT: its refusals sit exactly where IntelliJ's own placement goes wrong. #>
if ($Rebuild.Count -gt 0) {
    Write-Host "  NOTE  -Rebuild is now -Compile: JetBrains' form compiler builds the class (docs\adr\0011)."
    $Compile = $Rebuild
    $Rebuild = @()
}

$jdkBin = Find-JdkBin -Requires "javap.exe"
$javac = Join-Path $jdkBin "javac.exe"
$java  = Join-Path $jdkBin "java.exe"
$javap = Join-Path $jdkBin "javap.exe"
$ver = ((& $javac -version 2>&1 | ForEach-Object { "$_" }) -join " ").Trim()
if ($ver -ne "javac 16.0.2") {
    throw ("formclass.ps1 needs JDK 16.0.2 and found '$ver' at $jdkBin. Set JAVA_HOME to a 16.0.2 JDK " +
           "for this command (CLAUDE.md section 1 says where to get a portable one).")
}

$srcRoot = (Get-Item -LiteralPath (Join-Path $root "java")).FullName.TrimEnd([char]92)
$asmExports = @("--add-exports", "java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED",
                "--add-exports", "java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED")

# Is $Name one of the class files <Base>.java produces -- <Base>.class or <Base>$*.class? The ONE
# place that rule lives. Case-insensitive, like the file system: a wrong-case -Rebuild argument once
# replaced the top-level class and left both inner classes stale, because -eq ignored case and
# StartsWith did not.
function Test-ClassOf {
    param([string]$Name, [string]$Base)
    return ($Name -eq "$Base.class" -or $Name.StartsWith($Base + '$', [StringComparison]::OrdinalIgnoreCase))
}

# The class files <Base>.java produces, directly in $Dir.
function Get-ClassSet {
    param([string]$Dir, [string]$Base)
    if (-not (Test-Path $Dir)) { return @() }
    return @(Get-ChildItem $Dir -File -Filter "*.class" | Where-Object { Test-ClassOf $_.Name $Base } | Sort-Object Name)
}

# Every .class javac wrote that is NOT <Base>.class or <Base>$*.class: a second top-level class in
# the same .java. Copying only the expected set dropped it silently -- measured by review: -Rebuild
# exited 0, -Check passed, and the window threw NoClassDefFoundError. Committing it instead would
# trip verify-splice's orphan check (no <Helper>.java), so it is refused: give it its own file.
function Get-UnexpectedClasses {
    param([string]$JavacRoot, [string]$Pkg, [string]$Base)
    # Canonical first (build-config.ps1): CI's TEMP is an 8.3 short path and Get-ChildItem reports
    # long names, so a raw comparison would call every class unexpected.
    $JavacRoot = Get-CanonicalDir $JavacRoot
    $expected = if ($Pkg) { Join-Path $JavacRoot $Pkg } else { $JavacRoot }
    return @(Get-ChildItem $JavacRoot -Recurse -File -Filter "*.class" |
             Where-Object { $_.DirectoryName -ne $expected -or -not (Test-ClassOf $_.Name $Base) } |
             ForEach-Object { $_.FullName.Substring($JavacRoot.Length + 1) })
}

# The fifteen form classes, as java\-relative paths in their TRUE case (the .form files define them).
$formRels = @(Get-ChildItem $srcRoot -Recurse -Filter *.form | Sort-Object FullName |
              ForEach-Object { [IO.Path]::ChangeExtension($_.FullName.Substring($srcRoot.Length + 1), ".java") })

# Short names on purpose: Windows paths cap at 260 characters, and package folders, class names
# and inner-class suffixes are appended below this.
$work = Join-Path $env:TEMP ("scc-fc-" + [Guid]::NewGuid().ToString("N").Substring(0, 8))
New-Item -ItemType Directory -Force -Path $work | Out-Null
$problems = 0
try {
    # The classpath: a built jar (see the header for why not the baseline tree).
    if ($Jar) {
        # Test-Path first: Resolve-Path fails NON-terminating, which left $cp null, dropped the
        # argument from javac's command line, and reported fifteen misleading "does not compile".
        if (-not (Test-Path -LiteralPath $Jar -PathType Leaf)) { throw "-Jar '$Jar' does not exist" }
        $cp = (Resolve-Path -LiteralPath $Jar).Path
    } else {
        $cp = Join-Path $work "classpath.jar"
        $buildLog = & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $root "build.ps1") -Out $cp 2>&1
        if ($LASTEXITCODE -ne 0 -or -not (Test-Path $cp)) {
            $buildLog | Write-Host
            throw "build.ps1 failed, so there is no classpath to compile against"
        }
    }

    $toolOut = Join-Path $work "tool"
    New-Item -ItemType Directory -Force -Path $toolOut | Out-Null
    & $javac @asmExports -d $toolOut (Join-Path $root "formclass\FormTransplant.java")
    if ($LASTEXITCODE -ne 0) { throw "FormTransplant.java does not compile (exit $LASTEXITCODE)" }
    # The compiler: required to compile, and for -Check / -SelfTest required when -RequireCompiler
    # says so (CI, the release workflow). Without it locally they still run, minus the byte check,
    # and say so loudly -- that is the weaker check, and a green run must not be mistaken for it.
    $formscCp = Get-FormsCompilerClasspath -Into (Join-Path $work "formsc")
    if (-not $formscCp) {
        if ($Compile -or $RequireCompiler) { throw $script:formscProblem }
        Write-Host ""
        Write-Host "  WARN  JetBrains' form compiler is not available ($script:formscProblem)."
        Write-Host "  WARN  Skipping the BYTE check -- a .form edit that was never compiled would pass. CI requires it."
    } else {
        & $javac -cp $formscCp -d $toolOut (Join-Path $root "formclass\FormCompile.java")
        if ($LASTEXITCODE -ne 0) { throw "FormCompile.java does not compile (exit $LASTEXITCODE)" }
    }

    # javac's class + its .form -> JetBrains' compiler -> $OutClass; then the transplant's refusals
    # as a lint over the same javac output, with the members taken from that fresh output. Returns
    # $null on success, else the reason. The lint is what stops IntelliJ's own known misplacements
    # (ADR 0010: a second setup in a this(...) constructor, a bound read before the call, a try or
    # loop that can skip it) -- the real compiler reproduces those faithfully, bugs included -- and
    # its result must also agree with the compiler's on where the call goes.
    function Invoke-FormCompile {
        param([string]$Form, [string]$JavacClass, [string]$OutClass, [string]$JavacRoot, [string]$Fqcn)
        # The repo's own driver FIRST: a jar earlier on the path could otherwise shadow FormCompile.
        $msg = ((& $java -cp "$toolOut;$formscCp" FormCompile $Form $JavacClass $OutClass $JavacRoot $cp 2>&1 |
                 ForEach-Object { "$_" }) -join "`n").Trim()
        if ($LASTEXITCODE -ne 0) { return "JetBrains' form compiler failed: $msg" }
        $lintRoot = Join-Path $work ("lint-" + [Guid]::NewGuid().ToString("N").Substring(0, 8))
        $pkgPath = ($Fqcn -replace '\.[^.]+$', '') -replace '\.', '\'
        $lintClass = Join-Path (Join-Path $lintRoot $pkgPath) ([IO.Path]::GetFileName($OutClass))
        New-Item -ItemType Directory -Force -Path (Split-Path $lintClass) | Out-Null
        $lint = ((& $java @asmExports -cp $toolOut FormTransplant $OutClass $JavacClass $Form $lintClass 2>&1 |
                  ForEach-Object { "$_" }) -join "`n").Trim()
        if ($LASTEXITCODE -ne 0) { return "refused by the lint (IntelliJ's placement is not proven safe here): $lint" }
        $compiledRoot = $OutClass.Substring(0, $OutClass.Length - (($Fqcn -replace '\.', '\') + ".class").Length).TrimEnd([char]92)
        if ((Get-ClassKey $lintRoot $Fqcn -IgnorePoolIndexes) -ne (Get-ClassKey $compiledRoot $Fqcn -IgnorePoolIndexes)) {
            return "the compiler and the lint disagree on where `$`$`$setupUI`$`$`$ is called -- refused rather than guessed"
        }
        return $null
    }

    <# The BYTE check (docs\adr\0011), in ONE function that -Check and -SelfTest both call. -SelfTest
       used to carry its own copy of the comparison, and with -Check's made to compare a hash with
       itself, -SelfTest still reported 22 of 22 while -Check passed an uncompiled .form edit
       (review, reproduced). Compiles <Base> from javac's output under $JavacRoot plus $Form, and
       compares the whole class set -- names and bytes -- with $CommittedDir. $null when they match,
       otherwise the reason. #>
    function Test-CompiledBytes {
        param([string]$Form, [string]$JavacRoot, [string]$Pkg, [string]$Base, [string]$CommittedDir, [string]$Scratch)
        $newDir = if ($Pkg) { Join-Path $JavacRoot $Pkg } else { $JavacRoot }
        $bcDir  = if ($Pkg) { Join-Path $Scratch $Pkg } else { $Scratch }
        New-Item -ItemType Directory -Force -Path $bcDir | Out-Null
        foreach ($c in (Get-ClassSet $newDir $Base)) { [IO.File]::Copy($c.FullName, (Join-Path $bcDir $c.Name), $true) }
        $fqcn = if ($Pkg) { ($Pkg -replace '\\', '.') + ".$Base" } else { $Base }
        $why = Invoke-FormCompile $Form (Join-Path $newDir "$Base.class") (Join-Path $bcDir "$Base.class") $JavacRoot $fqcn
        if ($why) { return $why }
        $made = @(Get-ClassSet $bcDir $Base); $have = @(Get-ClassSet $CommittedDir $Base)
        if ((($made | ForEach-Object Name) -join " ") -ne (($have | ForEach-Object Name) -join " ")) {
            return ("compiles to [" + (($made | ForEach-Object Name) -join " ") + "] but the committed classes are [" +
                    (($have | ForEach-Object Name) -join " ") + "]")
        }
        $notSame = @()
        foreach ($c in $made) {
            if ((Get-FileHash -LiteralPath $c.FullName -Algorithm SHA256).Hash -ne
                (Get-FileHash -LiteralPath (Join-Path $CommittedDir $c.Name) -Algorithm SHA256).Hash) { $notSame += $c.Name }
        }
        if ($notSame.Count -gt 0) {
            return ("the committed " + ($notSame -join ", ") + " is not what javac + JetBrains' compiler make from the " +
                    "committed .java and .form -- a .form edit never compiled, or a class built another way")
        }
        return $null
    }

    $empty = Join-Path $work "__empty"
    New-Item -ItemType Directory -Force -Path $empty | Out-Null

    if ($SelfTest) {
        <# Proves FormTransplant still refuses what it must, and still places the call where IntelliJ
           does. Each refusal guards a class that would compile, rebuild, pass -Check, and then open
           a window with no components (the "nothing happens" failure of ADR 0001) or crash on open.
           Built from three classes: tools\SeedSearch (field initializers, custom-created
           components, default-binding attributes), tools\ChangeTimer (a constructor body that runs
           first, for the control-flow cases) and tools\ChangeInventory (it extends JPanel). #>
        Write-Host "`n== self-test on $ver (built from tools\SeedSearch, ChangeTimer, ChangeInventory) =="
        $utf8 = New-Object Text.UTF8Encoding $false
        $seed  = [IO.File]::ReadAllText((Join-Path $srcRoot "tools\SeedSearch.java"))
        $timer = [IO.File]::ReadAllText((Join-Path $srcRoot "tools\ChangeTimer.java"))
        $selfCount = 0

        function Test-Specimen {
            param([string]$Class, [string]$Source, [switch]$JavacIsIj, [switch]$CommittedAsJavac)
            $d = Join-Path $work ("st-" + [Guid]::NewGuid().ToString("N").Substring(0, 8))
            $s = Join-Path $d "src\tools"; $j = Join-Path $d "jc"; $o = Join-Path $d "out\tools"
            New-Item -ItemType Directory -Force -Path $s, $j, $o | Out-Null
            [IO.File]::WriteAllText((Join-Path $s "$Class.java"), $Source, $utf8)
            & $javac -g --release $SPLICE_RELEASE -encoding $SPLICE_ENCODING -cp $cp -sourcepath $empty `
                     -implicit:none -d $j (Join-Path $s "$Class.java") 2>&1 | Out-Null
            if ($LASTEXITCODE -ne 0) { return @{ Exit = -1; Text = "specimen does not compile"; Out = $null; Extra = @() } }
            $extra = Get-UnexpectedClasses $j "tools" $Class
            $committed  = Join-Path $root "tools\$Class.class"
            $javacClass = Join-Path $j "tools\$Class.class"
            $ij  = if ($JavacIsIj) { $javacClass } else { $committed }
            $in  = if ($CommittedAsJavac) { $committed } else { $javacClass }
            # "$_" per line, not Out-String: stderr arrives as ErrorRecords, and Out-String renders
            # those wrapped at the console width -- which once split a message across two lines.
            $text = ((& $java @asmExports -cp $toolOut FormTransplant $ij $in (Join-Path $srcRoot "tools\$Class.form") `
                      (Join-Path $o "$Class.class") 2>&1 | ForEach-Object { "$_" }) -join "`n").Trim()
            return @{ Exit = $LASTEXITCODE; Text = $text; Out = (Join-Path $d "out"); Extra = $extra }
        }

        # javap -c lines of one constructor, chosen by its parameter list as javap prints it.
        function Get-CtorLines {
            param([string]$ClassDir, [string]$Class, [string]$Params)
            $out = @(); $in = $false
            foreach ($line in (& $javap -c -p -cp $ClassDir "tools.$Class")) {
                if ($line -match "^\s+(public |private |protected )?tools\.$Class\((.*)\);") { $in = ($Matches[2] -eq $Params); continue }
                if ($line -match '^\s*$') { $in = $false; continue }
                if ($in) { $out += $line }
            }
            return $out
        }
        function Get-Index { param($Lines, [string]$Pattern)
            for ($i = 0; $i -lt $Lines.Count; $i++) { if ($Lines[$i] -match $Pattern) { return $i } }
            return -1 }
        $setupRx = 'Method \$\$\$setupUI\$\$\$:\(\)V'

        function Assert-Self { param([string]$What, [bool]$Ok, [string]$Detail)
            $script:selfCount++
            if ($Ok) { Write-Host "  PASS  $What" } else { Write-Host "  FAIL  $What"; if ($Detail) { Write-Host "        $Detail" }; $script:problems++ } }
        function Assert-Refused { param([string]$What, $R, [string]$Pattern)
            Assert-Self "REFUSED: $What" ($R.Exit -gt 0 -and $R.Text -match $Pattern) ("exit $($R.Exit): $($R.Text)") }

        $seedParams = 'emulator.SuperCC, emulator.Solution'
        $r = Test-Specimen SeedSearch $seed
        $ctor = if ($r.Exit -eq 0) { Get-CtorLines $r.Out SeedSearch $seedParams } else { @() }
        Assert-Self "the unchanged SeedSearch rebuilds (its default-binding attributes are not fields)" ($r.Exit -eq 0) $r.Text
        Assert-Self "and its constructor calls `$`$`$setupUI`$`$`$ exactly once" (@($ctor | Where-Object { $_ -match $setupRx }).Count -eq 1)

        $r = Test-Specimen SeedSearch ($seed -replace '\bstartField\b', 'startFieldRenamed')
        Assert-Refused "a bound field renamed in the .java but not the .form" $r "binds 'startField'"

        $r = Test-Specimen SeedSearch ($seed.Replace('private JTextField positionField;', 'private JTextArea positionField;'))
        Assert-Refused "a bound field whose type changed" $r "declares it"

        # Measured by review: accepted, every gate green, IllegalAccessError the moment the window opened.
        $r = Test-Specimen SeedSearch ($seed.Replace('    private JTextField startField;', '    private final JTextField startField = new JTextField();'))
        Assert-Refused "a final bound field (`$`$`$setupUI`$`$`$ cannot assign it; IntelliJ refuses it too)" $r "is final"

        # Measured by review: accepted, every gate green, IncompatibleClassChangeError on open --
        # $$$setupUI$$$ calls createUIComponents() with invokespecial, which a static method refuses.
        $r = Test-Specimen SeedSearch ($seed -replace '(?s)    private void createUIComponents\(\) \{.*?\n    \}', '    private static void createUIComponents() { }')
        Assert-Refused "a method `$`$`$setupUI`$`$`$ calls, changed to static" $r "declares it the other way"

        $r = Test-Specimen SeedSearch $seed -CommittedAsJavac
        Assert-Refused "an already-instrumented class given as javac's output" $r "already has"

        $r = Test-Specimen SeedSearch $seed -JavacIsIj
        Assert-Refused "a class with no generated members given as IntelliJ's" $r "not a form-compiled class"

        $anchor = '    private void createUIComponents() {'
        $r = Test-Specimen SeedSearch ($seed.Replace($anchor, "    public SeedSearch(SuperCC emulator, Solution solution, int unused) { this(emulator, solution); }`n`n" + $anchor))
        $main = if ($r.Exit -eq 0) { @(Get-CtorLines $r.Out SeedSearch $seedParams | Where-Object { $_ -match $setupRx }).Count } else { -1 }
        $del  = if ($r.Exit -eq 0) { @(Get-CtorLines $r.Out SeedSearch "$seedParams, int" | Where-Object { $_ -match $setupRx }).Count } else { -1 }
        Assert-Self "a this(...) constructor is left alone; the one calling super() gets the call" ($r.Exit -eq 0 -and $main -eq 1 -and $del -eq 0) ("exit $($r.Exit); main=$main delegating=$del; $($r.Text)")

        # IntelliJ reads `invokespecial <init>` on the SUPERCLASS as super() again, not as a trigger.
        # Measured by review: treating `new Object()` as a trigger ran $$$setupUI$$$ before `lock` was
        # assigned, and a createUIComponents() that used it threw NullPointerException.
        $r = Test-Specimen SeedSearch ($seed.Replace('    private DecimalFormat df;', "    private final Object lock = new Object();`n    private DecimalFormat df;"))
        $ctor = if ($r.Exit -eq 0) { Get-CtorLines $r.Out SeedSearch $seedParams } else { @() }
        $iLock = Get-Index $ctor 'putfield .*// Field lock:'; $iSetup = Get-Index $ctor $setupRx
        Assert-Self "``new Object()`` in a class extending Object is not a trigger: the call comes after ``lock`` is assigned" ($r.Exit -eq 0 -and $iLock -ge 0 -and $iSetup -gt $iLock) ("exit $($r.Exit); putfield lock at $iLock, setup at $iSetup; $($r.Text)")

        $timerAnchor = '    public ChangeTimer(SuperCC emulator){'
        function Timer-With { param([string]$Body) return $timer.Replace($timerAnchor, "$timerAnchor`n$Body") }

        # A jump IS a trigger, so an early `if` gets the call BEFORE the branch, on both paths. (Review
        # measured the older linear rule putting it inside the throw arm: NullPointerException on open.)
        $r = Test-Specimen ChangeTimer (Timer-With '        if (emulator == null) throw new IllegalArgumentException("no emulator");')
        $ctor = if ($r.Exit -eq 0) { Get-CtorLines $r.Out ChangeTimer 'emulator.SuperCC' } else { @() }
        $iJump = Get-Index $ctor '\s(if\w+|goto)\s'; $iSetup = Get-Index $ctor $setupRx
        Assert-Self "an early ``if``: accepted, with the call before the branch, as IntelliJ places it" ($r.Exit -eq 0 -and $iSetup -ge 0 -and $iJump -gt $iSetup) ("exit $($r.Exit); jump at $iJump, setup at $iSetup; $($r.Text)")

        $r = Test-Specimen ChangeTimer (Timer-With '        int n = 0; while (n < 3) n++;')
        Assert-Refused "a loop before the first call (the call would sit inside it)" $r "a jump lands"

        $r = Test-Specimen ChangeTimer (Timer-With '        int k = 1; switch (k) { case 0: k = 5; break; default: k = 6; }')
        Assert-Refused "a switch before the first call (the call would sit in one case)" $r "a switch or throw"

        # Measured by review: accepted, and with divisor 0 the window opened with no components.
        $r = Test-Specimen ChangeTimer (Timer-With '        int d = 1; int q; try { q = 10 / d; emulator.getLevel(); } catch (ArithmeticException e) { q = 0; }')
        Assert-Refused "a try block around code before the first call (an exception could skip it)" $r "inside a try block"

        # Extra constructors added next to the real one, the shapes IntelliJ's own flag gets wrong.
        $extraCtors = "    private ChangeTimer(int unused) { }`n    private ChangeTimer(Object o, int unused) { }`n"
        function Timer-WithCtor { param([string]$Ctor) return $timer.Replace($timerAnchor, $extraCtors + $Ctor + "`n" + $timerAnchor) }

        # Measured by review: `new ChangeTimer(0)` read as this(...), the bound read after it ignored,
        # the call placed after `getfield spinner` -- NullPointerException on open.
        $r = Test-Specimen ChangeTimer (Timer-WithCtor '    public ChangeTimer(SuperCC emulator, String s) { new ChangeTimer(0); spinner.setValue(1.0); }')
        Assert-Refused "a ``new ChangeTimer(...)`` before the first call (IntelliJ's flag mistakes it for this(...))" $r "constructs another"

        # Measured by review: the inner Object.<init> set IntelliJ's flag first, and the delegating
        # constructor got a SECOND call -- every component built twice.
        $r = Test-Specimen ChangeTimer (Timer-WithCtor '    public ChangeTimer(SuperCC emulator, boolean b) { this(new Object(), 1); spinner.setValue(1.0); }')
        $dele = if ($r.Exit -eq 0) { @(Get-CtorLines $r.Out ChangeTimer 'emulator.SuperCC, boolean' | Where-Object { $_ -match $setupRx }).Count } else { -1 }
        $targ = if ($r.Exit -eq 0) { @(Get-CtorLines $r.Out ChangeTimer 'java.lang.Object, int' | Where-Object { $_ -match $setupRx }).Count } else { -1 }
        Assert-Self "``this(new Object(), 1)``: the delegating constructor gets NO call, the one it reaches gets one" ($r.Exit -eq 0 -and $dele -eq 0 -and $targ -eq 1) ("exit $($r.Exit); delegating=$dele target=$targ; $($r.Text)")

        $r = Test-Specimen ChangeTimer ($timer + "`nclass ChangeTimerHelper { }`n")
        Assert-Self "REFUSED: a second top-level class in the .java (formclass.ps1 sees javac write it)" (@($r.Extra).Count -eq 1 -and @($r.Extra)[0] -match 'ChangeTimerHelper\.class$') ("unexpected classes: " + (@($r.Extra) -join ", "))

        # ChangeInventory extends JPanel, so `new JPanel()` inside super(...)'s own arguments looks like
        # super(...) to IntelliJ's flag. Measured by review: the call landed before the real super(...)
        # and the class failed verification (VerifyError) the moment it was loaded.
        $inv = [IO.File]::ReadAllText((Join-Path $srcRoot "tools\ChangeInventory.java"))
        $r = Test-Specimen ChangeInventory ($inv.Replace('    private ChangeInventory(){}', "    private ChangeInventory(){}`n    public ChangeInventory(SuperCC emulator, int unused) { super(new JPanel().getLayout()); }"))
        Assert-Refused "a ``new <superclass>()`` inside super(...)'s arguments (the call would precede the real super)" $r "before super"

        <# JetBrains' compiler (docs\adr\0011): that -Check's byte comparison catches the two things it
           exists for, and that -Compile's lint refuses what IntelliJ's own placement gets wrong. #>
        if ($formscCp) {
            # javac on a specimen source, into a fresh folder; returns that folder (javac's root).
            function Javac-Specimen {
                param([string]$Class, [string]$Source)
                $d = Join-Path $work ("sc-" + [Guid]::NewGuid().ToString("N").Substring(0, 8))
                $s = Join-Path $d "src\tools"; $j = Join-Path $d "jc"
                New-Item -ItemType Directory -Force -Path $s, $j | Out-Null
                [IO.File]::WriteAllText((Join-Path $s "$Class.java"), $Source, $utf8)
                & $javac -g --release $SPLICE_RELEASE -encoding $SPLICE_ENCODING -cp $cp -sourcepath $empty `
                         -implicit:none -d $j (Join-Path $s "$Class.java") 2>&1 | Out-Null
                if ($LASTEXITCODE -ne 0) { return $null }
                return $j
            }
            function Compile-Specimen {
                param([string]$Class, [string]$Source, [string]$Form)
                $j = Javac-Specimen $Class $Source
                if (-not $j) { return @{ Why = "specimen does not compile" } }
                $o = Join-Path (Split-Path $j) "out\tools"
                New-Item -ItemType Directory -Force -Path $o | Out-Null
                return @{ Why = (Invoke-FormCompile $Form (Join-Path $j "tools\$Class.class") (Join-Path $o "$Class.class") $j "tools.$Class") }
            }
            # The three byte-check cases go through Test-CompiledBytes -- the function -Check itself
            # runs -- so a broken comparison fails here too (review).
            $cgSrc  = [IO.File]::ReadAllText((Join-Path $srcRoot "tools\ControlGUI.java"))
            $cgForm = Join-Path $srcRoot "tools\ControlGUI.form"
            $cgJc   = Javac-Specimen ControlGUI $cgSrc
            $why = if ($cgJc) { Test-CompiledBytes $cgForm $cgJc "tools" "ControlGUI" (Join-Path $root "tools") (Join-Path $work "bc-ok") } else { "does not compile" }
            Assert-Self "the byte check passes the committed ControlGUI (JetBrains' compiler reproduces it exactly)" (-not $why) $why
            # A one-word label edit in the .form, never compiled: the transplant check copies the
            # generated members from the committed class and so cannot see it; the byte check must.
            $edited = Join-Path $work "ControlGUI-edited.form"
            [IO.File]::WriteAllText($edited, ([IO.File]::ReadAllText($cgForm)).Replace('<text value="Up Left"/>', '<text value="Up-Left"/>'), $utf8)
            $why = if ($cgJc) { Test-CompiledBytes $edited $cgJc "tools" "ControlGUI" (Join-Path $root "tools") (Join-Path $work "bc-form") } else { $null }
            Assert-Self "the byte check FAILS a .form edit that was never compiled" ($why -match 'is not what javac') $why
            # A class built by the transplant keeps javac's stack-map frames; IntelliJ recomputed them.
            # Put it where the committed classes would be, beside the real committed inner classes.
            $t = Test-Specimen SeedSearch $seed
            $fake = Join-Path $work "fake-committed\tools"
            New-Item -ItemType Directory -Force -Path $fake | Out-Null
            foreach ($c in (Get-ClassSet (Join-Path $root "tools") "SeedSearch")) { [IO.File]::Copy($c.FullName, (Join-Path $fake $c.Name), $true) }
            if ($t.Exit -eq 0) { [IO.File]::Copy((Join-Path $t.Out "tools\SeedSearch.class"), (Join-Path $fake "SeedSearch.class"), $true) }
            $ssJc = Javac-Specimen SeedSearch $seed
            $why = if ($t.Exit -eq 0 -and $ssJc) { Test-CompiledBytes (Join-Path $srcRoot "tools\SeedSearch.form") $ssJc "tools" "SeedSearch" $fake (Join-Path $work "bc-tp") } else { $null }
            Assert-Self "the byte check FAILS a class built by the transplant" ($why -match 'SeedSearch\.class is not what javac') ("$why $($t.Text)")
            # The lint: IntelliJ's flag mistakes `new ChangeTimer(0)` for this(...) and places the call
            # after a bound read (NullPointerException on open, ADR 0010). JetBrains' compiler does
            # exactly that; -Compile must refuse it.
            $l = Compile-Specimen ChangeTimer (Timer-WithCtor '    public ChangeTimer(SuperCC emulator, String s) { new ChangeTimer(0); spinner.setValue(1.0); }') (Join-Path $srcRoot "tools\ChangeTimer.form")
            Assert-Self "REFUSED by -Compile's lint: a ``new ChangeTimer(...)`` before the call, which IntelliJ's compiler misplaces" ($l.Why -match 'refused by the lint') $l.Why
        } else {
            Write-Host "  SKIP  the four JetBrains-compiler checks (no compiler; run -DownloadCompiler)"
        }

        if ($problems -eq 0) { Write-Host "  PASS  self-test: $selfCount of $selfCount" }
        $targets = @()
    }
    elseif ($Check) {
        $targets = $formRels
    } else {
        # Under -File, `-Rebuild a,b` arrives as ONE string, so commas are split here. Each name is
        # matched case-insensitively against the form classes and replaced by its true-case path.
        $targets = @()
        foreach ($t in @(@($Rebuild) + @($Compile) | ForEach-Object { $_ -split ',' } | ForEach-Object { $_.Trim() } | Where-Object { $_ })) {
            $want = $t -replace '/', '\' -replace '^java\\', ''
            $hit = @($formRels | Where-Object { $_ -eq $want })   # -eq ignores case
            if ($hit.Count -eq 1) { $targets += $hit[0]; continue }
            if (Test-Path (Join-Path $srcRoot $want)) {
                Write-Host "  FAIL  $want has no sibling .form -- it is an ordinary file; splice it (build-config.ps1)"
            } else { Write-Host "  FAIL  $want does not exist" }
            $problems++
        }
    }

    if (-not $SelfTest) {
        Write-Host ("`n== " + $(if ($Check) { "checking" } elseif ($Compile) { "compiling (JetBrains form compiler $FORMSC_VERSION)" } else { "rebuilding" }) + " $($targets.Count) form class(es) on $ver ==")
    }
    foreach ($rel in $targets) {
        $src  = Join-Path $srcRoot $rel
        $form = [IO.Path]::ChangeExtension($src, ".form")

        $pkg  = Split-Path $rel
        $base = [IO.Path]::GetFileNameWithoutExtension($rel)
        $fqcnBase = if ($pkg) { ($pkg -replace '\\', '.') + "." } else { "" }
        $committedDir = Join-Path $root $pkg
        $committedTop = Join-Path $committedDir "$base.class"
        if (-not $Compile -and -not (Test-Path $committedTop)) { Write-Host "  FAIL  $rel has no committed $base.class to take the generated members from"; $problems++; continue }

        $one = Join-Path $work ([Guid]::NewGuid().ToString("N").Substring(0, 8))
        $jc  = Join-Path $one "javac"
        $out = Join-Path $one "out"
        New-Item -ItemType Directory -Force -Path $jc, $out | Out-Null
        # -g: IntelliJ compiles with full debug info, and the committed classes carry it.
        & $javac -g --release $SPLICE_RELEASE -encoding $SPLICE_ENCODING -cp $cp -sourcepath $empty `
                 -implicit:none -d $jc $src 2>&1 | Where-Object { $_ -notmatch '^Note:' } | ForEach-Object { Write-Host "        $_" }
        if ($LASTEXITCODE -ne 0) { Write-Host "  FAIL  $rel does not compile"; $problems++; continue }
        $extra = Get-UnexpectedClasses $jc $pkg $base
        if ($extra.Count -gt 0) {
            Write-Host ("  FAIL  $rel also compiles to " + ($extra -join ", ") + " -- a second top-level class in a form " +
                        ".java is not supported here: move it to its own .java (which the splice build can compile)")
            $problems++; continue
        }

        # The rebuilt set lands in a package-shaped tree, so javap can read it with $out as classpath.
        $newDir = Join-Path $jc $pkg
        $outDir = Join-Path $out $pkg
        New-Item -ItemType Directory -Force -Path $outDir | Out-Null
        $newSet = Get-ClassSet $newDir $base
        # [IO.File]::Copy THROWS; Copy-Item under "Continue" does not. Measured by review: with TEMP
        # deep enough to push an inner class past 260 characters, Copy-Item skipped it quietly, the
        # rebuilt set was one class short, and -Rebuild deleted the committed one and printed DONE.
        try {
            foreach ($c in $newSet) { [IO.File]::Copy($c.FullName, (Join-Path $outDir $c.Name), $true) }
        } catch {
            Write-Host "  FAIL  $rel could not stage javac's classes: $($_.Exception.Message)"
            Write-Host "        (a TEMP path too deep for Windows' 260-character limit does this -- use a shorter TEMP)"
            $problems++; continue
        }
        if ($Compile) {
            # The finder sees javac's output root (the inner classes) and the built jar (the program),
            # after the JDK's own classes, which FormCompile puts first.
            $why = Invoke-FormCompile $form (Join-Path $newDir "$base.class") (Join-Path $outDir "$base.class") $jc ($fqcnBase + $base)
            if ($why) { Write-Host "  FAIL  $rel : $why"; $problems++; continue }
        } else {
            & $java @asmExports -cp $toolOut FormTransplant $committedTop (Join-Path $newDir "$base.class") $form (Join-Path $outDir "$base.class") 2>&1 |
                ForEach-Object { Write-Host "        $_" }
            if ($LASTEXITCODE -ne 0) { Write-Host "  FAIL  $rel could not be rebuilt (see the message above)"; $problems++; continue }
        }
        $outSet = Get-ClassSet $outDir $base
        # And checked, not assumed: the rebuilt set must name exactly the classes javac wrote.
        if ((($newSet | ForEach-Object Name) -join " ") -ne (($outSet | ForEach-Object Name) -join " ")) {
            Write-Host ("  FAIL  $rel : javac wrote [" + (($newSet | ForEach-Object Name) -join " ") + "] but the rebuilt set is [" +
                        (($outSet | ForEach-Object Name) -join " ") + "]")
            $problems++; continue
        }

        if ($Compile) {
            # Read everything we need from the committed class BEFORE touching it -- the transplant
            # above already did. Now replace the whole set, so a dropped inner class goes too.
            #
            # .NET calls, which THROW, not Copy-Item, which fails non-terminating under "Continue":
            # measured by review with a TEMP deep enough to pass 260 characters, Copy-Item failed
            # quietly, an inner class was deleted and not replaced, and this printed DONE, exit 0.
            # And the result is checked, not assumed.
            try {
                foreach ($c in (Get-ClassSet $committedDir $base)) { [IO.File]::Delete($c.FullName) }
                foreach ($c in $outSet) { [IO.File]::Copy($c.FullName, (Join-Path $committedDir $c.Name), $true) }
            } catch {
                Write-Host "  FAIL  $rel could not replace its committed classes: $($_.Exception.Message)"
                Write-Host "        The tree may be half-written: restore with  git checkout -- $(Join-Path $pkg "$base*.class")"
                $problems++; continue
            }
            $want = @($outSet | ForEach-Object { "{0} {1}" -f $_.Name, (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash })
            $got  = @(Get-ClassSet $committedDir $base | ForEach-Object { "{0} {1}" -f $_.Name, (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash })
            if (($want -join "`n") -ne ($got -join "`n")) {
                Write-Host "  FAIL  $rel was rebuilt, but the committed classes do not match what was built -- restore with git checkout"
                $problems++; continue
            }
            Write-Host ("  DONE  $rel -> " + (($outSet | ForEach-Object { Join-Path $pkg $_.Name }) -join ", "))
            continue
        }

        $committedSet = Get-ClassSet $committedDir $base
        $a = ($committedSet | ForEach-Object Name) -join " "
        $b = ($outSet | ForEach-Object Name) -join " "
        if ($a -ne $b) {
            Write-Host "  FAIL  $rel compiles to [$b] but the committed classes are [$a]"
            $problems++; continue
        }
        $differs = @()
        foreach ($c in $outSet) {
            $fqcn = $fqcnBase + [IO.Path]::GetFileNameWithoutExtension($c.Name)
            # build-config.ps1's key, the one verify-splice uses, without pool index numbers.
            if ((Get-ClassKey $out $fqcn -IgnorePoolIndexes) -ne (Get-ClassKey $root $fqcn -IgnorePoolIndexes)) { $differs += $c.Name }
        }
        if ($differs.Count -gt 0) {
            Write-Host ("  FAIL  $rel does not match its committed bytecode (" + ($differs -join ", ") + ") -- the jar " +
                        "would ship the old class. Compile it: formclass.ps1 -Compile $rel")
            $problems++
            continue
        }
        <# The BYTE check (docs\adr\0011): javac + JetBrains' compiler, from the committed .java AND .form,
           must reproduce every committed class exactly. The check above copies the generated members
           from the committed class, so it cannot see a .form edit at all; this can. Byte-identical
           because it was measured to be: all fifteen IntelliJ originals reproduce exactly. #>
        if ($formscCp) {
            $why = Test-CompiledBytes $form $jc $pkg $base $committedDir (Join-Path $one "compiled")
            # The ONE line -SelfTest does not reach: it proves Test-CompiledBytes, not this call site
            # (review: making this `if` ignore $why passed -SelfTest). Keep it this small.
            if ($why) { Write-Host "  FAIL  $rel : $why. Compile it: formclass.ps1 -Compile $rel"; $problems++; continue }
        }
        Write-Verbose "  ok  $rel"
    }
    if ($Check -and $problems -eq 0) {
        if ($formscCp) { Write-Host "  PASS  all $($targets.Count) form classes match their .java, and byte for byte what JetBrains' compiler makes of .java + .form" }
        else           { Write-Host "  PASS  all $($targets.Count) form classes match their .java (byte check SKIPPED: no compiler)" }
    }
}
finally {
    # A cleanup failure (the same deep-TEMP case) must not hide the run's real result, so it warns.
    try { if (Test-Path $work) { [IO.Directory]::Delete($work, $true) } }
    catch { Write-Host "  WARN  could not remove the temp folder $work -- $($_.Exception.Message)" }
}

$global:LASTEXITCODE = 0
if ($problems -gt 0) { Write-Host "`n$problems problem(s)."; exit 1 }
exit 0
