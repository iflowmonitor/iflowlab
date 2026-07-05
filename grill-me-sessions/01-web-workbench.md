# Grill-me Session 01 — Web Workbench

> Stage 1 (grill-me) of the iFlowMonitor spec workflow for the **iflowlab web workbench**.
> Input artefact: `BRAINSTORM-CHECKPOINT-web-workbench.md` (Stage 0, claude.ai 2026-07-04..06).
> Empirical ground truth: `spike/groovy-debug/PROBE-REPORT.md` @ `probe/groovy-debug-instrumentation` (524d8ea).
> Scope: everything needed to write a **slice-able** PRD for the run/debug workbench
> (React + Monaco + Quarkus/Quinoa).
> Resolves: O1–O8 + D5 mock-oracle survey. Corrects: O1 premise, D8 rationale.
> Product line: **first grill of the web workbench** — this transcript supersedes nothing.

## Status legend

- **RESOLVED** — decision reached this session
- **CORRECTION** — factual/decision correction to the Stage-0 checkpoint
- **NEW** — branch surfaced during the grill (not in the checkpoint)
- **DEFERRED** — explicitly punted to a named later slice/enhancement

## Research provenance (all claims Context7/web, never training data)

| Claim | Value | Source (fetched 2026-07-06) |
|---|---|---|
| CPI current Groovy runtime | **4.0.29** (Groovy Script step v2.x) | SAP Community "How to Fix Groovy Script Warnings… Upgrade to Script Version 2.x" (2026); "Upgrade Readiness Design Guidelines" (2025/26) |
| CPI Camel runtime | 2.24.2 → **3.14.7** | SAP Help "Apache Camel 3.14 Upgrade"; Figaf |
| Pre-upgrade snapshot (struck) | Groovy 2.4.21 / Java 1.8.0_311 / Camel 2.24.2-sap-23 | SAP Community "Testing Groovy Locally (IntelliJ)" — **historical, not current** |
| Groovy 4.0.x JDK range | Java 17+ … up to Java 25 | Context7 `/apache/groovy` core-getting-started |
| Groovy 4 XML/JSON modules | `groovy-xml` (`groovy.xml.*`), `groovy-json` (`groovy.json.*`) — separate Maven modules | Context7 `/apache/groovy` groovy-xml/groovy-json READMEs |
| Browser DAP client | **none production-grade** exists | Context7 (only the DAP spec site); monaco #558, monaco-languageclient #690; `monaco-debugger` self-declared unstable |
| `@vscode/debugprotocol` | **1.68.0**, no `dependencies` field (types-only) | npm registry |
| Monaco Groovy highlighting | **not** in `basic-languages`; needs a Monarch grammar | monaco-editor #1490; Context7 monaco-editor |
| OSS CPI mock oracles | `com.equaliseit:sap-cpi-mocks`, SCPIScriptTest, CPIGroovyDebugger, SAPCI_GroovySim | web survey |

---

## CORRECTIONS to the checkpoint

**C1 — O1 Groovy version premise (struck & replaced).**
My initial research anchored on a **pre-Camel-3 snapshot** (Groovy 2.4.21 / Java 8). That is
historical. Current CPI runtime (Groovy Script step **v2.x**) runs **Groovy 4.0.29** on the
post-Camel-3.14.7 stack. The "2.x" is the SAP step version and it *is* the carrier of the
language bump — I had wrongly dismissed it as mere step-versioning. Net effect: the spike's
4.0.32 is only a **patch** away from the pinned 4.0.29, so the version tension the checkpoint
feared largely evaporates.

**C2 — D8 rationale (dead half struck).**
D8 justified DAP partly as "the Monaco side uses **standard DAP client machinery**." Research
proved there is **no** production-grade browser/Monaco DAP client. That half of the rationale
is struck. DAP is retained for the reasons that actually hold: **engine swappability**
(instrumentation ↔ JDWP fallback speak one protocol) and **future VS-Code-attach optionality**.
The PRD must carry the honest rationale, not the dead one.

---

## Resolved decisions

### R1 (O1) — Groovy pin = 4.0.29, config-swappable; probe rerun is a trivial follow-up — RESOLVED
- **Pin default Groovy = 4.0.29** (the current CPI step-2.x runtime), exposed as a **config
  knob** per D3 → SAP patch bumps become a dependency rev, not a rewrite.
- **Probe rerun**: re-run the existing 14-test spike suite pinned to 4.0.29 (one Maven property,
  minutes) as an **immediate follow-up — NOT a heavyweight PRD prerequisite**. If 14/14 stay
  green, D8-primary and D3 stand unchanged. *(Action item — see Follow-up.)*
