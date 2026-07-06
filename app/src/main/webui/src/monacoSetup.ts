// Minimal Monaco: the core editor API only (no built-in languages/workers for
// TS/JSON/CSS/HTML). Groovy highlighting is our own Monarch grammar, which needs
// no worker — so this drops the multi-MB all-languages bundle. See groovyLang.ts.
import * as monaco from "monaco-editor/esm/vs/editor/editor.api";
// The editor.api trim drops most contributions; pull back the ones we rely on.
// Suggest widget/controller for cpiCompletions (slice 6):
import "monaco-editor/esm/vs/editor/contrib/suggest/browser/suggestController";
// Marker squiggle rendering + hover tooltips for fidelity lint markers (slice 7):
import "monaco-editor/esm/vs/editor/browser/services/markerDecorations";
import "monaco-editor/esm/vs/editor/contrib/hover/browser/hoverContribution";
import editorWorker from "monaco-editor/esm/vs/editor/editor.worker?worker";
import { loader } from "@monaco-editor/react";
import { registerGroovy } from "./groovyLang";
import { registerCpiCompletions } from "./cpiCompletions";

// Bundle Monaco locally (no CDN) so the single jar stays offline/self-contained (D1).
// Groovy highlighting is Monarch-only, so the base editor worker is all we need.
self.MonacoEnvironment = {
  getWorker() {
    return new editorWorker();
  },
};

registerGroovy(monaco);
registerCpiCompletions(monaco);
loader.config({ monaco });
