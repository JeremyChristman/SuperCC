<#
Corpus replay: run EVERY stored solution through a SuperCC jar, and compare two runs.

WHY THIS IS COMMITTED
---------------------
It is the widest oracle this fork has. test\ checks SuperCC against transcriptions of Tile World's
rules, one synthesized fixture at a time, so it can only see the arrangements someone thought to
build. This replays the whole solution collection through the real engine and records the final
state of each one, so two builds can be compared line for line.

That is not a theoretical advantage. On 2026-11-01 upstream's trap rework (858dbd0) passed all
1,878 assertions in test\ while changing 13 of 24,204 real solutions: five levels would not open
at all (four of them solved), six more solved routes stopped reaching the exit, and two unsolved
routes ended differently -- ten solved routes lost in all -- because no fixture starts a block or
Chip on a wired trap. This caught it, and pinned the one ordering bug responsible.

Its previous version lived in a scratch folder and was lost -- exactly what happened to the trace
harness before it was committed. That is the reason it is here.

WHAT IT NEEDS THAT THE REPO DOES NOT HAVE
-----------------------------------------
A Chip's Challenge folder holding level sets and solutions, neither of which may be committed here
(docs\adr\0007):

  <CCDir>\data\<pack>.dat (or .ccl)            the level sets
  <CCDir>\succsave\<pack>\<n>_<title>-<MS|LYNX>.json   SuperCC's own solution store

So the comparison is LOCAL and on demand, like trace.ps1. -SelfTest needs neither and runs in CI.

THE WORKFLOW FOR AN ENGINE CHANGE
---------------------------------
  # (from the repo root; corpus-results\ is gitignored -- these files list a personal collection,
  #  so never write them anywhere a `git add -A` would pick them up)
  # 1. BEFORE: the jar you trust, named explicitly -- the deployed or previous-release jar.
  #    Not the repo's SuperCC.jar: build.ps1 overwrites that with whatever you just built.
  powershell -File corpus.ps1 -CCDir "<CC>" -Jar <trusted.jar> -Out corpus-results\before.txt
  # 2. AFTER: build the change; the default jar is the repo's fresh build.
  powershell -File corpus.ps1 -CCDir "<CC>" -Out corpus-results\after.txt
  # 3. compare
  powershell -File corpus.ps1 -Compare -Baseline corpus-results\before.txt -Candidate corpus-results\after.txt [-Show 20]

  # after anything touches Solution.java: prove the harness's replay still matches SuperCC's
  powershell -File corpus.ps1 -CCDir "<CC>" -CrossCheck

  # the harness checking itself on a synthesized collection -- no level set, runs in CI
  powershell -File corpus.ps1 -SelfTest

Every difference the compare finds is a behavior change on a real level, classified as:

  STATUS            it no longer loads or replays at all ("(was solved)" when it reached the exit),
                    or it already failed and now fails differently ("detail changed")
  LOST THE EXIT     it replays, and a solved route no longer solves
  GAINED THE EXIT   the reverse
  FINAL STATE ONLY  the outcome survived but the end state did not

plus "solved routes lost in all", which counts across every one of those. Differences in the
COLLECTION are kept apart and never counted there: INPUT CHANGED (the save, or the level record its
filename points at, has different bytes in the two runs -- every line ends with its inputs,
"src=<save>:<level>" CRC32s and any "pos=N", to tell), and saves present ONLY IN one run. A solved
route lost where the input ALSO changed is counted on a line of its own, since nothing can say
which caused it: re-run the baseline jar on the current collection and compare again. A clean compare is the
evidence that a change to game\**, io\** or emulator\** is replay-neutral; "the tests pass" is not, as 858dbd0
showed.

Exit codes: 0 identical (or a clean run), 1 differences found, 2 an error or a refused compare,
3 -CrossCheck found the harness's replay disagreeing with SuperCC's own.

THE ONE FAILURE THIS TOOL MUST NEVER HIDE
-----------------------------------------
A before/after pair that replayed the SAME jar twice compares IDENTICAL and proves nothing. So
every output file opens with "#jar <sha256> <path>" -- the jar the JVM actually loaded, read by
CorpusCheck from its own class source, not what this script believes it passed -- and -Compare
REFUSES (exit 2) a pair whose jars are the same file unless -SameJar says that is intended (a
determinism check, or comparing two collections). It fails CLOSED: a run whose jar is unknown --
no header, a header CorpusCheck could not hash, or one pushed off line 0 (an empty line is refused
for that reason) -- cannot be told apart from the same jar, and is refused the same way. Every run
also prints the jar and its hash.

The header names the HARNESS too ("harness=<sha256>" of corpus\CorpusCheck.java, line endings
normalized), and -Compare refuses a pair written by two versions of it, or by an unknown one, unless
-MixedHarness: a harness change shows up on every line it touches -- one counted the fatal move,
and moved 23,095 lines -- and all of it would be blamed on the engine. Keep a baseline only as long
as the harness that wrote it; after a harness change, re-run both sides.

And every run ENDS with "#end <count>": a file without it (a run killed, interrupted or failed) is
refused as INCOMPLETE. Compared, a cut-short run would read as a short collection, and every save it
never reached as "only in baseline" -- the collection's difference, not the engine's -- which is
exactly backwards. The header also gives the scope ("pack=<name|*>"): a one-pack run against a
whole-collection run compares that pack alone.

Relative paths -- -Jar, -CCDir, -Out, -Baseline, -Candidate -- are all taken from the CURRENT
directory; only the DEFAULT jar is the repo's own SuperCC.jar. That differs on purpose from
trace.ps1 and run-tests.ps1, which take -Jar from the repo root: this is the one script whose -Jar
is routinely a jar OUTSIDE the repo (a previous release), and resolving "-Jar SuperCC.jar" typed in
a release folder to the repo's fresh build is exactly the same-jar trap above. Without -Out the
result goes to corpus-results\ (gitignored: it lists a personal collection).

-CrossCheck replays every solution a second time through SuperCC's own Solution.load and fails if
any final state differs from the harness's. The harness copies Solution.tickBasicMoves (it cannot
observe it from outside); this is what proves the copy still matches. It doubles the run time.

The whole collection (about 24,000 solutions) takes 3-4 minutes per jar on the laptop, 5 with
-CrossCheck. Each JVM may take 2 GB, so on a 10 GB machine run the jars ONE AT A TIME: four at once
were killed for low memory.
#>
param(
    [string]$CCDir,
    [string]$Jar,
    [string]$Out,
    [string]$Pack,
    [switch]$CrossCheck,
    [switch]$Compare,
    [string]$Baseline,
    [string]$Candidate,
    [switch]$SameJar,
    [switch]$MixedHarness,
    [int]$Show = 20,
    [switch]$SelfTest
)

# Native tools write notes to stderr; under "Stop" PowerShell 5.1 turns those into terminating
# NativeCommandErrors even on success. Exit codes are checked explicitly instead.
$ErrorActionPreference = "Continue"
$root = $PSScriptRoot
. (Join-Path $root "build-config.ps1")

$utf8 = New-Object Text.UTF8Encoding($false)

# Exit 2, never 1: in -Compare, 1 means "differences found", and a comparison that never ran must not
# be mistakable for one that found something.
function Fail($msg) { Write-Host "  ERROR  $msg"; exit 2 }

<# A user-supplied path, made absolute against the CURRENT PowerShell location. Both [IO.File] and
   the java child resolve a relative path against the process's working directory, which in
   PowerShell 5.1 does not follow `cd` -- so a relative path has to be pinned before it leaves
   PowerShell, or it silently names a different file. #>
