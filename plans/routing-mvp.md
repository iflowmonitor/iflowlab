# Plan: Routing MVP (iflowlab)

> Source PRD: [`prd/routing-mvp.md`](../prd/routing-mvp.md) (commit 7268d92).
> Stage-1 transcript: [`grill-me-sessions/01-routing-mvp.md`](../grill-me-sessions/01-routing-mvp.md).
> Repo state at planning: greenfield — only docs + `LICENSE`, no code yet. Phase 1 stands up the
> build from scratch.

This is a **tracer-bullet** plan: every phase is a thin vertical slice cutting manifest → engine →
assertion → CLI exit code end-to-end, never a horizontal layer. Phase 1 establishes the seam; later
phases widen it. Each phase is sized ≤ ~8h.

## Architectural decisions

Durable decisions that apply across all phases (from PRD D1–D12):

- **Language/runtime**: Kotlin/JVM library + a **CLI test runner** as the first surface. Gradle
  build. (PRD D1, D11; Stage-0 D4/D5.)
- **Engine**: Saxon-HE pinned **9.9.1-x** from Maven Central. (PRD D11.)
- **Test-definition format**: a single **YAML manifest** per suite. Top-level `xslt`, `mode`,
  `namespaces`, `tests[]`; per-test `name`, `params`, `namespaces`, `body`/`bodyFile`, `expect`.
  (PRD D1, D10.)
- **Path resolution**: `xslt`, `bodyFile`, and fixture paths resolve **relative to the manifest
  file's directory** → portable suites. (PRD D10.)
- **Modes**: `receiver` | `combined` | `interface`. `mode` is **explicit, never inferred**, and
  drives root document, XSD gate, assertion shape, and an actual-output shape-consistency check.
  (PRD D10.)
- **Two independent pass gates** (both must pass): (1) emitted XML is **schema-valid** against the
  mode's XSD; (2) **selection matches** the expected block exactly (strict, order-insensitive set
  equality). (PRD D8.)
- **Wire contract**: `ns0:Receivers` / `ns0:Interfaces`, namespace `http://sap.com/xi/XI/System`;
  ergonomic YAML keys mapped 1:1 to wire elements (defusing the overloaded `<Service>`). (PRD
  D6/D7.) `ns0` is **pre-registered** by default.
- **Tool is never stricter than the XSD**: `Type` is free-form string (not enum); `Interface/Index`
  is optional+string; `DefaultReceiver` is a full receiver tuple; `receiver.party` is optional.
  (PRD D7/D9.)
- **License**: Apache-2.0, holder = natural person, NOTICE convention. (PRD D12.)
- **Open item (O9)**: the standalone **Interfaces XSD** (Step05 template) is **unsourced**.
  Interface-only mode's XSD gate stays pending until it lands; see Phase 8.

---

## Phase 1: Tracer bullet — receiver-name routing end-to-end

**User stories**: 1, 2, 4, 14, 20, 21

### What to build

The thinnest complete path. Stand up the Gradle/Kotlin build with Saxon-HE 9.9.1-x pinned, and a
CLI runner entrypoint. Parse a minimal YAML manifest (`xslt`, `mode: receiver`, `tests[]` with
`name`, `params`, `expect.receivers[].name`); reject non-YAML input. Resolve the `xslt` path
relative to the manifest directory, run the stylesheet on Saxon-HE injecting one `dc_` parameter by
its literal name, capture the emitted `ns0:Receivers` XML, and apply a single selection gate: exact,
order-insensitive **receiver-name set match** (extra OR missing receiver fails). Establish the
runner-extension seam (a pluggable gate/assertion pipeline) so later phases widen rather than rewrite.
The CLI prints, per case, the stylesheet used and pass/fail, and exits non-zero if any case fails,
zero only when all pass.

### Acceptance criteria

