# Grill-me Session 01 — Routing MVP

> Stage 1 (grill-me) of the iFlowMonitor spec workflow for iflowlab.
> Input artefact: BRAINSTORM-CHECKPOINT.md (Stage 0), repo main @ 6180131.
> Scope: routing MVP — the first PRD for iflowlab.
> Drives to recommendation: O1, O4, O5, O6. Confirms: O3. Deferred (not grilled): O2, O7.
> Anchored to checkpoint decisions D1–D10.

## Status legend

- RESOLVED — decision reached this session
- CORRECTION — changes a checkpoint decision (D1–D10)
- NEW — new decision/branch surfaced during the grill
- DEFERRED — explicitly punted with an open-question tag

---

## Branches

### O1 — Test-definition format — RESOLVED

**Decision:** YAML manifest is the single MVP test-definition format.

- Rationale: fits the actual authors (SAP/XSLT developers, not Kotlin developers); fits
  the CLI-in-CI first surface (D5); diffs cleanly in PRs; is the natural emission target
  for the future AI tier (D7).
- Kotlin DSL is NOT built for MVP. The engine remains a Kotlin library (D4), so a thin
  DSL builder can be added later as a power-user surface.

**NEW — O1a (DEFERRED):** Kotlin DSL as a later power-user surface over the same core.
Not build-blocking; revisit only on demand.

---

### O4 — dc_ injection + namespace-declaration UX (CPI fidelity)

O4 has three sub-branches walked in order:
- O4a — how dc_ params are expressed in the manifest
- O4b — how namespace prefix→URI mappings are declared and scoped
- O4c — how the dummy-XML-body (header-only routing) case is signalled

#### O4a — dc_ param expression — RESOLVED

**Decision:** A flat `params:` map keyed by the **exact** stylesheet parameter name
(e.g. `dc_Receiver`). No auto-prefixing — the tool binds values to Saxon stylesheet
params by literal name and never invents names. The `dc_` convention is documented only.

```yaml
params:
  dc_Receiver: "BANK_A"
  dc_Priority: "HIGH"
```

**O4a' — RESOLVED:** dc_ values are **string-typed** to match the CPI runtime boundary
(headers/properties arrive as strings). The tool coerces/validates `params:` values to
string rather than passing YAML-typed int/bool to Saxon.

#### O4b — namespace declaration mechanism + scope — RESOLVED

**Decision:** A flat `namespaces:` map (prefix → URI).

- The SAP system namespace is **built-in**: `ns0 → http://sap.com/xi/XI/System` is
  pre-registered by default, overridable if a stylesheet emits a different prefix.
  Authors declare only their own payload namespaces.
- **Scope:** suite-level default with optional **per-case override** (per-case map merges
  over suite-level).
- Consumers: (a) the assertion-layer XPath/XSD against the namespaced output (always
  needs `ns0`), and (b) runtime parity for prefixed XPath the XSLT uses on the input.

```yaml
namespaces:          # suite-level; ns0 is implicit/pre-registered
  p1: "urn:bank:payments:v1"
tests:
  - name: ...
    namespaces:      # optional per-case override, merged over suite-level
      p1: "urn:bank:payments:v2"
```

#### O4c — dummy-XML-body (header-only routing) signalling — RESOLVED

**Decision (signalling):** **Omission = dummy body.** A case that declares neither
`body:` nor `bodyFile:` is treated as header-only; the tool injects the canonical CPI
dummy and the runner **explicitly labels that case as "header-only (dummy body)"** in its
output. `body:` (inline XML) / `bodyFile:` (fixture path) express a real XML body. No
required `body: dummy` boilerplate — visibility comes from runner output, not ceremony.

**NEW — O8 (fidelity: exact CPI dummy body) — RESOLVED** (value supplied by operator from
direct tenant/package knowledge):

