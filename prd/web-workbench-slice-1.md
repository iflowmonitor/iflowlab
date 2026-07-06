# PRD — iflowlab Web Workbench, Slice 1 (v0.1): Interactive Run-and-Inspect

> Stage 2 (`write-a-prd`). Grounded in `grill-me-sessions/01-web-workbench.md` (R1–R12) and
> `BRAINSTORM-CHECKPOINT-web-workbench.md` (D1–D9). First shippable slice.
> Slices 2 (debug) and 3+ (assertions/suites) are roadmap, not this PRD.

## Problem Statement

An SAP Integration Suite developer writing a CPI **Groovy script** has no fast, faithful,
offline way to run it and see what it does. Today they either deploy to a tenant and inspect
via MPL/trace (slow, shared, network round-trips), or hand-assemble a local harness in IntelliJ
with a non-redistributable SAP jar and a hand-written `Message` mock — a setup that is fiddly,
undocumented, per-developer, and drifts from the real runtime. There is no tool that says:
*here is my script, here is a sample message, show me the output, the changed headers/properties,
the logs, and — if it blew up — which line.*

## Solution

A standalone local tool — `java -jar iflowlab.jar` — that opens a browser workbench where the
developer:

1. Edits a Groovy script in a Monaco editor (Groovy syntax highlighting).
2. Supplies an input **Message** — body (paste or file), headers, properties.
3. Clicks **Run**.
4. Sees a **type-aware output panel**: pretty-printed XML/JSON, raw text, or binary
   (content-type + size + hex preview + download); a **before/after diff** of headers and
   properties; a **log pane** (`println` + `messageLog`); and on failure, the **exception mapped
   to the script line**.

The script runs **in-process** against a **clean-room mock of the CPI `Message` API** pinned to
the tenant's Groovy runtime (**4.0.29**), so behavior faithfully mirrors CPI. The workspace is a
user-pointed directory; scripts and message fixtures live as **files** (git-friendly), which also
makes a future headless CLI/CI runner essentially free. No breakpoints in this slice — that is
Slice 2.

## User Stories

1. As a CPI developer, I want to launch the workbench with `java -jar iflowlab.jar --workspace <dir>`, so that I can work against my own iflow project directory.
2. As a CPI developer, I want the workspace to default to the current directory when I omit `--workspace`, so that I can start with zero config.
3. As a CPI developer, I want the tool to bind to localhost only, so that my machine's script-execution surface is never network-reachable.
4. As a CPI developer, I want to open a `.groovy` script from my workspace in the editor, so that I can run the exact script from my iflow.
5. As a CPI developer, I want Groovy syntax highlighting in the editor, so that the script is readable.
6. As a CPI developer, I want to paste or upload a message body, so that I can feed my script real payloads.
7. As a CPI developer, I want to set headers and properties as key/value pairs, so that I reproduce the message context my script reads.
8. As a CPI developer, I want to save an input as a reusable `messages/<name>/` fixture (body file + `message.yaml`), so that the run is reproducible and diffable in git.
9. As a CPI developer, I want to pick a script and a message independently and run them together, so that I can test one script against many payloads.
10. As a CPI developer, I want to click Run and get the result quickly, so that my edit→run→inspect loop is tight.
11. As a CPI developer, I want XML output pretty-printed, so that I can read the structure.
12. As a CPI developer, I want JSON output pretty-printed, so that I can read the structure.
13. As a CPI developer, I want plain-text output shown raw, so that I see exactly what my script emitted.
14. As a CPI developer, I want binary output shown as content-type + size + hex preview with a download, so that I can inspect zipped/binary payloads without corrupting them.
15. As a CPI developer, I want to override the detected output type (XML/JSON/Text/Hex), so that I can force the right rendering when detection guesses wrong.
16. As a CPI developer, I want a before/after diff of headers, so that I can see what my script changed.
17. As a CPI developer, I want a before/after diff of properties, so that I can see what my script changed.
18. As a CPI developer, I want `println` and `messageLog` output captured in a log pane, so that I can trace what my script did.
19. As a CPI developer, I want an uncaught exception shown with its message and mapped to the offending script line, so that I can fix it fast.
20. As a CPI developer, I want `message.getBody(String.class)` / `getBody(InputStream.class)` / `getBody(byte[].class)` / raw `getBody()` to coerce exactly as CPI does, so that my body handling behaves the same locally.
21. As a CPI developer, I want `InputStream` bodies to be one-shot exactly as in CPI, so that I catch double-read bugs locally instead of in production.
22. As a CPI developer, I want `getHeaders`/`setHeader` and `getProperties`/`setProperty` to preserve types as CPI does, so that my header/property logic is faithful.
23. As a CPI developer, I want `getAttachments()` present and an intra-run write-then-read to work, so that scripts touching attachments don't crash (even though fixture-seeded attachments come later).
24. As a CPI developer, I want to use `groovy.xml.XmlSlurper`/`XmlParser` and `groovy.json.JsonSlurper`/`JsonOutput`, so that standard CPI parsing works.
25. As a CPI developer, I want to add my own jars to the run classpath via the workspace, so that scripts needing libraries the tool doesn't mock still run.
26. As a CPI developer, I want a configurable run timeout (default ~10s) enforced, so that an accidental infinite loop doesn't hang the tool.
27. As a CPI developer, I want to cancel an in-flight run, so that I'm not stuck waiting on a slow or looping script.
28. As a CPI developer, I want the tool packaged as a single runnable jar, so that installation is trivial.
29. As a CPI developer, I want an honest note that this executes scripts with my own privileges (no sandbox), so that I understand the trust model.
30. As a CPI developer, I want the workspace config (`iflowlab.yaml`) to let me pin the Groovy version and declare extra libs, so that I can match a specific tenant patch level.

