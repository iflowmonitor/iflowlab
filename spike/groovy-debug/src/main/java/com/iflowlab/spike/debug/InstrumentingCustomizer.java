package com.iflowlab.spike.debug;

import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.CodeVisitorSupport;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.Parameter;
import org.codehaus.groovy.ast.Variable;
import org.codehaus.groovy.ast.expr.ArgumentListExpression;
import org.codehaus.groovy.ast.expr.ClosureExpression;
import org.codehaus.groovy.ast.expr.ClosureListExpression;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.DeclarationExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.MapEntryExpression;
import org.codehaus.groovy.ast.expr.MapExpression;
import org.codehaus.groovy.ast.expr.StaticMethodCallExpression;
import org.codehaus.groovy.ast.expr.VariableExpression;
import org.codehaus.groovy.ast.stmt.BlockStatement;
import org.codehaus.groovy.ast.stmt.CaseStatement;
import org.codehaus.groovy.ast.stmt.CatchStatement;
import org.codehaus.groovy.ast.stmt.DoWhileStatement;
import org.codehaus.groovy.ast.stmt.ExpressionStatement;
import org.codehaus.groovy.ast.stmt.ForStatement;
import org.codehaus.groovy.ast.stmt.IfStatement;
import org.codehaus.groovy.ast.stmt.Statement;
import org.codehaus.groovy.ast.stmt.SwitchStatement;
import org.codehaus.groovy.ast.stmt.SynchronizedStatement;
import org.codehaus.groovy.ast.stmt.TryCatchStatement;
import org.codehaus.groovy.ast.stmt.WhileStatement;
import org.codehaus.groovy.classgen.GeneratorContext;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.control.customizers.CompilationCustomizer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rewrites every compiled method/closure body so each original statement is
 * preceded by a call to {@link DebugRuntime#onStatement(int, int, Map)},
 * carrying the source line, the lexical scope depth, and a live snapshot of
 * in-scope locals.
 *
 * <p>Runs at {@link CompilePhase#CANONICALIZATION} so variable references are
 * already resolved; injected local reads reuse the resolved {@link Variable}.
 *
 * <p>Scope depth is bumped only at method and closure boundaries (not at
 * if/for/while blocks), so step-over treats a closure invocation as a deeper
 * scope while ordinary control flow stays at the same depth.
 */
public final class InstrumentingCustomizer extends CompilationCustomizer {

    private static final ClassNode RUNTIME = ClassHelper.make(DebugRuntime.class);

    public InstrumentingCustomizer() {
        super(CompilePhase.CANONICALIZATION);
    }

    @Override
    public void call(SourceUnit source, GeneratorContext context, ClassNode classNode) {
        for (MethodNode method : classNode.getMethods()) {
            Statement code = method.getCode();
            if (code instanceof BlockStatement block) {
                LinkedHashMap<String, Variable> scope = new LinkedHashMap<>();
                for (Parameter p : method.getParameters()) {
                    scope.put(p.getName(), p);
                }
                instrumentBlock(block, 1, scope);
            }
        }
    }

    // ------------------------------------------------------------------
    // Statement rewriting
    // ------------------------------------------------------------------

    /** Interleave a hook before each child; recurse; track declared locals. */
    private void instrumentBlock(BlockStatement block, int depth, LinkedHashMap<String, Variable> inherited) {
        List<Statement> original = new ArrayList<>(block.getStatements());
        List<Statement> rewritten = new ArrayList<>(original.size() * 2);
        LinkedHashMap<String, Variable> visible = new LinkedHashMap<>(inherited);

        for (Statement child : original) {
            int line = child.getLineNumber();
            if (line > 0) {
                rewritten.add(hook(line, depth, visible));
            }
            rewritten.add(instrumentStatement(child, depth, visible));
            registerDeclarations(child, visible);
        }

        block.getStatements().clear();
        block.getStatements().addAll(rewritten);
    }

    private Statement instrumentStatement(Statement s, int depth, LinkedHashMap<String, Variable> visible) {
        if (s instanceof BlockStatement bs) {
            instrumentBlock(bs, depth, visible);
            return bs;
        }
        if (s instanceof IfStatement ifs) {
            ifs.setIfBlock(instrumentStatement(ifs.getIfBlock(), depth, copy(visible)));
            ifs.setElseBlock(instrumentStatement(ifs.getElseBlock(), depth, copy(visible)));
            instrumentClosures(ifs.getBooleanExpression(), depth, visible);
            return ifs;
        }
        if (s instanceof ForStatement fs) {
            LinkedHashMap<String, Variable> inner = copy(visible);
            Parameter v = fs.getVariable();
            if (v != null && !"forLoopDummyParameter".equals(v.getName())) {
                inner.put(v.getName(), v); // for-each loop variable
            }
            // C-style for: the counter is declared in the init of the ClosureListExpression.
            if (fs.getCollectionExpression() instanceof ClosureListExpression cle) {
                for (Expression init : cle.getExpressions()) {
                    if (init instanceof DeclarationExpression de
                            && de.getLeftExpression() instanceof VariableExpression ve) {
                        inner.put(ve.getName(),
                                ve.getAccessedVariable() != null ? ve.getAccessedVariable() : ve);
                    }
                }
            }
            instrumentClosures(fs.getCollectionExpression(), depth, visible);
            fs.setLoopBlock(instrumentStatement(fs.getLoopBlock(), depth, inner));
            return fs;
        }
        if (s instanceof WhileStatement ws) {
            instrumentClosures(ws.getBooleanExpression(), depth, visible);
            ws.setLoopBlock(instrumentStatement(ws.getLoopBlock(), depth, copy(visible)));
            return ws;
        }
        if (s instanceof DoWhileStatement ds) {
            ds.setLoopBlock(instrumentStatement(ds.getLoopBlock(), depth, copy(visible)));
            return ds;
        }
        if (s instanceof TryCatchStatement ts) {
            ts.setTryStatement(instrumentStatement(ts.getTryStatement(), depth, copy(visible)));
            for (CatchStatement cs : ts.getCatchStatements()) {
                LinkedHashMap<String, Variable> inner = copy(visible);
                Parameter ev = cs.getVariable();
                if (ev != null) {
                    inner.put(ev.getName(), ev);
                }
                cs.setCode(instrumentStatement(cs.getCode(), depth, inner));
            }
            if (ts.getFinallyStatement() != null) {
                ts.setFinallyStatement(instrumentStatement(ts.getFinallyStatement(), depth, copy(visible)));
            }
            return ts;
        }
        if (s instanceof SynchronizedStatement ss) {
            ss.setCode(instrumentStatement(ss.getCode(), depth, copy(visible)));
            return ss;
        }
        if (s instanceof SwitchStatement sw) {
            instrumentClosures(sw.getExpression(), depth, visible);
            for (CaseStatement cs : sw.getCaseStatements()) {
                cs.setCode(instrumentStatement(cs.getCode(), depth, copy(visible)));
            }
            sw.setDefaultStatement(instrumentStatement(sw.getDefaultStatement(), depth, copy(visible)));
            return sw;
        }
        // Leaf statements (ExpressionStatement, ReturnStatement, ...): only
        // closures nested inside their expressions still need instrumenting.
        instrumentClosures(s, depth, visible);
        return s;
    }

    /**
     * Rewrite the body of every closure found inside {@code node} at depth+1.
     *
     * <p>The closure snapshot captures the closure's own parameters and locals
     * declared inside it. It does NOT capture enclosing (captured) locals:
     * injecting a synthetic read of an enclosing local inside a closure resolves
     * to a dynamic property lookup rather than the closure's shared variable, and
     * throws MissingPropertyException at runtime. That is the documented P3 wall.
     * The {@code enclosing} argument is intentionally unused for snapshot content.
     */
    private void instrumentClosures(ASTNode node, int depth, Map<String, Variable> enclosing) {
        if (node == null) {
            return;
        }
        node.visit(new CodeVisitorSupport() {
            @Override
            public void visitClosureExpression(ClosureExpression ce) {
                if (ce.getCode() instanceof BlockStatement body) {
                    LinkedHashMap<String, Variable> scope = new LinkedHashMap<>();
                    Parameter[] ps = ce.getParameters();
                    if (ps != null) {
                        for (Parameter p : ps) {
                            scope.put(p.getName(), p);
                        }
                    }
                    // Deliberately do NOT recurse into nested closures here;
                    // instrumentBlock will reach them via each child statement.
                    instrumentBlock(body, depth + 1, scope);
                }
            }
        });
    }

    private void registerDeclarations(Statement child, LinkedHashMap<String, Variable> visible) {
        if (child instanceof ExpressionStatement es
                && es.getExpression() instanceof DeclarationExpression de
                && de.getLeftExpression() instanceof VariableExpression ve) {
            visible.put(ve.getName(), ve.getAccessedVariable() != null ? ve.getAccessedVariable() : ve);
        }
    }

    // ------------------------------------------------------------------
    // Hook construction
    // ------------------------------------------------------------------

    private Statement hook(int line, int depth, Map<String, Variable> visible) {
        StaticMethodCallExpression call = new StaticMethodCallExpression(
                RUNTIME,
                "onStatement",
                new ArgumentListExpression(
                        new ConstantExpression(line, true),
                        new ConstantExpression(depth, true),
                        localsMap(visible)));
        ExpressionStatement stmt = new ExpressionStatement(call);
        stmt.setLineNumber(line);
        stmt.setColumnNumber(1);
        return stmt;
    }

    /** Build a {@code [name: value, ...]} map that re-reads each live local. */
    private MapExpression localsMap(Map<String, Variable> visible) {
        MapExpression map = new MapExpression();
        for (Map.Entry<String, Variable> e : visible.entrySet()) {
            Variable var = e.getValue();
            VariableExpression read = new VariableExpression(var);
            read.setAccessedVariable(var);
            map.addMapEntryExpression(new MapEntryExpression(
                    new ConstantExpression(e.getKey()), read));
        }
        return map;
    }

    private static LinkedHashMap<String, Variable> copy(LinkedHashMap<String, Variable> m) {
        return new LinkedHashMap<>(m);
    }
}
