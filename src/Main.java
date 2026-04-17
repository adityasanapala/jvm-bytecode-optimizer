import soot.*;
import soot.options.Options;
import java.util.*;
import java.io.File;

/**
 * PA4: Beat the Interpreter
 * Optimizations:
 *   1. Monomorphization          – virtual calls → static dispatch (single-target sites)
 *   2. Method Inlining           – inline small methods after monomorphization
 *   3. Redundant Load Elimination– remove repeated field loads with no intervening store
 *   4. Null-Check Elimination    – remove provably redundant null guards
 *   5. Dead Field Elimination    – remove stores to fields never subsequently read
 */
public class Main {

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java Main <classdir> <classname> [<outdir>]");
            System.err.println("  classdir  : directory containing compiled .class files");
            System.err.println("  classname : fully-qualified main class to optimize");
            System.err.println("  outdir    : output directory (default: ./optimized)");
            System.exit(1);
        }

        String classDir   = args[0];
        String mainClass  = args[1];
        String outDir     = args.length >= 3 ? args[2] : "optimized";

        new File(outDir).mkdirs();

        configureSoot(classDir, mainClass, outDir);

        // Register all five transformation passes (in order)
        // Order matters: mono fires first to open inlining opportunities;
        // null-check and dead-field elimination run last on the cleaned-up body.
        PackManager.v().getPack("jtp").add(
            new Transform("jtp.mono",      new MonomorphizationTransformer()));
        PackManager.v().getPack("jtp").add(
            new Transform("jtp.inline",    new MethodInliningTransformer()));
        PackManager.v().getPack("jtp").add(
            new Transform("jtp.rle",       new RedundantLoadEliminationTransformer()));
        PackManager.v().getPack("jtp").add(
            new Transform("jtp.nullelim",  new NullCheckEliminationTransformer()));
        PackManager.v().getPack("jtp").add(
            new Transform("jtp.deadfield", new DeadFieldEliminationTransformer()));

        PackManager.v().runPacks();
        PackManager.v().writeOutput();

        System.out.println("\n[PA4] Done. Optimized classes written to: " + outDir);
    }

    // -----------------------------------------------------------------------
    private static void configureSoot(String classDir, String mainClass, String outDir) {
        G.reset();
        Options opt = Options.v();

        opt.set_prepend_classpath(true);
        opt.set_allow_phantom_refs(true);
        opt.set_whole_program(true);
        opt.set_app(true);
        opt.set_keep_line_number(true);

        // Input / output
        opt.set_process_dir(Collections.singletonList(classDir));
        opt.set_output_dir(outDir);
        opt.set_output_format(Options.output_format_class);

        // Call-graph construction (CHA gives us the type hierarchy)
        opt.setPhaseOption("cg.cha", "enabled:true");
        opt.setPhaseOption("cg",     "safe-forname:true");
        opt.setPhaseOption("cg",     "safe-newinstance:true");

        Scene.v().addBasicClass(mainClass, SootClass.BODIES);
        Scene.v().loadNecessaryClasses();
    }
}
