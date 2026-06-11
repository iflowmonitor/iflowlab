# PRD — Desktop Workbench (iflowlab)

> Stage 2 (write-a-prd) of the iFlowMonitor spec workflow for **iflowlab**.
> Authoritative input: `grill-me-sessions/02-desktop-workbench.md` (Stage 1, committed ebf154a),
> including its Follow-up / Non-goals section. Background: `BRAINSTORM-CHECKPOINT-desktop-workbench.md`
> (Stage 0). Where the two differ, the Stage-1 transcript supersedes the checkpoint.
> Prior art it builds on: the shipped routing-MVP engine (`src/`, `prd/routing-mvp.md`).
> This PRD is intended to read standalone.

---

## Problem Statement

The routing-MVP shipped a **CLI engine**: it loads a routing stylesheet, runs it on Saxon-HE
9.9.1, applies the pass gates (XSD-valid + exact selection match), and prints per-case PASS/FAIL
to stdout with a CI-suitable exit code. That is excellent for regression-in-CI, but it is a poor
**authoring** surface.

A routing-XSLT developer iterating on a stylesheet today must: edit the XSLT in a separate editor,
switch to a terminal, type a Gradle invocation, read wall-of-text stdout, mentally diff "expected
vs got," switch back to the editor, and repeat. There is no fast, integrated **edit → run → see
results → iterate** loop. The author cannot see syntax-highlighted XSLT, cannot run with a
keystroke, cannot scan structured pass/fail at a glance, and gets no signal that the results on
screen are stale relative to the edit they just made.

The author needs an **interactive desktop workbench** whose centerpiece is authoring routing XSLT:
a real code editor, a one-keystroke run against the *existing* engine, and a structured results
view — all **offline and deterministic** (drives the engine only; no SAP runtime, no network).

## Solution Sketch

A **Compose for Desktop (JVM)** application that reuses the routing engine **in-process** and
presents the authoring loop as a GUI. The engine is the ground truth; the GUI is a frontend with
**no routing logic of its own**.

Delivered in an agreed four-phase sequence (formal vertical slicing / parallelization is Stage 3,
`prd-to-plan`; this PRD fixes the sequence and its acceptance criteria):

1. **Phase 0 — Module split (refactor-first PR).** Extract the engine into a reusable `:engine`
   library, leaving a thin `:cli` app. Pure move of existing code, zero new code, full suite green,
   byte-identical CLI stdout. No `:desktop` module is created here.
2. **Phase 1 — Typed result model (DL3).** Reshape the engine so `run(manifestPath)` returns a
   typed `SuiteResult`/`CaseResult` (errors as nullable strings, never thrown) instead of printing.
   Rendering and the exit-code mapping move into `:cli`; a golden/characterization test proves the
   CLI output is byte-identical.
3. **Phase 2 — Slice 1: single-manifest edit→run.** The first runnable desktop slice, gated at the
   top by an **editor-embedding go/no-go spike**. Open one manifest, edit its XSLT (and the manifest
   YAML) in a syntax-highlighted editor, Run with a keystroke (off the UI thread, save-then-run),
   and see a structured results panel.
4. **Phase 3 — Slice 2: project navigation.** Open a **folder** as a project, scan it for manifests
   (no new project-file format), and navigate manifests + the XSLTs they reference in a tree.

The editor is **RSyntaxTextArea embedded via Compose `SwingPanel`** — as of current Compose
Multiplatform (1.11.x) there is no mature Compose-native code editor, and SwingPanel interop is
first-class. v1 runs from Gradle (`./gradlew :desktop:run`), validated on Windows 11.

## User Stories

1. As a routing-XSLT developer, I want to open a **folder of routing test suites as a project**, so
   that I can navigate and work across all my manifests and stylesheets in one workbench. *(AC28)*
2. As a routing-XSLT developer, I want suites **discovered by scanning the folder** (no extra
   project file), so that I don't maintain a second source of truth. *(AC28, AC29)*
3. As a routing-XSLT developer, I want a **broken/malformed suite to still appear in the tree,
   flagged**, rather than silently vanish, so that I can find and fix it. *(AC29)*
4. As a routing-XSLT developer, I want to edit my routing XSLT in a **syntax-highlighted code
   editor** with a gutter and inline error markers, so that authoring feels like a real IDE.
   *(AC14)*
5. As a routing-XSLT developer, I want to **edit the test manifest as raw YAML** in the same editor,
   so that I can add/adjust test cases without a separate UI. *(AC15)*