- **JDK concern dissolves**: Groovy 4.0.x supports Java 17–25; spike already ran 4.0.32 on
  JDK 25 friction-free → **4.0.29 in-process on the Quarkus JDK 17/21 is supported**.
- **DEFERRED**: legacy tenants/iflows still on Groovy step **1.x** (old runtime) — out of scope
  for slice 1; logged as a deferred open question (multi-version engine via the config knob),
  not designed for now.

### R2 (Slices) — Shippable v0.1 run-and-inspect → v0.2 debug → v0.3+ suites — RESOLVED
- **Slice 1 = v0.1, genuinely shippable** run-and-inspect: Monaco (basic) + input panel →
  Quarkus HTTP → in-process GroovyShell (4.0.29) + clean-room Message mock → type-aware output +
  log pane + exception→line. **No breakpoints.** Built behind D2's `Engine` interface as
  `GroovyRunEngine`. Delivers standalone value (today needs a hand-rolled IntelliJ + non-
  redistributable-jar setup) and de-risks the full stack.
- **Slice 2 = v0.2, the differentiator** debug: `InstrumentedGroovyDebugEngine` (proven AST
  engine + the two carried caveats) + DAP-over-WebSocket + Monaco breakpoints/step/inspect.
  Lands *immediately* after slice 1 — honours "debugging is core, not an add-on."
- **Slice 3+**: assertions / saved suites / fixtures-as-first-class / XSLT engine.

### R3 (Transport) — Two transports matched to two interaction models — RESOLVED
- **Slice 1: plain HTTP** `POST /run` → buffered result envelope. Stateless; *is* the future
  headless CLI/CI runner (D4); cancellation = client-abort → server interrupts the worker
  (spike cooperative-cancel + hard watchdog). At a ~10s timeout, buffering logs is adequate.
- **Slice 2: DAP-over-WebSocket** — the stateful bidirectional channel DAP requires anyway.
- **DEFERRED (NEW)**: live-log-streaming-over-WS for long runs (only relevant if the timeout
  ceiling is later raised well above 10s) — addable without disturbing the DAP channel.

### R4 (O4a) — DAP as wire contract; hand-rolled React client — RESOLVED
- **Keep strict-subset DAP** as the wire vocabulary, for **swappability + VS-Code-attach**
  (not for a free client — see C2). Type the client with **`@vscode/debugprotocol@1.68.0`**
  (types-only, zero runtime deps — verified).
- **Hand-rolled frontend**: thin JSON-RPC-over-WebSocket DAP client + **bespoke React debug
  panels** (variables tree, call stack, step controls) + **Monaco glyph-margin breakpoints** +
  **delta-decoration** for the stopped line. No `monaco-debugger`, no VS Code runtime deps.
- **Backend DAP subset** (honestly-declared `capabilities`; exact list refined at PRD time from
  P1–P6): `initialize`, `setBreakpoints`, `launch`, `continue`, `next`, `stepIn`, `stepOut`,
  `stackTrace`, `scopes`, `variables`, `terminate` (+ `initialized`/`stopped`/`output`/
  `terminated` events). Carries the P3/P4 caveats explicitly:
  - **P4** → runtime **call-depth counter** for correct `next`/`stepOut` across method calls.
  - **P3** → **closure-captured variables** surfaced as a **documented variables-view
    limitation** (harvest resolved refs / shared-variable holders), *not hidden*.

### R5 (O2) — File-based fixtures from slice 1; `messages/<name>/` unit — RESOLVED
- **Input persisted as files from slice 1** (not transient) — cheap, honours D4, unlocks the
  free CLI runner. Assertions are the deferred part, not the input.
- **Canonical unit = a "message" directory**:
  ```
  messages/<name>/
    body.xml        # raw body bytes; extension hints content-type + editor rendering
    message.yaml    # headers:, properties:, contentType:   (later: attachments:)
  ```
  YAML metadata (hand-editable, comments); body a native file (diffs as its real type).
- **Script ↔ message decoupled** (`scripts/Foo.groovy` × `messages/bar/`); run = pick × pick.
  One script ↔ many messages. Rigid `Foo.groovy`+`Foo.input` sidecar coupling rejected.
- **DEFERRED (NEW)**: single-file `<name>.message.yaml` convenience form; saved **run/case
  descriptor** binding script+message+**assertions** (slice 3); archived desktop "manifest"
  model (greenfield here per D4).

