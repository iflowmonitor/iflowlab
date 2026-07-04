# PROBE REPORT — Groovy AST-instrumentation debugger (Decision D8)

**Throwaway empirical spike.** Validates ONLY the engine core for a Groovy
script debugger built on AST instrumentation: compile the script with an injected
per-statement hook that checks a breakpoint set and parks the thread. No Quarkus,
no WebSocket, no DAP, no UI — plain JVM + JUnit drivers.

| Item | Value |
|------|-------|
| Groovy | **4.0.32** (`org.apache.groovy:groovy`, latest 4.0.x on Maven Central) |
| Host JVM | Oracle GraalVM 25.0.2 (JDK 25 LTS) — no Groovy/JDK friction observed |
| Build | Maven (`spike/groovy-debug/pom.xml`), `--release 21` |
| Tests | 14 JUnit-5 tests, all green (`mvn test` → BUILD SUCCESS) |
| Approach | Custom `CompilationCustomizer` @ `CANONICALIZATION` rewriting every statement |

## Overall verdict

**AST instrumentation is VIABLE for D8.** The engine core works: per-statement
hooks with exact line numbers, breakpoint park/resume across threads, live
variable capture (with one documented wall), step-over/into, and clean external
cancellation. Overhead with a debugger fully attached is ~2–3×, negligible for an
interactive test tool.

Two structural caveats must be carried into the DAP layer (details per probe):
1. **Enclosing (closure-captured) locals are not capturable** by synthetic reads.
2. **Step-over across a call to a script-defined method** behaves like step-into
   under the static-lexical-depth model; true method step-over needs a runtime
   call-depth counter.

## Architecture (as built)

