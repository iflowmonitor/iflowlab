# Grill-me Session 02 — Desktop Workbench

> Stage 1 (grill-me) of the iFlowMonitor spec workflow for iflowlab.
> Input artefact: BRAINSTORM-CHECKPOINT-desktop-workbench.md (Stage 0), repo main @ 879d8e4.
> Scope: desktop workbench — an interactive GUI for authoring routing XSLT on top of the
> routing-MVP engine. XSLT editor is the centerpiece (DD1).
> Resolves: DL1, DL2, DL3, OQ1–OQ7. All checkpoint open questions closed.
> Tech-currency checks via Context7 (Compose Multiplatform 1.11.x): OQ1 editor, OQ6 packaging.

## Status legend

- RESOLVED — decision reached this session
- REVERSAL — overrides a checkpoint lean (DL*) or my recommendation
- NEW — new decision/branch surfaced during the grill (not in the checkpoint)
- DEFERRED — explicitly punted to a named later enhancement

---

## Context findings (grounding, before Q1)

**Context7 — OQ1 (editor):** As of current Compose Multiplatform (1.11.x) there is **no
mature Compose-native code editor** (syntax highlighting + gutter + error markers + bracket
matching). `mohamedrejeb/compose-rich-editor` is a *rich-text* editor (prose styling), not a
code editor — wrong tool. Compose Desktop's **SwingPanel interop is first-class and
documented** — the supported way to host a Swing component. → backs the DL1/OQ1 lean.

**Context7 — OQ6 (packaging):** `org.jetbrains.compose` plugin, `compose.desktop { application }`
DSL; `./gradlew :desktop:run` (dev loop); `./gradlew :desktop:packageDistributionForCurrentOS`
→ native installers (`windows {}` DSL: icon/shortcut/menu/upgradeUuid). jpackage under the
hood (present in any JDK ≥14; toolchain 25 fine). Compose ships a read-only `codeviewer`
example — prior art for rendering, but a viewer not an editor; does not disturb OQ1.

**Codebase — DL3 reality check:** `gate/Gate.kt` already exposes a typed `Gate` / `GateResult`
/ `GateContext` model; per-case results are already `List<GateResult>` with structured
`findings`/`warnings`. What's missing is the **per-case + suite envelope** — `RoutingRunner.run()`
loops and immediately collapses each case to stdout via `printCase()`, returning only an `Int`.
So DL3 is a **moderate lift** (extract envelope + move rendering), not a rewrite.

**Codebase — manifest model:** A manifest *is* the project descriptor — declares `xslt:`
(suite- or per-test, `tc.xslt ?: manifest.xslt`), the tests, and via `baseDir` the relative-path
root. `ManifestParser` is **strict**: root must be a mapping with a `tests:` list, else it throws.

---

## Branches

### DL1 — Stack — RESOLVED (settled by OQ1)

**Decision:** The stack is **Compose for Desktop (JVM)**, engine reused in-process.
RSyntaxTextArea-via-SwingPanel presupposes a Compose host, so the OQ1 editor choice locks the
stack too — recorded explicitly so it isn't an implicit side effect. JavaFX and web/Electron
rejected (older / process+language boundary, no gain) per the checkpoint.

---

### OQ1 — Editor component (make-or-break) — RESOLVED

**Decision:** **RSyntaxTextArea embedded via Compose `SwingPanel`** for v1. Only mature option
that delivers syntax highlighting + gutter + inline error markers today without building an
editor engine; serves the centerpiece (DD1) directly.

**NEW — spike is a HARD GATE (not a formality):** first thing in the first vertical slice,
before anything is built on the editor. Must prove all four:
- (a) Compose↔Swing focus/keyboard handoff — typing, editor shortcuts, tab focus in/out;
- (b) text in + highlighting and error markers out via RSyntaxTextArea's API;
- (c) HiDPI scaling;
- (d) acceptable theming (FlatLaf or equivalent).
If any is unworkable, **STOP and surface it** — do not paper over the seam.