function Resolve-UserPath([string]$p) {
    # PowerShell's own resolver, not IsPathRooted: that calls "C:foo" (drive-relative) and "\foo"
    # (root-relative) rooted, and java would then resolve them against ITS directory, not ours.
    $full = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($p)
    # A trailing backslash is dropped (a drive root keeps its own): PowerShell 5.1 quotes an argument
    # with a space in it, and to java's argument parser the "\" before that closing quote ESCAPES it --
    # "...\Chip's Challenge\" swallowed the next argument whole and CorpusCheck printed its usage.
    while ($full.Length -gt 3 -and $full.EndsWith("\")) { $full = $full.Substring(0, $full.Length - 1) }
    return $full
}

# The jar to replay with: an explicit -Jar from the current directory, else the repo's own build.
function Resolve-Jar {
    if ($Jar) { return (Resolve-UserPath $Jar) }
    return (Join-Path $root "SuperCC.jar")
}

# Find-JdkBin (build-config.ps1) THROWS when there is no JDK; an uncaught throw would exit 1, which
# here means "differences found". Routed through Fail so a missing JDK is an error, exit 2.
function Get-JdkBin([string]$requires) {
    try { return (Find-JdkBin -Requires $requires) } catch { Fail "$($_.Exception.Message)" }
}

function Get-Sha([string]$path) { return (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash }

<# JVM flags that shape what a run WRITES, so they are part of the harness version below: change
   one and older baselines are refused, exactly as for a change to CorpusCheck.java itself.
   -XX:-OmitStackTraceInFastThrow: once HotSpot compiles a site that throws often, it otherwise
   throws a PREALLOCATED exception with no message and no stack, so the same failure would read
   differently partway through a run -- and two runs of one jar would no longer match byte for byte. #>
$OUTPUT_JVM_FLAGS = @("-XX:-OmitStackTraceInFastThrow")

<# The version of the harness: sha256 of corpus\CorpusCheck.java with CRs removed -- so the same
   commit hashes the same on a CRLF checkout and an LF one (the two PCs need not agree on line
   endings) -- plus $OUTPUT_JVM_FLAGS. Written into every run's header, because two runs made by
   different harness versions differ wherever the harness changed what it writes, and a compare
   would blame all of it on the engine. #>
function Get-HarnessShaOf([string]$sourcePath) {
    $text = [IO.File]::ReadAllText($sourcePath, $utf8).Replace("`r", "") + "`n#jvm " + ($OUTPUT_JVM_FLAGS -join " ")
    $sha = [Security.Cryptography.SHA256]::Create()
    try { return (($sha.ComputeHash($utf8.GetBytes($text)) | ForEach-Object { $_.ToString("X2") }) -join "") }
    finally { $sha.Dispose() }
}
# Of the copy Build-Harness COMPILED -- not the repo's file, which may have been edited since.
function Get-HarnessSha([string]$classDir) { return (Get-HarnessShaOf (Join-Path $classDir "src\CorpusCheck.java")) }
function Get-ShortHash([string]$path) { return (Get-Sha $path).Substring(0, 16) }

<# Compiles the harness against a jar into a fresh temp directory and returns that directory.
   javac's output is captured rather than allowed through: anything a function writes to the
   pipeline becomes part of its return value. On failure the directory is removed BEFORE Fail,
   because Fail exits and the caller never receives the path to clean up.

   The jar is COPIED into that directory first (Get-JarCopy), and everything compiles and runs
   against the copy. A run takes minutes, and Windows lets build.ps1 overwrite a jar a live JVM has
   open: the JVM keeps its cached central directory and reads whatever bytes are now at those
   offsets, so classes loaded late come back as garbage (CLAUDE.md section 9, measured). Against a
   private copy, a rebuild mid-run cannot reach the replay, and the header's hash is of what ran. #>
function Get-JarCopy([string]$classDir) { return (Join-Path $classDir "SuperCC.jar") }
# The jar the user NAMED, which the copy was taken from: the header shows this path (the copy's temp
# folder is gone once the run ends), beside the hash of the copy that actually ran.
function Get-NamedJar([string]$classDir) { return [IO.File]::ReadAllText((Join-Path $classDir "named-jar.txt"), $utf8) }

function Build-Harness([string]$jarPath, [switch]$WithFixture) {
    $jdkBin = Get-JdkBin "javac.exe"
    $classDir = Join-Path $env:TEMP ("scc-corpus-" + [Guid]::NewGuid().ToString("N"))
    New-Item -ItemType Directory -Force -Path $classDir | Out-Null
    Copy-Item -LiteralPath $jarPath -Destination (Get-JarCopy $classDir)
    [IO.File]::WriteAllText((Join-Path $classDir "named-jar.txt"), $jarPath, $utf8)
    # The harness source too: what is compiled is what is hashed into the header (Get-HarnessSha).
    New-Item -ItemType Directory -Force -Path (Join-Path $classDir "src") | Out-Null
    Copy-Item -LiteralPath (Join-Path $root "corpus\CorpusCheck.java") -Destination (Join-Path $classDir "src\CorpusCheck.java")
    $sources = @(Join-Path $classDir "src\CorpusCheck.java")
    if ($WithFixture) {
        $sources += (Join-Path $root "corpus\CorpusFixture.java")
        $sources += (Join-Path $root "test\DatBuilder.java")
    }
    $log = @(& (Join-Path $jdkBin "javac.exe") -encoding $SPLICE_ENCODING --release $SPLICE_RELEASE `
                 -cp (Get-JarCopy $classDir) -d $classDir $sources 2>&1)
    if ($LASTEXITCODE -ne 0) {
        $code = $LASTEXITCODE
        Remove-Item -LiteralPath $classDir -Recurse -Force -ErrorAction SilentlyContinue
        $log | ForEach-Object { Write-Host "    $_" }
        Fail "could not compile the corpus harness against $jarPath (exit $code)"
    }
    return $classDir
}

<# The java command line for CorpusCheck, as an array. Callers must put it in a VARIABLE before
   passing it: @(Get-CorpusArgs ...) wraps it in a second array, and PowerShell then hands java the
   whole command line as ONE argument. The -D argument must stay a quoted string -- PowerShell 5.1
   splits an unquoted -Dname.with.dots=value at the first dot. #>
function Get-CorpusArgs([string]$classDir, [string]$ccDir, [string]$outFile,
                        [string]$onlyPack, [bool]$crossCheck) {
    # The jar is always Build-Harness's private copy, never the caller's path (see Build-Harness).
    # The flags that shape the output come from $OUTPUT_JVM_FLAGS, which the harness version covers.
    $javaArgs = @("-Djava.awt.headless=true") + $OUTPUT_JVM_FLAGS +
                @("-Dcorpus.harness=$(Get-HarnessSha $classDir)", "-Dcorpus.jarpath=$(Get-NamedJar $classDir)",
                  "-Dcorpus.tempdir=$classDir", "-Xmx2g",
                  "-cp", "$(Get-JarCopy $classDir);$classDir", "CorpusCheck", $ccDir, $outFile)
    if ($onlyPack) { $javaArgs += $onlyPack }
    if ($crossCheck) { $javaArgs += "--crosscheck" }
    return ,$javaArgs
}

<# Only the FIRST line of a run can be its "#jar" header. Every other line is data whatever it
   starts with: a line is "<pack><TAB>...", and a pack folder may be named "#anything" -- skipping
   every "#" line would silently drop that whole pack from both sides of a compare. #>
function Test-Header([string]$line, [int]$index) { return ($index -eq 0 -and $line.StartsWith("#jar`t")) }

# The LAST line of a finished run: "#end<TAB><count>". Exactly two fields, so no data line (three
# or more, whatever its pack is called) can be mistaken for it.
function Test-End([string]$line) { return ($line -match "^#end`t\d+$") }

# A run's data lines: every line but the header and the end line.
function Select-Data([string[]]$all) {
    for ($i = 0; $i -lt $all.Count; $i++) {
        if (-not (Test-Header $all[$i] $i) -and -not ($i -eq $all.Count - 1 -and (Test-End $all[$i]))) { $all[$i] }
    }
}

<# One run: its lines keyed by "pack<TAB>file", plus the jar recorded in its header. An ordinal
   dictionary, not a PowerShell hashtable: hashtable keys are case-INSENSITIVE, and two solution
   files may differ only in case. #>
function Read-Corpus([string]$path) {
    $map = New-Object 'System.Collections.Generic.Dictionary[string,string]' ([StringComparer]::Ordinal)
    $jarSha = $null
    $harness = $null
    $java = $null
    $scope = $null
    $end = $null
    $index = -1
    # Read up front, so a file that cannot be read (locked by Dropbox or an editor) fails as exactly
    # that -- not, half-read, as "INCOMPLETE ... re-run it", which may be impossible once its jar is gone.
    try { $fileLines = [IO.File]::ReadAllLines($path, $utf8) }
    catch { Fail "could not read ${path}: $($_.Exception.InnerException.Message)$($_.Exception.Message)" }
    foreach ($line in $fileLines) {
        $index++
        if ($null -ne $end) { Fail "a line after the #end line in ${path} (line $($index + 1)): not a CorpusCheck output as written" }
        # CorpusCheck never writes an empty line. Skipping one would shift a "#jar" header off line 0,
        # where it would be read as DATA and the same-jar check would never see it -- so it is refused.
        if ($line.Length -eq 0) { Fail "empty line $($index + 1) in ${path}: not a CorpusCheck output as written" }
        if (Test-End $line) { $end = [int]$line.Substring(5); continue }
        if (Test-Header $line $index) {
            # Only a well-formed sha256 counts as KNOWING the jar; "-", a truncated or garbled hash
            # is unknown, which the compare then refuses (fail closed).
            $h = $line.Split([char[]]"`t")
            if ($h.Count -ge 2 -and $h[1] -match '^[0-9A-Fa-f]{64}$') { $jarSha = $h[1].ToUpperInvariant() }
            # The key=value fields after the path, parsed once (fields 3 on: the path itself may hold "=").
            $kv = @{}
            foreach ($field in @($h | Select-Object -Skip 3)) {
                $eq = $field.IndexOf("=")
                if ($eq -gt 0) { $kv[$field.Substring(0, $eq)] = $field.Substring($eq + 1) }
            }
            if ($kv["harness"] -match '^[0-9A-Fa-f]{64}$') { $harness = $kv["harness"].ToUpperInvariant() }
            if ($kv.ContainsKey("java") -and $kv["java"] -ne "") { $java = $kv["java"] }      # never truthiness: "0" is a value
            if ($kv.ContainsKey("pack") -and $kv["pack"] -ne "") { $scope = $kv["pack"] }   # (a pack may be named 0)
            continue
        }
        $parts = $line.Split([char[]]"`t", 3)
        if ($parts.Count -lt 3) { Fail "malformed line in ${path}: $line" }
        $key = $parts[0] + "`t" + $parts[1]
        if ($map.ContainsKey($key)) { Fail "duplicate entry in ${path}: $key" }
        $map.Add($key, $parts[2])
    }
    <# A run that never wrote its "#end" was cut short -- killed, interrupted, or it failed -- and
       every save it did not reach would otherwise read as "only in baseline": blamed on the
       collection, out of the engine headline, when the cause is the run (often the engine under
       test, hanging or crashing the JVM). Refused, as is a count that does not match. #>
    if ($null -eq $end) {
        Fail "${path} is INCOMPLETE: it has no #end line, so the run that wrote it was cut short or failed. Re-run it."
    }
    if ($end -ne $map.Count) {
        Fail "${path} says it wrote $end solutions but holds $($map.Count): not a CorpusCheck output as written"
    }
    return [pscustomobject]@{ Map = $map; Jar = $jarSha; Harness = $harness; Java = $java; Scope = $scope }
}

function Test-Won([string]$rest) { return ($rest.StartsWith("OK`t") -and $rest.Contains("`twon=1`t")) }
function Get-Status([string]$rest) { return ($rest.Split([char[]]"`t", 2))[0] }

<# A line's state and its "src=<save>:<level>" input fingerprint, apart: @(state, src). src is $null
   on a line without one. #>
function Split-Src([string]$rest) {
    $at = $rest.LastIndexOf("`tsrc=")
    if ($at -lt 0) { return @($rest, $null) }
    return @($rest.Substring(0, $at), $rest.Substring($at + 5))
}

# ---------------------------------------------------------------- compare
if ($Compare) {
    if (-not $Baseline -or -not $Candidate) { Fail "-Compare needs -Baseline and -Candidate" }
    $Baseline = Resolve-UserPath $Baseline
    $Candidate = Resolve-UserPath $Candidate
    if (-not (Test-Path -LiteralPath $Baseline -PathType Leaf))  { Fail "no baseline FILE at $Baseline" }
    if (-not (Test-Path -LiteralPath $Candidate -PathType Leaf)) { Fail "no candidate FILE at $Candidate" }

    $bRun = Read-Corpus $Baseline
    $cRun = Read-Corpus $Candidate
    $b = $bRun.Map; $c = $cRun.Map
    if ($b.Count -eq 0) { Fail "the baseline is empty -- an empty run compares equal to anything" }
    if ($c.Count -eq 0) { Fail "the candidate is empty -- an empty run compares equal to anything" }

    $bJar = if ($bRun.Jar) { $bRun.Jar.Substring(0, 16) } else { "unknown" }
    $cJar = if ($cRun.Jar) { $cRun.Jar.Substring(0, 16) } else { "unknown" }
    Write-Host ""
    # "in the file": a one-pack run against a whole one is narrowed below, and the NOTE says to what.
    Write-Host ("baseline   {0}  ({1} solutions in the file, jar {2})" -f $Baseline, $b.Count, $bJar)
    Write-Host ("candidate  {0}  ({1} solutions in the file, jar {2})" -f $Candidate, $c.Count, $cJar)
    if ($bRun.Jar -and $cRun.Jar -and $bRun.Jar -eq $cRun.Jar -and -not $SameJar) {
        # Parenthesized before -f: -f binds tighter than +, and would format only the second half.
        Fail (("both runs used the SAME jar (sha256 {0}) -- this compare cannot show an engine difference. " +
               "Re-run one side with the other jar, or pass -SameJar if comparing a jar with itself is the point.") -f $bJar)
    }
    # Fails CLOSED: a run whose jar is unknown (no header, or one CorpusCheck could not hash) might be
    # the other run's jar, and an unprovable pair is refused exactly like a proven same-jar one.
    if ((-not $bRun.Jar -or -not $cRun.Jar) -and -not $SameJar) {
        # -SameJar is offered only where it could help: a run with no readable header lacks the harness
        # and scope too, and would be refused for those next.
        $hint = if ($bRun.Harness -and $cRun.Harness -and $bRun.Scope -and $cRun.Scope) {
            "Re-run it with corpus.ps1, or pass -SameJar to compare anyway." } else { "Re-run it with corpus.ps1." }
        Fail (("a run does not record which jar it replayed (baseline {0}, candidate {1}), so this compare " +
               "cannot show two different jars ran. {2}") -f $bJar, $cJar, $hint)
    }
    # The same for the HARNESS: two runs written by different versions of CorpusCheck differ wherever
    # it changed what it writes (one such change touched 23,095 lines), and every one would be blamed
    # on the engine. Unknown is refused too: it cannot be shown to match.
    if ((-not $bRun.Harness -or -not $cRun.Harness -or $bRun.Harness -ne $cRun.Harness) -and -not $MixedHarness) {
        $bH = if ($bRun.Harness) { $bRun.Harness.Substring(0, 16) } else { "unknown" }
        $cH = if ($cRun.Harness) { $cRun.Harness.Substring(0, 16) } else { "unknown" }
        Fail (("the runs were not written by one known version of the harness (baseline {0}, candidate {1}), so " +
               "their differences cannot be told from the harness's own changes. Re-run the older side with the " +
               "current corpus.ps1, or pass -MixedHarness to compare anyway.") -f $bH, $cH)
    }
    <# The SCOPE: a run of one -Pack against a whole-collection run compares that pack only -- the
       whole run's other packs are not "only in baseline", they were never asked of the other side.
       Two runs of different single packs share nothing, and an unknown scope cannot be matched. #>
    if (-not $bRun.Scope -or -not $cRun.Scope) { Fail "a run does not record its scope (pack=), so this compare cannot match the two" }
    if ($bRun.Scope -ne $cRun.Scope) {
        if ($bRun.Scope -ne "*" -and $cRun.Scope -ne "*") {
            Fail ("the runs cover different packs ({0} and {1}): there is nothing to compare" -f $bRun.Scope, $cRun.Scope)
        }
        $one = if ($bRun.Scope -ne "*") { $bRun.Scope } else { $cRun.Scope }
        $whole = if ($bRun.Scope -eq "*") { $bRun } else { $cRun }
        $kept = New-Object 'System.Collections.Generic.Dictionary[string,string]' ([StringComparer]::Ordinal)
        # equalsIgnoreCase, as CorpusCheck matches -Pack
        foreach ($k in $whole.Map.Keys) { if ($k.Split([char[]]"`t", 2)[0] -ieq $one) { $kept.Add($k, $whole.Map[$k]) } }
        $whole.Map = $kept
        $b = $bRun.Map; $c = $cRun.Map
        Write-Host ("  NOTE  one run covers only pack {0}: comparing that pack alone -- baseline {1}, candidate {2} solutions" -f $one, $b.Count, $c.Count)
        if ($kept.Count -eq 0) { Fail "the whole-collection run holds no pack named $one" }
    }
    # A different JDK is WARNED about, not refused: the engine is integer arithmetic and no end state
    # depends on it, but an exception line quotes the JDK's own message, whose wording can differ.
    if ($bRun.Java -and $cRun.Java -and $bRun.Java -ne $cRun.Java) {
        Write-Host ("  NOTE  the runs used different JDKs (baseline {0}, candidate {1}): a difference in an" -f $bRun.Java, $cRun.Java)
        Write-Host "        exception line's MESSAGE alone may be the JDK's wording, not the engine"
    }

    <# Of the ENGINE differences, STATUS is decided FIRST. A solved level that no longer loads at all is a load-path failure,
       not a movement one, and filing it under LOST THE EXIT sends whoever triages it looking in the
       wrong place -- under 858dbd0 that would have shown "status changed: 0" while five levels
       could not open. Whether it had been solved is kept, as a suffix and in the overall total. #>
    <# Differences in the COLLECTION are never counted as the engine's, in "solved routes lost in
       all" -- the engine-regression headline -- or anywhere else:
         * a save present on only one side: CorpusCheck writes a line for every solution file
           whatever the jar does with it, so that file was added or removed;
         * INPUT CHANGED: the save's bytes or its level record's differ between the runs (src=), so
           the solution was re-recorded or the pack updated in between. Decided before STATUS,
           because whatever else changed, the engine cannot be blamed for it.
       An input change whose end state is unchanged is simply identical. #>
    $diffs = New-Object System.Collections.ArrayList
    $same = 0
    $solvedLost = 0
    $solvedMissing = 0
    $solvedInputChanged = 0
    foreach ($key in $b.Keys) {
        if (-not $c.ContainsKey($key)) {
            [void]$diffs.Add([pscustomobject]@{ Rank = 6; Kind = "ONLY IN BASELINE"; Key = $key; B = $b[$key]; C = "" })
            if (Test-Won $b[$key]) { $solvedMissing++ }
            continue
        }
        $bv = $b[$key]; $cv = $c[$key]
        if ([string]::Equals($bv, $cv, [StringComparison]::Ordinal)) { $same++; continue }
        $bSplit = Split-Src $bv; $cSplit = Split-Src $cv
        if ([string]::Equals($bSplit[0], $cSplit[0], [StringComparison]::Ordinal)) { $same++; continue }
        $bw = Test-Won $bv; $cw = Test-Won $cv
        $bs = Get-Status $bv; $cs = Get-Status $cv
        if ($bSplit[1] -and $cSplit[1] -and $bSplit[1] -ne $cSplit[1]) {
            # "<save>:<level>" then any "<TAB>pos=N": a level that only MOVED is "position", not "level"
            $bPos = ""; $cPos = ""
            $bSrc = $bSplit[1]; $cSrc = $cSplit[1]
            if ($bSrc -match "`tpos=(\d+)$") { $bPos = $matches[1]; $bSrc = $bSrc.Substring(0, $bSrc.LastIndexOf("`tpos=")) }
            if ($cSrc -match "`tpos=(\d+)$") { $cPos = $matches[1]; $cSrc = $cSrc.Substring(0, $cSrc.LastIndexOf("`tpos=")) }
            $bIn = $bSrc.Split(":"); $cIn = $cSrc.Split(":")
            $what = @()
            if ($bIn[0] -ne $cIn[0]) { $what += "save" }
            if ($bIn[1] -ne $cIn[1]) { $what += "level" }
            if ($bPos -ne $cPos) { $what += "position" }
            $kind = "INPUT CHANGED ({0})" -f ($what -join ", ")
            if ($bw -and -not $cw) { $kind += " (was solved)"; $solvedInputChanged++ }
            [void]$diffs.Add([pscustomobject]@{ Rank = 5; Kind = $kind; Key = $key; B = $bv; C = $cv })
            continue
        }
        if ($bw -and -not $cw) { $solvedLost++ }
        # A line that is not OK has no end state to differ: the same failure with a different reason,
        # message or move is a change in HOW it fails, and belongs with STATUS, not FINAL STATE ONLY.
        if ($bs -ne $cs -or $bs -ne "OK") {
            $kind = if ($bs -ne $cs) { "STATUS $bs -> $cs" } else { "STATUS $bs, detail changed" }
            if ($bw) { $kind += " (was solved)" }
            $rank = 1
        }
        elseif ($bw -and -not $cw)   { $kind = "LOST THE EXIT";     $rank = 2 }
        elseif ($cw -and -not $bw)   { $kind = "GAINED THE EXIT";   $rank = 3 }
        else                         { $kind = "FINAL STATE ONLY";  $rank = 4 }
        [void]$diffs.Add([pscustomobject]@{ Rank = $rank; Kind = $kind; Key = $key; B = $bv; C = $cv })
    }
    foreach ($key in $c.Keys) {
        if (-not $b.ContainsKey($key)) {
            [void]$diffs.Add([pscustomobject]@{ Rank = 7; Kind = "ONLY IN CANDIDATE"; Key = $key; B = ""; C = $c[$key] })
        }
    }

    Write-Host ""
    Write-Host ("identical: {0} of {1}" -f $same, $b.Count)
    $byRank = @{}
    foreach ($d in $diffs) { $byRank[$d.Rank] = 1 + [int]$byRank[$d.Rank] }      # one pass, not seven
    $names = "status changed", "lost the exit", "gained the exit", "final state only",
             "input changed", "only in baseline", "only in candidate"                # ranks 1..7, in order
    for ($r = 1; $r -le $names.Count; $r++) {
        Write-Host ("  {0,-18} {1}" -f ($names[$r - 1] + ":"), [int]$byRank[$r])
    }
    Write-Host ("solved routes lost in all: {0}" -f $solvedLost)
    if ($solvedMissing -gt 0) {
        Write-Host ("solved routes the candidate never replayed (only in baseline: the collection differs, not the engine): {0}" -f $solvedMissing)
    }
    <# Not counted as the engine's, but never silent either: a real regression can land on a save
       whose input also changed, and nothing here can tell the two apart. The remedy is to make the
       inputs match, not to guess. #>
    if ($solvedInputChanged -gt 0) {
        Write-Host ("solved routes lost where the input ALSO changed, so not attributable to either: {0}" -f $solvedInputChanged)
    }
    if ([int]$byRank[5] -gt 0) {
        Write-Host "  -> inputs changed between the runs: re-run the BASELINE jar on the current collection to compare like with like"
    }

    if ($diffs.Count -eq 0) {
        Write-Host ""
        Write-Host "  IDENTICAL -- every solution ends in the same state under both runs"
        Write-Host ""
        exit 0
    }

    Write-Host ""
    $shown = @($diffs | Sort-Object Rank, Key | Select-Object -First $Show)
    foreach ($d in $shown) {
        Write-Host ("  {0,-18} {1}" -f $d.Kind, ($d.Key -replace "`t", "  "))
        if ($d.B) { Write-Host ("      baseline   {0}" -f ($d.B -replace "`t", "  ")) }
        if ($d.C) { Write-Host ("      candidate  {0}" -f ($d.C -replace "`t", "  ")) }
    }
    if ($diffs.Count -gt $shown.Count) {
        Write-Host ("  ... and {0} more (raise -Show to see them)" -f ($diffs.Count - $shown.Count))
    }
    Write-Host ""
    exit 1
}

# ---------------------------------------------------------------- self-test
<# The harness checking itself, with no level set: a synthesized collection (corpus\CorpusFixture.java,
   built with test\DatBuilder.java) whose every file is there to produce one specific line. It needs
   the jar, so in CI it runs after the build.

   What it proves, each by a check that can fail:
     * every status a real collection can produce comes out, in sorted order: OK (won and not won,
       with the move that reached the exit counted), OPENFAIL for a set SuperCC refuses (its
       openLevelset swallows that, and it once aborted the whole run) and for a set present as both
       .dat and .ccl, NODAT, BADNAME, BADJSON, LOADFAIL for a level with no title (the identity check
       that stops a stale level being reported), NOLEVEL four ways -- one of them a set cut short,
       whose last level's number cannot be read -- EXC for a replay that throws part-way, a .ccl set,
       a pack whose name starts with "#", a level whose record number differs from its position --
       replayed by its OWN number, the way SuperCC names saves, and not silently replayed under
       another's -- and two levels sharing one number, where the filename title picks the right one
       or the save is AMBIGUOUS (as it is in the cut-short set, unless the title confirms the one
       readable candidate, since the unreadable record might carry the same number).
       LOADEXC and STATEEXC are defensive catch-alls that no known input reaches, so they are the
       two statuses this does NOT produce;
     * the output names the jar that actually ran, by its full sha256;
     * the harness's copy of the replay loop agrees with SuperCC's own Solution.load (-CrossCheck),
       including on the replay that throws, and every replayed save is accounted for;
     * two runs of the same collection are byte-identical -- the property every comparison rests on;
     * a compare of a jar against itself is REFUSED without -SameJar, and so is one where a jar is
       UNKNOWN; a malformed run, an empty line and a -Pack naming no folder are errors (exit 2),
       never "differences found" (exit 1) or a clean empty run;
     * a save present on only one side is a difference, but not a solved route LOST; and a save
       re-recorded between runs is INPUT CHANGED (every line carries its inputs' CRC32s), never the
       engine's -- while the SAME end-state differences with unchanged inputs are the engine's;
     * REAL engine-level differences are caught and each is classified correctly, with every count
       asserted exactly -- so a compare that invents, drops or mis-files a difference fails. #>
if ($SelfTest) {
    $jarPath = Resolve-Jar
    if (-not (Test-Path -LiteralPath $jarPath -PathType Leaf)) { Fail "no jar file at $jarPath -- run build.ps1 first" }
    $javaExe = Join-Path (Get-JdkBin "java.exe") "java.exe"

    $td = Join-Path $env:TEMP ("scc-corpustest-" + [Guid]::NewGuid().ToString("N"))
    New-Item -ItemType Directory -Force -Path $td | Out-Null
    $classDir = $null
    $ok = $true
    function Check([string]$name, [bool]$cond) {
        if ($cond) { Write-Host "  PASS  $name" } else { Write-Host "  FAIL  $name"; $script:ok = $false }
    }
    function Run-Quiet([string[]]$javaArgs) {
        $null = & $javaExe @javaArgs 2>&1
        return $LASTEXITCODE
    }
    function Invoke-Compare([string]$base, [string]$cand, [switch]$Same, [switch]$Mixed) {
        # -NoProfile: a local profile must not run a dozen times and leak into the output checked here.
        $cmp = @("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", $PSCommandPath, "-Compare", "-Baseline", $base, "-Candidate", $cand)
        if ($Same) { $cmp += "-SameJar" }
        if ($Mixed) { $cmp += "-MixedHarness" }
        $lines = @(& powershell @cmp)
        return [pscustomobject]@{ Code = $LASTEXITCODE; Lines = $lines }
    }
    try {
        $classDir = Build-Harness $jarPath -WithFixture
        $cpath = "$(Get-JarCopy $classDir);$classDir"

        $ccA = Join-Path $td "cc A"     # a SPACE, as in "Chip's Challenge": quoting is part of what is tested
        $ccB = Join-Path $td "cc B"
        if ((Run-Quiet @("-Djava.awt.headless=true", "-cp", $cpath, "CorpusFixture", $ccA, "r-r-", "d-", "r-r-")) -ne 0) {
            Fail "CorpusFixture failed"
        }
        if ((Run-Quiet @("-Djava.awt.headless=true", "-cp", $cpath, "CorpusFixture", $ccB, "r-", "r-", "!badjson")) -ne 0) {
            Fail "CorpusFixture failed"
        }

        $a1 = Join-Path $td "a1.txt"; $a2 = Join-Path $td "a2.txt"; $bOut = Join-Path $td "b.txt"
        $aArgs = Get-CorpusArgs $classDir $ccA $a1 "" $false
        Check "the harness runs to completion past a set that will not open" ((Run-Quiet $aArgs) -eq 0)

        $all = @()
        if (Test-Path -LiteralPath $a1) { $all = @([IO.File]::ReadAllLines($a1, $utf8)) }
        $header = if ($all.Count -gt 0) { $all[0] } else { "" }
        Check "the output opens with the jar that actually ran, by its full sha256 -- and the path the user named" `
              ($header.StartsWith("#jar`t" + (Get-Sha $jarPath) + "`t" + $jarPath + "`t"))
        Check "... and the version of the harness that wrote it" ($header.Contains("`tharness=" + (Get-HarnessShaOf (Join-Path $root "corpus\CorpusCheck.java")) + "`t"))
        Check "... and its scope (pack=*), and the run ends with its #end line and count" `
              ($header.EndsWith("`tpack=*") -and ($all[-1] -eq ("#end`t" + ($all.Count - 2))))
        Check "... and the JDK that ran it" ($header -match "`tjava=[0-9]")
        $lines = @(Select-Data $all)
        $expect = @(
            @("#Hash`t1_Walk-MS.json`tOK`tMS`twon=1`t",          "a pack named '#...' is data, not mistaken for the header"),
            @("Badsig`t1_Walk-MS.json`tOPENFAIL`tIOException`t", "a well-formed set under a signature SuperCC refuses reads OPENFAIL"),
            @("Broken`t1_Any-MS.json`tOPENFAIL`tIOException`t",  "a set SuperCC refuses reads OPENFAIL, with the parser's reason"),
            @("Cclset`t1_Walk-MS.json`tOK`tMS`twon=1`t",         "a .ccl set is found and replayed"),
            @("Dupes`t1_Alpha-MS.json`tOK`tMS`twon=1`t",         "two levels share a number: the filename title picks Alpha"),
            @("Dupes`t1_Beta-MS.json`tOK`tMS`twon=1`t",          "... and picks Beta, whose route only works on Beta"),
            @("Dupes`t1_Gamma-MS.json`tAMBIGUOUS`tMS`t",         "... and when the title picks neither, AMBIGUOUS -- never a guess"),
            @("Orphan`t1_Lost-MS.json`tNODAT",                  "a folder with no level set reads NODAT"),
            @("Short`t1_Other-MS.json`tAMBIGUOUS`tMS`t",         "in a set cut short, one readable level 1 is not enough: an unconfirmed title is AMBIGUOUS"),
            @("Short`t1_Walk-MS.json`tOK`tMS`twon=1`t",          "... and the title that confirms it replays it"),
            @("Short`t2_Cut-MS.json`tNOLEVEL`tMS`t1 record(s) run past the end",
                                                                "... and a save whose level's number is unreadable says so, not a guessed LOADFAIL"),
            @("Synth`t1_Walk-LYNX.json`tOK`tLYNX`t",            "a LYNX save replays under Lynx (its bg= and slip= are checked below)"),
            @("Synth`t1_Walk-MS.json`tOK`tMS`twon=1`tdead=1`tmove=3/4`t",
                                                                "a route onto the exit reads won=1, counting the move that reached it (3 of 4)"),
            @("Synth`t2_Latin-MS.json`t",                        "an older save with no Encode field replays (its move count is checked below)"),
            @("Synth`t2_Stay-MS.json`tOK`tMS`twon=0`t",          "a route that never reaches the exit reads won=0"),
            @("Synth`t2_Utf-MS.json`t",                          "a modern UTF-8 save replays (its move count is checked below)"),
            @("Synth`t3_Again-MS.json`tOK`tMS`twon=1`t",         "a second solved route replays independently"),
            @("Synth`t4_Untitled-MS.json`tLOADFAIL`tMS",        "a level that will not load reads LOADFAIL, not a stale replay"),
            @("Synth`t5_Ghost-MS.json`tNOLEVEL`tMS",            "a number no level carries reads NOLEVEL, not a replay of whatever is at that position"),
            @("Synth`t6_Click-MS.json`tEXC`tMS`tmove=",          "a replay that throws part-way reads EXC with the move it reached"),
            @("Synth`t7_Beyond-MS.json`tNOLEVEL`tMS",           "a number past the set reads NOLEVEL, not a wrapped level 1"),
            @("Synth`t99999999999_Huge-MS.json`tNOLEVEL`tMS",   "a number too long for an int reads NOLEVEL instead of aborting the run"),
            @("Synth`t9_Renum-MS.json`tOK`tMS`twon=1`t",         "a save named by a level's OWN number replays that level"),
            @("Synth`treadme.json`tBADNAME",                    "a misnamed file reads BADNAME"),
            @("Twins`t1_Walk-MS.json`tOPENFAIL`tAMBIGUOUS`t",    "a folder whose set exists as BOTH .dat and .ccl reads OPENFAIL AMBIGUOUS")
        )
        Check ("one line per solution file, {0} in all (got {1})" -f $expect.Count, $lines.Count) ($lines.Count -eq $expect.Count)
        for ($i = 0; $i -lt $expect.Count; $i++) {
            $got = if ($i -lt $lines.Count) { $lines[$i] } else { "" }
            Check ("line {0}: {1}" -f ($i + 1), $expect[$i][1]) ($got.StartsWith($expect[$i][0]))
        }
        # SuperCC reads a save with no "Encode" as ISO-8859-1: its bytes C3 BF are TWO moves, four in
        # all. Read as UTF-8 they would be one different move, three in all -- replayed without a word.
        $latin = @($lines | Where-Object { $_.StartsWith("Synth`t2_Latin-MS.json`t") })
        Check "an older save with no Encode field is decoded as SuperCC decodes it: 4 moves, not 3" `
              (($latin.Count -eq 1) -and ($latin[0] -match "`tmove=\d+/4`t"))
        # ... and a modern one ("Encode":"UTF-8") the other way: the same two bytes are ONE move there.
        $utf = @($lines | Where-Object { $_.StartsWith("Synth`t2_Utf-MS.json`t") })
        Check "a modern save with Encode=UTF-8 is decoded as UTF-8: 3 moves, not 4" `
              (($utf.Count -eq 1) -and ($utf[0] -match "`tmove=\d+/3`t"))
        # Lynx has no background layer and no slip list: its line says so, rather than reading as a crash.
        $lynx = @($lines | Where-Object { $_.StartsWith("Synth`t1_Walk-LYNX.json`t") })
        Check "a Lynx save's line carries bg=- and slip=- (no background layer, no slip list)" `
              (($lynx.Count -eq 1) -and ($lynx[0] -match "`tbg=-`t") -and ($lynx[0] -match "`tslip=-`t"))
        Check "a finished run leaves no .partial file behind" (-not (Test-Path -LiteralPath "$a1.partial"))
        $renum = @($lines | Where-Object { $_.StartsWith("Synth`t9_Renum-MS.json`t") })
        # pos= is input identity, so it comes AFTER src=, in the part a compare treats as the inputs.
        Check "the renumbered save says which position it was found at (pos=5), among its inputs" `
              (($renum.Count -eq 1) -and $renum[0] -match "`tsrc=[^`t]+`tpos=5$")
        $beta = @($lines | Where-Object { $_.StartsWith("Dupes`t1_Beta-MS.json`t") })
        Check "the Beta save says it was found at position 2 (pos=2), among its inputs" `
              (($beta.Count -eq 1) -and $beta[0] -match "`tsrc=[^`t]+`tpos=2$")
        # Badsig's Walk record is byte-identical to Cclset's, and the fingerprint comes from the inputs
        # alone, so an OPENFAIL save carries the same level half a jar that opened the set would write.
        # The level half is "<signature>/<record CRC>": the records match, the signatures do not.
        $levelHalf = { param($k) $l = @($lines | Where-Object { $_.StartsWith($k) }); if ($l.Count -eq 1) { $l[0].Split(":")[-1] } else { "?" + $l.Count } }
        $refusedLevel = & $levelHalf "Badsig`t1_Walk-MS.json`t"; $openedLevel = & $levelHalf "Cclset`t1_Walk-MS.json`t"
        Check "an OPENFAIL save is fingerprinted by its level's record, as an opened set's would be ($refusedLevel ~ $openedLevel)" `
              (($refusedLevel -match '^0004aaac/([0-9a-f]{8})$') -and ($openedLevel -match ('^0002aaac/' + $refusedLevel.Split("/")[-1] + '$')))
        # Every CRC is exactly 8 hex digits, so a truncated or garbled one cannot pass. The level half is
        # "<signature>/<record CRC>" for a picked level, the whole set's CRC, or "-".
        $unprinted = @($lines | Where-Object { $_ -notmatch "`tsrc=[0-9a-f]{8}:([0-9a-f]{8}/[0-9a-f]{8}|[0-9a-f]{8}|-)(`tpos=\d+)?$" })
        Check ("every line ends with its inputs' fingerprint, src=<save>:<level>{0}" -f $(if ($unprinted) { " (missing on " + $unprinted.Count + ")" } else { "" })) `
              ($unprinted.Count -eq 0)

        $xArgs = Get-CorpusArgs $classDir $ccA $a2 "" $true
        $xo = @(& $javaExe $xArgs 2>&1 | ForEach-Object { "$_" })
        $xcode = $LASTEXITCODE
        Check "-CrossCheck: the harness's replay matches SuperCC's own Solution.load (exit 0)" ($xcode -eq 0)
        # checked 11 = #Hash Walk, Cclset Walk, Dupes Alpha, Short Walk, Synth Walk (MS and LYNX)/Latin/Stay/Utf/Again, and the
        # Click replay that THROWS (both paths must throw alike); skipped 2 = Dupes Beta and Synth Renum,
        # whose own numbers are not their positions, so Solution.load would reload a different level.
        Check "-CrossCheck accounts for every replayed save, including the one that throws" `
              (@($xo | Where-Object { $_ -match '^CROSSCHECK checked=11 skipped=2 differ=0$' }).Count -eq 1)
        Check "-CrossCheck SAYS that SuperCC's own Solution.load would replay the 2 renumbered saves on the wrong level" `
              (@($xo | Where-Object { $_ -match '^NOTE: 2 save\(s\) belong to a level whose number is not its position' }).Count -eq 1)

        Check "two runs of the same collection are byte-identical (the second with -CrossCheck on)" `
              ((Test-Path -LiteralPath $a2) -and ((Get-ShortHash $a1) -eq (Get-ShortHash $a2)))

        $r = Invoke-Compare $a1 $a2
        Check "a compare of a jar against ITSELF is refused without -SameJar (exit 2)" ($r.Code -eq 2)
        $r = Invoke-Compare $a1 $a2 -Same
        Check "an identical pair compares as identical with -SameJar (exit 0)" ($r.Code -eq 0)

        $bad = Join-Path $td "malformed.txt"
        [IO.File]::WriteAllLines($bad, [string[]]@("Synth`tonly-two-fields"), $utf8)
        $r = Invoke-Compare $a1 $bad -Same
        Check "a malformed run is an error (exit 2), not 'differences found' (exit 1)" ($r.Code -eq 2)

        # The refusal fails CLOSED. Each copy keeps a1's data, so only the header is in question.
        $data = @($all | Select-Object -Skip 1)
        $unknown = Join-Path $td "unknown-jar.txt"
        [IO.File]::WriteAllLines($unknown, [string[]](@("#jar`t-`tNoSuchFileException") + $data), $utf8)
        $r = Invoke-Compare $a1 $unknown
        Check "a run whose jar is UNKNOWN is refused like the same jar (exit 2)" ($r.Code -eq 2)
        $garbled = Join-Path $td "garbled-jar.txt"
        [IO.File]::WriteAllLines($garbled, [string[]](@("#jar`tABC`tx.jar") + $data), $utf8)
        $r = Invoke-Compare $a1 $garbled
        Check "... and so is one whose hash is not a sha256 (exit 2, not a crash)" `
              (($r.Code -eq 2) -and (@($r.Lines | Where-Object { $_ -match 'does not record which jar' }).Count -eq 1))
        $shifted = Join-Path $td "shifted-header.txt"
        # The #end count is raised by one to cover the displaced header as a data line, and -SameJar
        # (and -MixedHarness) waive the header checks: so it is the EMPTY LINE that must be refused,
        # not the count or a missing header, either of which would catch this too.
        $shiftedLines = @(@("") + @($all | Where-Object { -not (Test-End $_) }) + ("#end`t" + ($all.Count - 1)))
        [IO.File]::WriteAllLines($shifted, [string[]]$shiftedLines, $utf8)
        $r = Invoke-Compare $shifted $a1 -Same -Mixed
        Check "an empty first line, which would push the header into the data, is refused (exit 2)" `
              (($r.Code -eq 2) -and (@($r.Lines | Where-Object { $_ -match 'empty line 1' }).Count -eq 1))

        # Runs written by different HARNESS versions are refused too: their differences would be the
        # harness's own and be blamed on the engine. a1's data, with only the harness field changed.
        $otherHarness = Join-Path $td "other-harness.txt"
        [IO.File]::WriteAllLines($otherHarness, [string[]](@(($header -replace "`tharness=[0-9A-F]{64}", ("`tharness=" + ("0" * 64)))) + $data), $utf8)
        $r = Invoke-Compare $a1 $otherHarness -Same
        Check "a pair written by two different harness versions is refused (exit 2)" `
              (($r.Code -eq 2) -and (@($r.Lines | Where-Object { $_ -match 'one known version of the harness' }).Count -eq 1))
        $r = Invoke-Compare $a1 $otherHarness -Same -Mixed
        Check "... unless -MixedHarness says that is the point (exit 0 here: the data is a1's)" ($r.Code -eq 0)
        $noHarness = Join-Path $td "no-harness.txt"
        [IO.File]::WriteAllLines($noHarness, [string[]](@(($header -replace "`tharness=[0-9A-F]{64}", "")) + $data), $utf8)
        $r = Invoke-Compare $a1 $noHarness -Same
        Check "... and so is a run that does not say which harness wrote it (exit 2)" ($r.Code -eq 2)

        # A save missing from one side is a difference in the COLLECTION: shown, but not an engine loss.
        $noWalk = Join-Path $td "missing-walk.txt"
        $kept = @($all | Where-Object { -not $_.StartsWith("Synth`t1_Walk-MS.json`t") -and -not (Test-End $_) })
        [IO.File]::WriteAllLines($noWalk, [string[]]($kept + ("#end`t" + ($kept.Count - 1))), $utf8)
        $r = Invoke-Compare $a1 $noWalk -Same
        Check "a solved save only in the baseline is a difference (exit 1) but not a solved route LOST" `
              (($r.Code -eq 1) -and
               (@($r.Lines | Where-Object { $_ -match '^solved routes lost in all: 0$' }).Count -eq 1) -and
               (@($r.Lines | Where-Object { $_ -match '^solved routes the candidate never replayed .*: 1$' }).Count -eq 1))

        $typoArgs = Get-CorpusArgs $classDir $ccA (Join-Path $td "typo.txt") "Synht" $false
        Check "a -Pack that names no folder is an error (exit 2), not an empty clean run" ((Run-Quiet $typoArgs) -eq 2)

        # The same failure, failing differently, is a STATUS change -- not "final state only": a line
        # that is not OK has no end state. a1's data with only the EXC line's message changed.
        $otherCrash = Join-Path $td "other-crash.txt"
        [IO.File]::WriteAllLines($otherCrash, [string[]]@($all | ForEach-Object {
            if ($_.StartsWith("Synth`t6_Click-MS.json`tEXC`t")) { $_ -replace "`tmove=[0-9]+/", "`tmove=0/" } else { $_ } }), $utf8)
        $r = Invoke-Compare $a1 $otherCrash -Same
        Check "a crash that moved is STATUS EXC, detail changed -- never FINAL STATE ONLY" `
              (($r.Code -eq 1) -and
               (@($r.Lines | Where-Object { $_ -match '^\s+STATUS EXC, detail changed\s+Synth\s+6_Click-MS\.json' }).Count -eq 1) -and
               (@($r.Lines | Where-Object { $_ -match '^\s+final state only:\s+0$' }).Count -eq 1))

        # A different JDK is noted, not refused (end states do not depend on it; exception wording may).
        $otherJdk = Join-Path $td "other-jdk.txt"
        [IO.File]::WriteAllLines($otherJdk, [string[]](@(($header -replace "`tjava=[^`t]+", "`tjava=1.0-test")) + $data), $utf8)
        $r = Invoke-Compare $a1 $otherJdk -Same
        Check "a pair run on different JDKs is compared, with a NOTE that exception wording may differ (exit 0)" `
              (($r.Code -eq 0) -and (@($r.Lines | Where-Object { $_ -match 'the runs used different JDKs' }).Count -eq 1))

        # A folder given WITH a trailing backslash, in a path with a space -- as "...\Chip's Challenge\"
        # tab-completes. Through the real run mode: PowerShell 5.1 would quote it, the "\" would escape
        # that quote for java, and the next argument would vanish into this one.
        $trail = Join-Path $td "trailing.txt"
        $q = { param($s) $s.Replace("'", "''") }      # each path inside single quotes: double its own '
        $null = & powershell -NoProfile -ExecutionPolicy Bypass -Command ("& '{0}' -CCDir '{1}\' -Jar '{2}' -Pack Synth -Out '{3}'" -f `
                    (& $q $PSCommandPath), (& $q $ccA), (& $q $jarPath), (& $q $trail)) 2>&1
        $trailCode = $LASTEXITCODE
        $trailLines = if (Test-Path -LiteralPath $trail) { @(Select-Data ([IO.File]::ReadAllLines($trail, $utf8))) } else { @() }
        Check ("a -CCDir with a trailing backslash and a space still runs (exit {0}, {1} lines)" -f $trailCode, $trailLines.Count) `
              (($trailCode -eq 0) -and ($trailLines.Count -eq @($lines | Where-Object { $_.StartsWith("Synth`t") }).Count))

        # That run covers one pack (pack=Synth). Against the whole collection it compares that pack
        # ALONE -- the other packs were never asked of it -- and two different single packs share nothing.
        $r = Invoke-Compare $a1 $trail -Same
        Check "a one-pack run against a whole-collection run compares that pack alone (identical, exit 0)" `
              (($r.Code -eq 0) -and (@($r.Lines | Where-Object { $_ -match 'covers only pack Synth' }).Count -eq 1))
        $otherPack = Join-Path $td "other-pack.txt"
        $tAll = @([IO.File]::ReadAllLines($trail, $utf8))
        [IO.File]::WriteAllLines($otherPack, [string[]](@($tAll[0] -replace "`tpack=Synth$", "`tpack=Dupes") + @($tAll | Select-Object -Skip 1)), $utf8)
        $r = Invoke-Compare $trail $otherPack -Same
        Check "... and two runs of DIFFERENT single packs are refused (exit 2)" ($r.Code -eq 2)

        # A run cut short (killed, interrupted, failed) is refused, never read as a short collection:
        # every save it never reached would be "only in baseline" and out of the engine headline.
        $cut = Join-Path $td "cut-short.txt"
        [IO.File]::WriteAllLines($cut, [string[]]@($all | Select-Object -First 8), $utf8)
        $r = Invoke-Compare $a1 $cut -Same
        Check "a run with no #end line -- cut short -- is refused as INCOMPLETE (exit 2)" `
              (($r.Code -eq 2) -and (@($r.Lines | Where-Object { $_ -match 'INCOMPLETE' }).Count -eq 1))
        $miscount = Join-Path $td "miscount.txt"
        [IO.File]::WriteAllLines($miscount, [string[]]@($all | Where-Object { -not $_.StartsWith("Synth`t2_Stay-MS.json`t") }), $utf8)
        $r = Invoke-Compare $a1 $miscount -Same
        Check "... and so is one whose #end count does not match its lines (exit 2)" ($r.Code -eq 2)

        Check "the second fixture collection runs to completion too (exit 0)" `
              ((Run-Quiet (Get-CorpusArgs $classDir $ccB $bOut "" $false)) -eq 0)

        # ccB differs from ccA in three SAVES, so as it stands that is a change in the collection:
        # every one is INPUT CHANGED, and none may be blamed on the engine.
        $r = Invoke-Compare $a1 $bOut -Same
        Check "a re-recorded save is INPUT CHANGED, never an engine loss (3 of them, 0 solved routes lost)" `
              (($r.Code -eq 1) -and
               (@($r.Lines | Where-Object { $_ -match '^\s+input changed:\s+3$' }).Count -eq 1) -and
               (@($r.Lines | Where-Object { $_ -match '^solved routes lost in all: 0$' }).Count -eq 1) -and
               (@($r.Lines | Where-Object { $_ -match '^\s+INPUT CHANGED \(save\) \(was solved\)\s+Synth\s+1_Walk-MS\.json' }).Count -eq 1))
        # ... but never silent: the solved routes among them are counted on their own line (Walk, and
        # Again, which no longer parses), with what to do about it.
        Check "... and the solved routes among them are still counted, apart (2), with the remedy" `
              ((@($r.Lines | Where-Object { $_ -match '^solved routes lost where the input ALSO changed.*: 2$' }).Count -eq 1) -and
               (@($r.Lines | Where-Object { $_ -match 're-run the BASELINE jar on the current collection' }).Count -eq 1))

        # The fingerprint must depend on the INPUTS only. ccA and ccB hold byte-identical level sets,
        # so every save's LEVEL half must match across the two runs -- above all Again's, whose status
        # changes (OK -> BADJSON): a fingerprint that followed the status would file a real engine
        # regression as INPUT CHANGED. And the SAVE half must differ for exactly the three re-recorded
        # saves. (A two-jar run over one collection cannot be staged here with one jar; this is the
        # property that makes its fingerprints identical.)
        $bAll = @([IO.File]::ReadAllLines($bOut, $utf8))
        $bLines = @(Select-Data $bAll)
        $aIn = New-Object 'System.Collections.Generic.Dictionary[string,string[]]' ([StringComparer]::Ordinal)
        foreach ($l in $lines) { $p = $l.Split([char[]]"`t", 3); $aIn[$p[0] + "`t" + $p[1]] = (Split-Src $p[2])[1].Split(":") }
        $levelMoved = @(); $saveMoved = @()
        foreach ($l in $bLines) {
            $p = $l.Split([char[]]"`t", 3); $key = $p[0] + "`t" + $p[1]; $in = (Split-Src $p[2])[1].Split(":")
            if (-not $aIn.ContainsKey($key) -or $aIn[$key][1] -ne $in[1]) { $levelMoved += $p[1] }
            if (-not $aIn.ContainsKey($key) -or $aIn[$key][0] -ne $in[0]) { $saveMoved += $p[1] }
        }
        Check ("the level fingerprint depends on the inputs only: the same in both runs for every save, even one whose status changed{0}" -f $(if ($levelMoved) { " (moved: " + ($levelMoved -join ", ") + ")" } else { "" })) `
              (($bLines.Count -eq $lines.Count) -and ($levelMoved.Count -eq 0))
        Check ("the save fingerprint changes for exactly the three re-recorded saves (got: {0})" -f ($saveMoved -join ", ")) `
              ((($saveMoved | Sort-Object) -join ",") -eq "1_Walk-MS.json,2_Stay-MS.json,3_Again-MS.json")

        # The same end-state differences as a different JAR would produce them over ONE collection:
        # the states are the real replays above, and the save halves -- the only inputs that differ,
        # as just checked -- are set to ccA's. Now each must be classified as the engine's.
        $bSameInputs = Join-Path $td "b-same-inputs.txt"
        [IO.File]::WriteAllLines($bSameInputs, [string[]]@(for ($i = 0; $i -lt $bAll.Count; $i++) {
            if ($i -eq 0 -or (Test-End $bAll[$i])) { $bAll[$i]; continue }
            $p = $bAll[$i].Split([char[]]"`t", 3)
            $srcAt = $bAll[$i].LastIndexOf("`tsrc=")
            $bAll[$i].Substring(0, $srcAt) + "`tsrc=" + $aIn[$p[0] + "`t" + $p[1]][0] + ":" + $bAll[$i].Substring($srcAt).Split(":")[1]
        }), $utf8)
        $r = Invoke-Compare $a1 $bSameInputs -Same
        $o = $r.Lines
        Check "real engine-level differences fail the compare (exit 1)" ($r.Code -eq 1)
        Check "a solved route that no longer parses is STATUS ... (was solved), not LOST THE EXIT" `
              (@($o | Where-Object { $_ -match '^\s+STATUS OK -> BADJSON \(was solved\)\s+Synth\s+3_Again-MS\.json' }).Count -eq 1)
        Check "a shorter Walk route is reported as LOST THE EXIT" `
              (@($o | Where-Object { $_ -match '^\s+LOST THE EXIT\s+Synth\s+1_Walk-MS\.json' }).Count -eq 1)
        Check "a different Stay route, same outcome, is reported as FINAL STATE ONLY" `
              (@($o | Where-Object { $_ -match '^\s+FINAL STATE ONLY\s+Synth\s+2_Stay-MS\.json' }).Count -eq 1)
        $want = [ordered]@{
            "identical"         = '^identical: 22 of 25$'
            "status changed"    = '^\s+status changed:\s+1$'
            "lost the exit"     = '^\s+lost the exit:\s+1$'
            "gained the exit"   = '^\s+gained the exit:\s+0$'
            "final state only"  = '^\s+final state only:\s+1$'
            "input changed"     = '^\s+input changed:\s+0$'
            "only in baseline"  = '^\s+only in baseline:\s+0$'
            "only in candidate" = '^\s+only in candidate:\s+0$'
            "solved routes lost in all" = '^solved routes lost in all: 2$'
        }
        $missing = @($want.Keys | Where-Object { $rx = $want[$_]; @($o | Where-Object { $_ -match $rx }).Count -ne 1 })
        Check ("every count is exactly right, nothing invented or dropped{0}" -f $(if ($missing) { " (wrong: " + ($missing -join ", ") + ")" } else { "" })) `
              ($missing.Count -eq 0)

        if ($ok) { Write-Host "  corpus harness self-test: all green"; exit 0 }
        exit 1
    } finally {
        if (Test-Path -LiteralPath $td) { Remove-Item -LiteralPath $td -Recurse -Force -ErrorAction SilentlyContinue }
        if ($classDir -and (Test-Path -LiteralPath $classDir)) {
            Remove-Item -LiteralPath $classDir -Recurse -Force -ErrorAction SilentlyContinue
        }
    }
}

# ---------------------------------------------------------------- run
if (-not $CCDir) {
    Fail "-CCDir is required: a folder holding data\<pack>.dat and succsave\<pack>\*.json (not in this repo)"
}
$CCDir = Resolve-UserPath $CCDir
if (-not (Test-Path -LiteralPath (Join-Path $CCDir "data")) -or
    -not (Test-Path -LiteralPath (Join-Path $CCDir "succsave"))) {
    Fail "no data\ and succsave\ under $CCDir"
}
$jarPath = Resolve-Jar
if (-not (Test-Path -LiteralPath $jarPath -PathType Leaf)) { Fail "no jar FILE at $jarPath (a folder, or nothing)" }
if (-not $Out) {
    $leaf = [IO.Path]::GetFileNameWithoutExtension($jarPath)
    $Out = Join-Path $root ("corpus-results\corpus-{0}-{1}.txt" -f $leaf, (Get-Date -Format "yyyyMMdd-HHmmss"))
}
$Out = Resolve-UserPath $Out
# A folder is not an output file: the finished run is MOVED onto -Out, which would replace an empty
# folder outright, or fail on a full one after minutes of work.
if (Test-Path -LiteralPath $Out -PathType Container) { Fail "-Out names a folder ($Out); give a file in it, e.g. $Out\before.txt" }
$outDir = Split-Path -Parent $Out
if ($outDir -and -not (Test-Path -LiteralPath $outDir)) { New-Item -ItemType Directory -Force -Path $outDir | Out-Null }
<# A run lists a personal collection. Inside ANY git working tree it may only go where git ignores
   it: anywhere else, one `git add -A` would publish it (CI's guard only catches it after the push).
   Git itself is asked -- from the output's own folder -- rather than comparing path strings, which
   an 8.3 short name, a subst drive or a junction into the repo would slip past. #>
$inTree = & git -C $outDir rev-parse --is-inside-work-tree 2>$null
if ($LASTEXITCODE -eq 0 -and $inTree -eq "true") {
    # Both names: the run is written as <out>.partial first, and left there if it fails -- an ignore
    # pattern that covers <out> need not cover that.
    foreach ($leaf in @((Split-Path -Leaf $Out), ((Split-Path -Leaf $Out) + ".partial"))) {
        & git -C $outDir check-ignore -q -- $leaf 2>$null
        if ($LASTEXITCODE -ne 0) {
            Fail "-Out $Out (or its $leaf while running) is inside a git working tree and NOT ignored there: use corpus-results\ (gitignored), or a folder outside the repo"
        }
    }
}
$global:LASTEXITCODE = 0

$classDir = Build-Harness $jarPath
try {
    # Inside the try, so the finally removes the temp folder (and its jar copy) whatever happens here.
    # The hash of the COPY -- the bytes that actually run -- not of $jarPath, which may change under us.
    $jarHash = Get-ShortHash (Get-JarCopy $classDir)
    Write-Host ("jar  {0}  (sha256 {1}; replayed from a private copy)" -f $jarPath, $jarHash)
    $started = Get-Date
    $javaExe = Join-Path (Get-JdkBin "java.exe") "java.exe"
    # Into a variable first -- @(Get-CorpusArgs ...) would nest the array (see Get-CorpusArgs).
    # Out-Host streams the progress to the console; the DATA goes straight to $Out from Java as
    # UTF-8, never through `>` (UTF-16 in 5.1 -- the trace harness's original defect).
    $runArgs = Get-CorpusArgs $classDir $CCDir $Out $Pack ([bool]$CrossCheck)
    # Reset first: if java.exe cannot even start, PowerShell sets no exit code, and javac's 0 from
    # Build-Harness would read as a successful run.
    $global:LASTEXITCODE = -1
    & $javaExe $runArgs | Out-Host
    $code = $LASTEXITCODE
} finally {
    if (Test-Path -LiteralPath $classDir) { Remove-Item -LiteralPath $classDir -Recurse -Force -ErrorAction SilentlyContinue }
}
if ($code -ne 0 -and $code -ne 3) {
    # The output was never moved into place, so $Out is as it was. Say where the partial one is:
    # its last lines show how far the run got (which save hung or crashed), and it is safe to delete.
    if (Test-Path -LiteralPath "$Out.partial") {
        Write-Host "  the incomplete output is at $Out.partial (never compared; delete it when done)"
    }
    Fail "CorpusCheck failed (exit $code)"
}
if ($code -eq 3) {
    # The run was NOT moved into place: a replay shown not to match SuperCC's must never become a
    # baseline. $Out is as it was; the run is at $Out.partial, for the MIRRORDIFF lines' context.
    Write-Host ""
    Write-Host "  -CrossCheck: the harness's replay DIFFERS from SuperCC's own Solution.load (MIRRORDIFF lines above)."
    Write-Host "  corpus\CorpusCheck.java's loop no longer matches Solution.tickBasicMoves -- fix that before trusting a compare."
    Write-Host "  This run was NOT kept as $Out; it is at $Out.partial (never compared; delete it when done)."
    exit 3
}

$all = @([IO.File]::ReadAllLines($Out, $utf8))
# The file at $Out must be THIS run's: its header names the copy that just ran. Anything else -- an
# older baseline left in place by a run that never really happened -- is not reported as this one.
$head = if ($all.Count -gt 0) { $all[0].Split([char[]]"`t") } else { @() }
if ($head.Count -lt 2 -or $head[0] -ne "#jar" -or -not $head[1].StartsWith($jarHash)) {
    Fail "$Out does not hold this run's output -- its header names a different jar, or none -- so this run is not reported"
}
$lines = @(Select-Data $all)
Write-Host ""
Write-Host ("jar  {0}  (sha256 {1})" -f $jarPath, $jarHash)
$nowHash = if (Test-Path -LiteralPath $jarPath) { Get-ShortHash $jarPath } else { "gone" }
if ($nowHash -ne $jarHash) {
    Write-Host ("  NOTE  {0} changed during the run (now {1}). This run replayed the copy taken at the start," -f $jarPath, $nowHash)
    Write-Host ("        sha256 {0}, as its header says -- not the jar now on disk." -f $jarHash)
}
Write-Host ("out  {0}  ({1} solutions in {2:N0} s, sha256 {3})" -f $Out, $lines.Count,
            ((Get-Date) - $started).TotalSeconds, (Get-ShortHash $Out))
$lines | ForEach-Object { ($_.Split([char[]]"`t", 4))[2] } | Group-Object | Sort-Object Name |
    ForEach-Object { Write-Host ("  {0,-9} {1}" -f $_.Name, $_.Count) }
# The same definition of "solved" -Compare uses: Test-Won on everything after pack and file.
Write-Host ("  reaching the exit: {0}" -f @($lines | Where-Object { $p = $_.Split([char[]]"`t", 3); $p.Count -eq 3 -and (Test-Won $p[2]) }).Count)
Write-Host ""
Write-Host "compare against another run with:"
Write-Host "  powershell -File corpus.ps1 -Compare -Baseline <earlier.txt> -Candidate `"$Out`""
exit 0
