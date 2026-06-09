# Brainstorm Checkpoint — iflowlab

> Status: Stage 0 output (durable). Feeds Stage 1 grill-me.
> Repo: iflowmonitor/iflowlab (public). Org membership = brand halo for iFlowMonitor.
> Scope of this checkpoint: the MVP concept for a routing-XSLT test tool for the
> SAP Integration Suite pipeline concept.

## North star

iflowlab is a fast create-and-test loop for the routing XSLT used in the SAP
Integration Suite pipeline concept — receiver determination, interface determination,
and the combined variant. Open source first; lives in the iflowmonitor org for brand
visibility. A later paid/AI tier is possible but explicitly not the present goal.

Correctness anchor: fidelity to how SAP Cloud Integration actually executes routing
XSLT (engine, params, namespaces, dummy-body case). The output contract is small and
structured, which is what makes routing a high-value, automatable target.

## Guiding principles

- Routing XSLT is not general data mapping. In the pipeline concept, XSLT is used for
  routing; the output is a fixed SAP contract (ns0:Receivers / ns0:Interfaces,
  namespace http://sap.com/xi/XI/System), not an arbitrary target format.
- The tool tests the XSLT output contract, not the generic flow's runtime reaction.
- Architecture is layered so the routing MVP, future general-mapping mode, and a future
  AI tier sit on one shared execution core without a rewrite.
- Zero hard dependency on a live SAP tenant for the MVP dev loop.

## Decisions

D1 — MVP scope: routing determination only. Receiver determination, interface
determination, and combined receiver+interface determination. General data-mapping XSLT
is deferred. Output validated against the SAP Receivers/Interfaces contract.

D2 — Engine: Saxon-HE, pinned 9.9.1-x (Maven Central). Matches the tenant's Saxon-EE
9.9.1.6 behaviour for routing (routing XSLT uses only xsl:if test, dc_ params, namespaces,
version 3.0 — no EE-only features; tenant runs schema-awareness OFF anyway). Forward note:
general-mapping mode may hit XSLTs using Java calls / saxon: extensions that HE cannot run
(needs PE/EE) — out of scope now.

D3 — Three-layer architecture. (1) Execution core — run XSLT on Saxon-HE, inject dc_
params, declare namespace prefixes, capture output; shared across routing/general/AI.
(2) Assertion layer (pluggable) — routing: semantic assertions on selected
receivers/interfaces + XSD validation; general (future): golden-file / structural diff.
(3) AI layer (future, paid tier) — above the assertion layer, consumes parsed XSLT +
execution results.

D4 — Language/runtime: Kotlin/JVM core library (NOT full KMP). Saxon is JVM-only, so KMP
across non-JVM targets (iOS/native/wasm/browser) cannot carry the engine. Engine runs
locally as a library.

D5 — Surfaces, in order: CLI -> Compose Desktop -> Quarkus service. CLI test runner first
(CI regression value, the immediate employer need). Compose Desktop GUI later as a skin
over the same core. A Quarkus service wrapping the core comes later for the web/AI tier.
The engine-as-library keeps both local and server-side futures open.

D6 — XSLT-under-test source: local files first, Partner Directory pull later. Local files
= primary dev inner loop, no auth. PD pull (base64-decode via the Partner Directory API) =
"test what's deployed", deferred (depends on tenant access).

D7 — Test-input source: hand-written fixtures first. Baseline = hand-written XML fixtures
(zero deps). Later: iFlowMonitor historical-payload corpus as golden fixtures (the IFM
bridge), and AI-generated branch-coverage inputs (paid tier). Fixture model = input XML
body + set of dc_ params, including the dummy-XML-body case for non-XML, header-only
routing.

D8 — Assertion model (routing): semantic + schema. "For input X + params Y, expect
receivers [...] / interfaces [...] with endpoints", plus XSD validation against the
Receivers/Interfaces schema. The tool can assert the declared ReceiverNotDetermined Type
value but does NOT simulate the generic flow's runtime Error/Ignore/Default behaviour
(that is pipeline runtime, out of scope).

D9 — Home & packaging. Repo iflowmonitor/iflowlab, public. License: Apache-2.0
(placeholder, see O3). Tagline: "Fast routing-XSLT testing for the SAP Integration Suite
pipeline concept."

D10 — Execution-context fidelity (correctness-critical). Inject dc_ params; let the user
declare namespace prefix mappings (CPI requires the namespace mapping in the runtime
config); handle the dummy-XML-body case for non-XML, header-only routing.

## Rejected alternatives

- Saxon-EE for the MVP — routing doesn't need EE; HE matches and is free. Revisit only for
  general-mapping mode.
- Full Kotlin Multiplatform (iOS/native/wasm/browser) — Saxon is JVM-only; no XSLT engine
  in those targets, so the core feature can't follow. Compose Desktop (JVM) is fine; reach
  beyond JVM is not.
- Engine server-side as the MVP shape — local CLI/desktop is simpler and offline for the
  dev/CI loop; engine-as-library keeps the Quarkus-service option open for later.
- Standalone product brand (e.g. xforge) — OSS-dev-tool framing + IFM brand halo preferred
  over a product brand for now.
- Generic-XSLT framing / name xslt-lab — (a) the identity should be tied to Integration
  Suite, not generic XSLT; (b) xslt-lab collides with an existing project (alexandrev/xslt-lab).
- Name pipelab — crowded (multiple existing projects).

## Open questions (deferred)

- O1 — Test-definition format: YAML manifest vs Kotlin DSL vs both. (Primary grill-me topic.)
- O2 — Where it lives / entity / IP: OSS in iflowmonitor org now; future paid tier and
  s.r.o./IP assignment deferred — not a build decision today.
- O3 — Final license: Apache-2.0 placeholder; confirm Apache-2.0 vs MIT given the possible
  future paid tier.
- O4 — dc_ injection + namespace-declaration UX (CPI fidelity) — likely a grill-me focus.
- O5 — Combined receiver+interface representation in the assertion model (nested Interfaces
  per Receiver).
- O6 — Receiver-not-determined semantics beyond asserting the declared Type — how far to go.
- O7 — PD-pull auth model (OAuth) and base64 decode — deferred until tenant access (D6 phase 2).

## Workflow note (surfaced deviation)

WORKFLOW.md Stage 1 places grill-me artefacts in iflowmonitor-platform. iflowlab is a
separate public repo, so its BRAINSTORM-CHECKPOINT.md, grill-me-sessions/, prd/, and plans/
live in iflowlab to keep the public repo self-contained. Same stage shapes, different home.
The #1 differentiator framing in WORKFLOW.md is iFlowMonitor's; iflowlab's connection to it
is the payload-corpus-as-fixtures bridge (D7), not a shared product requirement.

## Readiness for grill-me

The decision tree below the open questions is concrete enough to start a focused grill-me
session. The grill should drive O1, O4, O5, O6 to explicit recommendations and confirm O3;
O2, O7 stay deferred (not build-blocking).