**NEW — named fallback escalation (not committed now, escape hatch only):**
- Primary fallback: **pure Swing + RSyntaxTextArea** (editor native, no interop seam, FlatLaf).
- Premium fallback: **Monaco-in-JCEF** — only if a web-grade editor proves necessary.

**Rejected:** `BasicTextField` + custom token coloring for v1 — no gutter/markers/bracket
matching makes it a glorified TextField and undercuts DD1.

---

### DL2 — Module split — RESOLVED

**Decision:** Split the single module into **`:engine` (library) + `:cli` (app) + `:desktop`
(app)**; `:cli` and `:desktop` depend on `:engine`'s **public API only** (compiler-enforces
"no routing logic in the desktop layer"). Keeping `:cli` separate (not folded into `:engine`)
keeps `exitProcess`/stdout entry-point concerns out of the library and preserves two-consumer
pressure that keeps the API honest.

**Sequencing (RESOLVED):**
- (a) **PR N** = module split; **PR N+1** = DL3 typed-result reshape. Separate, not folded.
  Both land **before** the first desktop slice.
- (b) **`:cli` behavior frozen** — identical stdout; the DL3 typed model is added *alongside*
  the strings; no CLI output changes in workbench scope.

**NEW — refinements:**
1. The refactor PR (N) moves code into `:engine` + `:cli` **ONLY** — pure move of existing
   code, **zero new code**. Do **NOT** create `:desktop` in this PR (would break the no-new-code
   property and leave a dangling stub on main). `:desktop` is born in slice 1 as genuine new
   code; `settings.gradle.kts` gains `:desktop` then.
2. "Zero behavior change" is **verified, not asserted**: (a) full existing suite green after the
   move (engine tests → `:engine`; CLI-specific tests stay in `:cli`); (b) `:cli` produces
   **byte-identical** stdout. The one unavoidable behavior-adjacent change — run invocation moves
   from `gradlew run` → `gradlew :cli:run` — is named explicitly and README + examples updated in
   the same PR.

---

### DL3 — Typed result model (load-bearing) — RESOLVED

**Decision:** `RoutingRunner.run(manifestPath): SuiteResult` — **pure engine**: no `Appendable`,
no printing, no `exitProcess`. Rendering + process control live only in `:cli`.

```kotlin
// in :engine
data class SuiteResult(
    val cases: List<CaseResult>,
    val configError: String? = null,   // manifest/config failure; cases empty when set
)

data class CaseResult(
    val name: String,
    val xslt: String,
    val headerOnly: Boolean,
    val gateResults: List<GateResult>,  // empty when engineError set
    val engineError: String? = null,
) {
    val passed: Boolean get() = engineError == null && gateResults.none { it.failed }
}
```

Errors as **nullable strings, not thrown** — `configError` (suite-level) and `engineError`
(per-case) — so the GUI never catches an engine exception to show a red case.

**REVERSAL of my proposed shape — exitCode moves OFF `SuiteResult` into `:cli`:** the exit-code
mapping (config→2, any-fail→1, ok→0) is a CLI/OS convention. `:engine` exposes **facts only**
(`configError`, per-case `passed`, suite failure derivable); `:cli` maps facts → integer using
named constants **`EXIT_OK` / `EXIT_FAIL` / `EXIT_CONFIG`** (not literals). The GUI never needs an
exit code — confirming it isn't engine-layer.

**Rendering boundary:** a separate renderer in `:cli` (`CliReporter`) reproduces today's stdout;
`cli/Main.kt` calls `exitProcess(<mapped code>)`. `:desktop` renders straight off
`GateResult.findings`/`warnings` — no string parsing ever.

**Sub-decisions (RESOLVED):**
- (a) **Defer structured diffs.** v1 ships `GateResult.findings: List<String>` as-is; GUI renders
  them as a text list. Do **not** redesign gate outputs now. → tracked follow-up (see Follow-up).
- (b) **Batch rendering.** Collect all, then render; byte-identical if the renderer iterates in
  the same order; avoids a callback seam in the pure engine. **DEFERRED:** an *optional* progress
  callback when suites grow or the GUI wants live per-case progress — not now.
