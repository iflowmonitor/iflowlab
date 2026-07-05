import * as monaco from "monaco-editor";
import editorWorker from "monaco-editor/esm/vs/editor/editor.worker?worker";
import { loader } from "@monaco-editor/react";
import { registerGroovy } from "./groovyLang";

// Bundle Monaco locally (no CDN) so the single jar stays offline/self-contained (D1).
// Groovy highlighting is Monarch-only, so the base editor worker is all we need.
self.MonacoEnvironment = {
  getWorker() {
    return new editorWorker();
  },
};

registerGroovy(monaco);
loader.config({ monaco });
