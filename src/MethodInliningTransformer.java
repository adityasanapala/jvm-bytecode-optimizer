import soot.*;
import soot.jimple.*;
import soot.jimple.toolkits.scalar.LocalNameStandardizer;
import soot.util.Chain;
import java.util.*;

/**
 * Method Inlining Transformer
 * ────────────────────────────
 * Inlines small, non-recursive static methods (including the wrappers produced
 * by MonomorphizationTransformer) and small final/private instance methods.
 *
 * Criteria for inlining:
 *   – Callee body has ≤ INLINE_THRESHOLD Jimple statements.
 *   – Callee is not recursive (simple self-call check).
 *   – Callee has no exception handlers (keeps transformation simple & sound).
 *   – Callee is a StaticInvokeExpr OR a non-overridable instance call
 *     (private / final).
 *
 * Transformation: copy callee's locals into caller with fresh names,
 * replace the call site with the inlined body, redirect return values.
 *
 * Soundness: The transformation is sound because we only inline methods whose
 * dispatch is already fully resolved (static or provably non-virtual).
 */
public class MethodInliningTransformer extends BodyTransformer {

    private static final int INLINE_THRESHOLD = 30; // max stmts in callee

    @Override
    protected void internalTransform(Body body, String phase, Map<String, String> opts) {
        SootMethod enclosing = body.getMethod();
        if (!enclosing.getDeclaringClass().isApplicationClass()) return;

        boolean changed = true;
        int totalInlined = 0;

        // Repeat until no more inlining opportunities (callee may expose more)
        while (changed) {
            changed = false;
            List<Stmt> callSites = collectInlineCandidates(body, enclosing);
            for (Stmt callStmt : callSites) {
                SootMethod callee = callStmt.getInvokeExpr().getMethod();
                if (!callee.hasActiveBody()) {
                    try { callee.retrieveActiveBody(); } catch (Exception e) { continue; }
                }
                inlineCall(body, callStmt, callee);
                changed = true;
                totalInlined++;
            }
        }

        if (totalInlined > 0) {
            // Re-standardize local names after inlining
            LocalNameStandardizer.v().transform(body);
            System.out.printf("[Inline] %-50s  inlined %d call(s)%n",
                enclosing.getSignature(), totalInlined);
        }
    }

    // -----------------------------------------------------------------------
    private List<Stmt> collectInlineCandidates(Body body, SootMethod enclosing) {
        List<Stmt> result = new ArrayList<>();
        for (Unit u : body.getUnits()) {
            Stmt s = (Stmt) u;
            if (!s.containsInvokeExpr()) continue;
            InvokeExpr ie = s.getInvokeExpr();

            boolean isStaticCall    = ie instanceof StaticInvokeExpr;
            boolean isNonVirtual    = ie instanceof SpecialInvokeExpr;
            boolean isFinalPrivate  = false;
            if (ie instanceof InstanceInvokeExpr && !(ie instanceof InterfaceInvokeExpr)) {
                SootMethod m = ie.getMethod();
                isFinalPrivate = m.isPrivate() || m.isFinal() ||
                                 m.getDeclaringClass().isFinal();
            }

            if (!isStaticCall && !isNonVirtual && !isFinalPrivate) continue;

            SootMethod callee = ie.getMethod();
            if (!callee.hasActiveBody()) {
                try { callee.retrieveActiveBody(); } catch (Exception e) { continue; }
            }
            Body calleeBody = callee.getActiveBody();

            // Guard conditions
            if (calleeBody.getUnits().size() > INLINE_THRESHOLD) continue;
            if (!calleeBody.getTraps().isEmpty()) continue;  // has try/catch
            if (isSelfRecursive(callee)) continue;
            if (callee.equals(enclosing)) continue;          // self-inline

            result.add(s);
        }
        return result;
    }

