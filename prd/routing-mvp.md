# PRD — Routing MVP (iflowlab)

> Stage 2 (write-a-prd) of the iFlowMonitor spec workflow for **iflowlab**.
> Authoritative input: `grill-me-sessions/01-routing-mvp.md` (Stage 1, committed f6a77b5),
> including its Follow-up section. Background: `BRAINSTORM-CHECKPOINT.md` (Stage 0).
> Where the two differ, the Stage-1 transcript's Follow-up supersedes the checkpoint.
> This PRD is intended to read standalone.

---

## Problem Statement

SAP/XSLT developers building **routing XSLT** for the SAP Integration Suite pipeline concept
(receiver determination, interface determination, and the combined receiver+interface
variant) have no fast, offline, repeatable way to test that XSLT before it is deployed to a
tenant. The routing XSLT emits a small, fixed, structured SAP contract
(`ns0:Receivers` / `ns0:Interfaces`, namespace `http://sap.com/xi/XI/System`), yet today the
only way to know whether a stylesheet routes correctly is to deploy it and observe runtime
behaviour. That loop is slow, requires live tenant access, and conflates "did the XSLT emit
the right selection?" with "how did the pipeline react to that selection?".

The author needs to assert, locally and in CI: *for input XML body X plus `dc_` parameters Y,
this stylesheet selects exactly these receivers / interfaces, with these endpoints, and emits
output that conforms to the official SAP routing schema* — with **fidelity to how SAP Cloud
Integration actually executes routing XSLT** (engine, params, namespaces, the header-only
dummy-body case).

## Solution Sketch

A Kotlin/JVM library with a **CLI test runner** as its first surface, driven by a **YAML test
manifest**. The tool:

1. Loads a routing stylesheet from a local file.
2. Runs it on **Saxon-HE 9.9.1-x** (matching the tenant's Saxon-EE 9.9.1.6 routing behaviour),
   injecting `dc_` parameters as string-typed Saxon stylesheet params and registering declared
   namespace prefix→URI mappings.
3. For each test case, feeds the declared input body (inline `body:`, fixture `bodyFile:`, or —
   when neither is present — the canonical CPI header-only **dummy body**), captures the emitted
   routing XML.
4. Applies **two independent pass gates**: (1) the emitted XML is schema-valid against the
   official SAP routing XSD for the declared mode; (2) the selected receivers/interfaces match
   the expected block **exactly** (strict, order-insensitive set equality).
5. Reports per-case pass/fail with a non-zero process exit code on any failure, suitable for CI.

The architecture (per Stage-0 D3/D4) layers a shared **execution core** (Saxon run + param/
namespace injection + output capture) beneath a pluggable **routing assertion layer** (semantic
selection match + XSD validation), leaving room for a future general-mapping mode and a future
paid AI tier without a rewrite. This PRD covers the routing MVP only.

## User Stories

1. As a routing-XSLT developer, I want to describe a test as a YAML manifest, so that my tests
   diff cleanly in PRs and I author them in the same format my future tooling will emit. *(AC1)*
2. As a routing-XSLT developer, I want to bind a stylesheet at the suite level with an optional
   per-test override, so that the common inner loop (one stylesheet, many inputs) is declared
   once. *(AC2, AC13)*
3. As a routing-XSLT developer, I want to declare the determination `mode` explicitly
   (receiver / interface / combined), so that the tool never silently guesses the wrong contract
   from the shape of my expectations. *(AC3, AC14)*
4. As a routing-XSLT developer, I want to inject `dc_` parameters by their exact stylesheet
   parameter name, so that values bind to the real Saxon params without the tool inventing or
   prefixing names. *(AC4)*
5. As a routing-XSLT developer, I want `dc_` values coerced to strings, so that the test matches
   the CPI runtime boundary where headers/properties arrive as strings. *(AC5)*
6. As a routing-XSLT developer, I want to declare namespace prefix→URI mappings at suite level
   with optional per-case override, so that my payload namespaces resolve for both the XSLT input
   XPath and the assertion layer. *(AC6, AC15)*
7. As a routing-XSLT developer, I want `ns0 → http://sap.com/xi/XI/System` pre-registered by
   default, so that I only declare my own payload namespaces and the SAP system namespace always
   resolves. *(AC7)*
8. As a routing-XSLT developer testing header-only routing, I want a case that declares neither
   `body:` nor `bodyFile:` to be treated as header-only, so that I express the dummy-body case by
   omission rather than boilerplate. *(AC8)*
9. As a routing-XSLT developer, I want the runner to explicitly label header-only cases as
   "header-only (dummy body)" in its output, so that the dummy-body substitution is visible and
   not silent. *(AC9)*
10. As a routing-XSLT developer, I want the injected dummy body to be byte-for-byte the exact CPI
    dummy `<dummy></dummy>`, so that my tests have true runtime fidelity. *(AC10, AC11)*
11. As a routing-XSLT developer, I want to assert the selected receivers using ergonomic YAML keys
    mapped 1:1 to the wire contract, so that I am not exposed to the overloaded `<Service>` element
    name. *(AC12, AC16)*
12. As a routing-XSLT developer in combined mode, I want each receiver to carry a nested
    `interfaces` list, so that my assertions mirror the SAP combined-determination wire structure.
    *(AC16, AC17)*
13. As a routing-XSLT developer, I want interface identity anchored on `endpoint` (not `index`),
    so that my assertions are not fragile against the optional `Index` element. *(AC18)*
14. As a routing-XSLT developer, I want receiver/interface matching to be strict exact-set,
    order-insensitive, so that an extra OR missing receiver/interface fails the test. *(AC17, AC19)*
15. As a routing-XSLT developer, I want the emitted XML validated against the official SAP routing
    XSD as an independent gate, so that malformed output is caught separately from wrong routing.
    *(AC20)*
16. As a routing-XSLT developer, I want to assert the static `ReceiverNotDetermined` block
    (`type` plus a full-tuple `defaultReceiver`) when I declare it, so that I can test
    not-determined output without the tool simulating runtime reaction. *(AC21, AC22)*
17. As a routing-XSLT developer, I want the zero-receivers case to be a first-class expected
    outcome, so that I can write negative-routing tests (expect no receivers selected). *(AC23)*
18. As a routing-XSLT developer, I want `mode=receiver` to FAIL if the stylesheet emits nested
    `<Interfaces>`, so that mode is enforced as a shape-consistency check the XSD gate alone cannot
    provide. *(AC24)*
19. As a routing-XSLT developer running interface-only mode before the Interfaces XSD is sourced,
    I want the run to apply the assertion gate and emit an explicit "XSD gate pending (O9)"
    warning, so that the XSD gate is never silently skipped. *(AC25)*
20. As a CI maintainer, I want the runner to exit non-zero when any case fails and zero when all
    pass, so that routing regressions break the build. *(AC26)*
21. As a routing-XSLT developer, I want `xslt`, `bodyFile`, and namespace/fixture paths resolved
    relative to the manifest file's directory, so that my test suites are portable. *(AC27)*
22. As an open-source consumer, I want the project licensed under Apache-2.0 with a NOTICE
    convention, so that the patent grant and enterprise palatability are clear. *(AC28)*

## Implementation Decisions

Decisions are taken **verbatim** from the Stage-1 transcript where the grill resolved them.
Decision IDs below reuse the transcript's branch tags.

### D1 — Test-definition format: YAML manifest (O1)
YAML manifest is the **single MVP test-definition format**. It fits the actual authors
(SAP/XSLT developers, not Kotlin developers), fits the CLI-in-CI first surface, diffs cleanly in
PRs, and is the natural emission target for the future AI tier. A Kotlin DSL is **NOT** built for
MVP; the engine remains a Kotlin library so a thin DSL builder can be added later (O1a, deferred).

### D2 — `dc_` param expression (O4a)
A flat `params:` map keyed by the **exact** stylesheet parameter name (e.g. `dc_Receiver`). No
auto-prefixing — the tool binds values to Saxon stylesheet params by literal name and never
invents names. The `dc_` convention is documented only.

```yaml
params:
  dc_Receiver: "BANK_A"
  dc_Priority: "HIGH"
```

### D2' — `dc_` value typing (O4a')
`dc_` values are **string-typed** to match the CPI runtime boundary (headers/properties arrive
as strings). The tool coerces/validates `params:` values to string rather than passing
YAML-typed int/bool to Saxon.

### D3 — Namespace declaration mechanism + scope (O4b)
A flat `namespaces:` map (prefix → URI).
- `ns0 → http://sap.com/xi/XI/System` is **built-in / pre-registered** by default, overridable
  if a stylesheet emits a different prefix. Authors declare only their own payload namespaces.
- **Scope:** suite-level default with optional **per-case override** (per-case map merges over
  suite-level).
- Consumers: (a) the assertion-layer XPath/XSD against the namespaced output (always needs `ns0`),
  and (b) runtime parity for prefixed XPath the XSLT uses on the input.

```yaml
namespaces:          # suite-level; ns0 is implicit/pre-registered
  p1: "urn:bank:payments:v1"
tests:
  - name: ...
    namespaces:      # optional per-case override, merged over suite-level
      p1: "urn:bank:payments:v2"
```

### D4 — Dummy-XML-body signalling (O4c)
**Omission = dummy body.** A case that declares neither `body:` nor `bodyFile:` is treated as
header-only; the tool injects the canonical CPI dummy and the runner **explicitly labels that
case as "header-only (dummy body)"** in its output. `body:` (inline XML) / `bodyFile:` (fixture
path) express a real XML body. No required `body: dummy` boilerplate — visibility comes from
runner output, not ceremony.

### D5 — Exact CPI dummy body bytes (O8)
CPI's dummy body is exactly `<dummy></dummy>` — root element `dummy`, **no namespace**,
**expanded form** (NOT self-closing `<dummy/>`), **no XML prolog**. Pin as the canonical dummy:
a single, documented, hard-coded constant, injected **byte-for-byte** for header-only cases. The
PRD hardens these bytes now (no placeholder / TODO caveat). A fidelity **drift-guard unit test**
asserts the injected dummy equals `<dummy></dummy>`, so any future drift in the constant is
caught.

### D6 — Assertion representation shape (O5a, nested = option A)
The assertion STRUCTURE **mirrors the SAP wire contract** (non-negotiable). The combined
determination output is ONE `ns0:Receivers` document with an `ns0:Interfaces` block nested inside
each `ns0:Receiver` (the documented SAP combined-determination output). Wire structure
(namespace `http://sap.com/xi/XI/System`):

```xml
<ns0:Receivers xmlns:ns0="http://sap.com/xi/XI/System">
  <ReceiverNotDetermined>
    <Type>Error</Type>             <!-- free-form string; Error | Ignore | Default by convention -->
    <DefaultReceiver/>             <!-- a full ReceiverX when Type=Default -->
  </ReceiverNotDetermined>
  <Receiver>
    <Service>Receiver_1</Service>  <!-- receiver SYSTEM NAME -->
    <Interfaces>
      <Interface>
        <Index>1</Index>
        <Service>/pip/07/scn1/rcv1/ifidx1</Service>  <!-- ProcessDirect ENDPOINT -->
        <Name>Interface_1</Name>                      <!-- OPTIONAL interface name -->
      </Interface>
    </Interfaces>
  </Receiver>
</ns0:Receivers>
```

**CRITICAL footgun — `<Service>` is overloaded:** at Receiver level it is the receiver system
name; at Interface level it is the ProcessDirect endpoint. The YAML must NOT mirror that element
name literally.

### D7 — Ergonomic assertion keys, mapped 1:1 to wire (O5c, corrected by official Receivers.xsd)
YAML keys are ergonomic, mapped 1:1 to wire elements (precisely to defuse the `Service` overload).
The fidelity anchor is XSD validation, NOT the YAML naming. Corrected 1:1 map (authoritative,
supersedes the pre-XSD pins):

| YAML key                         | Wire element                                            |
|----------------------------------|---------------------------------------------------------|
| `receiver.name`                  | `Receiver/Service`                                      |
| `receiver.party`                 | `Receiver/Party` (+ `@agency`, `@scheme`)               |
| `interface.index` (opt, string)  | `Interface/Index`                                       |
| `interface.endpoint`             | `Interface/Service`                                     |
| `interface.name` (opt)           | `Interface/Name`                                        |
| `notDetermined.type`             | `ReceiverNotDetermined/Type` (free-form string)         |
| `notDetermined.defaultReceiver`  | `ReceiverNotDetermined/DefaultReceiver` (full receiver) |

**Schema facts the tool must honour (must never be stricter than the XSD):**
- `ReceiverNotDetermined/Type` is `xsd:string`, **NOT a restricted enum**. Do not validate Type
  as an enum.
- `DefaultReceiver` is a **full `ReceiverX`** (optional) — not an empty element, not a bare name.
- `ReceiverX`: `Party` (optional, string maxLength 60, attrs `agency`/`scheme` maxLength 120);
  `Service` (string maxLength 60, receiver SYSTEM NAME, **required**); `Interfaces` (optional,
  nested).
- `Interface`: `Index` (`xsd:string`, **OPTIONAL** — may be absent; do NOT require or coerce to
  int); `Service` (`xsd:string`, **required**, ProcessDirect ENDPOINT); `Name` (`xsd:string`,
  OPTIONAL).

**Unified assertion model:** a `receivers` list where each receiver MAY carry a nested
`interfaces` list (combined mode), plus a standalone `interfaces` assertion for interface-only
mode.

### D8 — Matching semantics: strict exact-set (O5b)
**Strict exact-set matching, NO subset mode in the MVP**, reconciled to the XSD:
- **Receivers:** exact set equality, **order-insensitive**. Identity = `Service` name, or
  **(Party + Service)** when Party is present. Full receiver tuple compared (name + optional
  `party{value,agency,scheme}` + nested interfaces). Extra OR missing receiver fails.
- **Interfaces (per receiver):** exact set equality, order-insensitive, on **full tuples**
  `{index?, endpoint, name?}`. **Do NOT key solely on Index** — anchor identity on `endpoint`
  (`Interface/Service`, always required); assert the `index` VALUE as a field when present; if
  actual omits Index, the expected tuple must omit it too. **Index is an asserted value, never a
  position.** Extra OR missing interface fails.
- **notDetermined:** asserted only when present in the expected block. `type` = exact string
  compare (free-form, not enum). `defaultReceiver` = a full receiver tuple, matched like any
  receiver.

**Two independent pass gates, both must pass:**
1. emitted XML is **schema-valid** against the routing XSD (catches malformed output);
2. **selection matches** the expected block exactly (catches wrong routing).

(O5b' — opt-in match relaxation — is deferred.)

### D9 — Receiver-not-determined semantics (O6; corrects checkpoint D8)
**Principle:** assert what the XSLT statically emits inside `<ReceiverNotDetermined>`; never
simulate how the pipeline reacts to it.
- **IN scope (static output only):** `notDetermined.type` (free-form string compare, asserted when
  declared); `notDetermined.defaultReceiver` (a full receiver tuple, asserted when declared,
  matched with the same exact-tuple rules as any receiver); the **zero-receivers case** (expecting
  no `<Receiver>` elements, optionally with a `notDetermined` block) as a first-class expected
  outcome — the empty-set special case of exact-set matching (empty expected receiver set ⇒ any
  actual receiver fails). Important negative-routing category.
- **OUT of scope (hard line):** any simulation of the runtime reaction — raising the real error,
  ignoring/dropping the message, runtime-dispatching to the default receiver.
- **No stricter-than-XSD rules:** do NOT enforce "Type=Default ⇒ DefaultReceiver present"
  (DefaultReceiver is optional). Type-not-in-{Error,Ignore,Default} warnings are deferred, not MVP.
- **Correction to checkpoint D8:** the static not-determined assertion extends from "Type only" to
  "**Type + DefaultReceiver (full tuple)**" (the official Receivers.xsd shows `DefaultReceiver` is
  a structured `ReceiverX`). Runtime-reaction simulation stays out.

### D10 — Manifest top-level: explicit mode + XSLT binding (O10)
Explicit `mode` declaration (suite default + per-test override) and suite-level `xslt:` binding
(per-test override allowed). Inferring mode from expect-block shape is the silent-false-pass trap;
explicit mode is mandatory.

`mode` drives **three** things:
(a) root document + XSD gate; (b) assertion shape (receiver: no interfaces; combined: nested
interfaces; interface: flat interfaces); (c) a **shape-consistency check on ACTUAL output** — the
emitted structure must match the declared mode. Payoff: `mode=receiver` must FAIL if the XSLT
emits nested `<Interfaces>` (Receivers.xsd allows them optionally, so the XSD gate alone won't
catch it).

| mode      | root            | XSD gate                                                   |
|-----------|-----------------|------------------------------------------------------------|
| receiver  | `ns0:Receivers` | `Receivers.xsd`                                            |
| combined  | `ns0:Receivers` | `Receivers.xsd` (same root + schema as receiver; mode disambiguates assertion shape + consistency check) |
| interface | `ns0:Interfaces`| the O9 Interfaces XSD (still to source — see Prerequisites) |

- **interface mode — manifest support NOW, XSD gate blocked on O9.** Until O9 lands, run
  interface-mode tests with the **assertion gate only** and emit an explicit
  **"XSD gate pending (O9)"** warning — never silently skip the gate.
- `mode` and `xslt` **co-vary** — a stylesheet has a fixed mode, so a per-test `mode` override is
  only meaningful when paired with a per-test `xslt` override. The common inner loop sets both once
  at suite level.
- **Path resolution:** `xslt` (and `namespaces`/`bodyFile`) paths resolve **relative to the
  manifest file's directory** → portable suites.

Confirmed manifest shape:

```yaml
xslt: ./routing/receiver-determination.xslt   # suite-level; per-test override (pair w/ mode override)
mode: combined                                # receiver | interface | combined
namespaces: { ... }
tests:
  - name: ...
    params: { ... }
    expect:
      receivers: [ ... ]
```

### D11 — Engine (Stage-0 D2)
Saxon-HE, pinned **9.9.1-x** (Maven Central). Matches the tenant's Saxon-EE 9.9.1.6 behaviour for
routing (routing XSLT uses only `xsl:if` test, `dc_` params, namespaces, version 3.0 — no EE-only
features; tenant runs schema-awareness off). General-mapping XSLTs needing Java calls / `saxon:`
extensions are out of scope.

### D12 — License (O3; locks checkpoint D9)
**Apache-2.0**, "placeholder" caveat dropped. Rationale: paid tier is license-neutral, so the real
axis is patents + enterprise palatability; for a SAP-enterprise audience under an iFlowMonitor
brand halo, Apache-2.0's explicit **patent grant + retaliation clause + NOTICE convention** wins.
Copyleft/source-available (AGPL/BSL) **consciously excluded** (not deferred). LICENSE copyright
holder = **natural person now** (entity doesn't exist yet; the repo's `[your name]` placeholder is
filled with the natural person). Apache-2.0 + DCO sign-off is the forward model if outside
contributions are ever accepted; **no CLA** for a solo project (deferred).

## Acceptance Criteria

Each criterion is externally observable (a CLI exit code, a runner-output line, an XSD pass/fail,
or a matched/failed assertion).

1. **AC1** — Given a valid YAML manifest, the CLI runner parses it and runs each `tests:` entry;
   a manifest in any non-YAML format is rejected with a non-zero exit code. *(Story 1)*
2. **AC2** — A suite-level `xslt:` binding is applied to every test that does not override it;
   runner output names the stylesheet used per case. *(Story 2)*
3. **AC3** — A manifest with no `mode` (neither suite-level nor per-test for a case) fails with a
   non-zero exit code and an explicit "mode required" message; mode is never inferred. *(Story 3)*
4. **AC4** — A `params:` entry keyed `dc_Receiver: "BANK_A"` binds to the Saxon stylesheet
   parameter literally named `dc_Receiver`; no prefix is added and no name is invented (verified by
   a stylesheet that echoes the param into output). *(Story 4)*
5. **AC5** — A `params:` value given as a YAML int/bool (e.g. `dc_Priority: 1`) is passed to Saxon
   as the string `"1"` (verified by a stylesheet that emits the param value). *(Story 5)*
6. **AC6** — A per-case `namespaces:` map overrides the suite-level mapping for the same prefix
   (merged over suite-level), observable by an assertion that resolves the per-case URI. *(Story 6)*
7. **AC7** — With no `namespaces:` declared, an assertion using prefix `ns0` resolves
   `http://sap.com/xi/XI/System` and passes against namespaced output. *(Story 7)*
8. **AC8** — A test case declaring neither `body:` nor `bodyFile:` runs successfully against a
   header-only stylesheet (does not error for missing input). *(Story 8)*
9. **AC9** — For such a case the runner output contains the literal label `header-only (dummy
   body)`. *(Story 9)*
10. **AC10** — The drift-guard unit test asserts the injected dummy equals the exact bytes
    `<dummy></dummy>` (expanded form, no namespace, no XML prolog) and passes. *(Story 10)*
11. **AC11** — The drift-guard unit test FAILS if the constant is changed to the self-closing form
    `<dummy/>` or gains an XML prolog. *(Story 10)*
12. **AC12** — A receiver assertion authored with key `name:` matches the wire element
    `Receiver/Service`; no manifest key named `Service` is required or accepted at receiver level.
    *(Story 11)*
13. **AC13** — A per-test `xslt:` override replaces the suite-level binding for that case only;
    runner output reflects the overriding stylesheet. *(Story 2)*
14. **AC14** — A per-test `mode:` override applies to that case only and is honoured for root/XSD/
    shape selection. *(Story 3)*
15. **AC15** — A suite-level `namespaces:` prefix not overridden per-case remains in effect for
    that case. *(Story 6)*
16. **AC16** — In `combined` mode, a receiver assertion carrying a nested `interfaces:` list
    matches `Receiver/Interfaces/Interface` tuples; the same nested list in `receiver` mode is a
    shape mismatch (see AC24). *(Stories 11, 12)*
17. **AC17** — Receiver matching is order-insensitive: a manifest listing receivers in a different
    order than the emitted output passes when the sets are equal, and fails when an extra OR missing
    receiver is present. *(Stories 12, 14)*
18. **AC18** — An interface assertion with `endpoint:` but no `index:` matches an emitted
    `Interface` that has `Service` and omits `Index`; identity is anchored on `endpoint`, not
    `index`. *(Story 13)*
19. **AC19** — When an expected interface tuple omits `index` but the actual emits an `Index`, or
    vice versa, the case fails (index is an asserted value, not a position). *(Story 14)*
20. **AC20** — A stylesheet that emits malformed/non-conformant routing XML fails the **XSD gate**
    (gate 1) with a schema-validation error in runner output, independently of selection matching.
    *(Story 15)*
21. **AC21** — A `notDetermined.type` assertion is checked by exact string compare only when present
    in the expected block, and is not validated against an enum (a Type value outside
    {Error,Ignore,Default} that matches the emitted string passes). *(Story 16)*
22. **AC22** — A `notDetermined.defaultReceiver` assertion is matched as a full receiver tuple
    (name + optional party + optional nested interfaces) using the same exact-tuple rules as any
    receiver. *(Story 16)*
23. **AC23** — A test expecting zero receivers passes when the output contains no `<Receiver>`
    elements and fails when any receiver is emitted. *(Story 17)*
24. **AC24** — A test with `mode: receiver` FAILS (shape-consistency check) when the stylesheet
    emits nested `<Interfaces>`, even though the output is XSD-valid. *(Story 18)*
25. **AC25** — An `interface`-mode run, while the O9 Interfaces XSD is unsourced, applies the
    assertion gate and emits the literal warning `XSD gate pending (O9)`; the XSD gate is never
    silently skipped. *(Story 19)*
26. **AC26** — The CLI runner exits non-zero if any case fails either gate, and exits zero only when
    all cases pass both applicable gates. *(Story 20)*
27. **AC27** — A relative `xslt`/`bodyFile` path in the manifest resolves against the manifest
    file's directory; running the same suite from a different working directory produces identical
    results. *(Story 21)*
28. **AC28** — The repository contains an Apache-2.0 `LICENSE` with the copyright holder set to a
    natural person (no `[your name]` placeholder) and a `NOTICE` file. *(Story 22)*

## Prior Art

- **Stage-0 brainstorm** (`BRAINSTORM-CHECKPOINT.md`): D1–D10, north star, rejected alternatives.
- **Stage-1 grill** (`grill-me-sessions/01-routing-mvp.md`): the authoritative resolution of
  O1/O3/O4/O5/O6/O8/O10 (this PRD's source).
- **Reference prototype** (read-only): `C:\Users\pavol\Documents\iflowmonitor\` — earlier
  iFlowMonitor prototype.
- **Official SAP artefacts:** the **Cloud Integration Pipeline - Templates** package —
  "Pipeline v2 Template Step04 - Custom Receiver Determination" (source of `Receivers.xsd` and the
  exact dummy-body knowledge) and the expected Step05 template (source of the standalone Interfaces
  XSD, O9). Same package-inspection path sourced O8.
- The overloaded `<Service>` wire element and the `http://sap.com/xi/XI/System` namespace are the
  established SAP routing-determination contract.

## Out of Scope

Deferred or explicitly excluded for this MVP (carried from the transcript):

- **General data-mapping XSLT** — the tool tests routing output contract, not arbitrary target
  formats (Stage-0 D1/D2; future general-mapping mode).
- **Partner Directory pull** ("test what's deployed", base64-decode via the PD API) — D6 phase 2,
  depends on tenant access.
- **AI tier** (paid) — future layer above the assertion layer.
- **Compose Desktop and Quarkus-service surfaces** — later surfaces over the same core (D5); MVP is
  CLI only.
- **Runtime-reaction simulation** — raising the real not-determined error, ignoring/dropping the
  message, runtime-dispatching to the default receiver (D9 / O6 hard line).
- **Kotlin DSL** (O1a) — later power-user surface over the same core.
- **Subset / partial matching** (O5b' — set-level "contains" and/or field-level
  "assert-only-specified") — opt-in relaxation, only if real use demands it.
- **Copyleft / source-available licensing** (AGPL/BSL) — consciously excluded (O3).
- **O2** — entity / IP / s.r.o. formation; LICENSE holder is a natural person now.
- **O7** — PD-pull auth (OAuth) + base64 decode; until tenant access.
- **DCO / CLA** — Apache-2.0 + DCO sign-off only if outside contributions are accepted; no CLA for
  a solo project.

## Prerequisites

1. **Saxon-HE 9.9.1-x** from Maven Central — the pinned execution engine (D11/Stage-0 D2).
2. **Official `Receivers.xsd`** (namespace `http://sap.com/xi/XI/System`) — extracted from the
   "Pipeline v2 Template Step04 - Custom Receiver Determination" template in the Cloud Integration
   Pipeline - Templates package. **Covers the receiver-only and combined modes' XSD gate.** Already
   available (it is the authoritative source for the D7 corrections).
3. **O9 — standalone Interfaces XSD (Step05 template) — STILL TO SOURCE.** Interface-only mode
   roots at `ns0:Interfaces` and is NOT in `Receivers.xsd`; the separate Interfaces XSD is expected
   in the Step05 template (same package-inspection path as O8/O5 XSD). It is **not build-blocking**
   for receiver/combined modes. **Until sourced, interface mode runs assertion-gate-only with an
   explicit "XSD gate pending (O9)" warning** (AC25). O9 is the one in-flight sourcing item to
   close before hardening interface-only mode.

## Correction Log

Decisions taken or clarified during PRD writing that are not stated verbatim in the transcript:

- **C1 — LICENSE holder placeholder resolution.** The transcript records the repo has a
  `[your name]` placeholder and that the holder is "a natural person now." AC28 makes this
  externally observable by requiring the placeholder be replaced with the natural person and a
  `NOTICE` file present (Apache-2.0 NOTICE convention). This operationalises O3; it does not change
  the licensing decision.
- **C2 — Decision-ID renumbering.** This PRD's `D1–D12` are local IDs that re-tag the transcript's
  branch outcomes (O1, O4a/b/c, O5a/b/c, O6, O8, O10) plus carried Stage-0 engine/license decisions,
  for readability. They do **not** correspond to Stage-0 checkpoint IDs D1–D10. Where this PRD's D9
  corrects checkpoint D8 and D12 locks checkpoint D9, the relationship is stated inline. No decision
  content was altered.
- **C3 — User-story↔AC mapping.** The transcript did not enumerate user stories or acceptance
  criteria; these are derived from the resolved decisions. Each story maps to ≥1 AC and each AC is
  externally observable. No new product decisions were introduced — any apparent novelty is a
  restatement of a transcript decision as an observable test.

## Grill Session Reference

Stage-1 transcript (authoritative source for all decisions above):
[`grill-me-sessions/01-routing-mvp.md`](../grill-me-sessions/01-routing-mvp.md) (committed f6a77b5).
Stage-0 background: [`BRAINSTORM-CHECKPOINT.md`](../BRAINSTORM-CHECKPOINT.md).