6. As a routing-XSLT developer, I want to **select which referenced stylesheet to edit** from across
   the project's manifests, so that multi-XSLT suites are first-class. *(AC30)*
7. As a routing-XSLT developer, I want an **explicit Run** action on a button and on **both F5 and
   Ctrl+Enter**, so that I can trigger a run by mouse or keyboard. *(AC16, AC20)*
8. As a routing-XSLT developer, I want Run to **save my dirty buffers first** and to **show which
   documents are modified**, so that what runs is always what I see and file mutation is never
   silent. *(AC17, AC18)*
9. As a routing-XSLT developer, I want runs to execute **off the UI thread** with an in-progress
   state, so that the window never freezes while Saxon runs. *(AC19)*
10. As a routing-XSLT developer, I want **per-case PASS/FAIL results in a structured panel** with
    badges and color, so that I can scan outcomes faster than reading stdout. *(AC20, AC21, AC22,
    AC27)*
11. As a routing-XSLT developer, I want **each error class** (XSD / selection mismatch / engine
    error / config error) styled **distinctly**, so that I can tell at a glance what kind of failure
    I'm looking at. *(AC22, AC23)*
12. As a routing-XSLT developer, I want **warnings to show even on a passing case**, so that a
    passing-with-warnings case doesn't hide them. *(AC24)*
13. As a routing-XSLT developer, I want the results panel **marked stale when I edit after a run**,
    so that I'm never misled by results that predate my current buffer. *(AC26)*
14. As a routing-XSLT developer, I want the **latest run's results** held in view (overwritten each
    run), so that the workbench stays focused on the current state without history clutter. *(AC25)*
15. As a routing-XSLT developer, I want to **launch the workbench with a single Gradle command**, so
    that I can run it without installing anything. *(AC13)*
16. As an iflowlab maintainer, I want the engine extracted into an **`:engine` library** consumed by
    `:cli` and `:desktop`, so that no routing logic can leak into the GUI. *(AC1, AC3)*
17. As an iflowlab maintainer, I want the module split to be a **pure move with the full suite green
    and byte-identical CLI stdout**, so that the refactor provably changes no behavior. *(AC1, AC2,
    AC4)*
18. As an iflowlab maintainer, I want the engine to **return a typed `SuiteResult` instead of
    printing**, so that both the CLI and the GUI consume structured results without parsing stdout.
    *(AC5, AC6, AC7, AC8)*
19. As an iflowlab maintainer, I want the **exit-code mapping to live in `:cli`** using named
    constants, so that the engine stays free of OS/CLI conventions. *(AC9, AC10)*
20. As an iflowlab maintainer, I want a **golden/characterization test** asserting byte-identical CLI
    stdout after the DL3 reshape, so that the rendering migration is safe. *(AC11)*
21. As a routing-XSLT developer, I want the **first runnable slice to be a single-manifest edit→run
    loop**, so that the core loop is proven before project navigation is built. *(AC14, AC20)*
22. As an iflowlab maintainer, I want the **editor embedding proven by a go/no-go spike** before
    building on it, so that an unworkable interop seam is surfaced early, not papered over. *(AC12)*
23. As a routing-XSLT developer on Windows, I want the workbench **validated on Windows 11**
    (including HiDPI and theming), so that it works on my actual machine. *(AC31)*

## Implementation Decisions

Decisions are taken **verbatim** from the Stage-1 transcript where the grill resolved them.
Decision IDs below are local PRD tags that re-map the transcript's branch tags (DL1–DL3, OQ1–OQ7);
see Correction Log C2. The **two reversals** of checkpoint leans / earlier recommendations are
recorded as the resolved state: **(D5)** workspace = project / scanned folder (NOT single-manifest),
and **(D4)** exit-code mapping lives in `:cli` (NOT on `SuiteResult` in `:engine`).

### D1 — Stack: Compose for Desktop (DL1)
The stack is **Compose for Desktop (JVM)**, engine reused **in-process**. This is locked by the
editor decision (D2): RSyntaxTextArea-via-SwingPanel presupposes a Compose host. JavaFX (older) and
web/Electron (process + language boundary, no gain) are rejected.

### D2 — Editor: RSyntaxTextArea via SwingPanel + spike hard-gate (OQ1)
The editor is **RSyntaxTextArea embedded via Compose `SwingPanel`** for v1 — the only mature option
delivering syntax highlighting + gutter + inline error markers today without building an editor
engine. Context7 confirmed (Compose Multiplatform 1.11.x): no mature Compose-native code editor;
`compose-rich-editor` is a prose/rich-text editor, not a code editor; SwingPanel interop is
first-class and documented.

