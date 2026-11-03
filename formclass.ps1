<#
Rebuilds -- or checks -- the committed bytecode of a FORM-BASED class whose .form is unchanged.

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
    Overwrites the committed <Name>.class and every <Name>$*.class with the rebuilt set, deleting
    any committed inner class the new source no longer produces (a leftover one would ship
    unseen: verify-splice's orphan check only asks whether <Name>.java exists). Then re-run
    verify-splice.ps1 -UpdateFormBaseline and commit the classes and the baseline together.

-Check
    Rebuilds all fifteen into a temp folder and compares each against the committed classes, as
    verify-splice compares ordinary files: the disassembly plus the sorted string constants, with
    constant-pool index numbers removed (IntelliJ numbered the pool differently in the three forms
    that use $$$getFont$$$; the code is identical). This is what proves a committed form class
    matches its .java. It cannot prove the class matches its .form -- verify-splice's .form hash
    does that part, and a .form edit still needs IntelliJ. Nor can it prove the TOOL right: once a
    class has been rebuilt, both sides of the comparison came from FormTransplant. That is
    test\FormClassTest.java's job -- it runs every form window's real constructor.

-SelfTest
    Eighteen checks on seventeen specimens built from tools\SeedSearch, ChangeTimer and ChangeInventory:
    each refusal that guards a window that would break when opened, and the placements that differ
    from a naive rule.

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
param([string[]]$Rebuild, [switch]$Check, [switch]$SelfTest, [string]$Jar)

# Native tools write notes to stderr; under "Stop" PowerShell 5.1 turns those into terminating
# errors even on success. Exit codes are checked explicitly instead.
$ErrorActionPreference = "Continue"
$root = $PSScriptRoot
. (Join-Path $root "build-config.ps1")

# Each term parenthesized: PowerShell binds the comma tighter than -gt, so `$a.Count -gt 0, $b`
# compares against an ARRAY, fails, and under "Continue" carries on as if no mode had been given.
$modeCount = [int]($Rebuild.Count -gt 0) + [int][bool]$Check + [int][bool]$SelfTest
if ($modeCount -ne 1) { throw "Pass exactly one of -Rebuild <java\-relative file>, -Check or -SelfTest." }

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

        if ($problems -eq 0) { Write-Host "  PASS  self-test: $selfCount of $selfCount" }
        $targets = @()
    }
    elseif ($Check) {
        $targets = $formRels
    } else {
        # Under -File, `-Rebuild a,b` arrives as ONE string, so commas are split here. Each name is
        # matched case-insensitively against the form classes and replaced by its true-case path.
        $targets = @()
        foreach ($t in @($Rebuild | ForEach-Object { $_ -split ',' } | ForEach-Object { $_.Trim() } | Where-Object { $_ })) {
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
        Write-Host ("`n== " + $(if ($Check) { "checking" } else { "rebuilding" }) + " $($targets.Count) form class(es) on $ver ==")
    }
    foreach ($rel in $targets) {
        $src  = Join-Path $srcRoot $rel
        $form = [IO.Path]::ChangeExtension($src, ".form")

        $pkg  = Split-Path $rel
        $base = [IO.Path]::GetFileNameWithoutExtension($rel)
        $fqcnBase = if ($pkg) { ($pkg -replace '\\', '.') + "." } else { "" }
        $committedDir = Join-Path $root $pkg
        $committedTop = Join-Path $committedDir "$base.class"
        if (-not (Test-Path $committedTop)) { Write-Host "  FAIL  $rel has no committed $base.class to take the generated members from"; $problems++; continue }

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
        & $java @asmExports -cp $toolOut FormTransplant $committedTop (Join-Path $newDir "$base.class") $form (Join-Path $outDir "$base.class") 2>&1 |
            ForEach-Object { Write-Host "        $_" }
        if ($LASTEXITCODE -ne 0) { Write-Host "  FAIL  $rel could not be rebuilt (see the message above)"; $problems++; continue }
        $outSet = Get-ClassSet $outDir $base
        # And checked, not assumed: the rebuilt set must name exactly the classes javac wrote.
        if ((($newSet | ForEach-Object Name) -join " ") -ne (($outSet | ForEach-Object Name) -join " ")) {
            Write-Host ("  FAIL  $rel : javac wrote [" + (($newSet | ForEach-Object Name) -join " ") + "] but the rebuilt set is [" +
                        (($outSet | ForEach-Object Name) -join " ") + "]")
            $problems++; continue
        }

        if ($Rebuild) {
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
                        "would ship the old class. Rebuild it: formclass.ps1 -Rebuild $rel")
            $problems++
        } else {
            Write-Verbose "  ok  $rel"
        }
    }
    if ($Check -and $problems -eq 0) { Write-Host "  PASS  all $($targets.Count) form classes match their .java" }
}
finally {
    # A cleanup failure (the same deep-TEMP case) must not hide the run's real result, so it warns.
    try { if (Test-Path $work) { [IO.Directory]::Delete($work, $true) } }
    catch { Write-Host "  WARN  could not remove the temp folder $work -- $($_.Exception.Message)" }
}

$global:LASTEXITCODE = 0
if ($problems -gt 0) { Write-Host "`n$problems problem(s)."; exit 1 }
exit 0