- (c) **Model + `:cli` rewire in the same PR N+1.** No half-migrated engine, no dead code.
  **Acceptance = golden/characterization test:** capture today's stdout for the example suite(s)
  and assert the new render path is **byte-identical**. That golden test is the safety net for
  the whole reshape.

---

### OQ3 — Workspace model — RESOLVED (REVERSAL of my recommendation)

**Decision:** v1 workspace model is a **PROJECT, not a single open manifest.** "Open one
manifest" is too thin for the full workbench (DD1).

- **Project = a FOLDER (workspace root) opened in the workbench, discovered by SCANNING.** No
  new `.iflowlab-project` file, no second source of truth (this half of my recommendation stays).
  The workbench scans the opened folder for manifests and shows them — plus the XSLTs they
  reference — in a sidebar/tree. `baseDir` resolution stays per-manifest as today.
- **Sequencing (loop-first):** even though the model is "project," the **first runnable slice is
  still single-manifest edit→run**, carrying the OQ1 spike, proving the core loop before any IA is
  built. **Folder-scan + tree navigation is the second slice** on top — in v1 scope, delivered
  loop-first.

**Sub-decisions:**
- (a) **REJECT** "one open manifest." Unit of work = an opened **folder/project**, scan-discovered,
  no new on-disk format.
- (b) **Editor scope = the distinct XSLTs referenced across the project's manifests** (per-test
  `tc.xslt ?: manifest.xslt` makes multi-XSLT fall out; the project widens it from one manifest to
  several). The selected stylesheet opens in the editor.
- (c) **Manifest is editable as raw YAML** in the same RSyntaxTextArea (YAML mode); existing
  `ManifestException` messages surface parse errors. Structured-form test-case editor = DEFERRED.
- (d) **REJECT** "multi-suite deferred." Folder/project navigation **IS** in v1 (slice 2). What
  stays deferred: **project-level config files**, and the **structured-form editor**.

**NEW — manifest discovery (settled non-blocking detail): two-tier attempt-parse-and-list.**
`ManifestParser` is strict (needs a `tests:` list), so arbitrary YAML can't false-positive. Naive
parse-and-list's only failure mode is a *broken-but-intended* suite silently vanishing. Fix:
(1) shallow candidate check — top-level YAML is a mapping with a `tests:` key → list it;
(2) full parse → valid vs **shown-as-broken node**. No false positives, no renaming, broken suites
still appear (flagged). A `*.suite.yaml` convention stays available as a later opt-in if scans get
slow.

---

### OQ2 — Run model — RESOLVED

**Decision:** **Explicit Run + save-then-run + whole-suite**, reusing the DL3 engine
(`run(manifestPath): SuiteResult`) with **zero API change**.

- (a) **Trigger = explicit Run** (run-on-save DEFERRED — debounced, behind a toggle, later). Bind
  **BOTH F5 and Ctrl+Enter** to Run (F5 overloaded across IDEs; Ctrl+Enter is the clean "execute"
  idiom; both cover muscle memory cheaply). The **Run button stays the primary discoverable
  trigger**; shortcuts are accelerators.
- (b) **Save-then-run** — persist buffer(s) to disk, then `run(manifestPath)`. Disk = what ran;
  no stale-run ambiguity. **Save-on-Run must be VISIBLE, not silent:** per-document dirty/modified
  indicator, and surface that Run persists the dirty buffer(s) first (XSLT and/or manifest). That
  defuses the file-mutation risk.
- (c) **Whole-suite run** (suites are small/fast). Single-case run DEFERRED (needs a case-subset
  entry point).

**NEW — threading (v1, load-bearing, NOT deferred):** Run executes **off the UI thread**
(background coroutine/dispatcher); UI shows an in-progress state and renders `SuiteResult` on
completion. Load-bearing precisely because DL3(b) is batch rendering — on-thread Saxon freezes the
window on every Run. **Never run Saxon on the composition/UI thread.**