- **Spike is a HARD GATE**, not a formality — the first thing in Phase 2, before anything is built
  on the editor. It must prove all four: (a) Compose↔Swing **focus/keyboard handoff** (typing,
  editor shortcuts, tab focus in/out); (b) **text in + syntax highlighting and error markers out**
  via the RSyntaxTextArea API; (c) **HiDPI scaling**; (d) acceptable **theming** (FlatLaf or
  equivalent). If any is unworkable, **STOP and surface it** — do not paper over the seam.
- **Fallback escalation** (named, not committed now): primary fallback = **pure Swing +
  RSyntaxTextArea** (editor native, no interop seam, FlatLaf); premium fallback =
  **Monaco-in-JCEF**, only if a web-grade editor proves necessary.
- **Rejected:** `BasicTextField` + custom token coloring for v1 — no gutter/markers/bracket
  matching makes it a glorified TextField and undercuts the centerpiece.

### D3 — Module split: :engine / :cli / :desktop (DL2)
Split the single module into **`:engine` (library) + `:cli` (app) + `:desktop` (app)**; `:cli` and
`:desktop` depend on **`:engine`'s public API only**, so "no routing logic in the desktop layer" is
compiler-enforced. `:cli` stays a separate thin app (not folded into `:engine`) to keep
`exitProcess`/stdout entry-point concerns out of the library and preserve two-consumer pressure on
the engine API.

- **Sequencing:** the module split is **PR N**; the DL3 reshape (D4) is **PR N+1**; both land
  **before** the first desktop slice. They are separate, not folded.
- The refactor PR (N) moves code into `:engine` + `:cli` **only** — pure move, **zero new code**.
  It does **NOT** create `:desktop` (that would break the no-new-code property and leave a dangling
  stub on main). `:desktop` is born in Phase 2 as genuine new code; `settings.gradle.kts` gains
  `:desktop` then.
- **`:cli` behavior is frozen** — identical stdout; the typed model is added *alongside* the
  strings; no CLI output changes are in workbench scope.
- "Zero behavior change" is **verified, not asserted**: full existing suite green after the move
  (engine tests → `:engine`; CLI-specific tests stay in `:cli`), and byte-identical `:cli` stdout.
  The one unavoidable behavior-adjacent change — run invocation moves from `gradlew run` →
  `gradlew :cli:run` — is named explicitly and README + examples updated in the same PR.

### D4 — Typed result model (DL3) — exit-code mapping in :cli (REVERSAL)
**`RoutingRunner.run(manifestPath): SuiteResult`** — the engine becomes **pure**: no `Appendable`,
no printing, no `exitProcess`. Rendering and process control live only in `:cli`.

- **Shape:** `SuiteResult { cases: List<CaseResult>, configError: String? }`;
  `CaseResult { name, xslt, headerOnly, gateResults: List<GateResult>, engineError: String? }` with
  `passed = engineError == null && gateResults.none { it.failed }`. The existing typed
  `GateResult`/`GateContext` model is reused.
- **Errors as nullable strings, not thrown:** `configError` (suite-level manifest/config failure;
  `cases` empty when set) and `engineError` (per-case stylesheet failure). `run` never throws an
  engine/config exception out — so the GUI never catches one to show a red case.
- **REVERSAL — exit code lives in `:cli`, not `:engine`.** The mapping (config → 2, any-fail → 1,
  ok → 0) is a CLI/OS convention. `:engine` exposes **facts only** (`configError`, per-case
  `passed`, suite failure derivable). `:cli` maps facts → integer using named constants
  **`EXIT_OK` / `EXIT_FAIL` / `EXIT_CONFIG`** (not literals). The GUI needs no exit code, confirming
  it is not engine-layer.
- **Rendering boundary:** a renderer in `:cli` reproduces today's stdout; `:desktop` renders
  straight off `GateResult.findings`/`warnings` — **no string parsing ever**.
- **Diffs deferred (DL3a):** v1 ships `GateResult.findings: List<String>` as-is (rendered as a text
  list); gate outputs are **not** redesigned. A structured `SelectionDiff` is a tracked follow-up
  (Out of Scope).
- **Batch rendering (DL3b):** collect all, then render; byte-identical if the renderer iterates in
  the same order; no callback seam in the pure engine. An optional progress callback is deferred.
