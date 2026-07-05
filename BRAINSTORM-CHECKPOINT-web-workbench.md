# BRAINSTORM CHECKPOINT — iflowlab web workbench

> Stage 0 output (brainstorm, claude.ai session 2026-07-04..06).
> Supersedes the desktop-workbench direction entirely. Desktop iteration
> archived at tag `v0-desktop-archive` / branch `archive/desktop-workbench`
> (PR #3 closed without merge).
> Next stage: grill-me (Stage 1), transcript to `grill-me-sessions/01-web-workbench.md`.

## Product framing

iflowlab is a standalone local dev tool for SAP Integration Suite developers:
author, run, inspect, and debug CPI Groovy scripts (and later XSLT) offline,
against a faithful mock of the CPI Message API. Positioned under the
`iflowmonitor` GitHub org for brand visibility; separate product from the
iFlowMonitor monitoring platform.

Primary use case (D6): **interactive run-and-inspect workbench** — script +
input Message → run → type-aware result rendering. Debugging (D7/D8) is a
core capability, not an add-on. Saved test suites / assertions are a later
layer, NOT slice 1.

## Decisions

### D1 — Repo layout & delivery: monorepo, Quarkus + Quinoa, single jar
React app built and served by Quarkus via Quinoa; one build, one deliverable
(`java -jar iflowlab.jar`). Rejected: split dev servers (two moving parts),
two repos (overkill solo).
Note: future SSO/hosted scenarios do not invalidate this — auth would live at
the Quarkus layer (`quarkus-oidc`); same-origin SPA simplifies it.

### D2 — Harness scope: Groovy-first, engine-agnostic abstraction
Harness designed engine-agnostic (TestCase → Engine → Result); Groovy is the
first engine, XSLT ports later as the second and validates the abstraction.
Desktop XSLT CLI runner remains usable at the archive tag meanwhile.
Rejected: both engines day one (delays Groovy, teaches nothing); no
abstraction (known rework).

### D3 — Execution model: in-process GroovyShell, local-trust
No sandbox; explicit trust model = "executes scripts with the privileges of
the local user, same as any build tool" — documented in an ADR. Configurable
run timeout (default ~10s) via executor interruption guards against
accidental infinite loops. Groovy version pinned to match the CPI tenant
runtime (exact version to be verified against SAP docs at execution time —
NOT from memory). Rejected: SecureASTCustomizer (leaky, false positives fight
the user), subprocess-per-run (kills feedback loop).
Hosted multi-tenant execution is explicitly OUT OF SCOPE; revisiting it is a
new ADR-level decision, not a retrofit.

### D4 — Workspace model: file-based, filesystem is source of truth
Workspace = user-pointed directory. Scripts, inputs, saved cases live as
files — git-friendly, diffable, versioned next to the user's iflow project.
UI is an editor over those files. Keeps a future headless CLI/CI runner
essentially free (author in UI, run in pipeline). Rejected: embedded DB
(locks tests inside the tool), hybrid (source-of-truth ambiguity).
Fixture format = grill/PRD-level detail.

### D5 — CPI Message API mock: clean-room, validated against oracles
Hand-written clean-room implementation of
`com.sap.gateway.ip.core.customdev.util.Message` (+ MessageLogFactory etc.)
matching SAP's documented API. SAP's jar is not redistributable. Existing OSS
CPI mocks + SAP docs serve as test oracles for edge cases — NOT as adopted
dependencies. Fidelity features at PRD level: body type coercion
(getBody(String.class) vs InputStream), one-shot InputStream reads, header
handling. OSS landscape survey happens at grill time via Context7/web.

### D6 — Core loop: interactive run-and-inspect (REVISED during brainstorm)
Monaco (script) + input panel (body paste/upload, headers, properties) →
Run → output panel with type-aware rendering:
- XML/JSON pretty-printed; plain text raw
- binary (e.g. zipped payload): content-type + size + hex preview + download
- headers/properties before/after diff
- exception: stack trace mapped to script line
- messageLog + println captured into a log pane
Assertion catalog / saved suites deferred to a later slice (original D6
draft assumed suites-first; Pavol corrected: run-and-see-results is the
product).

### D7 — Debugging: full breakpoint debugging (pause/step/inspect in Monaco)
Log-based and trace-based alternatives rejected — live debugging was a
motivation for the pivot.

### D8 — Debugger architecture: Groovy AST instrumentation + DAP
Engine: compile user script with injected per-statement hooks (breakpoint
check + thread park). Wire contract: DAP over WebSocket, so the Monaco side
uses standard DAP client machinery and the engine stays swappable (JDWP
child-JVM = fallback if instrumentation hits a wall). Rejected as primary:
JDWP (line-mapping pain through Groovy codegen, child-JVM latency).

### D9 — Empirical probe before grill: EXECUTED, verdict GO
Spike on branch `probe/groovy-debug-instrumentation` (SHA 524d8ea, no PR),
standalone Maven module `spike/groovy-debug/`, Groovy 4.0.32, GraalVM JDK 25,
14/14 tests green. Full details: `spike/groovy-debug/PROBE-REPORT.md`.

| Probe | Verdict |
|---|---|
| P1 statement hooks + exact line numbers | GO |
| P2 breakpoint park/resume | GO |
| P3 variable state read | PARTIAL — binding, run()-body locals, method params+locals, closure params+own-locals all OK; closure-CAPTURED enclosing locals unreadable via synthetic reads (MissingPropertyException) |
| P4 step-over/into | PARTIAL — sequence/loop/closure OK; step-over a script-method call behaves as step-into (lexical depth insufficient) |
| P5 cancel/timeout | GO — caveat: empty `while(true){}` has no hook → watchdog needed |
| P6 overhead | GO — ~2.3–2.8× with full capture; fine for interactive |

**Structural caveats feeding the DAP layer (from PROBE-REPORT.md):**
1. Runtime call-depth counter required for correct step-over/step-out across
   method calls — static lexical depth is insufficient.
2. Closure-captured variables need a harvest strategy (already-resolved
   refs / shared-variable holders); synthetic name reads are a dead end.

## Open questions (deferred to grill)

- O1: Groovy version tension — spike ran on Groovy 4.0.32; D3 requires
  pinning to the CPI tenant runtime version. Verify the CPI version from SAP
  docs AND re-validate instrumentation on that version (probe rerun on pinned
  version = cheap; instrumentation APIs may differ across Groovy majors).
- O2: Fixture/workspace file format and directory conventions (bodies,
  headers/properties files, saved runs).
- O3: Monaco Groovy language support depth — syntax highlighting baseline vs.
  completion over the mock Message API (ambition level, effort).
- O4: DAP client library on the frontend (monaco + DAP-over-WebSocket
  glue) and which DAP subset the backend implements for slice scope.
- O5: Script classpath model — what libraries a CPI script can legitimately
  import (Groovy stdlib XmlSlurper/JsonSlurper vs. CPI-shipped jars) and how
  the workbench classpath mirrors that.
- O6: Concurrency/session model — single debug session at a time vs.
  multiple; run cancellation UX.
- O7: Attachments support in the Message mock (slice 1 or later).
- O8: Workspace picker UX (startup flag vs. in-UI directory picker) and
  multi-workspace handling.

## Rejected alternatives log

- Desktop delivery (Compose) — abandoned, archived (strategic pivot to web).
- App-managed DB store for test cases (D4-B), hybrid store (D4-C).
- SecureASTCustomizer sandboxing (D3-B), subprocess isolation (D3-C).
- Adopting an OSS CPI mock as a dependency (D5-B).
- Exact-match-only assertions and Groovy-code assertions as slice-1 items
  (moved behind the interactive loop; assertion catalog = later slice).
- Log-only (D7-A) and trace-only (D7-B) debugging.
- JDWP as primary debug engine (D8-A) — retained as fallback only.
