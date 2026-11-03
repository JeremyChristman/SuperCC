import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.internal.org.objectweb.asm.tree.AbstractInsnNode;
import jdk.internal.org.objectweb.asm.tree.ClassNode;
import jdk.internal.org.objectweb.asm.tree.FieldInsnNode;
import jdk.internal.org.objectweb.asm.tree.FieldNode;
import jdk.internal.org.objectweb.asm.tree.InsnList;
import jdk.internal.org.objectweb.asm.tree.JumpInsnNode;
import jdk.internal.org.objectweb.asm.tree.LabelNode;
import jdk.internal.org.objectweb.asm.tree.LookupSwitchInsnNode;
import jdk.internal.org.objectweb.asm.tree.MethodInsnNode;
import jdk.internal.org.objectweb.asm.tree.MethodNode;
import jdk.internal.org.objectweb.asm.tree.TableSwitchInsnNode;
import jdk.internal.org.objectweb.asm.tree.TryCatchBlockNode;
import jdk.internal.org.objectweb.asm.tree.VarInsnNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rebuilds ONE form-based class whose .form is unchanged: javac's class for the edited .java, plus
 * the members IntelliJ's form compiler generated, copied from the committed IntelliJ-built class.
 *
 * <pre>
 *   java --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED
 *        --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED
 *        FormTransplant &lt;intellij.class&gt; &lt;javac.class&gt; &lt;form&gt; &lt;out.class&gt;
 * </pre>
 *
 * WHAT INTELLIJ ADDS, AND THEREFORE ALL THIS COPIES. The form compiler (intellij-community,
 * java/compiler/forms-compiler, AsmCodeGenerator) instruments the javac output in two ways:
 *
 *   1. It ADDS methods named $$$...$$$: $$$setupUI$$$() builds every component the .form describes,
 *      $$$getRootComponent$$$() returns the root, and $$$getFont$$$ exists when the form sets a font.
 *      Their bodies depend only on the .form and on the names and types of the bound fields -- not
 *      on anything else in the .java -- so while the .form is unchanged, the committed copies are
 *      exactly what IntelliJ would generate again.
 *   2. It INSERTS `aload_0; invokespecial $$$setupUI$$$()V` into every constructor that calls
 *      super(...) rather than this(...), at the point FormConstructorVisitor picks -- ported line
 *      for line in insertSetupCall, whose comment has the rule. It is why IntelliJ's SeedSearch
 *      constructor calls $$$setupUI$$$ in the middle of its first field initializer (between
 *      `iconst_0` and `AtomicInteger.<init>`): that invokespecial is the first trigger after super().
 *
 * WHAT IT REFUSES. Everything it cannot prove is safe stops the run with a non-zero exit:
 *   - the IntelliJ class has no $$$setupUI$$$ (not a form class, or already a javac build);
 *   - the javac class already has $$$ members (it was fed an instrumented class by mistake), or
 *     calls $$$setupUI$$$ itself;
 *   - the two classes differ in name or superclass;
 *   - a copied member refers to a field of this class that the new class lacks, or has with a
 *     different type or static-ness -- e.g. a bound component renamed in the .java, not the .form;
 *   - a copied member assigns a field that is final in the new class;
 *   - a copied member calls a method of this class that the new class lacks, or has changed
 *     between static and instance;
 *   - a bound field named in the .form is missing, static or final (IntelliJ's own three refusals);
 *   - a constructor where IntelliJ's placement is not proven to run exactly once on every path
 *     (a loop, switch, throw or try block before it -- see insertSetupCall);
 *   - IntelliJ's class added a $$$ FIELD (none of the fifteen forms here has one; if a future one
 *     does, this tool must learn to copy it rather than silently drop it).
 * A second top-level class in the same .java is refused by formclass.ps1, which sees every file
 * javac wrote.
 *
 * WHAT IT CANNOT DO: rebuild a class whose .form CHANGED. Then $$$setupUI$$$ itself must change, and
 * only IntelliJ's form compiler can write it. verify-splice.ps1 hashes every .form, so that edit is
 * caught there.
 *
 * Uses the JDK's own internal copy of ASM, so the repository takes on no dependency. That copy exists
 * on the JDK 16.0.2 this tool is pinned to (the JDK that also produces javac output identical to the
 * baseline's -- see formclass.ps1).
 */
public final class FormTransplant {

    private static final String SETUP = "$$$setupUI$$$";

    public static void main(String[] args) throws Exception {
        if (args.length != 4) {
            System.err.println("usage: FormTransplant <intellij.class> <javac.class> <form> <out.class>");
            System.exit(2);
        }
        try {
            byte[] out = transplant(Files.readAllBytes(Path.of(args[0])), Files.readAllBytes(Path.of(args[1])),
                                    Files.readString(Path.of(args[2])));
            Files.write(Path.of(args[3]), out);
        } catch (IllegalStateException e) {
            System.err.println("FormTransplant: " + e.getMessage());
            System.exit(1);
        }
    }

    static boolean generated(String name) {
        return name.length() > 6 && name.startsWith("$$$") && name.endsWith("$$$");
    }

    /**
     * Every binding="..." in the .form: the fields IntelliJ's FormConstructorVisitor watches.
     * The lookbehind matters: seven of the forms also carry default-binding="true", and a plain
     * word boundary matches inside it (the hyphen ends a word) and reports a field named "true".
     */
    static Set<String> boundFields(String form) {
        Set<String> out = new LinkedHashSet<>();
        Matcher m = Pattern.compile("(?<![\\w-])binding\\s*=\\s*\"([^\"]+)\"").matcher(form);
        while (m.find()) out.add(m.group(1));
        return out;
    }

    static byte[] transplant(byte[] intellijBytes, byte[] javacBytes, String form) {
        ClassNode ij = read(intellijBytes);
        ClassNode jc = read(javacBytes);

        if (!ij.name.equals(jc.name))
            throw new IllegalStateException("class names differ: " + ij.name + " vs " + jc.name);
        if (!String.valueOf(ij.superName).equals(String.valueOf(jc.superName)))
            throw new IllegalStateException(jc.name + ": superclass differs: " + ij.superName + " vs " + jc.superName);
        for (FieldNode f : ij.fields)
            if (generated(f.name))
                throw new IllegalStateException(jc.name + ": IntelliJ's class has a generated FIELD " + f.name
                                                + "; this tool only knows how to copy methods");
        for (MethodNode m : jc.methods)
            if (generated(m.name))
                throw new IllegalStateException(jc.name + ": the javac class already has " + m.name
                                                + " -- was an instrumented class passed as the javac input?");
        for (FieldNode f : jc.fields)
            if (generated(f.name))
                throw new IllegalStateException(jc.name + ": the javac class already has a field " + f.name);

        List<MethodNode> copied = new ArrayList<>();
        for (MethodNode m : ij.methods) if (generated(m.name)) copied.add(m);
        if (copied.stream().noneMatch(m -> m.name.equals(SETUP) && m.desc.equals("()V")))
            throw new IllegalStateException(jc.name + ": IntelliJ's class has no " + SETUP
                                            + "()V -- it is not a form-compiled class");

        // IntelliJ's own "Cannot bind" refusals (AsmCodeGenerator: does not exist / is static / is
        // final). Final is the one with teeth: $$$setupUI$$$ assigns every bound field, and the JVM
        // rejects a final field assigned outside <init> -- measured by review, the rebuilt window threw
        // IllegalAccessError the moment it was opened, with every other gate green.
        Set<String> bound = boundFields(form);
        for (String b : bound) {
            FieldNode f = field(jc, b);
            if (f == null)
                throw new IllegalStateException(jc.name + ": the .form binds '" + b + "' but the class has no such field");
            if ((f.access & Opcodes.ACC_STATIC) != 0)
                throw new IllegalStateException(jc.name + ": the .form binds '" + b + "', which is static (IntelliJ refuses this too)");
            if ((f.access & Opcodes.ACC_FINAL) != 0)
                throw new IllegalStateException(jc.name + ": the .form binds '" + b + "', which is final -- "
                                                + "$$$setupUI$$$ assigns it, and the JVM forbids that (IntelliJ refuses this too)");
        }
        // IntelliJ inserts nothing when the source calls $$$setupUI$$$ itself (FirstPassClassVisitor).
        for (MethodNode m : jc.methods)
            for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext())
                if (n instanceof MethodInsnNode && ((MethodInsnNode) n).name.equals(SETUP))
                    throw new IllegalStateException(jc.name + "." + m.name + " calls " + SETUP
                                                    + " itself; this tool does not handle an explicit call");

        // Every reference a copied member makes into this class must still resolve, with the same type.
        for (MethodNode m : copied) {
            for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext()) {
                if (n instanceof FieldInsnNode) {
                    FieldInsnNode fi = (FieldInsnNode) n;
                    if (!fi.owner.equals(jc.name)) continue;
                    FieldNode f = field(jc, fi.name);
                    if (f == null)
                        throw new IllegalStateException(jc.name + "." + m.name + " uses field '" + fi.name
                                                        + "', which the new class does not have");
                    if (!f.desc.equals(fi.desc))
                        throw new IllegalStateException(jc.name + "." + m.name + " uses field '" + fi.name + "' as "
                                                        + fi.desc + ", but the new class declares it " + f.desc);
                    boolean wantStatic = fi.getOpcode() == Opcodes.GETSTATIC || fi.getOpcode() == Opcodes.PUTSTATIC;
                    if (wantStatic != ((f.access & Opcodes.ACC_STATIC) != 0))
                        throw new IllegalStateException(jc.name + "." + m.name + " uses field '" + fi.name
                                                        + "' with the wrong static-ness");
                    boolean writes = fi.getOpcode() == Opcodes.PUTFIELD || fi.getOpcode() == Opcodes.PUTSTATIC;
                    if (writes && (f.access & Opcodes.ACC_FINAL) != 0)
                        throw new IllegalStateException(jc.name + "." + m.name + " assigns field '" + fi.name
                                                        + "', which is final in the new class -- the JVM forbids that");
                }
                else if (n instanceof MethodInsnNode) {
                    MethodInsnNode mi = (MethodInsnNode) n;
                    if (!mi.owner.equals(jc.name)) continue;
                    MethodNode target = method(jc, mi.name, mi.desc);
                    if (target == null)
                        target = copied.stream().filter(c -> c.name.equals(mi.name) && c.desc.equals(mi.desc))
                                       .findFirst().orElse(null);
                    if (target == null)
                        throw new IllegalStateException(jc.name + "." + m.name + " calls " + mi.name + mi.desc
                                                        + ", which the new class does not have");
                    // The kind must still match the call, as static-ness must for a field: measured
                    // by review, a createUIComponents() made static passed every gate and threw
                    // IncompatibleClassChangeError when the window opened.
                    boolean wantStatic = mi.getOpcode() == Opcodes.INVOKESTATIC;
                    if (wantStatic != ((target.access & Opcodes.ACC_STATIC) != 0))
                        throw new IllegalStateException(jc.name + "." + m.name + " calls " + mi.name + mi.desc + " as "
                                                        + (wantStatic ? "static" : "an instance method")
                                                        + ", but the new class declares it the other way");
                }
            }
        }

        jc.methods.addAll(copied);

        int ctors = 0;
        for (MethodNode m : jc.methods) {
            if (!m.name.equals("<init>")) continue;
            if (insertSetupCall(jc, m, bound)) ctors++;
        }
        if (ctors == 0)
            throw new IllegalStateException(jc.name + ": no constructor received a " + SETUP + " call");

        // COMPUTE_MAXS, not COMPUTE_FRAMES: the inserted call pushes `this` for one instruction, so a
        // constructor's max_stack can grow by one, but no local and no branch target changes -- the
        // frames javac wrote stay valid, and recomputing them would need every superclass on hand.
        //
        // Seeding the writer with javac's ClassReader keeps javac's constant pool as it is and appends
        // only what is new, which is what IntelliJ's instrumenter does too. Without it the pool is
        // rebuilt in visiting order: the code is the same, but every #index in javap's output moves,
        // and verify-splice-style comparison against the committed class would see noise everywhere.
        ClassWriter w = new ClassWriter(new ClassReader(javacBytes), ClassWriter.COMPUTE_MAXS);
        jc.accept(w);
        return w.toByteArray();
    }

    /**
     * Inserts the $$$setupUI$$$ call where IntelliJ would, or refuses. Returns false for a
     * constructor that delegates through this(...), which IntelliJ leaves alone.
     *
     * THE PLACEMENT is a line-for-line port of FormConstructorVisitor (AsmCodeGenerator.java, read
     * at idea/211.7628.21 -- the 2021 line that built the committed classes -- and unchanged on
     * master). Scanning forward, the call goes before the FIRST of:
     *   - a GETFIELD whose name the .form binds (any owner, and not gated on super(...)), unless
     *     this(...) has been seen;
     *   - after super(...): any method call, except an `invokespecial <init>` whose owner is the
     *     SUPERCLASS, which IntelliJ reads as super(...) again (so `new Object()` in a class that
     *     extends Object is not a trigger);
     *   - after super(...): any jump;
     *   - a RETURN, unless this(...) has been seen.
     * A jump being a trigger is what keeps an early `if` safe: the call lands before the branch,
     * on every path.
     *
     * THE REFUSALS are the cases where even that placement is not proven to run exactly once on
     * every path, so this tool will not guess (each was either measured or follows from the rule):
     *   - the call would land before super(...) has initialized `this` -- an early GETFIELD of a
     *     bound name, or a `new <Superclass>()` among super(...)'s own arguments, which IntelliJ's
     *     flag mistakes for super(...) itself -- because that class cannot pass verification;
     *   - a `new <ThisClass>(...)` or a read of a bound field between super(...) and the call (the
     *     flag mistakes the first for this(...), then waits past the second), or no point at all;
     * And unlike IntelliJ, a constructor whose real first call is this(...) is ALWAYS left alone
     * (IntelliJ's flag could give one a second call; see the code).
     *   - a label something jumps to lies between super(...) and the call: a loop, which would run
     *     the call again, or a join the call sits after on only some paths;
     *   - a switch or a throw lies there: IntelliJ does not treat either as a trigger, so the call
     *     would sit inside one case, or after code only a jump reaches;
     *   - an instruction there is inside a try block: an exception before the call, caught by a
     *     handler past it, skips setup entirely -- measured by review with `q = 10 / divisor` inside
     *     a try as the first statement (divisor 0: the window's first bound field was null).
     * None of the fifteen forms here does any of these.
     */
    static boolean insertSetupCall(ClassNode cls, MethodNode ctor, Set<String> bound) {
        InsnList code = ctor.instructions;

        // The call that really initializes `this`: the first `invokespecial <init>` not paired with a
        // NEW. IntelliJ's flag below does not know this -- in `super(new JPanel().getLayout())` in a
        // JPanel subclass, the inner JPanel.<init> sets it, and the call lands before the real
        // super(...): measured by review, the class then failed verification when loaded.
        AbstractInsnNode thisInit = null;
        int pendingNews = 0;
        for (AbstractInsnNode n = code.getFirst(); n != null && thisInit == null; n = n.getNext()) {
            if (n.getOpcode() == Opcodes.NEW) pendingNews++;
            else if (n.getOpcode() == Opcodes.INVOKESPECIAL && ((MethodInsnNode) n).name.equals("<init>")) {
                if (pendingNews > 0) pendingNews--;
                else thisInit = n;
            }
        }
        if (thisInit == null)
            throw new IllegalStateException(cls.name + ": constructor " + ctor.desc + " never calls super(...) or this(...)");

        boolean superCalled = false, callsSelf = false;
        AbstractInsnNode at = null;
        for (AbstractInsnNode n = code.getFirst(); n != null && at == null; n = n.getNext()) {
            int op = n.getOpcode();
            if (op < 0) continue;  // labels, line numbers, frames
            if (n instanceof FieldInsnNode) {
                if (op == Opcodes.GETFIELD && !callsSelf && bound.contains(((FieldInsnNode) n).name)) at = n;
            } else if (n instanceof MethodInsnNode) {
                MethodInsnNode mi = (MethodInsnNode) n;
                if (op == Opcodes.INVOKESPECIAL && mi.name.equals("<init>")) {
                    if (mi.owner.equals(cls.name)) callsSelf = true;
                    else if (mi.owner.equals(cls.superName)) superCalled = true;
                    else if (superCalled) at = n;
                } else if (superCalled) at = n;
            } else if (n instanceof JumpInsnNode) {
                if (superCalled) at = n;
            } else if (op == Opcodes.RETURN) {
                if (!callsSelf) at = n;
            }
        }
        // A constructor that delegates through this(...) is left alone: the one it delegates to calls
        // $$$setupUI$$$. Decided from thisInit, not IntelliJ's flag: measured by review,
        // `this(new Object(), 1)` let the inner Object.<init> set the flag first, and IntelliJ's
        // rule put a SECOND call in the delegating constructor (every component built twice).
        if (((MethodInsnNode) thisInit).owner.equals(cls.name)) return false;

        if (at == null)
            throw refuse(cls, ctor, "IntelliJ's rule finds no point to call " + SETUP + " at all (a `new "
                                    + cls.name + "(...)` here turns its RETURN trigger off)");
        if (!superCalled || code.indexOf(at) <= code.indexOf(thisInit))
            throw refuse(cls, ctor, "IntelliJ's rule would call " + SETUP + " before super(...) has initialized "
                                    + "`this`, which cannot verify");
        AbstractInsnNode superCall = thisInit;

        // IntelliJ's flag also takes `new <ThisClass>(...)` for this(...), and from then on ignores
        // reads of bound fields: measured by review, `super(); new ChangeTimer(0);
        // spinner.setValue(1.0);` placed the call AFTER `getfield spinner` (NullPointerException on
        // open). So: no construction of this class, and no bound read, before the call.
        for (AbstractInsnNode n = superCall.getNext(); n != at; n = n.getNext()) {
            if (n.getOpcode() == Opcodes.INVOKESPECIAL && ((MethodInsnNode) n).name.equals("<init>")
                    && ((MethodInsnNode) n).owner.equals(cls.name))
                throw refuse(cls, ctor, "it constructs another " + cls.name + " before the call, which IntelliJ's rule "
                                        + "mistakes for this(...)");
            if (n.getOpcode() == Opcodes.GETFIELD && bound.contains(((FieldInsnNode) n).name))
                throw refuse(cls, ctor, "it reads the bound field '" + ((FieldInsnNode) n).name + "' before the call");
        }

        Set<LabelNode> targets = new HashSet<>();
        for (AbstractInsnNode n = code.getFirst(); n != null; n = n.getNext()) {
            if (n instanceof JumpInsnNode) targets.add(((JumpInsnNode) n).label);
            else if (n instanceof TableSwitchInsnNode) {
                targets.add(((TableSwitchInsnNode) n).dflt); targets.addAll(((TableSwitchInsnNode) n).labels);
            } else if (n instanceof LookupSwitchInsnNode) {
                targets.add(((LookupSwitchInsnNode) n).dflt); targets.addAll(((LookupSwitchInsnNode) n).labels);
            }
        }
        for (TryCatchBlockNode t : ctor.tryCatchBlocks) targets.add(t.handler);
        for (AbstractInsnNode n = superCall.getNext(); n != at; n = n.getNext()) {
            if (n instanceof LabelNode && targets.contains(n))
                throw refuse(cls, ctor, "a jump lands between super(...) and the call -- a loop or a join");
            int op = n.getOpcode();
            if (op < 0) continue;
            if (n instanceof TableSwitchInsnNode || n instanceof LookupSwitchInsnNode || op == Opcodes.ATHROW)
                throw refuse(cls, ctor, "a switch or throw comes between super(...) and the call");
            int i = code.indexOf(n);
            for (TryCatchBlockNode t : ctor.tryCatchBlocks)
                if (code.indexOf(t.start) <= i && i < code.indexOf(t.end))
                    throw refuse(cls, ctor, "code between super(...) and the call is inside a try block, so an "
                                            + "exception there could skip the call");
        }

        InsnList call = new InsnList();
        call.add(new VarInsnNode(Opcodes.ALOAD, 0));
        call.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, cls.name, SETUP, "()V", false));
        code.insertBefore(at, call);
        return true;
    }

    static IllegalStateException refuse(ClassNode cls, MethodNode ctor, String why) {
        return new IllegalStateException(cls.name + ": constructor " + ctor.desc + ": " + why
                + ". No single point is proven to run " + SETUP + " exactly once on every path, so this tool "
                + "will not guess -- rebuild this class in IntelliJ, or restructure the constructor");
    }

    static ClassNode read(byte[] bytes) {
        ClassNode n = new ClassNode();
        new ClassReader(bytes).accept(n, 0);
        return n;
    }

    static FieldNode field(ClassNode c, String name) {
        for (FieldNode f : c.fields) if (f.name.equals(name)) return f;
        return null;
    }

    static MethodNode method(ClassNode c, String name, String desc) {
        for (MethodNode m : c.methods) if (m.name.equals(name) && m.desc.equals(desc)) return m;
        return null;
    }
}