- **Acceptance = golden/characterization test (DL3c):** model + `:cli` rewire land in the same PR
  N+1; a test captures today's stdout for the example suite(s) and asserts the new render path is
  **byte-identical**.

### D5 — Workspace model: project / scanned folder (OQ3) (REVERSAL)
**REVERSAL — the v1 workspace model is a PROJECT, not a single open manifest** ("open one manifest"
is too thin for the full workbench).

- **Project = a FOLDER (workspace root) opened in the workbench, discovered by SCANNING.** No new
  `.iflowlab-project` file, no second source of truth. The workbench scans the opened folder for
  manifests and shows them — plus the XSLTs they reference — in a sidebar/tree. `baseDir` resolution
  stays per-manifest as today.
- **Delivered loop-first:** even though the model is "project," the **first runnable slice is
  single-manifest edit→run** (Phase 2), carrying the editor spike, proving the core loop before any
  IA is built. **Folder-scan + tree navigation is the second slice** (Phase 3) — in v1 scope.
- **Editor scope** = the distinct XSLTs referenced across the project's manifests (per-test
  `tc.xslt ?: manifest.xslt` makes multi-XSLT fall out; the project widens it from one manifest to
  several). The selected stylesheet opens in the editor.
- **Manifest editing** = raw YAML in the same RSyntaxTextArea (YAML mode); existing
  `ManifestException` messages surface parse errors. A structured-form test-case editor is deferred.
- **Manifest discovery — two-tier attempt-parse-and-list.** `ManifestParser` is strict (needs a
  `tests:` list), so arbitrary YAML can't false-positive. To avoid a broken-but-intended suite
  silently vanishing: (1) shallow candidate check — top-level YAML is a mapping with a `tests:` key
  → list it; (2) full parse → valid vs **shown-as-broken node**. No false positives, no renaming,
  broken suites still appear (flagged). A `*.suite.yaml` convention stays available as a later opt-in
  if scans get slow.
- Deferred under this model: **project-level config files** and the **structured-form editor**.

### D6 — Run model: explicit Run, save-then-run, whole-suite, off-thread (OQ2)
**Explicit Run + save-then-run + whole-suite**, reusing the D4 engine (`run(manifestPath):
SuiteResult`) with **zero API change**.

- **Trigger = explicit Run** (run-on-save deferred). Bound to **BOTH F5 and Ctrl+Enter** (F5 is
  overloaded across IDEs; Ctrl+Enter is the clean "execute" idiom; both cover muscle memory). The
  **Run button is the primary discoverable trigger**; shortcuts are accelerators.
- **Save-then-run** — persist dirty buffer(s) to disk, then `run(manifestPath)`. Disk = what ran; no
  stale-run ambiguity. **Save-on-Run must be VISIBLE, not silent:** a per-document dirty/modified
  indicator, and the fact that Run persists the dirty buffer(s) first (XSLT and/or manifest) is
  surfaced. That defuses the file-mutation risk.
- **Whole-suite run** (suites are small/fast). Single-case run deferred (needs a case-subset entry
  point).
- **Threading (load-bearing, NOT deferred):** Run executes **off the UI thread** (background
  coroutine/dispatcher); the UI shows an in-progress state and renders `SuiteResult` on completion.
  Load-bearing because rendering is batch — on-thread Saxon would freeze the window on every Run.
  **Never run Saxon on the composition/UI thread.**
- **run-from-in-memory-buffer is deferred** — it needs a new engine entry point accepting in-memory
  strings; **no engine API expansion in v1**.

### D7 — Error surfacing: structured results panel (OQ5)
**v1 = a results panel as the single, complete surface.** It renders the whole `SuiteResult`:
per-case PASS/FAIL badge, stylesheet name, `headerOnly` label, each `GateResult`'s
`message`+`findings`+`warnings`, plus `engineError` and `configError` as their own rows — all four
error classes on one surface.

- **Verbatim DATA, structured PRESENTATION.** `findings`/`warnings` strings render unchanged (engine
  is ground truth), but the panel is **NOT a stdout reprint** — real visual structure: pass/fail
  badges, color, per-case grouping, and **visually distinct styling per error class** (XSD /
  selection / engineError / configError).
- **Selection mismatch (coherence with D4/DL3a):** surfaces in v1 as the existing `findings` text.
  The prettier expected-vs-actual visual diff (add/remove coloring) is **gated on the deferred
  structured `SelectionDiff`** — v1 makes no visual-diff promise.
- **Warnings render even on a PASS case** (distinct styling, e.g. a warning badge), so a
  passing-with-warnings case doesn't swallow them.
