# Plan: Desktop Workbench (iflowlab)

> Source PRD: `prd/desktop-workbench.md` (Stage 2, committed 6521f5b).
> Stage 3 (prd-to-plan) — tracer-bullet vertical slices over the PRD's agreed Phase 0–3 backbone
> (PRD D10). Stage 4 (prd-to-issues) and execution are NOT started here.
> Sequencing is HARD: Phase 0 → Phase 1 → Phase 2 (spike-gated) → Phase 3. Phases 0–1 are strict
> prerequisites for every desktop slice.

## Architectural decisions

Durable decisions that apply across all slices (from the PRD; do not re-litigate per slice):

- **Module structure**: `:engine` (library) + `:cli` (app) + `:desktop` (app). `:cli` and
  `:desktop` depend on **`:engine`'s public API only** (compiler-enforces "no routing logic in the
  desktop layer"). `:desktop` is created in Phase 2, NOT in the Phase 0 refactor.
- **Engine contract (D4)**: `run(manifestPath): SuiteResult` — pure (no printing, no `exitProcess`,
  never throws engine/config exceptions out). `SuiteResult { cases: List<CaseResult>, configError:
  String? }`; `CaseResult { name, xslt, headerOnly, gateResults: List<GateResult>, engineError:
  String? }` with `passed = engineError == null && gateResults.none { it.failed }`. Existing
  `GateResult`/`GateContext` reused unchanged.
- **Exit-code mapping (D4 REVERSAL)**: lives in `:cli` via named constants `EXIT_OK`/`EXIT_FAIL`/
  `EXIT_CONFIG` (0/1/2). `:engine` exposes facts only.
- **Stack (D1/D2)**: Compose for Desktop (JVM), engine reused in-process. Editor = RSyntaxTextArea
  embedded via Compose `SwingPanel`; theming = FlatLaf (or equivalent — OI-3). No Compose-native
  editor (Context7-confirmed).
- **Run model (D6)**: explicit Run (button = primary; **both F5 and Ctrl+Enter** as accelerators);
  **save-then-run** to disk (visible, dirty indicator); **whole-suite**; **off the UI thread**.
- **Workspace (D5 REVERSAL)**: project = a **scanned folder**, no `.iflowlab-project` file.
  **Two-tier manifest discovery** (candidate = top-level YAML mapping with a `tests:` key → list;
  full-parse → valid vs broken-node; non-candidate → ignored). Manifest edited as **raw YAML**.
- **History (D8)**: latest result only, in-memory, overwritten each Run; **stale-results marker**
  tied to dirty state. No persistence in v1.
- **Packaging (D9)**: run-from-Gradle (`gradlew :desktop:run`); Windows-first validation.
- **Spike gate (D2/AC12)**: the editor-embedding spike is a HARD GO/NO-GO gate and the FIRST slice
  of Phase 2. No editor-dependent slice may precede it. STOP if any criterion fails.

Carried inline open items (from PRD Correction Log; resolved during design, not blocking):
**OI-1** multi-document UX (tabs vs switchable pane), **OI-2** in-progress affordance (spinner/bar/
disabled-Run), **OI-3** exact theming library, **OI-4** editor pane composition (one surface vs two).

---

## Phase 0 — Module split (PR N) — strict prerequisite

### Slice 0.1 — Extract `:engine` + `:cli` (pure move)

