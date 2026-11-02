import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Every call and field access between the fork's own classes, in the BUILT jar, resolves.
 *
 * WHY A SPLICE BUILD NEEDS THIS
 * -----------------------------
 * build.ps1 recompiles only the files in $SPLICE_MODIFIED and lays them over a committed baseline
 * of older .class files. javac checks each recompiled file against the others, but nothing checks
 * the baseline classes against the recompiled ones: if a spliced change removes or re-signs a
 * method, a baseline class that still calls the old one is copied into the jar unchanged. The jar
 * builds, most of the program runs, and the stale call throws NoSuchMethodError only when that
 * line finally executes -- in a release, in front of whoever hit it.
 *
 * The upstream merge makes this the expected case rather than a rare one: upstream's trap rework
 * removed BrownButton.getTrapIndex(), its three-argument constructor and two Level setters.
 * verify-splice.ps1 does not cover it -- it recompiles ordinary files to compare bytecode, but it
 * cannot recompile the fifteen form classes, which it only hashes.
 *
 * So this reads every class file's constant pool and resolves each Methodref, InterfaceMethodref
 * and Fieldref whose owner is in one of the fork's packages, the way the JVM would: the owner,
 * then its superclasses and interfaces. Planted check: replacing the jar's MSCreature.class with
 * jc-14's (which calls getTrapIndex) reports exactly that reference.
 *
 * It sees references the constant pool states, and checks that each NAME AND DESCRIPTOR exists. It
 * does not check access or static-ness (a method made private, or static, still passes here and
 * would throw IllegalAccessError or IncompatibleClassChangeError at the call), and it does not see
 * reflection, invokedynamic targets (lambdas resolve through their synthetic methods, which ARE
 * checked) or a method whose behavior, not signature, changed. Third-party bytecode (com/, org/) is not checked: the IntelliJ runtime is
 * shipped incomplete upstream and has never been loadable in full.
 *
 * Needs -Dsupercc.jar (run-tests.ps1 passes it).
 */
public class LinkTest {

    private static final String FORK_PACKAGES = "(emulator|game|graphics|io|tools|util)/.*";

    public static void main(String[] args) {
        System.exit(Harness.run("LinkTest", LinkTest::body));
    }

    private static int refs, unresolved;
    private static final Set<String> seenRefs = new TreeSet<>();

    private static void body() throws Exception {
        String jarPath = System.getProperty("supercc.jar");
        if (jarPath == null || !new File(jarPath).isFile()) {
            Harness.skip("no -Dsupercc.jar given");
            return;
        }
        File jar = new File(jarPath);

        Harness.section("1. the resolver can say no");
        /* A checker that answers yes to everything passes every jar. These name members that do
         * not exist in any build since the trap rework, and a class that never existed. */
        try (URLClassLoader cl = new URLClassLoader(new URL[]{ jar.toURI().toURL() }, null)) {
            Harness.check("a removed method is unresolved (BrownButton.getTrapIndex()I)",
                          !resolves(cl, "game/button/BrownButton", "getTrapIndex", "()I", false));
            Harness.check("a removed constructor is unresolved (BrownButton(Position,Position,int))",
                          !resolves(cl, "game/button/BrownButton", "<init>", "(Lgame/Position;Lgame/Position;I)V", false));
            Harness.check("a method on a class that does not exist is unresolved",
                          !resolves(cl, "game/NoSuchClass", "x", "()V", false));
            Harness.check("a field with the wrong type is unresolved",
                          !resolves(cl, "game/MS/MSLevel", "trapIndexMap", "Ljava/util/List;", true));
            Harness.check("and a real one resolves (MSLevel.trapIndexMap)",
                          resolves(cl, "game/MS/MSLevel", "trapIndexMap", "Ljava/util/Map;", true));
            Harness.check("an interface call to an Object method resolves, as the JVM resolves it (Level.equals)",
                          resolves(cl, "game/Level", "equals", "(Ljava/lang/Object;)Z", false));
            Harness.check("but not to a non-public one (Level.clone)",
                          !resolves(cl, "game/Level", "clone", "()Ljava/lang/Object;", false));
        }

        Harness.section("2. every reference between the fork's classes resolves");
        int classes = 0;
        try (URLClassLoader cl = new URLClassLoader(new URL[]{ jar.toURI().toURL() }, null);
             ZipFile z = new ZipFile(jar)) {
            for (Enumeration<? extends ZipEntry> e = z.entries(); e.hasMoreElements(); ) {
                ZipEntry ze = e.nextElement();
                if (!ze.getName().endsWith(".class")) continue;
                classes++;
                scan(cl, ze.getName(), z);
            }
        }
        Harness.check("scanned every class in the jar (" + classes + " classes)", classes > 150);
        Harness.check("and checked a real number of references (" + refs + ")", refs > 3000);
        /* Spot checks that the scan really walked the paths the trap rework changed, so a scan that
         * silently skipped game/ would not pass as clean. */
        Harness.check("including MSCreature's use of MSLevel.trapIndexMap",
                      seenRefs.contains("game/MS/MSCreature -> game/MS/MSLevel.trapIndexMap:Ljava/util/Map;"));
        Harness.check("and LevelFactory's two-argument BrownButton constructor",
                      seenRefs.contains("io/LevelFactory -> game/button/BrownButton.<init>:(Lgame/Position;Lgame/Position;)V"));
        Harness.eq("references that do not resolve", unresolved, 0);
    }