- **Inline editor markers deferred**, and when built, scoped **only to errors carrying a real source
  position** (XSD/XML parse `line:col`); semantic selection mismatches stay panel-only forever.
  (Impl note for that later slice: first verify whether `XsdGate` captures Saxon's `line:col` or only
  a message — markers are infeasible without the position.)
- **Click-to-navigate deferred** (jump from a failed case to the manifest test / stylesheet); v1
  shows case name + stylesheet as plain text.

### D8 — Run history: latest-only + stale marker (OQ4)
**v1 = latest result only**, held in memory, overwritten on each Run. No history list, no
persistence. History is orthogonal to the core loop and cheap-to-reverse (the clean data classes
make kotlinx.serialization JSON near-free later), so deferral is safe.

- **Stale-results marker (v1, loop-integrity, NOT history):** after a Run, if any document goes
  dirty (edited since that Run), the results panel is marked **stale ("re-run")**. Ties to the D6
  dirty indicator; keeps latest-only honest — the panel must never silently present results that
  predate the current buffer.
- Deferred history rungs, reprioritized: when history is built, go **straight to run-to-run
  regression diff** (the only feature that motivates history for authoring); a session recent-runs
  list may never be worth building; persistence only if cross-session comparison is wanted.

### D9 — Packaging: run-from-Gradle, Windows-first (OQ6)
**v1 = run-from-Gradle (`./gradlew :desktop:run`)** — the Compose plugin run task, the same task the
editor spike uses from Phase 2.

- Native **Windows `.msi` via jpackage** (`packageDistributionForCurrentOS`, with
  icon/signing/`upgradeUuid`) is **deferred** until there's a non-dev audience to distribute to.
- **Windows-first:** v1 is validated only on Windows 11, including the spike's HiDPI + FlatLaf
  criteria. macOS/Linux come "for free" via `:desktop:run` but are not claimed or tested in v1.
- **Doc note:** README / run instructions reflect `gradlew.bat :desktop:run` (PowerShell),
  consistent with the `:cli:run` invocation update from the D3 refactor PR.

### D10 — Delivery boundary: bounded v1 PRD, Phase 0–3 sequence (OQ7)
This is a **bounded v1 PRD** — it fixes the v1 contract; it is not a full-workbench roadmap.
**Bounded ≠ single-manifest** — the project model (D5) stays in v1, delivered loop-first. The agreed
phase sequence (formal slicing/parallelization is Stage 3, `prd-to-plan`):

- **Phase 0** — module split (PR N; D3). ACs externally observable: full suite green after the move;
  `:cli` byte-identical stdout.
- **Phase 1** — DL3 typed model + `:cli` rewire + golden test (PR N+1; D4).
- **Phase 2 / slice 1** — single-manifest edit→run, carrying the **editor spike hard-gate** at the
  top (D2, D6, D7).
- **Phase 3 / slice 2** — project folder-scan + tree navigation (D5).

The two refactor PRs are **Phases 0–1 inside this PRD**, not a separate ticket — load-bearing (the
spike gate sits immediately after Phase 1) and the PRD→plan flow needs the whole critical path in
one place.

## Testing Decisions

A good test asserts **externally observable behavior**, not implementation detail.

- **Phase 0 (module split) — characterization by output.** The existing engine/runner test suite is
  the safety net: it relocates into `:engine` and must stay green after the move. CLI behavior is
  pinned by a **byte-identical stdout** check for the example suite(s) (prior art: routing-MVP's
  runner tests already assert runner output lines, e.g. `RoutingRunnerTest`, `XsdGateRunnerTest`).
- **Phase 1 (DL3) — golden/characterization test.** Capture today's `:cli` stdout for the example
  suite(s) before the reshape; assert the post-reshape render path is byte-identical. Additional
  engine-level tests assert the typed contract: a parse failure yields `configError != null` with
  empty `cases` and **no thrown exception**; a stylesheet failure yields `engineError != null` with
  **no thrown exception**; `run` writes nothing to a captured output stream (it is pure).
- **Phase 1 — exit-code mapping (in `:cli`).** A `:cli` test asserts the named constants map
  facts → `0/1/2` unchanged from routing-MVP (all-pass → `EXIT_OK`, any-fail → `EXIT_FAIL`, config
  error → `EXIT_CONFIG`).
