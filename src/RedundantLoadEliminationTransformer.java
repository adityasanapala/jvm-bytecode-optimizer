import soot.*;
import soot.jimple.*;
import soot.toolkits.graph.*;
import soot.toolkits.scalar.*;
import java.util.*;

/**
 * Redundant Load Elimination (RLE) Transformer
 * ─────────────────────────────────────────────
 * Removes repeated loads of the same instance/static field within a method
 * when no intervening store (or call that could alias-store) exists.
 *
 * Analysis: Forward, flow-sensitive, intra-procedural dataflow.
 *   – Domain  : Map<FieldRef-key, Local> — "available field value"
 *   – Gen     : f.x read  → make (f,x) → tmpLocal available
 *   – Kill    : f.x write → kill (f,x) and any (?,x) where receiver may alias
 *               method call → conservatively kill all instance field entries
 *               (static fields survive unless explicitly stored)
 *   – Meet    : intersection of available sets (may-available = conservative)
 *
 * Assumptions:
 *   – No multi-threading races within a method body (standard for local opts).
 *   – Calls may write any instance field (conservative); static fields are only
 *     killed by explicit static field stores.
 */
public class RedundantLoadEliminationTransformer extends BodyTransformer {

    @Override
    protected void internalTransform(Body body, String phase, Map<String, String> opts) {
        SootMethod m = body.getMethod();
        if (!m.getDeclaringClass().isApplicationClass()) return;

        // Run the dataflow analysis
        UnitGraph cfg = new BriefUnitGraph(body);
        RLEAnalysis analysis = new RLEAnalysis(cfg, body);

        // Apply substitutions
        int replaced = 0;
        for (Unit u : body.getUnits()) {
            Stmt s = (Stmt) u;
            FlowMap before = analysis.getFlowBefore(u);

            if (s instanceof AssignStmt) {
                AssignStmt as = (AssignStmt) s;
                Value rhs = as.getRightOp();

                // Is RHS a field load that is already available?
                String key = fieldKey(rhs);
                if (key != null && before.containsKey(key)) {
                    Local cached = before.get(key);
                    if (!cached.equals(as.getLeftOp())) { // don't replace with itself
                        as.setRightOp(cached);
                        replaced++;
                    }
                }
            }
        }

        if (replaced > 0) {
            System.out.printf("[RLE]    %-50s  eliminated %d redundant load(s)%n",
                m.getSignature(), replaced);
        }
    }

    // -----------------------------------------------------------------------
    /** Canonical string key for a field access expression. */
    static String fieldKey(Value v) {
        if (v instanceof InstanceFieldRef) {
            InstanceFieldRef ifr = (InstanceFieldRef) v;
            // Key includes the receiver local name so different objects are distinct
            return ifr.getBase() + "." + ifr.getFieldRef().getSignature();
        }
        if (v instanceof StaticFieldRef) {
            return "STATIC." + ((StaticFieldRef) v).getFieldRef().getSignature();
        }
        return null;
    }

    // -----------------------------------------------------------------------
    /** Simple map wrapper used as the lattice element. */
    static class FlowMap extends HashMap<String, Local> {
        FlowMap() { super(); }
        FlowMap(FlowMap other) { super(other); }
    }

    // -----------------------------------------------------------------------
    /**
     * Forward dataflow analysis: available field loads.
     * We store, for each program point, the map of field-key → local that
     * currently holds its value.
     */
    static class RLEAnalysis extends ForwardFlowAnalysis<Unit, FlowMap> {

        private final Body body;
        private int tempCounter = 0;

        RLEAnalysis(UnitGraph graph, Body body) {
            super(graph);
            this.body = body;
            doAnalysis();
        }

        @Override
        protected FlowMap newInitialFlow() { return new FlowMap(); }

        @Override
        protected FlowMap entryInitialFlow() { return new FlowMap(); }

        @Override
        protected void merge(FlowMap in1, FlowMap in2, FlowMap out) {
            // Intersection: only keep entries present in BOTH predecessors
            out.clear();
            for (Map.Entry<String, Local> e : in1.entrySet()) {
                Local l2 = in2.get(e.getKey());
                if (l2 != null && l2.equals(e.getValue())) {
                    out.put(e.getKey(), e.getValue());
                }
            }
        }

        @Override
        protected void copy(FlowMap source, FlowMap dest) {
            dest.clear();
            dest.putAll(source);
        }

        @Override
        protected void flowThrough(FlowMap in, Unit unit, FlowMap out) {
            copy(in, out);
            Stmt s = (Stmt) unit;

            // --- KILL: method call may write any instance field ---
            if (s.containsInvokeExpr()) {
                killAllInstanceFields(out);
            }

            if (!(s instanceof AssignStmt)) return;
            AssignStmt as = (AssignStmt) s;
            Value lhs = as.getLeftOp();
            Value rhs = as.getRightOp();

            // --- KILL: field store on LHS ---
            if (lhs instanceof InstanceFieldRef) {
                InstanceFieldRef ifr = (InstanceFieldRef) lhs;
                String sig = ifr.getFieldRef().getSignature();
                // Kill all entries for this field (any receiver)
                out.entrySet().removeIf(e ->
                    e.getKey().contains(sig));
            } else if (lhs instanceof StaticFieldRef) {
                String key = "STATIC." + ((StaticFieldRef) lhs).getFieldRef().getSignature();
                out.remove(key);
            }

            // --- GEN: field load on RHS → record available value ---
            String rhsKey = fieldKey(rhs);
            if (rhsKey != null) {
                if (lhs instanceof Local) {
                    // The local now holds the field value
                    out.put(rhsKey, (Local) lhs);
                }
            }
        }

        private void killAllInstanceFields(FlowMap out) {
            out.entrySet().removeIf(e -> !e.getKey().startsWith("STATIC."));
        }
    }
}