    /** Resolves every fork-owned Methodref/InterfaceMethodref/Fieldref in one class file. */
    private static void scan(ClassLoader cl, String entry, ZipFile z) throws IOException {
        String self = entry.substring(0, entry.length() - ".class".length());
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(z.getInputStream(z.getEntry(entry))))) {
            in.readInt();                 // magic
            in.readUnsignedShort();       // minor
            in.readUnsignedShort();       // major
            int n = in.readUnsignedShort();
            Object[] cp = new Object[n];
            int[] tag = new int[n];
            for (int i = 1; i < n; i++) {
                int t = in.readUnsignedByte();
                tag[i] = t;
                switch (t) {
                    case 1 -> cp[i] = in.readUTF();                                    // Utf8
                    case 3, 4 -> in.readInt();                                         // Integer, Float
                    case 5, 6 -> { in.readLong(); i++; }                               // Long, Double: two slots
                    case 7, 8, 16, 19, 20 -> cp[i] = in.readUnsignedShort();           // Class, String, MethodType, Module, Package
                    case 9, 10, 11, 12, 17, 18 -> cp[i] = new int[]{ in.readUnsignedShort(), in.readUnsignedShort() };
                    case 15 -> { in.readUnsignedByte(); in.readUnsignedShort(); }      // MethodHandle
                    default -> throw new IOException("unknown constant-pool tag " + t + " in " + entry);
                }
            }
            for (int i = 1; i < n; i++) {
                if (tag[i] != 9 && tag[i] != 10 && tag[i] != 11) continue;
                int[] ref = (int[]) cp[i];
                String owner = (String) cp[(Integer) cp[ref[0]]];
                int[] nt = (int[]) cp[ref[1]];
                String name = (String) cp[nt[0]];
                String desc = (String) cp[nt[1]];
                if (owner.startsWith("[") || !owner.matches(FORK_PACKAGES)) continue;
                refs++;
                seenRefs.add(self + " -> " + owner + "." + name + ":" + desc);
                if (!resolves(cl, owner, name, desc, tag[i] == 9)) {
                    unresolved++;
                    Harness.check(self + " -> " + owner + "." + name + desc + " resolves", false);
                }
            }
        }
    }

    /**
     * Whether the JVM would find this member: the owner, then its superclasses and interfaces.
     * A class that cannot be loaded at all counts as unresolved -- unloadable is not clean.
     */
    static boolean resolves(ClassLoader cl, String owner, String name, String desc, boolean field) {
        Class<?> start;
        try {
            start = Class.forName(owner.replace('/', '.'), false, cl);
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
        Deque<Class<?>> todo = new ArrayDeque<>();
        todo.add(start);
        Set<Class<?>> seen = new HashSet<>();
        while (!todo.isEmpty()) {
            Class<?> c = todo.poll();
            if (!seen.add(c)) continue;
            try {
                if (field) {
                    for (Field f : c.getDeclaredFields())
                        if (f.getName().equals(name) && descriptor(f.getType()).equals(desc)) return true;
                } else if (name.equals("<init>")) {
                    // constructors are not inherited: only the owner's own count
                    for (Constructor<?> k : c.getDeclaredConstructors())
                        if (desc.equals(descriptor(k.getParameterTypes(), void.class))) return true;
                    return false;
                } else {
                    for (Method m : c.getDeclaredMethods())
                        if (m.getName().equals(name) && desc.equals(descriptor(m.getParameterTypes(), m.getReturnType())))
                            return true;
                }
            } catch (LinkageError e) {
                return false;
            }
            if (c.getSuperclass() != null) todo.add(c.getSuperclass());
            todo.addAll(Arrays.asList(c.getInterfaces()));
        }
        /* JVMS 5.4.3.4: a call through an INTERFACE also resolves to a public method of Object --
         * `level.equals(x)` on a Level-typed variable is invokeinterface game/Level.equals, and an
         * interface's getSuperclass() is null, so the walk above never reaches Object. */
        if (!field && start.isInterface()) {
            for (Method m : Object.class.getMethods())
                if (m.getName().equals(name) && desc.equals(descriptor(m.getParameterTypes(), m.getReturnType())))
                    return true;
        }
        return false;
    }

    private static String descriptor(Class<?>[] params, Class<?> ret) {
        StringBuilder s = new StringBuilder("(");
        for (Class<?> p : params) s.append(descriptor(p));
        return s.append(')').append(descriptor(ret)).toString();
    }

    private static String descriptor(Class<?> c) {
        if (c.isArray()) return c.getName().replace('.', '/');
        if (!c.isPrimitive()) return "L" + c.getName().replace('.', '/') + ";";
        return switch (c.getName()) {
            case "int" -> "I";
            case "long" -> "J";
            case "short" -> "S";
            case "byte" -> "B";
            case "char" -> "C";
            case "boolean" -> "Z";
            case "float" -> "F";
            case "double" -> "D";
            default -> "V";
        };
    }
}