**DEFERRED:** **run-from-in-memory-buffer** (run unsaved/scratch edits) — needs a new engine entry
point accepting in-memory strings; **no engine API expansion in v1.** Named escape hatch.

---

### OQ5 — Error surfacing — RESOLVED

**Decision:** **v1 = results panel as the single, complete surface.** Renders the whole
`SuiteResult`: per-case PASS/FAIL badge, stylesheet name, `headerOnly` label, each `GateResult`'s
`message`+`findings`+`warnings`, plus `engineError` and `configError` as their own rows — all four
error classes on one surface.

- (a) Results panel renders the full `SuiteResult` (all four error classes). ✓
- (b) **Inline editor markers DEFERRED**, and when built, scoped **only to errors carrying a real
  source position** (XSD/XML parse `line:col`). Semantic selection mismatches stay panel-only
  forever — no line "is" the mismatch, so a marker would be fiction. *Impl note for that slice:
  first verify whether `XsdGate` actually captures Saxon's `line:col` or only a message — markers
  infeasible without the position.*
- (c) **Click-to-navigate DEFERRED** (jump to the test in the manifest / to the stylesheet); v1
  panel shows case name + stylesheet as plain text. Source-position mapping (YAML + XSLT) ships
  with that later slice.

**NEW — reinforcements:**
1. **Verbatim DATA, structured PRESENTATION.** `findings`/`warnings` strings render unchanged
   (engine is ground truth), but the panel is **NOT a stdout reprint** — real visual structure:
   pass/fail badges, color, per-case grouping, **visually distinct styling per error class**
   (XSD vs selection vs engineError vs configError). That structure is the panel's reason to exist.
2. **Coherence with DL3(a):** selection mismatch surfaces in v1 as the existing `findings` text.
   The prettier expected-vs-actual visual diff (add/remove coloring) is **gated on the deferred
   structured `SelectionDiff`** — do **not** promise a visual diff in v1.
3. **Nit:** warnings must render **even on a PASS case** (distinct styling, e.g. a warning badge),
   so a passing-with-warnings case doesn't swallow them.

---

### OQ4 — Run history — RESOLVED

**Decision:** **v1 = latest result only**, held in memory, overwritten on each Run. No history
list, no persistence.