- `InstrumentingCustomizer` — a `CompilationCustomizer` at `CompilePhase.CANONICALIZATION`.
  Walks every `MethodNode` body (incl. the script's `run()`), and before each
  original statement injects `DebugRuntime.onStatement(line, depth, localsMap)`.
  Recurses into blocks, `if`/`for`/`while`/`try`/`switch`, and closure bodies.
- `DebugRuntime` — static entry point; binds the current `DebugSession` per thread
  so the hook works identically in top-level code, methods, and closures (a plain
  static call needs no lexical access).
- `DebugSession` — `ReentrantLock` + two `Condition`s. Script thread parks in
  `onStatement`; driver thread observes via `awaitPause` and drives
  `resume`/`stepOver`/`stepInto`/`cancel`.
- `DebugEngine` — compiles (plain or instrumented) via `GroovyShell` and runs the
  script on a dedicated worker thread, returning a `RunHandle`.
- `sample.groovy` — ~48-line script: C-style loop, for-each, two script methods,
  three closures (`collect`, `eachWithIndex`, `each`), a nested `if`.

`depth` = **static lexical scope depth**, bumped only at method and closure
boundaries (not at if/for/while). This makes a closure invocation look one scope
deeper (enables step-over of closures) while ordinary control flow stays level.

---

## P1 — Instrument every statement, correct line numbers — **GO**

`P1_InstrumentationTest`. Instrumented `sample.groovy`, recorded the full hook
trace, no pausing.

Evidence — `line→count` from the run:
```
{3=1,4=1,5=1,7=1,8=4,9=4,10=4,11=4,14=5,16=1,17=1,18=4,19=4,20=2,24=1,27=1,
 36=4,37=4,38=19,40=4,44=1,45=5,46=1,47=1}
```
- Every reported line is a real source-statement line (verified against the file).
- Loop body (8–11) fires 4× (4 items); `measure()` body (36–40) fires 4× (called
  4×); `c++` at line 38 fires 19× (5+4+5+5 chars); the `if`-body at line 20 fires
  2× (`running>10` only for gamma & delta).
- Closures instrumented: eachWithIndex body (18–20), `collect` body (14), `each`
  body (45). Line numbers are **exact** — including a closure whose body shares a
  source line with its enclosing call (line 45).
- Result correctness under instrumentation confirmed (total=19, lengths=[5,4,5,5],
  doubled=[10,8,10,10], running=17, summary string).

Note: an early apparent "-1 offset in methods" was a **miscount of the source
file**, not a real effect — `grep -n` confirmed line fidelity is exact everywhere.

## P2 — Park at breakpoint, driver resumes, run completes — **GO**

`P2_ParkResumeTest`. Breakpoint on line 11 (inside the loop).
- Driver's `awaitPause` returns; `frame().line == 11`; run not finished.
- Script thread is genuinely blocked: `join(300ms)` fails while parked.
- Breakpoint re-hits once per iteration → exactly **4 resumes** to completion.
- After the last resume the thread finishes, `exitCause == null`, and the final
  `result` map is correct. No thread leak.

## P3 — Read variable state while parked — **PARTIAL** (one hard wall)

`P3_VariableStateTest` (4 tests). Locals are captured by injecting a live
`[name: value, …]` map at each hook, built from resolved `Variable`s tracked
during the AST walk.

| Scope | Result | Evidence |
|-------|--------|----------|
| **Binding** (script globals / seeded CPI inputs) | **GO** | Driver owns the `Binding`; reads it live while parked. Note: script vars declared with `def`/type are `run()` **locals**, NOT Binding entries — only undeclared assignments (e.g. `result = …`) land in the Binding. |
| **`run()`-body locals** | **GO** | At line 11: `{total=0, items=[…], lengths=[5], item=alpha, len=5}` plus the C-style loop counter `i` (declared in the loop's `ClosureListExpression` init — needed explicit handling). |
| **Method params + method locals** | **GO** | Inside `measure(String s)` at line 38: `s='alpha'`, `c=0`, for-each var `ch` all visible. |
| **Closure params + closure-declared locals** | **GO** | Inside `eachWithIndex { name, idx -> … }`: `name='alpha'`, `idx=0` visible. |
| **Enclosing (closure-captured) locals** | **NO-GO** | `running` (a `run()` local mutated inside the closure) is **not** in the snapshot. |

**The wall (documented, reproduced):** injecting a synthetic read of an enclosing
local *inside a closure body* does not bind to the closure's shared variable — at
`CANONICALIZATION` it resolves to a dynamic property lookup and throws
`groovy.lang.MissingPropertyException: No such property: total` at runtime. So a
naive "snapshot every in-scope name" strategy is unsafe for captured variables.

Implications for DAP:
- Binding, run()-body locals, method locals, and closure-own locals are all fully
  inspectable — this covers the majority of a CPI `Message processData(...)` body.
- To expose **captured** variables inside closures we must NOT synthesize fresh
  reads. Viable follow-ups: (a) harvest the already-resolved `VariableExpression`
  nodes the closure body itself references; (b) capture via the closure's
  `Reference`/shared-variable holders; (c) read them at the closure's *definition*
  site instead of inside the body. Needs a dedicated design pass.

## P4 — Step-over / step-into semantics — **PARTIAL** (closures GO; method-calls limited)

`P4_StepOverTest` (5 tests). Step-over = resume until the next statement at the
same or shallower lexical depth.

- **Plain sequence** — **GO**: break @3, step-over → 4 → 5 → 7.
- **Inside a loop** — **GO**: break @10, step-over → 11, step-over → 8 (loop
  back-edge into the next iteration), same depth throughout.
- **Over a closure call** — **GO**: break @17 (`items.eachWithIndex{…}`, depth 1),
  step-over lands on line 24 — the closure body (depth 2) is skipped.
- **Into a closure call** — **GO**: break @17, step-into lands on line 18 inside
  the closure body at depth 2.
- **Over a script-METHOD call** — **LIMITATION (pinned by test)**: break @9
  (`def len = measure(item)`, depth 1), step-over stops on line 36 *inside*
  `measure()` — i.e. behaves like step-into.

**Why:** `depth` is static lexical. A script method (`measure`) is lexically a
sibling of `run()`, so its body is also depth 1 — the static model cannot see that
a *call* pushes a dynamic frame. Closures work because they are lexically nested.

Fix for DAP (straightforward, not attempted here to respect spike scope): maintain
a **runtime call-depth counter** in `DebugRuntime` (increment on method entry,
decrement in a `finally` on exit) and combine it with lexical depth for the
step-over comparison.

## P5 — Timeout / cancel without leaking the thread — **GO**

`P5_CancelTest` (2 tests). Cancellation is cooperative: the next hook throws
`DebugCancelledException`, unwinding the script thread.
- **Parked script**: break @11, `cancel()` → thread terminates within 2s,
  `exitCause` is `DebugCancelledException`, `result` never assigned. No leak.
- **Infinite loop** (`while(true){ x = x+1 }`): let it spin 150ms, `cancel()` →
  thread terminates within 2s. No leak.

**Caveat:** cancellation only fires at an instrumented statement. A genuinely
empty infinite loop (`while(true){}`, no body statement) has no hook and would not
be cancellable this way — it would need a JVM-level interrupt / thread budget as a
backstop. Not a concern for real CPI scripts, but the DAP layer should still cap
runs with a hard watchdog.

## P6 — Overhead sanity — **GO**

`P6_OverheadTest`. Same script, plain vs instrumented, 5000 iterations each after
warm-up. The instrumented path also builds the full P3 locals snapshot at *every*
statement — i.e. worst-case "debugger fully attached".

```
plain ≈ 150–185 ms   instrumented ≈ 420–435 ms   → ~2.3–2.8× (5000 iters)
```

Order-of-magnitude: **~2–3× with full per-statement locals capture and a lock
taken on every statement**. For an interactive single-run test tool this is
negligible. Obvious wins if ever needed: only snapshot locals on demand (when
paused), and a lock-free fast path when no breakpoints are set and not stepping.

---

## Bottom line for the architecture

- **Proceed with D8 (AST instrumentation).** The core mechanic — per-statement
  hooks, threaded park/resume, cancellation — is solid and cheap on Groovy 4.0.32 /
  JDK 25.
- **Feed two items into the DAP design as first-class work, not afterthoughts:**
  1. A **runtime call-depth counter** so step-over/step-out work across method
     calls (lexical depth alone is insufficient).
  2. A **safe strategy for closure-captured variables** (harvest resolved refs /
     shared-variable holders) — synthetic name reads are a dead end.
- Add a **hard watchdog** on every run regardless of breakpoints (empty-loop case).
- Line-number fidelity is exact, so DAP breakpoint↔source mapping is 1:1 with no
  correction needed.
```
