import soot.*;
import soot.jimple.*;
import soot.jimple.toolkits.callgraph.*;
import soot.util.Chain;
import java.util.*;

/**
 * Monomorphization Transformer
 * ─────────────────────────────
 * For each virtual/interface invoke site whose call-graph has exactly one
 * concrete target, we:
 *   1. Create (or reuse) a static wrapper method in the target class.
 *   2. Replace the virtual invoke with a static invoke passing the receiver
 *      as an explicit first argument.
 *
 * Analysis: context-insensitive CHA call graph (sound over-approximation).
 * Transformation: new SootMethod + patched InvokeExpr at each mono-site.
 *
 * Assumptions:
 *   – No dynamic class loading introduces new subclasses at runtime.
 *   – Reflection is absent (standard assumption for static devirtualization).
 */
public class MonomorphizationTransformer extends BodyTransformer {

    // Cache: original virtual method → its static wrapper
    private final Map<SootMethod, SootMethod> wrapperCache = new HashMap<>();

    @Override
    protected void internalTransform(Body body, String phase, Map<String, String> opts) {
        SootMethod enclosing = body.getMethod();

        // We only transform app classes
        if (!enclosing.getDeclaringClass().isApplicationClass()) return;

        CallGraph cg = Scene.v().getCallGraph();

        // Collect units to patch (avoid ConcurrentModificationException)
        List<Stmt> toReplace = new ArrayList<>();
        for (Unit u : body.getUnits()) {
            Stmt s = (Stmt) u;
            if (!s.containsInvokeExpr()) continue;
            InvokeExpr ie = s.getInvokeExpr();
            if (!(ie instanceof VirtualInvokeExpr) &&
                !(ie instanceof InterfaceInvokeExpr)) continue;

            List<SootMethod> targets = getConcreteTargets(cg, s, enclosing);
            if (targets.size() == 1) {
                toReplace.add(s);
            }
        }

        int count = 0;
        for (Stmt s : toReplace) {
            InvokeExpr ie   = s.getInvokeExpr();
            Value receiver  = ((InstanceInvokeExpr) ie).getBase();
            List<SootMethod> tgts = getConcreteTargets(cg, s, enclosing);
            SootMethod target = tgts.get(0);

            if (!target.hasActiveBody()) {
                try { target.retrieveActiveBody(); } catch (Exception e) { continue; }
            }

            SootMethod wrapper = getOrCreateStaticWrapper(target);
            if (wrapper == null) continue;

            // Build new argument list: receiver first, then original args
            List<Value> newArgs = new ArrayList<>();
            newArgs.add(receiver);
            newArgs.addAll(ie.getArgs());

            StaticInvokeExpr staticIE =
                Jimple.v().newStaticInvokeExpr(wrapper.makeRef(), newArgs);

            if (s instanceof AssignStmt) {
                ((AssignStmt) s).setRightOp(staticIE);
            } else {
                s.getInvokeExprBox().setValue(staticIE);
            }
            count++;
        }

        if (count > 0) {
            System.out.printf("[Mono]   %-50s  devirtualized %d site(s)%n",
                enclosing.getSignature(), count);
        }
    }

    // -----------------------------------------------------------------------
    /** Return all concrete (non-abstract, non-native) call-graph targets. */
    private List<SootMethod> getConcreteTargets(CallGraph cg, Stmt s, SootMethod src) {
        List<SootMethod> result = new ArrayList<>();
        Iterator<Edge> it = cg.edgesOutOf(s);
        while (it.hasNext()) {
            Edge e = it.next();
            SootMethod tgt = e.tgt();
            if (!tgt.isAbstract() && !tgt.isNative() && tgt.hasActiveBody()) {
                result.add(tgt);
            }
        }
        return result;
    }

    // -----------------------------------------------------------------------
    /**
     * For a given virtual target, return a static wrapper:
     *   RetType __mono__<name>(DeclClass receiver, <original params...>)
     * Body simply delegates to the original method via virtual call so that
     * the JVM's dispatch is skipped at every call site that was devirtualized.
     */
    private SootMethod getOrCreateStaticWrapper(SootMethod orig) {
        if (wrapperCache.containsKey(orig)) return wrapperCache.get(orig);

        SootClass cls = orig.getDeclaringClass();

        // Build parameter types: receiver type + original params
        List<Type> paramTypes = new ArrayList<>();
        paramTypes.add(cls.getType());
        paramTypes.addAll(orig.getParameterTypes());

        String wrapperName = "__mono__" + orig.getName() + "__" +
                             Integer.toHexString(orig.getSignature().hashCode());

        // Check if already exists (e.g. from a previous pass on same class)
        if (cls.declaresMethod(wrapperName, paramTypes)) {
            SootMethod existing = cls.getMethod(wrapperName, paramTypes);
            wrapperCache.put(orig, existing);
            return existing;
        }

        int mods = Modifier.PUBLIC | Modifier.STATIC;
        SootMethod wrapper = new SootMethod(wrapperName, paramTypes, orig.getReturnType(), mods);
        cls.addMethod(wrapper);

        // Build Jimple body that calls the original via virtual invoke
        JimpleBody wBody = Jimple.v().newBody(wrapper);
        wrapper.setActiveBody(wBody);
        Chain<Local> locals  = wBody.getLocals();
        PatchingChain<Unit> units = wBody.getUnits();

        // Parameter locals
        Local receiverLocal = Jimple.v().newLocal("_recv", cls.getType());
        locals.add(receiverLocal);
        units.add(Jimple.v().newIdentityStmt(receiverLocal,
            Jimple.v().newParameterRef(cls.getType(), 0)));

        List<Local> argLocals = new ArrayList<>();
        for (int i = 0; i < orig.getParameterCount(); i++) {
            Type pt = orig.getParameterType(i);
            Local al = Jimple.v().newLocal("_p" + i, pt);
            locals.add(al);
            units.add(Jimple.v().newIdentityStmt(al,
                Jimple.v().newParameterRef(pt, i + 1)));
            argLocals.add(al);
        }

        // Virtual invoke to the original
        InvokeExpr call = Jimple.v().newVirtualInvokeExpr(
            receiverLocal, orig.makeRef(), argLocals);

        if (orig.getReturnType() instanceof VoidType) {
            units.add(Jimple.v().newInvokeStmt(call));
            units.add(Jimple.v().newReturnVoidStmt());
        } else {
            Local ret = Jimple.v().newLocal("_ret", orig.getReturnType());
            locals.add(ret);
            units.add(Jimple.v().newAssignStmt(ret, call));
            units.add(Jimple.v().newReturnStmt(ret));
        }

        wrapperCache.put(orig, wrapper);
        return wrapper;
    }
}