> CPI's dummy body is exactly `<dummy></dummy>` — root element `dummy`, **no namespace**,
> **expanded form** (NOT self-closing `<dummy/>`), **no XML prolog**.

- Pin as the canonical dummy: a single, documented, hard-coded constant, injected
  **byte-for-byte** for header-only cases. The PRD hardens these bytes now (no placeholder
  / TODO caveat).
- **Hardening test (NEW, accepted):** one fidelity unit test asserting the injected dummy
  equals `<dummy></dummy>`, so any future drift in the constant is caught.

**O4 — fully RESOLVED** (O4a, O4a', O4b, O4c all closed).

---

### O5 — Combined receiver+interface representation in the assertion model

O5 walked as: O5a representation shape, O5b matching semantics, O5c field granularity.

#### O5a — representation shape — RESOLVED (option A, nested)

**Governing principle:** the assertion STRUCTURE mirrors the SAP wire contract
(non-negotiable). Confirmed by operator as the **documented** SAP combined-determination
output (not inferred): ONE `ns0:Receivers` document with an `ns0:Interfaces` block nested
inside each `ns0:Receiver`.

Exact wire structure (namespace `http://sap.com/xi/XI/System`):

```xml
<ns0:Receivers xmlns:ns0="http://sap.com/xi/XI/System">
  <ReceiverNotDetermined>
    <Type>Error</Type>             <!-- Error | Ignore | Default -->
    <DefaultReceiver/>             <!-- receiver name when Type=Default -->
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

**CRITICAL footgun — `<Service>` is overloaded:** at Receiver level it is the receiver
system name; at Interface level it is the ProcessDirect endpoint. The YAML must NOT mirror
that element name literally.

#### O5c — field granularity / ergonomic key mapping — RESOLVED

YAML keys are **ergonomic, mapped 1:1 to wire elements** (precisely to defuse the `Service`
overload):

| YAML key                | Wire element                  |
|-------------------------|-------------------------------|
| receiver `name`         | `Receiver/Service`            |
| interface `index`       | `Interface/Index`             |
| interface `endpoint`    | `Interface/Service`           |
| interface `name` (opt)  | `Interface/Name`              |
| `notDetermined.type`    | `ReceiverNotDetermined/Type`  (optional assert, per D8) |

**Fidelity anchor is XSD validation, NOT the YAML naming.** Validate the emitted XML
against the official SAP Receivers/Interfaces XSD; the ergonomic YAML then cannot drift
from the contract.

**XSD sourcing (no tenant needed) — NEW, recorded:** the canonical Receivers XSD ships in
the **"Pipeline v2 Template Step04 - Custom Receiver Determination"** template inside the
**"Cloud Integration Pipeline - Templates"** package. Download the package and extract the
XSD from the Step04 template — same package-inspection path used to source O8.

**All three modes, consistent with the wire:**
- receiver-only: flat `ns0:Receivers`, each `Receiver` has `Service` only (no `Interfaces`).
- interface-only: a separate flat `ns0:Interfaces` document (`Interface` = Index/Service/Name).
- combined: the nested form above.

**Unified assertion model:** a `receivers` list where each receiver MAY carry a nested
`interfaces` list (combined mode), plus a standalone `interfaces` assertion for
interface-only mode.

#### O5 — CORRECTIONS from the official Receivers.xsd (authoritative)

Verified against the official `Receivers.xsd` (ns `http://sap.com/xi/XI/System`). Nested
structure CONFIRMED (option A). The schema corrects several details the PDF examples hid —
**the tool must never be stricter than this XSD** (stricter-than-wire is a fidelity break):

- **`ReceiverNotDetermined/Type` is `xsd:string`, NOT a restricted enum.** Error / Ignore /
  Default are conventions, not schema-enforced. Do **not** validate Type as an enum.
- **`DefaultReceiver` is a FULL `ReceiverX`** (optional) — not an empty element, not a bare
  name.
- **`ReceiverX`:** `Party` (optional) = string content (maxLength 60) with attributes
  `agency` (maxLength 120) and `scheme` (maxLength 120); `Service` (string, maxLength 60) =
  receiver SYSTEM NAME (**required**); `Interfaces` (optional, nested).
- **`Interface`:** `Index` (`xsd:string`, **OPTIONAL** / minOccurs=0 — a STRING, may be
  absent; do NOT require or coerce to int); `Service` (`xsd:string`, **required**) =
  ProcessDirect ENDPOINT; `Name` (`xsd:string`, OPTIONAL).

**Corrections to earlier pins (supersede O5a/O5c above):**
- `interface.index` is **optional + string** (was wrongly pinned required/int).
- `notDetermined.defaultReceiver` is a **full receiver tuple** (was wrongly "bare name").
- Add optional **`receiver.party {value, agency, scheme}`** to the model.

**Ergonomic assertion keys — corrected 1:1 map:**

| YAML key                       | Wire element                                  |
|--------------------------------|-----------------------------------------------|
| `receiver.name`                | `Receiver/Service`                            |
| `receiver.party`               | `Receiver/Party` (+ `@agency`, `@scheme`)     |
| `interface.index` (opt, string)| `Interface/Index`                             |
| `interface.endpoint`           | `Interface/Service`                           |
| `interface.name` (opt)         | `Interface/Name`                              |
| `notDetermined.type`           | `ReceiverNotDetermined/Type` (free-form str)  |
| `notDetermined.defaultReceiver`| `ReceiverNotDetermined/DefaultReceiver` (full receiver) |

**Fidelity anchor:** validate emitted XML against `Receivers.xsd` (covers receiver-only +
combined modes).

**NEW — O9 (source the standalone Interfaces XSD) — OPEN:** interface-only mode roots at
`ns0:Interfaces` and is **NOT** in `Receivers.xsd`. The separate Interfaces XSD still needs
sourcing — expected in the **Step05** template (same Cloud Integration Pipeline - Templates
package-inspection path as O8/O5 XSD). Not build-blocking for receiver/combined modes; gates
the interface-only mode's schema-validation gate. Tagged for resolution before the PRD
hardens interface-only.

#### O5b — matching semantics (set/subset, ordering) — RESOLVED

**Decision: strict exact-set matching, NO subset mode in the MVP.** Reconciled to the XSD:

- **Receivers:** exact set equality, **order-insensitive**. Identity = `Service` name, or
  **(Party + Service)** when Party is present. Full receiver tuple compared (name + optional
  party{value,agency,scheme} + nested interfaces). Extra OR missing receiver fails.
- **Interfaces (per receiver):** exact set equality, order-insensitive, on **full tuples**
  `{index?, endpoint, name?}`. **Do NOT key solely on Index** — Index is OPTIONAL per the
  XSD, so keying on it is fragile. **Anchor identity on `endpoint`** (`Interface/Service`,
  always required); assert the `index` VALUE as a field when present; if actual omits Index,
  the expected tuple must omit it too. **Index is an asserted value, never a position.**
  Extra OR missing interface fails.
- **notDetermined:** asserted only when present in the expected block (per D8). `type` =
  **exact string compare** (free-form, not enum). `defaultReceiver` = a full receiver tuple,
  matched like any receiver (couples to O6).

**Two independent pass gates, both must pass:**
1. emitted XML is **schema-valid** against `Receivers.xsd` (catches malformed output);
2. **selection matches** the expected block exactly (catches wrong routing).

**O5b' (DEFERRED):** opt-in relaxation — set-level "contains" and/or field-level
"assert-only-specified" — as a single later sub-question if real use demands it.

**O5 — fully RESOLVED** (schema-accurate; O5a, O5b, O5c closed; O9 surfaced + open).

---

### O6 — receiver-not-determined semantics (how far) — RESOLVED

**Principle (accepted):** assert what the XSLT statically emits inside
`<ReceiverNotDetermined>`; never simulate how the pipeline reacts to it.

**IN scope (static output only):**
- `notDetermined.type` — free-form string compare, asserted when the author declares it.
- `notDetermined.defaultReceiver` — asserted as a **full receiver tuple** when declared,
  matched with the SAME exact-tuple rules as any receiver (Q6/O5b): name + optional
  party{value,agency,scheme} + optional nested interfaces. Usually just a Service name, but
  match whatever is declared, exactly.
- **The zero-receivers case** — expecting no `<Receiver>` elements (optionally with a
  `notDetermined` block) is a **first-class expected outcome**: the empty-set special case
  of Q6's exact-set matching (empty expected receiver set ⇒ any actual receiver fails).
  Important **negative-routing** test category — keep it.

**OUT of scope (hard line, confirms D8):** any simulation of the runtime reaction — raising
the real error, ignoring/dropping the message, runtime-dispatching to the default receiver.

**No stricter-than-XSD rules:** do NOT enforce "Type=Default ⇒ DefaultReceiver present"
(DefaultReceiver is optional in the XSD). Type-not-in-{Error,Ignore,Default} warnings are a
deferred convenience, not MVP.

**CORRECTION to D8:** D8 said "assert the declared Type." The XSD shows
`ReceiverNotDetermined` also carries a structured `DefaultReceiver`, so the static assertion
extends from **"Type only"** to **"Type + DefaultReceiver (full tuple)"**. Same principle,
broader surface.

---

### O3 — license (Apache-2.0 vs MIT) — CONFIRMED

**Decision: Apache-2.0. D9 locked, "placeholder" caveat dropped.**

- Paid tier is **license-neutral** (the proprietary AI layer stays proprietary under either
  permissive license), so the real axis is **patents + enterprise palatability**. For a
  SAP-enterprise audience under an iFlowMonitor brand halo, Apache-2.0's explicit **patent
  grant + retaliation clause + NOTICE convention** wins. MIT's brevity doesn't outweigh it.
- **Copyleft/source-available (AGPL/BSL) rejected** — the only thing that would restrict
  commercial reuse of the core, but it contradicts the open-source-first / brand-halo north
  star. **Conscious exclusion, confirmed (not deferred).**

**Follow-ups when locking (NEW):**
- **LICENSE copyright holder = natural person now** (entity doesn't exist yet; revisit on
  s.r.o. formation, per O2). Repo currently has a `[your name]` placeholder to fill.
- **Forward note (not MVP):** if outside contributions are ever accepted, Apache-2.0 + a
  **DCO sign-off** is the standard low-friction model. Deferred; **no CLA** for a solo
  project.

---

### O10 — manifest top-level: XSLT binding + determination-mode declaration — RESOLVED

**Decision:** explicit `mode` declaration (suite default + per-test override) and suite-level
`xslt:` binding (per-test override allowed). Inferring mode from expect-block shape is the
silent-false-pass trap — explicit mode is mandatory for a fidelity tool.

**1. `mode` drives THREE things (not two):**
   (a) root document + XSD gate;
   (b) assertion shape — receiver: no interfaces; combined: nested interfaces; interface: flat interfaces;
   (c) **shape-consistency check on ACTUAL output** — the emitted structure must match the
   declared mode. The real payoff: `mode=receiver` must FAIL if the XSLT emits nested
   `<Interfaces>` (Receivers.xsd allows them optionally, so the XSD gate alone won't catch
   it). Inference could never give this.

**2. Root / XSD per mode:**

| mode      | root            | XSD gate                  |
|-----------|-----------------|---------------------------|
| receiver  | `ns0:Receivers` | `Receivers.xsd`           |
| combined  | `ns0:Receivers` | `Receivers.xsd` (SAME root + schema as receiver; mode disambiguates assertion shape + consistency check, not the schema) |
| interface | `ns0:Interfaces`| the O9 Interfaces XSD     |

**3. interface mode — manifest support NOW, XSD gate blocked on O9.** Until O9 lands, run
interface-mode tests with the **assertion gate only** and emit an explicit
**"XSD gate pending (O9)"** warning — never silently skip the gate (same placeholder
discipline as O8).

**4. `mode` and `xslt` co-vary** — a stylesheet has a fixed mode (emits Receivers OR
Interfaces OR combined; it doesn't switch per input). So a per-test `mode` override is only
meaningful when paired with a per-test `xslt` override. Common inner loop (one stylesheet,
many inputs) sets both once at suite level.

**5. Path resolution** — `xslt` (and `namespaces`/`bodyFile`) paths resolve **relative to the
manifest file's directory** → portable suites.

**Confirmed manifest shape:**

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

---

## Follow-up

**What this transcript supersedes / amends in BRAINSTORM-CHECKPOINT.md:**

- **D8 — CORRECTED.** Receiver-not-determined assertion extends from "declared **Type** only"
  to "**Type + DefaultReceiver** (full receiver tuple)" — the official Receivers.xsd shows
  `DefaultReceiver` is a structured `ReceiverX`, not a bare name. Runtime-reaction simulation
  stays out (D8's core line holds). See O6.
- **D9 — LOCKED.** License = **Apache-2.0**, "placeholder" caveat dropped; copyleft
  consciously excluded. See O3.
- **D10 — SHARPENED, not changed.** The dc_/namespace/dummy-body fidelity requirements are now
  concretely specified: flat exact-name string `params:` map (O4a); built-in-`ns0`
  prefix→URI `namespaces:` map with per-case override (O4b); canonical dummy body pinned to
  the exact bytes `<dummy></dummy>` (O4c/O8).
- **Assertion model (D8) — now schema-accurate and fully specified** by O5 against the
  official `Receivers.xsd`: nested option A; ergonomic keys mapped 1:1 to wire elements
  (defusing the overloaded `<Service>`); `interface.index` optional+string; optional
  `receiver.party`; strict exact-set matching keyed on name/(party+service) and on
  interface `endpoint`; two independent pass gates (XSD-valid + selection-match).
- **New manifest-level decision (O10)** not in the checkpoint: explicit `mode` +
  suite-level `xslt` binding, with the mode-driven shape-consistency check.

**Resolved this session:** O1 (+O1a deferred), O3, O4 (O4a/O4a'/O4b/O4c), O5 (O5a/O5b/O5c,
+O5b' deferred), O6, O8, O10.

**Open / deferred after this session (none block the Stage-2 PRD):**
- **O9 — OPEN, sourcing (no tenant needed).** Source the standalone **Interfaces XSD** from
  the **Step05** template (Cloud Integration Pipeline - Templates package). Gates only the
  interface-only mode's XSD pass-gate; receiver/combined modes are unblocked. Until landed,
  interface-mode runs assertion-gate-only with an explicit "XSD gate pending (O9)" warning.
- **O1a — DEFERRED.** Kotlin DSL as a later power-user surface over the same core.
- **O5b' — DEFERRED.** Opt-in match relaxation (set-level "contains" / field-level
  "assert-only-specified"), only if real use demands it.
- **O2 — DEFERRED** (per scope). Entity/IP; LICENSE copyright holder is a natural person now,
  revisit on s.r.o. formation.
- **O7 — DEFERRED** (per scope). PD-pull auth (OAuth) + base64 decode; until tenant access.
- **DCO/CLA — DEFERRED.** Apache-2.0 + DCO sign-off if outside contributions are ever
  accepted; no CLA for a solo project.

**Stage gate:** targeted set complete (O1, O3, O4, O5, O6 + surfaced O8, O10 resolved).
Ready for **Stage 2 — write-a-prd** for the routing MVP. This transcript is the durable
Stage-1 input; the PRD should treat O9 as the one in-flight sourcing item to close before
hardening interface-only mode.