- **Phases 2–3 (desktop) — behavior over pixels.** The deepest testable seam is the **engine**,
  already covered. Desktop logic worth unit-testing in isolation (no UI): **manifest discovery**
  (two-tier candidate/broken/ignored classification over a fixture folder) and **dirty/stale state**
  (a buffer model where editing sets dirty, Run clears dirty and records a run, and a post-run edit
  sets stale). The **editor spike** is validated by demonstration on Windows 11 against its four
  criteria, not by an automated assertion. UI rendering (badge/color/styling) is verified by
  inspection, not asserted pixel-by-pixel.
- **Modules to test:** `:engine` (relocated suite + new typed-contract tests), `:cli` (golden stdout
  + exit-code mapping), and the headless desktop logic (discovery, dirty/stale state). The editor
  interop seam is spike-validated, not unit-tested.

## Acceptance Criteria

Each criterion is externally observable (a Gradle task result, a file's content, a typed engine
return value, an exit code, or an on-screen label/state).

**Phase 0 — module split (D3)**

1. **AC1** — After the split, the full existing test suite runs across `:engine` (+ any `:cli`
   tests) and **all tests pass**. *(Stories 16, 17)*
2. **AC2** — After the split, `:cli` produces **byte-identical stdout** to the pre-split CLI for the
   example suite(s) (golden capture). *(Story 17)*
3. **AC3** — The split PR adds `:engine` and `:cli` only; `settings.gradle.kts` does **not** contain
   `:desktop` after this PR. *(Story 16)*
4. **AC4** — The run invocation is `gradlew :cli:run` (not `gradlew run`); the README and the
   `examples/` references are updated to it in the same PR. *(Stories 15, 17)*

**Phase 1 — typed result model (D4)**

5. **AC5** — `run(manifestPath)` returns a `SuiteResult` value and performs **no printing and no
   `exitProcess`**; a test running a suite captures **empty** output from the engine. *(Story 18)*
6. **AC6** — `SuiteResult` exposes `cases: List<CaseResult>` and `configError: String?`; each
   `CaseResult` exposes `passed`, `engineError: String?`, and its `gateResults`. *(Story 18)*
7. **AC7** — A manifest parse/config failure yields a `SuiteResult` with **non-null `configError`
   and empty `cases`**, and `run` does **not** throw. *(Story 18)*
8. **AC8** — A case whose stylesheet throws yields a `CaseResult` with **non-null `engineError`**,
   and `run` does **not** throw. *(Story 18)*
9. **AC9** — The exit-code mapping lives in `:cli` using named constants
   **`EXIT_OK`/`EXIT_FAIL`/`EXIT_CONFIG`**; `:engine` exposes **no** exit-code member. *(Story 19)*
10. **AC10** — CLI exit codes are unchanged from routing-MVP: **0** all pass, **1** any case fails,
    **2** config error. *(Story 19)*
11. **AC11** — A golden/characterization test captures pre-reshape `:cli` stdout for the example
    suite(s) and asserts the post-reshape render is **byte-identical**. *(Story 20)*

**Phase 2 — slice 1: editor spike + single-manifest edit→run (D2, D6, D7)**

12. **AC12 (GO/NO-GO GATE)** — Before any editor-dependent feature is built, a spike demonstrates on
    Windows 11 **all four**: (a) Compose↔Swing focus/keyboard handoff in/out of the editor;
    (b) syntax highlighting + error markers via the RSyntaxTextArea API; (c) HiDPI scaling;
    (d) FlatLaf (or equivalent) theming. If **any** criterion fails, feature work **STOPS** and the
    failure is surfaced; the fallback escalation is pure Swing+RSTA, then Monaco-in-JCEF. *(Story 22)*
13. **AC13** — The workbench launches via **`gradlew :desktop:run`**. *(Story 15)*
14. **AC14** — Opening a single manifest shows its referenced XSLT in a **syntax-highlighted**
    editor; typing updates the buffer and highlighting. *(Stories 4, 21)*
15. **AC15** — The manifest is editable as **raw YAML** in the editor (YAML highlighting); a manifest
    parse error surfaces the engine's `ManifestException` message. *(Story 5)*
16. **AC16** — A **Run button** is present and Run is triggered by **both F5 and Ctrl+Enter**.
    *(Story 7)*
17. **AC17** — A document modified since load/last save shows a **per-document dirty indicator**.
    *(Story 8)*
18. **AC18** — Run **persists all dirty buffers to disk before executing**, visibly (the dirty
    indicators clear on Run); the file on disk that the engine reads matches the editor. *(Story 8)*
