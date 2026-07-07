package com.iflowmonitor.iflowlab.engine.debug;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.CodeVisitorSupport;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.Parameter;
import org.codehaus.groovy.ast.Variable;
import org.codehaus.groovy.ast.expr.ArgumentListExpression;
import org.codehaus.groovy.ast.expr.BinaryExpression;
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
import org.codehaus.groovy.ast.stmt.EmptyStatement;
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
import org.codehaus.groovy.syntax.Token;
import org.codehaus.groovy.syntax.Types;

/**
 * Rewrites every compiled method/closure body so each original statement is
 * preceded by {@link DebugRuntime#onStatement(int, int, Map)} (source line,
 * lexical closure-nesting depth, live locals), and wraps each method body with
 * {@link DebugRuntime#enterMethod}/{@link DebugRuntime#exitMethod} so a runtime
 * call-depth counter tracks method frames.
 *
 * <p>Runs at {@link CompilePhase#CANONICALIZATION} so variable references are
 * already resolved; injected local reads reuse the resolved {@link Variable}.
 *
 * <p>Known limitation (documented, P3): the locals snapshot inside a closure
 * covers the closure's own parameters and locals but NOT enclosing (captured)
 * locals — a synthetic read of a captured local resolves to a dynamic property
 * lookup and throws. Surfaced to the user, not hidden.
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
                instrumentBlock(block, 0, scope);
                method.setCode(wrapWithCallDepth(method.getName(), block));
            }
        }
    }

    /** enterMethod(name); try { body } finally { exitMethod() } */
    private Statement wrapWithCallDepth(String methodName, BlockStatement body) {
        BlockStatement wrapper = new BlockStatement();
        wrapper.addStatement(new ExpressionStatement(
                staticCall("enterMethod", new ArgumentListExpression(new ConstantExpression(methodName)))));
        wrapper.addStatement(new TryCatchStatement(
                body,
                new ExpressionStatement(staticCall("exitMethod", new ArgumentListExpression()))));
        return wrapper;
    }

    private void instrumentBlock(BlockStatement block, int depth, LinkedHashMap<String, Variable> inherited) {
        List<Statement> original = new ArrayList<>(block.getStatements());
        List<Statement> rewritten = new ArrayList<>(original.size() * 2);
        LinkedHashMap<String, Variable> visible = new LinkedHashMap<>(inherited);

        for (Statement child : original) {
            int line = child.getLineNumber();
            if (line > 0) {
                rewritten.add(hook("onStatement", line, depth, visible));
                // After the hook (where a pause may have set an override), write any
                // debugger-set value back into each visible local, so editing a
                // variable while paused takes effect for the rest of the run.
                for (Map.Entry<String, Variable> e : visible.entrySet()) {
                    rewritten.add(applyOverride(e.getKey(), e.getValue()));
                }
            }
            rewritten.add(instrumentStatement(child, depth, visible));
            registerDeclarations(child, visible);
            // Post-hook after a mutating (expression) statement, with the locals it
            // just produced — so a data breakpoint stops on the line that changed the
            // value, while the variable is still in scope (not one statement later).
            if (line > 0 && child instanceof ExpressionStatement) {
                rewritten.add(hook("afterStatement", line, depth, visible));
            }
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
            instrumentClosures(ifs.getBooleanExpression(), depth);
            return ifs;
        }
        if (s instanceof ForStatement fs) {
            LinkedHashMap<String, Variable> inner = copy(visible);
            Parameter v = fs.getVariable();
            if (v != null && !"forLoopDummyParameter".equals(v.getName())) {
                inner.put(v.getName(), v);
            }
            if (fs.getCollectionExpression() instanceof ClosureListExpression cle) {
                for (Expression init : cle.getExpressions()) {
                    if (init instanceof DeclarationExpression de
                            && de.getLeftExpression() instanceof VariableExpression ve) {
                        inner.put(ve.getName(), ve.getAccessedVariable() != null ? ve.getAccessedVariable() : ve);
                    }
                }
            }
            instrumentClosures(fs.getCollectionExpression(), depth);
            fs.setLoopBlock(instrumentStatement(fs.getLoopBlock(), depth, inner));
            return fs;
        }
        if (s instanceof WhileStatement ws) {
            instrumentClosures(ws.getBooleanExpression(), depth);
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
            if (ts.getFinallyStatement() != null && !(ts.getFinallyStatement() instanceof EmptyStatement)) {
                ts.setFinallyStatement(instrumentStatement(ts.getFinallyStatement(), depth, copy(visible)));
            }
            return ts;
        }
        if (s instanceof SynchronizedStatement ss) {
            ss.setCode(instrumentStatement(ss.getCode(), depth, copy(visible)));
            return ss;
        }
        if (s instanceof SwitchStatement sw) {
            instrumentClosures(sw.getExpression(), depth);
            for (CaseStatement cs : sw.getCaseStatements()) {
                cs.setCode(instrumentStatement(cs.getCode(), depth, copy(visible)));
            }
            sw.setDefaultStatement(instrumentStatement(sw.getDefaultStatement(), depth, copy(visible)));
            return sw;
        }
        instrumentClosures(s, depth);
        return s;
    }

    /** Instrument every closure body found in {@code node} at lexical depth+1. */
    private void instrumentClosures(ASTNode node, int depth) {
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

    private Statement hook(String method, int line, int depth, Map<String, Variable> visible) {
        StaticMethodCallExpression call = staticCall(
                method,
                new ArgumentListExpression(
                        new ConstantExpression(line, true),
                        new ConstantExpression(depth, true),
                        localsMap(visible)));
        ExpressionStatement stmt = new ExpressionStatement(call);
        stmt.setLineNumber(line);
        stmt.setColumnNumber(1);
        return stmt;
    }

    /** {@code name = DebugRuntime.applyOverride('name', name)} — writes a pending edit back. */
    private Statement applyOverride(String name, Variable var) {
        VariableExpression target = new VariableExpression(var);
        target.setAccessedVariable(var);
        VariableExpression read = new VariableExpression(var);
        read.setAccessedVariable(var);
        StaticMethodCallExpression call = staticCall(
                "applyOverride",
                new ArgumentListExpression(new ConstantExpression(name), read));
        BinaryExpression assign = new BinaryExpression(target, Token.newSymbol(Types.ASSIGN, -1, -1), call);
        return new ExpressionStatement(assign);
    }

    private MapExpression localsMap(Map<String, Variable> visible) {
        MapExpression map = new MapExpression();
        for (Map.Entry<String, Variable> e : visible.entrySet()) {
            Variable var = e.getValue();
            VariableExpression read = new VariableExpression(var);
            read.setAccessedVariable(var);
            map.addMapEntryExpression(new MapEntryExpression(new ConstantExpression(e.getKey()), read));
        }
        return map;
    }

    private static StaticMethodCallExpression staticCall(String method, ArgumentListExpression args) {
        return new StaticMethodCallExpression(RUNTIME, method, args);
    }

    private static LinkedHashMap<String, Variable> copy(LinkedHashMap<String, Variable> m) {
        return new LinkedHashMap<>(m);
    }
}