    // -----------------------------------------------------------------------
    /**
     * Inline a single call site.
     * Strategy:
     *   1. Clone callee's locals into caller with prefixed names.
     *   2. Insert identity statements for each parameter → map to actual args.
     *   3. Copy all callee statements, replacing:
     *        - ParameterRef → corresponding actual arg local
     *        - ReturnStmt   → assign to LHS (if AssignStmt) + goto after inlined block
     *        - ThisRef      → receiver local (for instance methods)
     *   4. Replace original call stmt with the inlined block.
     */
    private void inlineCall(Body callerBody, Stmt callStmt, SootMethod callee) {
        InvokeExpr ie    = callStmt.getInvokeExpr();
        Body calleeBody  = callee.getActiveBody();
        PatchingChain<Unit> callerUnits = callerBody.getUnits();
        Chain<Local> callerLocals = callerBody.getLocals();

        // Unique prefix to avoid name collisions
        String prefix = "__inl_" + callee.getName() + "_" +
                        Integer.toHexString(System.identityHashCode(callStmt)) + "_";

        // --- 1. Map callee locals → fresh caller locals ---
        Map<Local, Local> localMap = new HashMap<>();
        for (Local l : calleeBody.getLocals()) {
            Local fresh = Jimple.v().newLocal(prefix + l.getName(), l.getType());
            callerLocals.add(fresh);
            localMap.put(l, fresh);
        }

        // --- 2. Build parameter→argument mapping ---
        // For instance methods, param 0 is "this" accessed via ThisRef
        List<Value> args = ie.getArgs();
        Value receiverVal = (ie instanceof InstanceInvokeExpr)
            ? ((InstanceInvokeExpr) ie).getBase() : null;

        // --- 3. Clone callee units ---
        // We need a nop as the landing point after all inlined returns
        NopStmt afterInline = Jimple.v().newNopStmt();

        // Result local (only needed if caller uses the return value)
        Local resultLocal = null;
        if (callStmt instanceof AssignStmt && !(callee.getReturnType() instanceof VoidType)) {
            resultLocal = Jimple.v().newLocal(prefix + "_ret", callee.getReturnType());
            callerLocals.add(resultLocal);
        }

        List<Unit> inlinedUnits = new ArrayList<>();

        for (Unit u : calleeBody.getUnits()) {
            Stmt cs = (Stmt) u;

            // --- Handle IdentityStmt (ThisRef / ParameterRef) ---
            if (cs instanceof IdentityStmt) {
                IdentityStmt id = (IdentityStmt) cs;
                Value rhs = id.getRightOp();
                Local lhs = localMap.get((Local) id.getLeftOp());

                if (rhs instanceof ThisRef && receiverVal != null) {
                    inlinedUnits.add(Jimple.v().newAssignStmt(lhs, receiverVal));
                } else if (rhs instanceof ParameterRef) {
                    int idx = ((ParameterRef) rhs).getIndex();
                    inlinedUnits.add(Jimple.v().newAssignStmt(lhs, args.get(idx)));
                }
                // Skip CaughtExceptionRef etc.
                continue;
            }

            // --- Handle ReturnStmt ---
            if (cs instanceof ReturnStmt) {
                ReturnStmt rs = (ReturnStmt) cs;
                Value retVal  = cloneValue(rs.getOp(), localMap);
                if (resultLocal != null) {
                    inlinedUnits.add(Jimple.v().newAssignStmt(resultLocal, retVal));
                }
                inlinedUnits.add(Jimple.v().newGotoStmt(afterInline));
                continue;
            }
            if (cs instanceof ReturnVoidStmt) {
                inlinedUnits.add(Jimple.v().newGotoStmt(afterInline));
                continue;
            }

            // --- General statement: clone and remap locals ---
            Stmt cloned = (Stmt) cs.clone();
            remapLocals(cloned, localMap);
            inlinedUnits.add(cloned);
        }

        // --- 4. Splice into caller ---
        // Insert all inlined units BEFORE the original call stmt
        Unit insertPoint = callStmt;
        for (Unit iu : inlinedUnits) {
            callerUnits.insertBefore(iu, insertPoint);
        }
        callerUnits.insertBefore(afterInline, insertPoint);

        // Replace the original call stmt:
        //   If it was an assign, replace RHS with resultLocal
        //   Otherwise just remove it
        if (resultLocal != null && callStmt instanceof AssignStmt) {
            AssignStmt as = (AssignStmt) callStmt;
            as.setRightOp(resultLocal);
        } else {
            callerUnits.remove(callStmt);
        }
    }

    // -----------------------------------------------------------------------
    /** Shallow-clone a Value, mapping any Local through localMap. */
    private Value cloneValue(Value v, Map<Local, Local> localMap) {
        if (v instanceof Local) {
            return localMap.getOrDefault((Local) v, (Local) v);
        }
        return v;
    }

    /** Walk all use/def boxes in a cloned Stmt and remap locals. */
    private void remapLocals(Stmt s, Map<Local, Local> localMap) {
        for (ValueBox vb : s.getUseAndDefBoxes()) {
            Value v = vb.getValue();
            if (v instanceof Local && localMap.containsKey(v)) {
                vb.setValue(localMap.get(v));
            }
        }
    }

    /** Detect direct self-recursion (callee calls itself). */
    private boolean isSelfRecursive(SootMethod m) {
        if (!m.hasActiveBody()) return false;
        for (Unit u : m.getActiveBody().getUnits()) {
            Stmt s = (Stmt) u;
            if (s.containsInvokeExpr()) {
                if (s.getInvokeExpr().getMethod().equals(m)) return true;
            }
        }
        return false;
    }
}