- (a) UI holds the current `SuiteResult` in state, overwritten each Run. ✓
- (b) Defer (i) session recent-runs, (ii) persisted history, (iii) run-to-run diff. History is
  genuinely **orthogonal** to the core loop AND **cheap-to-reverse** (clean data classes →
  kotlinx.serialization JSON near-free later) — so deferral is safe (decided on merits, not
  scope-reflex, unlike the project model).
  **Reprioritize the deferred set:** when history is built, go **straight to (iii) run-to-run
  regression diff** — the only feature that motivates history for authoring ("case 3 went red
  since my last edit"). (i) recent-runs is a half-measure that may never be worth building given
  runs are instant; (ii) persistence only if cross-session comparison is wanted.

**NEW — stale-results marker (v1, loop-integrity, NOT history):** after a Run, if any document
goes dirty (edited since that Run), the results panel is marked **stale ("re-run")**. Ties to the
OQ2 dirty indicator; keeps latest-only honest — the panel must never silently present results that
predate the current buffer.

---

### OQ6 — Packaging — RESOLVED

**Decision:** **v1 = run-from-Gradle (`./gradlew :desktop:run`)**; native jpackage `.msi`
installer is a named, deferred enhancement.

- (a) v1 supported run = **`:desktop:run`** (Compose plugin run task — same task the OQ1 spike uses
  from slice 1). ✓
- (b) Native **Windows `.msi` via jpackage** (`packageDistributionForCurrentOS`, with
  icon/signing/`upgradeUuid`) DEFERRED until there's a non-dev audience to distribute to. None
  exists in v1.
- (c) **Windows-first:** v1 validated only on Windows 11, including the OQ1 spike's HiDPI + FlatLaf
  criteria. macOS/Linux come "for free" via `:desktop:run` but are not claimed or tested in v1.

**Doc note:** README / run instructions reflect `gradlew.bat :desktop:run` (PowerShell),
consistent with the `:cli:run` invocation update from the DL2 refactor PR.

---

### OQ7 — First-PRD boundary — RESOLVED

**Decision:** **A bounded v1 PRD + iterate** — not one big full-workbench PRD. The PRD fixes the
v1 *contract*; vertical slicing happens at the plan level regardless. **Bounded ≠ single-manifest**
— the project model stays in v1, delivered loop-first.

- (a) Bounded v1 PRD, not a mega-PRD. ✓
- (b) **Phase shape:**
  - **Phase 0** — module split (PR N). ACs externally observable: full suite green after the move;
    `:cli` byte-identical stdout (golden test).
  - **Phase 1** — DL3 typed `SuiteResult`/`CaseResult` + `:cli` rewire + golden test (PR N+1).
  - **Phase 2 / slice 1** — single-manifest edit→run, carrying the **editor spike hard-gate**.
  - **Phase 3 / slice 2** — project folder-scan + tree navigation.
  - Plus: results panel, save-then-run, latest-only, off-thread Run.
- (c) The two refactor PRs are **Phases 0–1 INSIDE the v1 PRD**, not a separate ticket — load-
  bearing (the spike gate sits immediately after Phase 1) and the PRD→plan flow needs the whole
  critical path in one place.
- (d) The PRD **names the editor spike as an explicit go/no-go gate** at the top of Phase 2, with
  its four pass criteria (Compose↔Swing focus/keyboard; highlighting + error markers via RSTA API;
  HiDPI; FlatLaf theming) and the fallback escalation (pure Swing+RSTA → Monaco-in-JCEF) written
  into the PRD, not implicit.

**The PRD's "v1 includes" section must enumerate the smaller load-bearing decisions** (so none get
dropped, not just headline features): visible save-on-Run + per-document dirty indicator; off-thread
Run; stale-results marker tied to dirty state; results-panel structured/visual presentation (not a
stdout reprint) with distinct styling per error class; warnings rendered even on PASS; Run bound to
**both F5 and Ctrl+Enter**; exitCode mapping living in `:cli` (not `:engine`).

---

## Follow-up / Deferred

### Tracked follow-up to file (so it isn't lost)
- **Structured `SelectionDiff`** — typed expected-vs-actual receiver/interface sets, enabling
  add/remove visual diff coloring in the results panel. Gated by DL3(a)'s decision to ship
  `findings: List<String>` in v1. The GUI diff UX will want it. **File as a tracked issue.**

### Non-goals (deferred named enhancements — full set)
1. **run-on-save** (debounced, behind a toggle)
2. **run-from-in-memory buffer** (needs new engine entry point; no engine API expansion in v1)
3. **single-case run** (needs case-subset entry point)
4. **run-all-suites / project-level aggregate results** — v1 Run is whole-SUITE on the selected
   manifest only; project-wide run/aggregate is deferred so the project-model boundary isn't
   ambiguous
5. **structured `SelectionDiff`** + visual add/remove diff (see tracked follow-up above)
6. **inline editor markers** (only for errors with real `line:col`; verify `XsdGate` captures it)
7. **click-to-navigate** (results → manifest test / stylesheet; needs source-position mapping)
8. **run history** — (i) session recent-runs, (ii) persisted history, (iii) run-to-run diff
   (build (iii) first when history is built)
9. **structured-form test-case editor** (v1 edits manifest as raw YAML)
10. **project-level config file** (default Saxon opts, manifest include/exclude; v1 is zero-config
    scan)
11. **native jpackage installer** (Windows `.msi` with icon/signing/upgradeUuid)
12. **macOS/Linux support** (v1 is Windows-first)

### Optional, deferred (not promised)
- Optional per-case **progress callback** in the engine (when suites grow / GUI wants live
  progress) — DL3(b) ships batch.

---

## Next step (gated)

PRD authoring (write-a-prd) is **NOT** started here — Pavol gates that next. This transcript is the
Stage-1 output that feeds it.