**User stories**: 16, 17 (and enables 15's eventual `:desktop:run`).
**Depends on**: nothing (first slice).
**Effort**: ~6h.

#### What to build
A behavior-preserving Gradle multi-module refactor. Move the engine/runner/gate/manifest/model/xml
code into `:engine`; leave the CLI entry point in `:cli`. Pure move — **zero new code**, **no
`:desktop` module**. Relocate engine tests into `:engine`; keep CLI-specific tests in `:cli`.
Update README + `examples/` run references from `gradlew run` to `gradlew :cli:run`.

#### Acceptance criteria
- [ ] **AC1** — full existing suite runs across `:engine` (+ `:cli`) and all tests pass.
- [ ] **AC2** — `:cli` produces byte-identical stdout to the pre-split CLI for the example suite(s).
- [ ] **AC3** — PR adds `:engine` + `:cli` only; `settings.gradle.kts` does NOT contain `:desktop`.
- [ ] **AC4** — run invocation is `gradlew :cli:run`; README + `examples/` updated in the same PR.

---

## Phase 1 — Typed result model (PR N+1) — strict prerequisite

### Slice 1.1 — `run(manifestPath): SuiteResult` + `:cli` rewire + golden test

**User stories**: 18, 19, 20.
**Depends on**: 0.1.
**Effort**: ~7h.

#### What to build
Reshape the engine to return a typed `SuiteResult`/`CaseResult` instead of printing; errors become
nullable strings (`configError`, `engineError`), never thrown out of `run`. Move all rendering and
the exit-code mapping into `:cli` (named constants). Rewire `:cli` to render from the typed model in
the **same** PR. Capture a pre-reshape stdout golden for the example suite(s) as the safety net.
(PRD D4 mandates model + `:cli` rewire in one PR; this stays a single slice for that reason.)

#### Acceptance criteria
- [ ] **AC5** — `run` returns `SuiteResult` and prints nothing / never calls `exitProcess` (engine
      output captured empty in a test).
- [ ] **AC6** — `SuiteResult.cases` + `configError`; `CaseResult.passed`/`engineError`/`gateResults`.
- [ ] **AC7** — parse/config failure → non-null `configError`, empty `cases`, no throw.
- [ ] **AC8** — stylesheet failure → non-null `engineError`, no throw.
- [ ] **AC9** — exit-code mapping in `:cli` via `EXIT_OK`/`EXIT_FAIL`/`EXIT_CONFIG`; `:engine` has none.
- [ ] **AC10** — CLI exit codes unchanged: 0 all pass / 1 any fail / 2 config error.
- [ ] **AC11** — golden/characterization test asserts post-reshape `:cli` stdout is byte-identical.

---

## Phase 2 — Desktop workbench (spike-gated)

> The first slice (2.1) is the HARD GO/NO-GO gate. Slices 2.2–2.6 may proceed only if 2.1 passes.

### Slice 2.1 — Editor-embedding spike (GO/NO-GO GATE) — STANDALONE

**User stories**: 22, 23.
**Depends on**: 0.1, 1.1 (Phases 0–1 complete). No editor-dependent slice precedes this.
**Effort**: ~5h.

#### What to build
A throwaway spike: embed RSyntaxTextArea in a minimal Compose Desktop window via `SwingPanel`, on
**Windows 11**. Prove all four criteria. This is a gate, not a feature — code may be discarded.

> **STOP-IF-ANY-CRITERION-FAILS.** If (a)–(d) cannot all be demonstrated, halt Phase 2 and escalate
> via the fallback ladder: **pure Swing + RSyntaxTextArea** (drop the Compose host, no interop seam)
> → **Monaco-in-JCEF** (web-grade editor) only if a web editor proves necessary. Do not paper over
> the seam; surface the failure.

#### Acceptance criteria
- [ ] **AC12** — on Windows 11, the spike demonstrates all four: (a) Compose↔Swing focus/keyboard
      handoff in/out of the editor; (b) syntax highlighting + error markers via the RSTA API;
      (c) HiDPI scaling; (d) FlatLaf (or equivalent) theming. Any failure → STOP + escalate.
- [ ] **AC31** — HiDPI + FlatLaf criteria validated on Windows 11 (standing acceptance, re-confirmed
      at each later desktop slice; macOS/Linux not claimed or tested).

---

### Slice 2.2 — `:desktop` app shell + open one manifest + XSLT in editor

**User stories**: 4, 15 (launch), 21.
**Depends on**: 2.1 (gate passed).
**Effort**: ~7h.

#### What to build
Birth the `:desktop` module and the app shell (window, menu, `:desktop:run` task). Implement
**File → Open manifest**: parse the manifest via `:engine`, resolve its referenced XSLT, and show
that XSLT in the syntax-highlighted RSTA editor. This is the foundation the loop is built on.
(OI-1/OI-4: single editor pane for v1 unless 2.3 forces a second surface.)

#### Acceptance criteria
- [ ] **AC13** — workbench launches via `gradlew :desktop:run`.
- [ ] **AC14** — opening a manifest shows its referenced XSLT syntax-highlighted; typing updates the
      buffer and highlighting.

---

### Slice 2.3 — Manifest YAML editing + dirty indicator

**User stories**: 5, 8 (dirty half).
**Depends on**: 2.2.
**Effort**: ~4h.

#### What to build
Make the manifest itself editable as **raw YAML** in the editor (YAML highlighting), alongside the
XSLT (OI-4: this is where one-vs-two editor surfaces is decided). Surface the engine's
`ManifestException` message on a parse error. Add a **per-document dirty/modified indicator** that
sets when a buffer diverges from disk — the state the save-then-run loop (2.4) and the stale marker
(2.6) both consume.

#### Acceptance criteria
- [ ] **AC15** — manifest editable as raw YAML; a manifest parse error surfaces the
      `ManifestException` message.
- [ ] **AC17** — a document modified since load/last save shows a per-document dirty indicator.

---

### Slice 2.4 — Close the loop: Run + save-then-run + off-thread + basic results

**User stories**: 7, 8 (save half), 9, 10 (basic), 21.
**Depends on**: 2.2, 2.3, **1.1** (needs `SuiteResult`).
**Effort**: ~8h.

#### What to build
The tracer bullet that closes the edit→run→see loop. A **Run button** plus **F5 and Ctrl+Enter**
accelerators. Run does: persist all dirty buffers to disk (visibly — dirty indicators clear), then
call `run(manifestPath)` **off the UI thread** with an in-progress state (OI-2: affordance TBD),
then render the returned `SuiteResult` as a **basic** per-case list (PASS/FAIL badge + stylesheet
name + header-only label). Rich styling is 2.5. The render overwrites the previous result
(establishes latest-only; formalized in 2.6).

#### Acceptance criteria
- [ ] **AC16** — Run button present; Run triggered by both F5 and Ctrl+Enter.
- [ ] **AC18** — Run persists all dirty buffers to disk before executing, visibly (indicators clear).
- [ ] **AC19** — Run executes off the UI thread; UI shows in-progress state and stays responsive.
- [ ] **AC20** — Run executes the whole suite via `run(manifestPath)` and renders the `SuiteResult`.
- [ ] **AC21** — per case: PASS/FAIL badge, stylesheet name, header-only label where applicable.
- [ ] **AC25** (partial) — a new Run overwrites the previous results (latest-only; see 2.6).

---

### Slice 2.5 — Structured results panel (rich presentation)

**User stories**: 10, 11, 12.
**Depends on**: 2.4.
**Effort**: ~6h.

#### What to build
Enrich the results panel from a basic list into structured presentation: render each `GateResult`'s
`findings`/`warnings`, and `engineError`/`configError` as their own rows, with **visually distinct
styling per error class** (XSD / selection mismatch / engineError / configError). Warnings render
even on PASS cases (warning badge). Selection mismatch shows the engine's `findings` text only — no
visual add/remove diff (gated on the deferred structured `SelectionDiff`).

#### Acceptance criteria
- [ ] **AC22** — panel renders each `GateResult`'s findings + warnings; `engineError`/`configError`
      as their own rows.
- [ ] **AC23** — each error class rendered with visually distinct styling (not a stdout reprint).
- [ ] **AC24** — warnings render even on a PASS case, with distinct styling.
- [ ] **AC27** — selection mismatch renders as `findings` text (no visual diff in v1).

---

### Slice 2.6 — Latest-only + stale-results marker

**User stories**: 13, 14.
**Depends on**: 2.4 (loop + render), 2.3 (dirty state).
**Effort**: ~3h.

#### What to build
Formalize result-state integrity: hold only the latest `SuiteResult`, overwritten each Run (no
history list/persistence). After a Run, if any document goes dirty (edited since that Run), mark the
results panel **stale ("re-run")** so it never silently presents results predating the current
buffer.

#### Acceptance criteria
- [ ] **AC25** — results panel reflects only the latest run; a new Run overwrites previous results.
- [ ] **AC26** — editing any document after a Run marks the panel stale ("re-run"); never silently
      shows pre-edit results.

---

## Phase 3 — Project navigation

### Slice 3.1 — Manifest discovery (headless two-tier classifier)

**User stories**: 2, 3.
**Depends on**: 1.1 (uses `:engine` `ManifestParser`). Headless — see Parallelization map.
**Effort**: ~3h.

#### What to build
A headless, unit-testable folder scanner: classify each YAML file as **candidate** (top-level
mapping with a `tests:` key), then **valid** vs **broken** via full parse; ignore non-candidates.
No project file written. This is pure logic off the app shell — the data the tree (3.2) renders.

#### Acceptance criteria
- [ ] **AC29** — two-tier discovery: candidate-by-`tests:`-key listed; listed-but-unparseable shown
      as broken node (not omitted); non-candidate YAML ignored.

---

### Slice 3.2 — Project tree navigation + open folder + select stylesheet

**User stories**: 1, 6.
**Depends on**: 3.1, 2.2 (app shell + editor).
**Effort**: ~5h.

#### What to build
**File → Open folder**: run discovery (3.1) and render manifests + the XSLTs they reference in a
sidebar/tree (broken suites flagged). Selecting a stylesheet opens it in the editor; editor scope
spans the distinct XSLTs referenced across the project's manifests. No project file created.

#### Acceptance criteria
- [ ] **AC28** — opening a folder scans it and lists manifests + referenced XSLTs in a tree; no
      project file created.
- [ ] **AC30** — selecting a stylesheet opens it in the editor; scope spans distinct XSLTs across the
      project's manifests.

---

## AC coverage map

Every PRD AC lands in ≥1 slice (no gaps):

| Slice | ACs |
|-------|-----|
| 0.1 | AC1, AC2, AC3, AC4 |
| 1.1 | AC5, AC6, AC7, AC8, AC9, AC10, AC11 |
| 2.1 | AC12, AC31 |
| 2.2 | AC13, AC14 |
| 2.3 | AC15, AC17 |
| 2.4 | AC16, AC18, AC19, AC20, AC21, AC25 (partial) |
| 2.5 | AC22, AC23, AC24, AC27 |
| 2.6 | AC25, AC26 |
| 3.1 | AC29 |
| 3.2 | AC28, AC30 |

AC1–AC31 all placed. **No unplaceable ACs.** (AC25 intentionally spans 2.4 establish + 2.6 formalize;
AC31 is primary in 2.1 and a standing re-confirmation at every desktop slice.)

---

## Parallelization map

Honest about the shared **desktop app shell**: most Phase 2 slices mutate the same window
composition / editor pane / results-panel state, so they are conflict-prone — analogous to the
routing-MVP runner-core caveat. Flagged below as serialize-or-modularize rather than free parallel.

**Strictly serial (no parallelism):**
- **0.1 → 1.1** — 1.1 builds on 0.1's module boundary. Both are hard prerequisites for ALL desktop
  slices; nothing in Phase 2/3 may start until 1.1 is green.
- **2.1 is the gate** — must be first in Phase 2 and must pass before 2.2–2.6 or 3.2 begin.
- **2.2 → 2.3 → 2.4 → {2.5, 2.6}** — a genuine dependency chain (shell → editing/dirty → loop →
  rich results / stale). They also all touch the same app shell, so even where a dep edge is weak,
  treat as **serialize-or-modularize**.

**Same-component conflict hotspots (serialize-or-modularize, do NOT claim free parallelism):**
- **2.5 and 2.6 both depend on 2.4 and both touch the results surface** — 2.5 mutates results
  *rendering/styling*, 2.6 mutates results *state* (latest-only + stale). Parallelizable ONLY if the
  results panel is first modularized into render-vs-state; otherwise serialize 2.5 then 2.6.
- **2.3 and 2.4 both touch dirty state + editor surface** — 2.4 consumes 2.3's dirty model; keep
  serial.

**Genuinely parallelizable (low shell conflict):**
- **3.1 (headless discovery classifier)** depends only on `:engine`'s `ManifestParser` and touches
  no app shell — it can be built **in parallel with Phase 2** (e.g. alongside 2.2–2.6) by a separate
  worker, then integrated by 3.2. This is the one clean fan-out edge in the desktop work.
- **3.2** re-enters the shared shell (tree + editor wiring) and depends on both 3.1 and 2.2 — once
  Phase 2's shell stabilizes, 3.2 is a contained addition.

**Recommended critical path:** 0.1 → 1.1 → 2.1(gate) → 2.2 → 2.3 → 2.4 → 2.5 → 2.6 → 3.2, with
**3.1 developed off-path in parallel** and merged at 3.2. Total ~54h of work; critical path ~51h
after pulling 3.1 (~3h) off-path.

---

## Open items (carried; non-blocking)

- **OI-1 / OI-4** — multi-document UX and editor pane composition (tabs vs single switchable pane;
  one surface vs two for XSLT + manifest YAML) are decided in 2.2/2.3 during build, not pre-fixed.
- **OI-2** — in-progress affordance for off-thread Run (spinner / progress bar / disabled Run) is a
  2.4 design choice; AC19 only requires a visible in-progress state + responsiveness.
- **OI-3** — exact theming library (FlatLaf "or equivalent") is confirmed by the 2.1 spike.
- **Deferred (PRD Out of Scope) are NOT sliced**: run-on-save, run-from-buffer, single-case run,
  run-all/project-aggregate, structured `SelectionDiff`, inline editor markers, click-to-navigate,
  run history (i/ii/iii), structured-form editor, project-level config, native installer,
  macOS/Linux. The tracked `SelectionDiff` follow-up remains a future enhancement, not a v1 slice.

## Source reference

PRD: [`prd/desktop-workbench.md`](../prd/desktop-workbench.md) (committed 6521f5b).
Stage-1 grill: [`grill-me-sessions/02-desktop-workbench.md`](../grill-me-sessions/02-desktop-workbench.md).