- [ ] AC1 — valid YAML manifest parses and runs each test; non-YAML input rejected with non-zero exit
- [ ] AC2 — suite-level `xslt` binding applied to each test; runner output names the stylesheet used
- [ ] AC4 — `dc_Receiver: "BANK_A"` binds to Saxon param literally named `dc_Receiver`; no prefixing
- [ ] AC12 — receiver key `name` matches `Receiver/Service`; no `Service` key accepted at receiver level
- [ ] AC17 — receiver match is order-insensitive; extra OR missing receiver fails
- [ ] AC26 — CLI exits non-zero on any case failure, zero only when all cases pass
- [ ] AC27 — relative `xslt`/`bodyFile` paths resolve against the manifest dir; identical results from any CWD

**Deps**: none (foundation). **Effort**: ~8h.

---

## Phase 2: Params + namespace fidelity

**User stories**: 5, 6, 7

### What to build

Widen input fidelity. Coerce YAML-typed `params` values (int/bool) to strings before binding to
Saxon (CPI runtime boundary). Add the `namespaces:` prefix→URI map with suite-level default and
per-case override (per-case merges over suite-level). Pre-register `ns0 →
http://sap.com/xi/XI/System` by default (overridable), consumed both by the assertion-layer XPath
against namespaced output and by runtime parity for prefixed XPath the XSLT uses on the input.

### Acceptance criteria

- [ ] AC5 — `dc_Priority: 1` (YAML int) is passed to Saxon as the string `"1"`
- [ ] AC6 — a per-case `namespaces` entry overrides the suite-level mapping for the same prefix
- [ ] AC7 — with no `namespaces` declared, an assertion using `ns0` resolves the SAP system URI and passes
- [ ] AC15 — a suite-level prefix not overridden per-case remains in effect for that case

**Deps**: Phase 1. **Effort**: ~6h.

---

## Phase 3: XSD gate (gate 1) for receiver/combined modes

**User stories**: 15

### What to build

Wire the official `Receivers.xsd` (namespace `http://sap.com/xi/XI/System`, from the Step04
template — already available) as **independent gate 1** for `receiver`/`combined` modes. The
emitted XML is validated against the schema; a malformed/non-conformant output fails the XSD gate
**independently** of selection matching. Formalize the both-gates-must-pass logic so the CLI exit
reflects failure of either gate.

### Acceptance criteria

- [ ] AC20 — a stylesheet emitting malformed/non-conformant XML fails the XSD gate with a schema-validation error, independently of selection matching
- [ ] AC26 (reinforced) — exit reflects failure of either gate (XSD or selection)

**Deps**: Phase 1. **Prereq**: `Receivers.xsd` (available, PRD Prerequisite 2). **Effort**: ~6h.

---

## Phase 4: Combined mode — nested interfaces + interface set-matching

**User stories**: 11, 12, 13, 14

### What to build

Support `mode: combined`: each receiver assertion may carry a nested `interfaces:` list matched
against `Receiver/Interfaces/Interface`. Map ergonomic keys 1:1 (`interface.endpoint →
Interface/Service`, `interface.index → Interface/Index` optional+string, `interface.name →
Interface/Name` optional). Anchor interface identity on **`endpoint`** (always required), never on
`index`. Assert `index` as a field VALUE when present; if actual omits `Index`, the expected tuple
must omit it too (and vice versa fails). Per-receiver interface matching is exact-set,
order-insensitive; extra OR missing interface fails.

### Acceptance criteria

- [ ] AC16 — combined-mode receiver with nested `interfaces` matches `Receiver/Interfaces/Interface` tuples
- [ ] AC18 — interface with `endpoint` and no `index` matches an emitted `Interface` that omits `Index` (identity on endpoint)
- [ ] AC19 — expected omits `index` but actual emits `Index` (or vice versa) fails the case

**Deps**: Phase 1 (set-match core), Phase 3 (combined shares the `Receivers.xsd` gate). **Effort**: ~7h.

---