19. **AC19** — Run executes **off the UI thread**: the UI shows an in-progress state during the run
    and remains responsive (the window does not freeze). *(Story 9)*
20. **AC20** — Run executes the **whole suite** of the selected manifest via the engine's
    `run(manifestPath)` and renders the returned `SuiteResult`. *(Stories 7, 10, 21)*
21. **AC21** — The results panel shows, per case, a **PASS/FAIL badge**, the **stylesheet name**, and
    a **header-only label** where applicable. *(Story 10)*
22. **AC22** — The panel renders each `GateResult`'s **findings and warnings**, and renders
    **`engineError` and `configError` as their own rows**. *(Stories 10, 11)*
23. **AC23** — Each error class (**XSD / selection mismatch / engineError / configError**) is
    rendered with **visually distinct styling** (not a stdout reprint). *(Story 11)*
24. **AC24** — **Warnings render even on a PASS case**, with distinct (warning) styling. *(Story 12)*
25. **AC25** — The results panel reflects **only the latest run**; a new Run overwrites the previous
    results (no history list, no persistence). *(Story 14)*
26. **AC26** — After a Run, editing any document (making it dirty) marks the results panel **stale
    ("re-run")**; the panel never silently presents results predating the current buffer. *(Story 13)*
27. **AC27** — A selection mismatch renders as the engine's **`findings` text** (no visual
    add/remove diff in v1). *(Story 10)*

**Phase 3 — slice 2: project navigation (D5)**

28. **AC28** — Opening a **folder** scans it and lists the discovered manifests — plus the XSLTs they
    reference — in a sidebar/tree; **no project file is created**. *(Stories 1, 2)*
29. **AC29** — Discovery is **two-tier**: a YAML file whose top level is a mapping containing a
    `tests:` key is **listed**; a listed file that fails full parse appears as a **broken node** (not
    omitted); non-candidate YAML is **ignored**. *(Stories 2, 3)*
30. **AC30** — Selecting a stylesheet in the tree opens it in the editor; the editor scope spans the
    **distinct XSLTs referenced across the project's manifests**. *(Story 6)*

**Cross-cutting — platform (D9)**

31. **AC31** — The workbench is **validated on Windows 11**, including the spike's HiDPI and FlatLaf
    criteria; macOS/Linux are **not** claimed or tested in v1. *(Story 23)*

## Prior Art

- **Stage-0 brainstorm** (`BRAINSTORM-CHECKPOINT-desktop-workbench.md`): DD1/DD2, leans DL1–DL3,
  open questions OQ1–OQ7, guiding principles.