### R6 (O3) — Monaco ladder: highlighting → mock-API completion → no LSP — RESOLVED
- **Slice 1**: syntax highlighting via a **Monarch Groovy grammar** (adapt Monaco's Java
  grammar / community gist) + editor essentials + glyph margin.
- **Slice 2 (or thin 1.5)**: a **static `CompletionItemProvider`** seeded from the clean-room
  Message API surface (`getBody(Class)`, `getHeaders/setHeader`, `getProperties/setProperty`,
  `messageLog.*`, `MessageLogFactory`) + CPI snippets. Cheap because we own the mock surface;
  directly teaches the CPI API.
- **Ceiling — full Groovy LSP REJECTED for now** (`groovy-language-server` + `monaco-
  languageclient@10.7.0` + `vscode-ws-jsonrpc`): a second Java process + heavy frontend stack
  vs D1's single deliverable; immature/Moonshine-oriented; wouldn't know the Message API;
  general IntelliSense isn't the product's value (debugging is). **DEFERRED** with one recorded
  option: embed the LSP **in-JVM (Quarkus) with the mock jar on its classpath** for real
  type-aware Message-API completion — only if completion ever becomes a headline need.

### R7 (O5) — Tiered classpath + user escape hatch; deferred lint — RESOLVED
- **Slice-1 classpath** = CPI-exposed **Groovy modules** (`groovy`, `groovy-xml`, `groovy-json`,
  `groovy-dateutil`, … — exact list enumerated at PRD time to mirror CPI) + JDK + the clean-room
  `Message`/`MessageLog(Factory)` mock + a **user-extensible classpath** (workspace `lib/` /
  config-declared jars via a child `ClassLoader`, safe under D3). Escape hatch: **user brings
  their own jars** for anything unmocked.
- **DEFERRED**: `com.sap.it.api.*` service stubs (ValueMapping, SecureStore, NumberRange) —
  need fixture-backed external state; added incrementally, demand-driven. Raw Camel jar —
  provided only if/when scripts need it.
- **NEW / resolves R5 sub-thread**: a **workspace-root config file** (e.g. `iflowlab.yaml`)
  carries `groovyVersion:` (the R1 knob) + extra `libs:`. **Zero-config default works** (bundled
  modules + mock).
- **DEFERRED (parallels R6 ceiling)**: allow-list import **lint that warns, never blocks** (per
  D3 local-trust) on imports outside the known CPI-allowed set — reusing SAP's published
  upgrade-readiness/design-guideline rules.

### R8 (O6) — Concurrent-bounded runs; single debug session; unified Stop — RESOLVED
- **Runs**: concurrent but **bounded** (small bounded executor + per-run timeout + hard
  watchdog). Falls out of stateless HTTP; the bound is the only deliberate constraint.
- **Debug**: **single active session** (slice 2). New session cleanly terminates the prior;
  UI surfaces "a debug session is already active." **Multi-session DEFERRED**.
- **Cancellation**: one unified **Stop** (run → interrupt worker; debug → DAP terminate → cancel
  parked thread), both on cooperative-cancel + hard watchdog.
- **NEW (forward-compat, cheap)**: model a `sessionId` in the DAP layer from day one so a future
  multi-session upgrade isn't a retrofit.

### R9 (O7) — Attachments: API-present + in-memory in slice 1 — RESOLVED
- **Slice 1**: attachment methods present with real signatures, backed by a **real in-memory
  mutable map** → intra-run write-then-read works; defensive `getAttachments()` doesn't crash.
  **Not** fixture-seeded, **not** rendered in the output panel. Emptiness **documented** so it
  isn't mistaken for a mock bug.
- **DEFERRED**: fixture-backed attachments (`attachments:` key in `message.yaml`), `DataHandler`
  content from files, output-panel attachment inspection.

### R10 (O8) — `--workspace` flag + CWD default; localhost-only — RESOLVED
- **Slice 1**: `java -jar iflowlab.jar --workspace <dir>` (+ env/config); **default = CWD**;
  **single active workspace**; switch = restart. Same flag drives the future CLI runner.
- **Slice 1.5/2**: in-UI **server-side directory browser** + **recent-workspaces list** in
  **app-level** config (distinct from per-workspace `iflowlab.yaml`).
- **DEFERRED**: multi-workspace (several open at once) — likely-never; quick-switch covers it.
- **NEW — hard PRD security constraint**: server **binds to localhost only**, never `0.0.0.0`.
  D3 executes arbitrary code with no sandbox *and* exposes an FS browser → that surface must not
  be network-reachable. Non-negotiable default; ties to the D3 ADR.

