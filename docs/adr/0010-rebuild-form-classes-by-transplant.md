# 0010 — Rebuild a form class whose `.form` is unchanged, without IntelliJ

**Status:** Accepted (2026-11-03) · **Amends:** 0001, 0002 · **Applies to:** `formclass.ps1`, `formclass/FormTransplant.java`

## Context

ADR 0001 rests on "fifteen files nobody modifies." The upstream merge broke that: of the commits
still to merge after jc-15, four edit a form-based class — `tools/SeedSearch.java` (40495dd),
`graphics/Gui.java` (72476bc, d78f6f3), `tools/ControlGUI.java` (5f928e0, fb8fe25) and
`tools/ChooseTileSize.java` (d78f6f3). Only one of them, 5f928e0, also edits a `.form`.

IntelliJ's form compiler (intellij-community, `java/compiler/forms-compiler`, `AsmCodeGenerator`)
does two things to javac's output, and nothing else:

1. It **adds** methods named `$$$…$$$` — `$$$setupUI$$$()`, `$$$getRootComponent$$$()`, and
   `$$$getFont$$$(…)` where the form sets a font. Their bodies are generated from the `.form` and
   from the bound fields' names and types; nothing else in the `.java` reaches them.
2. It **inserts** `aload_0; invokespecial $$$setupUI$$$()V` into each constructor that calls
   `super(…)`. `FormConstructorVisitor` (read at `idea/211.7628.21`, the 2021 line that built the
   committed classes, and unchanged on master) puts it before the first of:
   - a `GETFIELD` of a bound name;
   - after `super(…)`, any method call except `invokespecial <init>` on the superclass (which it
     reads as `super(…)` again);
   - after `super(…)`, any jump;
   - a `RETURN`.

So while a `.form` is unchanged, its generated members are already sitting in the committed class,
exactly as IntelliJ would generate them again.

Measured on the jc-15 tree with JDK 16.0.2 — javac on the **unchanged** source of all fifteen, the
committed members copied in, the call inserted by that rule — against IntelliJ's own classes:

- all 37 classes (15 plus 22 inner) match instruction for instruction, including where IntelliJ put
  the call — in jc-15's SeedSearch, inside the first field initializer, between `iconst_0` and
  `AtomicInteger.<init>`, exactly where the rule predicts;
- four top-level classes come out **byte-identical**: ChangeTimer, ChooseWindowSize,
  GameGifRecorder and SeedSearch;
- HelpWindow and TSPHelpWindow differ only in constant-pool *numbering*;
- the other nine also differ in **stack-map frames**. IntelliJ recomputed the frames for the whole
  class, choosing different but equally valid encodings (`same_frame_extended` where javac
  wrote `chop`, for one). This tool keeps javac's frames, and the JVM verifies either.

## Decision

`formclass.ps1 -Rebuild <file>` rebuilds a form class whose `.form` is unchanged: javac compiles the
`.java` (JDK 16.0.2, `-g`, against a built jar), and `formclass/FormTransplant.java` copies the
generated members from the committed class and inserts the constructor call by that rule, ported
line for line. The result replaces the committed `.class` files, which stay the authoritative
bytecode (ADR 0002).

The tool **refuses** whatever it cannot prove safe:
- **from IntelliJ's own "Cannot bind" refusals:** a bound field that is missing, static or final.
  `$$$setupUI$$$` assigns every bound field, and the JVM rejects that for a final one
  (IllegalAccessError the moment the window opens).
- **broken references:** a copied member that uses a field the new class lacks, has with a
  different type or static-ness, or has as final; or that calls a method the new class lacks or
  has changed between static and instance (IncompatibleClassChangeError on open, measured).
- **a call before `this` exists:** placement before the `<init>` that really initializes `this`.
  IntelliJ's flag mistakes a `new <Superclass>()` among `super(…)`'s own arguments for `super(…)`,
  and the class then fails verification (measured).
