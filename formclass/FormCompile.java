import com.intellij.compiler.instrumentation.FailSafeClassReader;
import com.intellij.compiler.instrumentation.InstrumentationClassFinder;
import com.intellij.compiler.instrumentation.InstrumenterClassWriter;
import com.intellij.uiDesigner.compiler.AsmCodeGenerator;
import com.intellij.uiDesigner.compiler.FormErrorInfo;
import com.intellij.uiDesigner.compiler.NestedFormLoader;
import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.uiDesigner.lw.CompiledClassPropertiesProvider;
import com.intellij.uiDesigner.lw.LwRootContainer;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Instruments ONE javac-compiled form class with its .form, using JetBrains' own form compiler
 * (java-gui-forms-compiler 211.7628.21, downloaded and hash-checked by formclass.ps1). This is what
 * IntelliJ's IDE build does to the fifteen form classes, so a .form edit -- which formclass.ps1's
 * transplant cannot follow -- is compiled exactly as IntelliJ would compile it (docs\adr\0011).
 *
 * The calls are the IDE's own, in its order: FormsInstrumenter.java lines 143-205 at the tag
 * idea/211.7628.21 (plugins/ui-designer/jps-plugin). Three details there decide the output bytes,
 * and each is kept:
 *   - the writer is built FROM the reader (InstrumenterClassWriter(reader, flags, finder)), so ASM
 *     keeps javac's constant pool and appends to it rather than renumbering it;
 *   - the flags come from the class's version (COMPUTE_FRAMES for Java 16), so frames are
 *     recomputed for the whole class, which needs the finder to see every class a frame can merge
 *     -- the JDK's own (its jrt image, first, as ClassProcessingBuilder puts the SDK) and the
 *     program's;
 *   - AsmCodeGenerator(root, finder, nested, ignoreCustomCreation=false, useDynamicBundles=false,
 *     writer).patchClass(reader): the IDE's defaults for a project that does not use dynamic bundles.
 * Measured: from unchanged sources, all fifteen committed IntelliJ classes and their 22 inner classes
 * come out byte-identical (the tree at 331a9bd, javac 16.0.2).
 *
 * No form here nests another or reads a resource bundle, so a nested form is refused, not looked up.
 *
 * usage: FormCompile <form> <javac .class in> <instrumented .class out> <classpath entry>...
 * The classpath must include javac's output folder (its inner classes) and the program the class
 * links against. Exit 0 ok; 1 the compiler reported errors (printed); 2 bad usage.
 */
public class FormCompile {
    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.err.println("usage: FormCompile <form> <javac .class in> <.class out> <classpath entry>...");
            System.exit(2);
        }
        File form = new File(args[0]);
        File in = new File(args[1]);
        File out = new File(args[2]);
        List<URL> urls = new ArrayList<>();
        urls.add(InstrumentationClassFinder.createJDKPlatformUrl(System.getProperty("java.home")));
        for (int i = 3; i < args.length; i++) urls.add(new File(args[i]).toURI().toURL());
        InstrumentationClassFinder finder = new InstrumentationClassFinder(urls.toArray(new URL[0]));
        try {
            LwRootContainer root = Utils.getRootContainer(form.toURI().toURL(),
                    new CompiledClassPropertiesProvider(finder.getLoader()));
            byte[] original = Files.readAllBytes(in.toPath());
            FailSafeClassReader reader = new FailSafeClassReader(original, 0, original.length);
            String expected = root.getClassToBind();
            String actual = reader.getClassName().replace('/', '.');
            if (expected == null || !expected.equals(actual)) {
                System.err.println("error: " + form + " binds to " + expected + ", but the class given is " + actual);
                System.exit(1);
            }
            int flags = InstrumenterClassWriter.getAsmClassWriterFlags(InstrumenterClassWriter.getClassFileVersion(reader));
            InstrumenterClassWriter writer = new InstrumenterClassWriter(reader, flags, finder);
            NestedFormLoader noNestedForms = new NestedFormLoader() {
                public LwRootContainer loadForm(String path) throws Exception {
                    throw new Exception("a nested form (" + path + ") is not supported by formclass.ps1");
                }
                public String getClassToBindName(LwRootContainer c) { return c.getClassToBind(); }
            };
            AsmCodeGenerator generator = new AsmCodeGenerator(root, finder, noNestedForms, false, false, writer);
            byte[] patched = generator.patchClass(reader);
            for (FormErrorInfo w : generator.getWarnings()) System.err.println("warning: " + w.getErrorMessage());
            FormErrorInfo[] errors = generator.getErrors();
            for (FormErrorInfo e : errors) System.err.println("error: " + e.getErrorMessage());
            if (errors.length > 0 || patched == null) System.exit(1);
            Files.write(out.toPath(), patched);
        } finally {
            finder.releaseResources();
        }
    }
}
