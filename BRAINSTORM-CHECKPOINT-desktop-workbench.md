# Brainstorm Checkpoint — iflowlab Desktop Workbench

Stage-0 artifact for the desktop-workbench grill. Sits alongside BRAINSTORM-CHECKPOINT.md
(routing-MVP engine); supersedes nothing.

## Context
The routing-MVP shipped a CLI engine (Saxon-HE 9.9.1, YAML manifest, two gates: XSD-valid +
exact selection match). The desktop workbench is an interactive GUI on top of that engine.
PRIMARY motivation: authoring routing XSLT with a fast edit -> run -> see-results loop. The
XSLT editor is the centerpiece, not an add-on.

## Decisions (confirmed in brainstorm)
- DD1 Scope: full workbench; XSLT authoring is the centerpiece. Core loop: edit routing XSLT ->
  edit/select test cases -> run offline against the engine -> visualize pass/fail + selection
  diffs + XSD errors -> iterate.
- DD2 Offline/deterministic: drives the existing engine only; no SAP runtime, no network.

## Leans (recommended; confirm early in grill)
- DL1 Stack: Compose for Desktop (JVM), engine reused in-process. Rejected: JavaFX (older),
  web/Electron (process+language boundary, no gain).
- DL2 Architecture: split the single application module into :engine (library) + :cli (app) +
  :desktop (app); CLI and GUI both depend on :engine. Same iflowlab repo, Gradle multi-module.
- DL3 Engine result model: :engine must expose a TYPED result model (per-test outcome, gate
  results, diffs as data) replacing today's stdout strings. Load-bearing — the real refactor.

## Open questions (for the grill)
- OQ1 Editor component (make-or-break): syntax-highlighted XSLT/XML editing in Compose Desktop.
  Lean: embed RSyntaxTextArea (Swing) via Compose SwingPanel; check Context7 for a mature
  Compose-native editor before committing.
- OQ2 Run model: run-on-save vs explicit Run button vs both.
- OQ3 Workspace model: single suite vs multi-XSLT/multi-suite project; on-disk layout.
- OQ4 Run history: none / in-memory / persisted (+ format).
- OQ5 Error surfacing: inline editor markers vs results panel vs both, for XSD + selection mismatch.
- OQ6 Packaging: run-from-Gradle vs jpackage native installer for v1.
- OQ7 First-PRD boundary: one big full-workbench PRD vs bounded v1 PRD + iterate (plan still
  slices vertically either way).

## Guiding principles
- Engine is ground truth; the GUI is a frontend. No routing logic in the desktop layer.
- Vertical slices: a runnable desktop slice early, even within the larger workbench.