- **the other shapes IntelliJ's flag gets wrong:** a `new <ThisClass>(…)` before the call (taken for
  `this(…)`, after which bound reads are ignored — the call landed after one: NullPointerException
  on open, measured), any bound read before the call, or no call point at all. And where the
  `<init>` that initializes `this` is `this(…)`, the constructor is always left alone, even when
  IntelliJ's flag would give it a second call (measured with `this(new Object(), 1)`: every
  component built twice). This is the one place the tool deliberately differs from IntelliJ.
- **a constructor where even IntelliJ's placement is not proven to run exactly once on every path:**
  a loop, switch, throw or try block between `super(…)` and the call. An exception inside a try
  there, caught past the call, skips setup. Deliberately conservative: it also refuses a try (or a
  `synchronized` block) whose code before the call cannot throw.
- **a second top-level class** in the `.java` (`formclass.ps1` sees every file javac writes).
- **wrong inputs:** javac input that is already instrumented, or that calls `$$$setupUI$$$` itself;
  an IntelliJ input with no `$$$setupUI$$$`; a generated *field*, which none of the fifteen has.

`formclass.ps1 -Check` rebuilds all fifteen into a temp folder and compares them with the committed
classes, using `verify-splice.ps1`'s comparison minus pool index numbers (the shared `Get-ClassKey`
in `build-config.ps1`). CI and the release workflow run it, so a `.java` edit whose class was never
rebuilt cannot ship. `-SelfTest` (CI) makes 18 checks on 17 specimens: the refusals that guard
real failures, and the placements that differ from a naive rule.

It uses the JDK's internal copy of ASM (`jdk.internal.org.objectweb.asm`), not a downloaded
library. That keeps the no-package-manager rule (ADR 0002) intact. The cost is a pin to JDK 16.0.2,
which `verify-splice.ps1` and the coverage baseline already need.

**How the first version was wrong, so nobody repeats it.** It used a rule written from memory: any
method call after `super(…)`, a bound `GETFIELD`, or `RETURN`. `/code-review` showed it putting the
call inside one arm of `flag ? new Object() : null`. The hostile review then read IntelliJ's source:
a jump IS a trigger, which is what keeps that case safe, and `new Object()` in a class extending
`Object` is NOT one. The old rule ran setup before such a field was assigned. The same review built a
`final` bound field, an exception that skips setup, and a dropped second class. A second
`/code-review` added a called method made static and a `new JPanel()` inside `super(…)`, and a second
hostile pass the two shapes where IntelliJ's own flag is wrong. Each passed `-Check`, `-SelfTest`
and the whole suite, and broke the window — or the class — only when it was used. Every one was
reproduced before it was fixed. The lesson: a port of IntelliJ's rule is only as safe as IntelliJ's
rule, so the refusals around it are what make it safe, and `FormClassTest` is what proves a window
still builds.

## Consequences

- **A `.java`-only edit to a form class is now an ordinary change:** run `-Rebuild`, run
  `verify-splice.ps1 -UpdateFormBaseline`, and commit the classes, the source and the baseline
  together.
- **A `.form` edit still needs IntelliJ.** `$$$setupUI$$$` itself changes, and this tool can only
  copy it. `verify-splice.ps1` hashes every `.form`, so such an edit fails there; `-Check` alone
  would not see it, because it copies the members from the committed class.
- **`-Check` proves a committed form class matches its `.java`, not its `.form`, and not that the
  tool is right.** Once a class has been rebuilt, both sides of its comparison came from the tool.
  So `test/FormClassTest.java` runs the real constructor of every form window that can run headless
  (14 of the 15). The review found the defects above by constructing windows the same way, and
  `FormClassTest` fails on the broken builds they produced (measured). `graphics.Gui` is the
  `JFrame` itself, so nothing headless reaches its setup; since jc-17, `test/GuiWindowTest.java`
  builds it in a separate non-headless JVM (skipped without a desktop). That test was written, and
  shown to fail on jc-16, before the first rebuild of `Gui`.
- The tool and its check need **JDK 16.0.2 exactly**, and say so instead of producing a near-miss.
  On JDK 18 javac's output differs (verify-splice's own false positives), and the internal ASM is
  not a public API — it may not exist in a much later JDK.
- ADR 0002's "merging upstream changes means rebuilding the baseline in IntelliJ" now holds only for
  `.form` changes.