- **Stage-1 grill** (`grill-me-sessions/02-desktop-workbench.md`, committed ebf154a): the
  authoritative resolution of DL1–DL3 and OQ1–OQ7, including the two reversals and the full Non-goals
  set (this PRD's source).
- **Routing-MVP engine** (`src/`, `prd/routing-mvp.md`): the shipped CLI engine the workbench builds
  on — Saxon-HE 9.9.1, YAML manifest, the typed `Gate`/`GateResult`/`GateContext` seam, the
  `RoutingRunner` loop, and the XSD + exact-selection gates. The workbench reuses this engine
  in-process and adds no routing logic.
- **Context7 (tech-currency)**: Compose Multiplatform 1.11.x — no mature Compose-native code editor;
  SwingPanel interop is first-class; `compose.desktop` Gradle DSL with `:desktop:run` and
  `packageDistributionForCurrentOS`; Compose's read-only `codeviewer` example as rendering prior art
  (a viewer, not an editor).
- **Reference prototype (read-only):** `C:\Users\pavol\Documents\iflowmonitor\`.

## Out of Scope

Deferred or explicitly excluded for v1 (the full Non-goals set, carried verbatim from the
transcript). Each is a named later enhancement unless marked excluded.

1. **run-on-save** (debounced, behind a toggle).
2. **run-from-in-memory buffer** (needs a new engine entry point accepting in-memory strings; **no
   engine API expansion in v1**).
3. **single-case run** (needs a case-subset entry point).
4. **run-all-suites / project-level aggregate results** — v1 Run is whole-**SUITE** on the selected
   manifest only; project-wide run/aggregate is deferred so the project-model boundary isn't
   ambiguous.
5. **structured `SelectionDiff`** + visual add/remove diff coloring (the GUI diff UX will want it;
   **tracked follow-up** — see Further Notes).
6. **inline editor markers** (only for errors with a real `line:col`; verify `XsdGate` captures it).
7. **click-to-navigate** (results → manifest test / stylesheet; needs source-position mapping).
8. **run history** — (i) session recent-runs, (ii) persisted history, (iii) run-to-run diff
   (build (iii) first when history is built).
9. **structured-form test-case editor** (v1 edits the manifest as raw YAML).
10. **project-level config file** (default Saxon opts, manifest include/exclude; v1 is zero-config
    scan).
11. **native jpackage installer** (Windows `.msi` with icon/signing/`upgradeUuid`).
12. **macOS/Linux support** (v1 is Windows-first).

Also deferred (not promised): an **optional per-case progress callback** in the engine (v1 ships
batch rendering).

## Prerequisites

1. **Routing-MVP engine** (shipped) — the in-process dependency the workbench drives. No SAP runtime,
   no network (DD2: offline/deterministic).
2. **Compose for Desktop** via the `org.jetbrains.compose` Gradle plugin (`compose.desktop`
   application DSL; `:desktop:run`). JDK toolchain 25 (current build) is compatible.
3. **RSyntaxTextArea** (Swing code editor) + **FlatLaf** (or equivalent) for theming — the editor and
   look-and-feel dependencies for the `:desktop` module.
4. **Editor-embedding spike PASS (AC12)** — the go/no-go gate that must pass on Windows 11 before any
   Phase 2 editor-dependent feature work proceeds.
5. PRs N (module split) and N+1 (DL3 typed model) — internal prerequisites that must land before the
   first desktop slice (Phases 0–1 of this PRD).

## Correction Log

Decisions taken or clarified during PRD writing that are not stated verbatim in the transcript, and
inline open items where context is missing (per the one-shot rule, recorded — not asked):

- **C1 — User-story↔AC derivation.** The transcript did not enumerate user stories or acceptance
  criteria; these are derived from the resolved decisions. Each story maps to ≥1 AC and each AC is
  externally observable. No new product decisions were introduced — any apparent novelty is a
  restatement of a transcript decision as an observable test.
- **C2 — Decision-ID renumbering.** This PRD's `D1–D10` are local IDs re-tagging the transcript's
  branch outcomes (DL1–DL3, OQ1–OQ7) for readability. The two **reversals** are preserved as the
  resolved state and flagged inline: **D4** (exit-code mapping in `:cli`, not on `SuiteResult` in
  `:engine`) and **D5** (workspace = project / scanned folder, not single-manifest).
- **C3 — AC3 observability of the "no `:desktop` stub" rule.** The transcript states the refactor PR
  must not create `:desktop`; AC3 operationalises this as an inspectable fact (`settings.gradle.kts`
  contents) rather than a process assertion. No decision content changed.
- **OI-1 (open item) — multi-document editor UX.** The transcript fixes the editor *component* and
  *scope* but not whether multiple open documents are presented as tabs vs a single switchable pane.
  Left open for design in Phase 2/3; does not affect any AC.
- **OI-2 (open item) — in-progress affordance.** AC19 requires an off-thread run with a visible
  in-progress state, but the exact affordance (spinner, progress bar, disabled Run button) is
  unspecified. Left to Phase 2 design.
- **OI-3 (open item) — theming library.** D2/AC12 say "FlatLaf or equivalent." The exact
  look-and-feel library is confirmed by the spike, not fixed here.
- **OI-4 (open item) — editor pane composition.** Whether the XSLT and the manifest YAML share one
  editor surface or occupy separate panes is unspecified; both satisfy AC14/AC15. Left to Phase 2
  design.

## Further Notes

- **Tracked follow-up to file:** a structured **`SelectionDiff`** (typed expected-vs-actual
  receiver/interface sets) enabling add/remove visual diff coloring in the results panel — gated by
  D4/DL3a's decision to ship `findings: List<String>` in v1. The GUI diff UX will want it.
- **Engine is ground truth; the GUI is a frontend** (guiding principle). No routing logic lives in
  the desktop layer; the module boundary (D3) makes this compiler-enforced.
- **Stage 3 (`prd-to-plan`) is not started by this PRD.** The Phase 0–3 sequence and ACs here are the
  input to formal vertical slicing / parallelization.

## Grill Session Reference

Stage-1 transcript (authoritative source for all decisions above):
[`grill-me-sessions/02-desktop-workbench.md`](../grill-me-sessions/02-desktop-workbench.md)
(committed ebf154a).
Stage-0 background:
[`BRAINSTORM-CHECKPOINT-desktop-workbench.md`](../BRAINSTORM-CHECKPOINT-desktop-workbench.md).