## Phase 5: Receiver-not-determined + party + zero-receivers

**User stories**: 16, 17

### What to build

Assert the **static** `ReceiverNotDetermined` block when declared: `notDetermined.type` by exact
string compare (free-form, NOT enum-validated); `notDetermined.defaultReceiver` as a **full receiver
tuple** matched with the same exact-tuple rules as any receiver (name + optional party + optional
nested interfaces). Add optional `receiver.party{value,agency,scheme}` to receiver identity
((Party + Service) when Party present). Make the **zero-receivers** case a first-class expected
outcome (empty expected receiver set ⇒ any actual receiver fails) for negative-routing tests. No
runtime-reaction simulation; no stricter-than-XSD rules.

### Acceptance criteria

- [ ] AC21 — `notDetermined.type` checked by exact string compare only when present; not enum-validated (a value outside {Error,Ignore,Default} that matches the emitted string passes)
- [ ] AC22 — `notDetermined.defaultReceiver` matched as a full receiver tuple using the same rules as any receiver
- [ ] AC23 — a test expecting zero receivers passes when no `<Receiver>` is emitted, fails when any is emitted

**Deps**: Phase 1 (set match), Phase 4 (full-tuple matching incl. party + nested for defaultReceiver). **Effort**: ~6h.

---

## Phase 6: Explicit mode + per-test overrides + shape-consistency check

**User stories**: 2, 3, 18

### What to build