### R11 (D5) — Three-tier oracle hierarchy; slice-1 fidelity set — RESOLVED
- **Oracle hierarchy** (read-for-behavior, clean-room implement, never adopt as dep):
  1. **Real CPI tenant = ground-truth tiebreaker** (Pavol has access) — for genuinely contested
     edge cases only.
  2. **Contract oracle** = SAP dev jar signatures + SAP Help docs (verify Maven coords at PRD).
  3. **Behavior oracles** = `com.equaliseit:sap-cpi-mocks` + SCPIScriptTest source (cross-checked
     by CPIGroovyDebugger / SAPCI_GroovySim) — *not authoritative*; confirmed on-tenant when they
     disagree or docs are silent.
- **Method**: each fidelity feature gets an **oracle-comparison test**. **License hygiene**: read
  for behavior, clean-room implement, copy no code (note licenses at PRD time).
- **Slice-1 fidelity set**: `getBody` **coercion matrix** (String/InputStream/byte[]/raw);
  **one-shot InputStream** semantics; headers (get/set + type preservation); properties
  (get/set); **in-memory attachments** (per R9); `MessageLog`/`MessageLogFactory` capture into
  the log pane.
- **DEFERRED**: `com.sap.it.api.*` services — bucketed with the R7 deferral.

### R12 (NEW) — Output classification + `/run` result envelope — RESOLVED
- **Classification = Content-Type → sniff → user override**: resulting Message `Content-Type`
  first; cheap sniff fallback (`<`→XML, `{`/`[`→JSON, valid-UTF-8→text, else binary); a UI
  dropdown to force XML/JSON/Text/Hex.
- **`/run` envelope**: `{ status, body:{contentType,size,encoding,inline?|ref?}, headersBefore,
  headersAfter, propertiesBefore, propertiesAfter, logs:[{level,source,message}],
  exception?:{type,message,mappedLine,stackTrace} }`.
- **Before/after diff**: backend snapshots headers/properties at run start (seeded input) and
  end (post-script); UI renders the diff.
- **Large-output guard**: cap inline body (first N KB inline + download link); binary → metadata
  + hex preview + download endpoint. Prevents huge payloads freezing Monaco/DOM.

---

## Remaining open questions (with defer tags)

- **OQ-A [DEFER: multi-version]** — legacy Groovy step-1.x tenant support via the version knob
  (R1). Not designed now.
- **OQ-B [DEFER: slice-3]** — saved run/case descriptor + assertion catalog format (R5).
- **OQ-C [DEFER: demand-driven]** — `com.sap.it.api.*` mock design incl. fixture-backed external
  state (value mapping / secure store / number range) (R7, R11).
- **OQ-D [DEFER: post-core]** — warn-don't-block import fidelity lint (R7); full Groovy LSP,
  incl. the in-JVM-LSP-with-mock-classpath option (R6).
- **OQ-E [DEFER: later-slice]** — fixture-backed + inspectable attachments (R9); raw Camel jar
  on the classpath (R7).
- **OQ-F [DEFER: if-timeout-raised]** — live-log-streaming-over-WS (R3).
- **OQ-G [PRD-time research, not a decision]** — verify current versions of Quarkus, Quinoa
  (prod single-jar serving maturity), React, and the SAP dev-jar Maven coordinates via Context7
  before they enter the PRD. Box has Maven 3.9.11, no Gradle (per journal) → Maven build.

---

## Follow-up

**This is the first grill of the iflowlab web workbench — it supersedes nothing.** It corrects
two points in its own Stage-0 checkpoint (C1: Groovy 4.0.29 not 2.4.21; C2: no free DAP client)
and resolves checkpoint open questions O1–O8 plus the D5 mock-oracle survey. Prior grill sessions
(`01-routing-mvp.md`, `02-desktop-workbench.md`) concern the **archived desktop/routing** line and
are unaffected.

**Action items feeding PRD authoring (Stage 2, `write-a-prd`):**
1. **[cheap, do first]** Re-run the `spike/groovy-debug` suite with Groovy pinned to **4.0.29**
   (one Maven property). Expect 14/14 green; if not, escalate — D8-primary/D3 would be at risk.
2. Enumerate the exact **CPI-exposed Groovy module list** (mirror the tenant) for the classpath.
3. Verify **SAP dev-jar Maven coordinates** and the current **Quarkus/Quinoa/React** versions
   (Context7) before any version enters the PRD (OQ-G).
4. Confirm the **licenses** of the OSS oracle mocks before reading them for behavior.

Next stage: `write-a-prd` for **slice 1 (v0.1 run-and-inspect)** as the first PRD, with slices
2/3 sketched as the roadmap.