## Implementation Decisions

### Build & structure
- **Maven multi-module reactor** at the repo root (per developer decision). Modules: `cpi-mock`,
  `engine`, `app`.
- **Java** for `cpi-mock`, `engine`, `app`; Quarkus-idiomatic and matches the D9 spike.
- The legacy Kotlin/Gradle **routing engine** moves to `legacy/routing/` (self-contained, `git mv`,
  preserved for D2's future XSLT engine) — out of scope here.
- **Quarkus** (latest 3.x at scaffold; ≥3.36) + **Quinoa 2.8.3** serve the React SPA and bundle it
  into the jar → one `java -jar iflowlab.jar` deliverable (D1).
- Frontend: **React + Vite + Monaco** (`@monaco-editor/react`), built by Quinoa.

### Deep modules (the D2 engine seam is real)
- **`cpi-mock`** — clean-room `com.sap.gateway.ip.core.customdev.util.Message` +
  `MessageLog`/`MessageLogFactory`. Encapsulates the `getBody` coercion matrix, one-shot
  `InputStream`, header/property type preservation, in-memory attachments, log capture. **No
  framework dependency.** Pure, isolated-testable against oracles (real tenant tiebreaker >
  SAP dev jar/docs > OSS mocks; clean-room, copy no code).
- **`engine`** — the engine-agnostic contract `Engine.run(RunRequest) → RunResult` (D2). First
  implementation `GroovyRunEngine`: assemble classpath (CPI Groovy modules + `cpi-mock` +
  workspace `lib/` jars via a child `ClassLoader`), compile+execute via `GroovyShell` (4.0.29) on
  a worker thread, enforce timeout + hard watchdog, support cooperative cancel, map exceptions to
  the script line, and classify output via a pure `BodyTypeClassifier` (Content-Type → sniff).
  Depends on `cpi-mock`. **No Quarkus dependency** — headless-testable. Slice 2's debug engine
  will implement the same `Engine` contract.
- **`app`** — Quarkus + Quinoa. `POST /run` maps a `RunRequest` DTO to the engine and returns the
  `RunResult` envelope; reads scripts/messages from the workspace; **binds to localhost only**;
  serves the SPA.

### API contract — `POST /run`
- Request: `{ script, body, contentType?, headers{}, properties{}, timeoutMs? }` (script and
  message may also be referenced by workspace-relative path).
- Response envelope:
  `{ status: ok | exception,
     body: { contentType, size, encoding, inline? | ref? },
     headersBefore, headersAfter, propertiesBefore, propertiesAfter,
     logs: [ { level, source: println|messageLog, message } ],
     exception?: { type, message, mappedLine, stackTrace } }`
- **Before/after**: engine snapshots headers/properties at run start (seeded input) and end.
- **Large-output guard**: inline body capped (first N KB) + download `ref` for the remainder;
  binary always metadata + hex preview + download.
- **Cancellation**: client aborts the request → server interrupts the worker (cooperative cancel
  + hard watchdog for the empty-`while(true){}` case).

### Workspace / fixtures (D4, R5)
- Workspace = user-pointed dir; filesystem is source of truth.
- Message fixture unit = `messages/<name>/` = a native `body.<ext>` + `message.yaml`
  (`headers:`, `properties:`, `contentType:`). Script and message decoupled.
- Optional `iflowlab.yaml` at workspace root: `groovyVersion:` (config knob, default 4.0.29) and
  `libs:` (extra classpath jars). Zero-config works.

### Execution model (D3)
- In-process `GroovyShell`, **local-trust, no sandbox** — documented in an ADR + a first-run
  notice. Configurable timeout (default ~10s) via executor interruption + hard watchdog.
- Concurrency: runs are **concurrent but bounded** (small bounded executor). (Single-session debug
  is Slice 2.)

## Testing Decisions

- **Test external behavior, not implementation.** Assert observable outcomes (returned bodies,
  coerced types, header/property snapshots, logs, mapped exception line, timeout/cancel), never
  private structure.
- **`cpi-mock`** — unit tests: `getBody` coercion matrix, one-shot `InputStream`, header/property
  type preservation, in-memory attachment round-trip, log capture. Each behavior is an
  **oracle-comparison** test; contested cases resolved on a real tenant. *Prior art:* the D9 spike
  JUnit-5 tests; existing Kotlin gate tests.
- **`engine`** — headless run tests: script + `RunRequest` → assert `RunResult` (output+type,
  headers/props after, logs, exception+`mappedLine`, timeout, cancel). *Prior art:* spike P1–P6.
- **`BodyTypeClassifier`** — pure unit tests over the Content-Type→sniff matrix.
- **`app`** — one `@QuarkusTest` on `POST /run`: happy path, exception path, timeout path.
- **Frontend** — manual E2E via `/verify` for Slice 1 (no automated FE tests this slice).

## Out of Scope

- **Debugging** (breakpoints/step/inspect, DAP-over-WebSocket, Monaco decorations) — Slice 2.
- **Assertions / saved test suites / run-case descriptors** — Slice 3+.
- **XSLT/routing engine** integration under the `Engine` contract — later (legacy code parked).
- **`com.sap.it.api.*`** services (ValueMapping, SecureStore, NumberRange) and raw Camel jar —
  deferred, demand-driven.
- **Fixture-seeded attachments** + output-panel attachment rendering — later.
- **Full Groovy LSP / completion** — Slice 2+ (mock-API static completion); no LSP.
- **Import fidelity lint** (warn-don't-block) — later.
- **In-UI workspace picker / multi-workspace / recent list** — Slice 1.5/2 (Slice 1 = flag + CWD).
- **Live-log-streaming over WebSocket** — only if the timeout ceiling is later raised.
- **Legacy Groovy step-1.x tenant support** — deferred via the version knob.
- **Hosted/multi-tenant execution, auth/SSO** — explicitly out (new ADR if ever revisited).

## Further Notes

- **Corrections carried from the grill:** CPI current Groovy runtime is **4.0.29** (Groovy Script
  step 2.x), not 2.4.21 (pre-Camel-3 snapshot). There is **no** off-the-shelf browser DAP client
  (relevant to Slice 2, not this slice).
- **Pre-build action items (from the grill Follow-up):** rerun the D9 spike suite pinned to 4.0.29
  (one Maven property — expect 14/14) as an early confidence check; enumerate the exact
  CPI-exposed Groovy module list for the classpath; verify Quarkus/Quinoa/React + SAP dev-jar
  Maven coordinates at scaffold; check OSS-oracle licenses before reading them for behavior.
- **Provenance:** Groovy 4.0.29 (SAP Community upgrade-readiness docs, 2026); Quinoa 2.8.3 /
  Quarkus ≥3.36 (Quarkiverse, 2026-06); `groovy-xml`/`groovy-json` separate modules (Context7
  `/apache/groovy`). All version claims verified via Context7/web, recorded in the transcript.