Enforce that `mode` is **explicit** — a case with no resolvable mode fails with a non-zero exit and
an explicit "mode required" message; mode is never inferred from expect-block shape. Support per-test
`mode` and `xslt` overrides (they co-vary — a per-test `mode` override is meaningful only paired with
a per-test `xslt` override). `mode` drives root document + XSD gate + assertion shape, plus the
**shape-consistency check on actual output**: `mode: receiver` must FAIL when the stylesheet emits
nested `<Interfaces>`, even though `Receivers.xsd` permits them (the XSD gate alone won't catch it).

### Acceptance criteria

- [ ] AC3 — manifest with no resolvable `mode` fails with non-zero exit and an explicit "mode required" message; never inferred
- [ ] AC13 — per-test `xslt` override replaces the suite-level binding for that case only; reflected in output
- [ ] AC14 — per-test `mode` override applies to that case only and drives root/XSD/shape selection
- [ ] AC24 — `mode: receiver` FAILS (shape-consistency) when nested `<Interfaces>` are emitted, even though XSD-valid

**Deps**: Phase 1, Phase 3 (XSD/root selection), Phase 4 (nested-interface detection). **Effort**: ~7h.

---

## Phase 7: Header-only dummy-body fidelity

**User stories**: 8, 9, 10

### What to build

Implement omission-as-dummy: a case declaring neither `body:` nor `bodyFile:` is header-only — inject
the canonical CPI dummy **byte-for-byte** as a single hard-coded constant `<dummy></dummy>` (expanded
form, no namespace, no XML prolog). The runner explicitly labels such cases `header-only (dummy
body)` in its output. Add the fidelity **drift-guard** unit test pinning the exact bytes.

### Acceptance criteria

- [ ] AC8 — a case declaring neither `body` nor `bodyFile` runs successfully against a header-only stylesheet (no missing-input error)
- [ ] AC9 — runner output for such a case contains the literal label `header-only (dummy body)`
- [ ] AC10 — drift-guard test asserts the injected dummy equals the exact bytes `<dummy></dummy>` and passes
- [ ] AC11 — drift-guard test FAILS if the constant becomes `<dummy/>` or gains an XML prolog

**Deps**: Phase 1. **Effort**: ~5h.

---

## Phase 8: Interface-only mode + O9 gating

**User stories**: 19

### What to build

Support `mode: interface`: root `ns0:Interfaces`, flat `interfaces` assertion shape (Index/Service/
Name tuples, endpoint-anchored, reusing Phase 4 interface matching). Because the standalone
**Interfaces XSD is unsourced (O9)**, interface-mode runs apply the **assertion gate only** and emit
the literal warning `XSD gate pending (O9)` — the XSD gate is never silently skipped.

> **Inline open item (O9)**: source the standalone Interfaces XSD from the Step05 template (Cloud
> Integration Pipeline - Templates package). When it lands, wire it as gate 1 for interface mode and
> drop the pending warning. Not build-blocking for receiver/combined modes. (PRD Prerequisite 3.)

### Acceptance criteria

- [ ] AC25 — an interface-mode run (Interfaces XSD unsourced) applies the assertion gate and emits the literal `XSD gate pending (O9)` warning; the XSD gate is never silently skipped

**Deps**: Phase 6 (mode root/XSD selection), Phase 4 (interface matching). **Effort**: ~6h.

---

## Phase 9: Licensing / packaging finalization

**User stories**: 22

### What to build

Finalize Apache-2.0 packaging: replace the `[your name]` placeholder in `LICENSE` with the natural
person, and add a `NOTICE` file (Apache-2.0 NOTICE convention).

### Acceptance criteria

- [ ] AC28 — `LICENSE` is Apache-2.0 with the copyright holder a natural person (no `[your name]` placeholder); a `NOTICE` file is present

**Deps**: none. **Effort**: ~2h.

---

## AC coverage map

Every PRD acceptance criterion (AC1–AC28) lands in ≥1 phase — no gaps:

| AC | Phase | AC | Phase | AC | Phase | AC | Phase |
|----|-------|----|-------|----|-------|----|-------|
| AC1 | P1 | AC8 | P7 | AC15 | P2 | AC22 | P5 |
| AC2 | P1 | AC9 | P7 | AC16 | P4 | AC23 | P5 |
| AC3 | P6 | AC10 | P7 | AC17 | P1 | AC24 | P6 |
| AC4 | P1 | AC11 | P7 | AC18 | P4 | AC25 | P8 |
| AC5 | P2 | AC12 | P1 | AC19 | P4 | AC26 | P1 (+P3) |
| AC6 | P2 | AC13 | P6 | AC20 | P3 | AC27 | P1 |
| AC7 | P2 | AC14 | P6 | AC21 | P5 | AC28 | P9 |

Total effort: ~53h across 9 phases.

---

## Parallelization map

**Strictly sequential backbone** (each needs the prior's seam):

- **P1** is the foundation — must complete first; everything depends on its runner/engine/assertion seam.
- **P3 → P4 → P5** — set-matching and XSD depth build on each other (P4 needs the P3 XSD gate for
  combined; P5 needs P4's full-tuple/party matching).
- **P4 → P6 → P8** — mode enforcement (P6) needs P4's nested-interface detection; interface mode (P8)
  needs P6's mode-driven root/XSD selection.

**Parallel-worktree-safe after P1**:

- **P9 (licensing)** — touches only `LICENSE`/`NOTICE`; fully independent, run anytime in parallel
  with anything.
- **P2 (params/namespaces)**, **P3 (XSD gate)**, **P7 (dummy body)** are logically independent of
  each other and can be developed in parallel worktrees once P1 lands. **Caveat**: all three extend
  the central runner/engine pipeline, so although logically parallel they are likely to **conflict
  in the runner orchestration code** — keep P1's extension seam thin and well-bounded, and either
  serialize their merges or assign each a distinct pipeline-stage module to stay genuinely
  worktree-safe.

**Suggested parallel waves**:

1. **Wave 0**: P1 (alone). P9 may also start immediately (independent).
2. **Wave 1** (after P1): P2 ∥ P3 ∥ P7 (merge-coordinate on the runner core).
3. **Wave 2** (after P3): P4.
4. **Wave 3** (after P4): P5 ∥ P6.
5. **Wave 4** (after P6): P8.

No AC could not be placed; no PRD requirement is left uncovered.
