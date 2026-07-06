import { useEffect, useMemo, useRef, useState } from "react";
import Editor, { type OnMount } from "@monaco-editor/react";
import {
  getCase,
  getMessage,
  getScript,
  getWorkspace,
  lintScript,
  openWorkspace,
  runAllCases,
  runCase,
  runScript,
  saveCase,
  saveMessage,
} from "./api";
import { DapClient, type StackFrame, type Variable } from "./dap";
import { renderBody } from "./format";
import { runScriptStreaming } from "./runStream";
import type { Assertion, AssertionKind, AttachmentSpec, BodyType, CaseReport, EngineKind, LogLine, RunResult, WorkspaceInfo } from "./types";

const ASSERTION_KINDS: { kind: AssertionKind; label: string; needsTarget: boolean }[] = [
  { kind: "STATUS", label: "status ==", needsTarget: false },
  { kind: "BODY_EQUALS", label: "body ==", needsTarget: false },
  { kind: "BODY_CONTAINS", label: "body contains", needsTarget: false },
  { kind: "BODY_TYPE", label: "body type ==", needsTarget: false },
  { kind: "HEADER", label: "header", needsTarget: true },
  { kind: "PROPERTY", label: "property", needsTarget: true },
];

interface Pair {
  key: string;
  value: string;
}

const SAMPLE_SCRIPT = `import com.sap.gateway.ip.core.customdev.util.Message

Message processData(Message message) {
    def body = message.getBody(String.class)
    def log = messageLogFactory.getMessageLog(message)
    log?.setStringProperty("Step", "uppercased")
    message.setBody(body.toUpperCase())
    message.setHeader("Content-Type", "text/plain")
    return message
}
`;

const SAMPLE_XSLT = `<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:output method="xml" indent="yes" omit-xml-declaration="yes"/>
  <xsl:template match="/order">
    <receipt id="{@id}">
      <total><xsl:value-of select="sum(item/@price)"/></total>
    </receipt>
  </xsl:template>
</xsl:stylesheet>
`;

const SAMPLE_XML = '<order id="42"><item price="10"/><item price="5"/></order>';

// Show a compact tail of a workspace path so the switcher stays readable.
function shortRoot(root: string): string {
  const parts = root.split(/[\\/]/).filter(Boolean);
  const tail = parts.slice(-2).join("/");
  return parts.length > 2 ? "…/" + tail : tail || root;
}

function toRecord(pairs: Pair[]): Record<string, unknown> {
  const out: Record<string, unknown> = {};
  for (const p of pairs) if (p.key.trim()) out[p.key] = p.value;
  return out;
}

function toPairs(rec: Record<string, unknown>): Pair[] {
  return Object.entries(rec).map(([key, value]) => ({ key, value: String(value) }));
}

export function App() {
  const [workspace, setWorkspace] = useState<WorkspaceInfo | null>(null);
  const [kind, setKind] = useState<EngineKind>("groovy");
  const [script, setScript] = useState(SAMPLE_SCRIPT);
  const [body, setBody] = useState("hello world");
  const [contentType, setContentType] = useState("text/plain");
  const [headers, setHeaders] = useState<Pair[]>([]);
  const [properties, setProperties] = useState<Pair[]>([]);
  const [attachments, setAttachments] = useState<AttachmentSpec[]>([]);
  const [result, setResult] = useState<RunResult | null>(null);
  const [running, setRunning] = useState(false);
  const [liveLogs, setLiveLogs] = useState<LogLine[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [override, setOverride] = useState<BodyType | "AUTO">("AUTO");
  const [saveName, setSaveName] = useState("");
  const [saved, setSaved] = useState<string | null>(null);
  const [wsPath, setWsPath] = useState("");

  // --- run-cases / assertions (slice 3) ---
  const [scriptPath, setScriptPath] = useState<string | null>(null);
  const [assertions, setAssertions] = useState<Assertion[]>([]);
  const [caseName, setCaseName] = useState("");
  const [caseSaved, setCaseSaved] = useState<string | null>(null);
  const [report, setReport] = useState<CaseReport | null>(null);
  const [suite, setSuite] = useState<CaseReport[] | null>(null);

  // --- debug state ---
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const editorRef = useRef<any>(null);
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const monacoRef = useRef<any>(null);
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const bpDeco = useRef<any>(null);
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const stoppedDeco = useRef<any>(null);
  const bpLines = useRef<Set<number>>(new Set());
  const dapRef = useRef<DapClient | null>(null);
  const [debugging, setDebugging] = useState(false);
  const [stopped, setStopped] = useState(false);
  const [frames, setFrames] = useState<StackFrame[]>([]);
  const [variables, setVariables] = useState<Variable[]>([]);
  const [debugOutput, setDebugOutput] = useState("");

  const [findings, setFindings] = useState<import("./types").Finding[]>([]);

  useEffect(() => {
    getWorkspace().then(setWorkspace).catch(() => setWorkspace(null));
  }, []);

  // Live fidelity lint (slice 7): debounced, Groovy only; surfaced as Monaco markers.
  useEffect(() => {
    const monaco = monacoRef.current;
    const editor = editorRef.current;
    if (!monaco || !editor) return;
    const model = editor.getModel();
    if (kind !== "groovy") {
      monaco.editor.setModelMarkers(model, "fidelity", []);
      setFindings([]);
      return;
    }
    const handle = setTimeout(() => {
      lintScript(script)
        .then((fs) => {
          setFindings(fs);
          const sev = (s: string) =>
            s === "ERROR" ? monaco.MarkerSeverity.Error : s === "INFO" ? monaco.MarkerSeverity.Info : monaco.MarkerSeverity.Warning;
          monaco.editor.setModelMarkers(
            model,
            "fidelity",
            fs.map((f) => ({
              startLineNumber: f.line,
              startColumn: f.column,
              endLineNumber: f.line,
              endColumn: f.endColumn,
              message: `${f.message} [${f.rule}]`,
              severity: sev(f.severity),
            })),
          );
        })
        .catch(() => {});
    }, 400);
    return () => clearTimeout(handle);
  }, [script, kind]);

  const onEditorMount: OnMount = (editor, monaco) => {
    editorRef.current = editor;
    monacoRef.current = monaco;
    bpDeco.current = editor.createDecorationsCollection([]);
    stoppedDeco.current = editor.createDecorationsCollection([]);
    editor.onMouseDown((e) => {
      if (e.target.type === monaco.editor.MouseTargetType.GUTTER_GLYPH_MARGIN && e.target.position) {
        toggleBreakpoint(e.target.position.lineNumber);
      }
    });
  };

  function renderBreakpoints(lines: number[]) {
    const monaco = monacoRef.current;
    if (!bpDeco.current || !monaco) return;
    bpDeco.current.set(
      lines.map((l) => ({
        range: new monaco.Range(l, 1, l, 1),
        options: { glyphMarginClassName: "bp-glyph", glyphMarginHoverMessage: { value: "Breakpoint" } },
      })),
    );
  }

  function renderStopped(line: number | null) {
    const monaco = monacoRef.current;
    if (!stoppedDeco.current || !monaco) return;
    stoppedDeco.current.set(
      line
        ? [{ range: new monaco.Range(line, 1, line, 1), options: { isWholeLine: true, className: "stopped-line", glyphMarginClassName: "stopped-glyph" } }]
        : [],
    );
    if (line) editorRef.current?.revealLineInCenter(line);
  }

  function toggleBreakpoint(line: number) {
    const set = bpLines.current;
    if (set.has(line)) set.delete(line);
    else set.add(line);
    const lines = [...set].sort((a, b) => a - b);
    renderBreakpoints(lines);
    dapRef.current?.request("setBreakpoints", { source: { name: "script" }, breakpoints: lines.map((l) => ({ line: l })) });
  }

  async function startDebug() {
    setError(null);
    setResult(null);
    setDebugOutput("");
    const proto = location.protocol === "https:" ? "wss" : "ws";
    const dap = new DapClient(`${proto}://${location.host}/debug`);
    dapRef.current = dap;
    dap.on("stopped", () => void onStopped());
    dap.on("terminated", () => onTerminated());
    dap.on("output", (b) => setDebugOutput((o) => o + ((b as { output?: string })?.output ?? "")));
    try {
      await dap.open();
      await dap.request("initialize", { adapterID: "iflowlab" });
      // Source breakpoints from the authoritative ref (drives the glyphs), not the
      // React state, which can lag behind rapid toggles.
      const lines = [...bpLines.current].sort((a, b) => a - b);
      await dap.request("setBreakpoints", { source: { name: "script" }, breakpoints: lines.map((l) => ({ line: l })) });
      await dap.request("configurationDone", {});
      setDebugging(true);
      setStopped(false);
      await dap.request("launch", {
        script,
        body,
        contentType: contentType || null,
        headers: toRecord(headers),
        properties: toRecord(properties),
      });
    } catch (e) {
      setError(String(e));
      onTerminated();
    }
  }

  async function onStopped() {
    const dap = dapRef.current;
    if (!dap) return;
    setStopped(true);
    const st = await dap.request<{ stackFrames: StackFrame[] }>("stackTrace", { threadId: 1 });
    setFrames(st.stackFrames);
    renderStopped(st.stackFrames[0]?.line ?? null);
    if (st.stackFrames[0]) await loadFrameVars(st.stackFrames[0].id);
  }

  async function loadFrameVars(frameId: number) {
    const dap = dapRef.current;
    if (!dap) return;
    const sc = await dap.request<{ scopes: { variablesReference: number }[] }>("scopes", { frameId });
    const ref = sc.scopes[0]?.variablesReference;
    if (ref != null) {
      const v = await dap.request<{ variables: Variable[] }>("variables", { variablesReference: ref });
      setVariables(v.variables);
    }
  }

  function onTerminated() {
    setDebugging(false);
    setStopped(false);
    setFrames([]);
    setVariables([]);
    renderStopped(null);
    dapRef.current?.close();
    dapRef.current = null;
  }

  async function debugStep(command: string) {
    setStopped(false);
    renderStopped(null);
    await dapRef.current?.request(command, { threadId: 1 });
  }

  async function stopDebug() {
    await dapRef.current?.request("terminate", {});
    onTerminated();
  }

  async function openWs(path: string) {
    if (!path.trim()) return;
    setError(null);
    try {
      const info = await openWorkspace(path.trim());
      setWorkspace(info);
      setWsPath("");
      // The new workspace has its own files — clear stale pickers/results.
      setScriptPath(null);
      setResult(null);
      setReport(null);
      setSuite(null);
    } catch (e) {
      setError(String(e instanceof Error ? e.message : e));
    }
  }

  async function loadScript(path: string) {
    if (!path) return;
    try {
      setScript(await getScript(path));
      setScriptPath(path);
      setKind(/\.xslt?$/i.test(path) ? "xslt" : "groovy");
    } catch (e) {
      setError(String(e));
    }
  }

  // Swap the editor to a working sample when toggling engines, but only if it still
  // holds an untouched sample (never clobber the user's own script).
  function switchKind(next: EngineKind) {
    setKind(next);
    if (next === "xslt" && script === SAMPLE_SCRIPT) {
      setScript(SAMPLE_XSLT);
      if (!body.trim() || body === "hello world") setBody(SAMPLE_XML);
      if (!contentType.trim() || contentType === "text/plain") setContentType("application/xml");
      setScriptPath(null);
    } else if (next === "groovy" && script === SAMPLE_XSLT) {
      setScript(SAMPLE_SCRIPT);
      setScriptPath(null);
    }
  }

  async function loadMessage(name: string) {
    if (!name) return;
    try {
      const m = await getMessage(name);
      setBody(m.body);
      setContentType(m.contentType ?? "");
      setHeaders(toPairs(m.headers));
      setProperties(toPairs(m.properties));
      setAttachments(m.attachments ?? []);
    } catch (e) {
      setError(String(e));
    }
  }

  async function saveFixture() {
    if (!saveName.trim()) return;
    setError(null);
    try {
      await saveMessage({
        name: saveName.trim(),
        body,
        contentType: contentType || null,
        headers: toRecord(headers),
        properties: toRecord(properties),
        attachments: attachments.filter((a) => a.name.trim()),
      });
      setSaved(saveName.trim());
      setSaveName("");
      setWorkspace(await getWorkspace());
    } catch (e) {
      setError(String(e));
    }
  }

  async function loadCase(name: string) {
    if (!name) return;
    setError(null);
    try {
      const c = await getCase(name);
      setScript(await getScript(c.script));
      setScriptPath(c.script);
      setBody(c.message?.body ?? "");
      setContentType(c.message?.contentType ?? "");
      setHeaders(toPairs(c.message?.headers ?? {}));
      setProperties(toPairs(c.message?.properties ?? {}));
      setAssertions(c.assertions ?? []);
      setCaseName(name);
      setReport(null);
      setSuite(null);
    } catch (e) {
      setError(String(e));
    }
  }

  function currentCase() {
    return {
      name: caseName.trim(),
      script: scriptPath ?? "",
      message: {
        body,
        contentType: contentType || null,
        headers: toRecord(headers),
        properties: toRecord(properties),
      },
      assertions,
    };
  }

  async function saveCurrentCase() {
    if (!caseName.trim()) return;
    if (!scriptPath) {
      setError("Load a script from the workspace first — a case binds to a saved script file.");
      return;
    }
    setError(null);
    try {
      await saveCase(currentCase());
      setCaseSaved(caseName.trim());
      setWorkspace(await getWorkspace());
    } catch (e) {
      setError(String(e));
    }
  }

  async function runCurrentCase() {
    if (!scriptPath) {
      setError("Load a script from the workspace first — a case binds to a saved script file.");
      return;
    }
    setError(null);
    setSuite(null);
    try {
      setReport(await runCase(currentCase()));
    } catch (e) {
      setError(String(e));
    }
  }

  async function runSuite() {
    setError(null);
    setReport(null);
    try {
      setSuite(await runAllCases());
    } catch (e) {
      setError(String(e));
    }
  }

  async function run() {
    setRunning(true);
    setError(null);
    setResult(null);
    setReport(null);
    setSuite(null);
    setLiveLogs([]);
    const payload = {
      script,
      body,
      contentType: contentType || null,
      headers: toRecord(headers),
      properties: toRecord(properties),
      attachments: attachments.filter((a) => a.name.trim()),
      kind,
    };
    try {
      // Groovy runs stream their log lines live over a WebSocket; XSLT has no logs, so
      // it stays on the plain request/response path.
      const r =
        kind === "groovy"
          ? await runScriptStreaming(payload, (line) => setLiveLogs((prev) => [...prev, line]))
          : await runScript(payload);
      setResult(r);
      setOverride("AUTO");
    } catch (e) {
      setError(String(e instanceof Error ? e.message : e));
    } finally {
      setRunning(false);
    }
  }

  return (
    <div className="app">
      <header className="topbar">
        <div className="brand">
          iflow<span>lab</span> <em>Groovy workbench</em>
        </div>
        <div className="wsswitch">
          <select
            className="wssel"
            value=""
            onChange={(e) => { if (e.target.value) void openWs(e.target.value); }}
            title={workspace?.root ?? "no workspace"}
          >
            <option value="">{workspace ? shortRoot(workspace.root) : "no workspace"}</option>
            {(workspace?.recents ?? [])
              .filter((r) => r !== workspace?.root)
              .map((r) => (
                <option key={r} value={r}>{shortRoot(r)}</option>
              ))}
          </select>
          <input
            className="wspath"
            placeholder="open workspace path…"
            value={wsPath}
            onChange={(e) => setWsPath(e.target.value)}
            onKeyDown={(e) => { if (e.key === "Enter") void openWs(wsPath); }}
          />
          <button className="wsopen" onClick={() => void openWs(wsPath)} disabled={!wsPath.trim() || debugging}>Open</button>
        </div>
        <select className="langsel" value={kind} onChange={(e) => switchKind(e.target.value as EngineKind)} disabled={debugging} title="Engine">
          <option value="groovy">Groovy</option>
          <option value="xslt">XSLT</option>
        </select>
        {debugging ? (
          <div className="debugbar">
            <button onClick={() => debugStep("continue")} disabled={!stopped} title="Continue">▶</button>
            <button onClick={() => debugStep("next")} disabled={!stopped} title="Step over">⤼</button>
            <button onClick={() => debugStep("stepIn")} disabled={!stopped} title="Step into">⤓</button>
            <button onClick={() => debugStep("stepOut")} disabled={!stopped} title="Step out">⤒</button>
            <button onClick={stopDebug} className="stopdebug" title="Stop">◼</button>
          </div>
        ) : (
          kind === "groovy" && (
            <button className="debug" onClick={startDebug} title="Debug (set breakpoints in the gutter)">
              🐞 Debug
            </button>
          )
        )}
        <button className="debug" onClick={runSuite} disabled={debugging} title="Run all saved cases">
          ✓ Run all cases
        </button>
        <button className="run" onClick={run} disabled={running || debugging}>
          {running ? "Running…" : "▶ Run"}
        </button>
      </header>

      <div className="columns">
        <section className="left">
          <Picker label="Script" options={workspace?.scripts ?? []} onPick={loadScript} placeholder="open a .groovy from workspace…" />
          <div className="editor">
            <Editor
              language={kind === "xslt" ? "xml" : "groovy"}
              theme="vs-dark"
              value={script}
              onChange={(v) => setScript(v ?? "")}
              onMount={onEditorMount}
              options={{
                minimap: { enabled: false },
                fontSize: 13,
                glyphMargin: true,
                scrollBeyondLastLine: false,
                automaticLayout: true,
              }}
            />
          </div>
          {kind === "groovy" && findings.length > 0 && (
            <div className="findings">
              <div className="findingshead">⚠ {findings.length} fidelity {findings.length === 1 ? "warning" : "warnings"}</div>
              {findings.map((f, i) => (
                <div className="findingrow" key={i}>
                  <span className="findingline">L{f.line}</span> {f.message}
                </div>
              ))}
            </div>
          )}

          <Picker label="Message" options={workspace?.messages ?? []} onPick={loadMessage} placeholder="load a message fixture…" />
          <label className="fieldlabel">Body</label>
          <textarea className="body" value={body} onChange={(e) => setBody(e.target.value)} spellCheck={false} />
          <label className="fieldlabel">Content-Type</label>
          <input className="ct" value={contentType} onChange={(e) => setContentType(e.target.value)} />

          <KeyValues title="Headers" pairs={headers} onChange={setHeaders} />
          <KeyValues title="Properties" pairs={properties} onChange={setProperties} />
          <AttachmentsEditor attachments={attachments} onChange={setAttachments} />

          <div className="savefixture">
            <input placeholder="fixture name…" value={saveName} onChange={(e) => { setSaveName(e.target.value); setSaved(null); }} />
            <button onClick={saveFixture} disabled={!saveName.trim()}>Save as fixture</button>
            {saved && <span className="savedok">saved “{saved}”</span>}
          </div>

          <Picker label="Case" options={workspace?.cases ?? []} onPick={loadCase} placeholder="load a saved test case…" />
          <Assertions assertions={assertions} onChange={setAssertions} />

          <div className="savefixture casebar">
            <input placeholder="case name…" value={caseName} onChange={(e) => { setCaseName(e.target.value); setCaseSaved(null); }} />
            <button onClick={saveCurrentCase} disabled={!caseName.trim()}>Save case</button>
            <button onClick={runCurrentCase} className="runcase">Run case</button>
            {caseSaved && <span className="savedok">saved “{caseSaved}”</span>}
          </div>
        </section>

        <section className="right">
          {error && <div className="banner err">{error}</div>}
          {debugging ? (
            <DebugView
              stopped={stopped}
              frames={frames}
              variables={variables}
              output={debugOutput}
              onSelectFrame={loadFrameVars}
            />
          ) : running && kind === "groovy" ? (
            <LiveRunView logs={liveLogs} />
          ) : suite ? (
            <SuiteView reports={suite} />
          ) : report ? (
            <CaseReportView report={report} />
          ) : result ? (
            <Output result={result} override={override} setOverride={setOverride} />
          ) : (
            !error && <div className="placeholder">Run a script, add assertions and Run case, or Debug.</div>
          )}
        </section>
      </div>
    </div>
  );
}

function LiveRunView(props: { logs: LogLine[] }) {
  return (
    <div className="output">
      <div className="statusrow">
        <span className="status running">RUNNING</span>
        <span className="meta">{props.logs.length} log {props.logs.length === 1 ? "line" : "lines"} streamed</span>
      </div>
      <div className="panel">
        <div className="paneltitle">Live log</div>
        {props.logs.length === 0 ? (
          <div className="muted" style={{ padding: "10px 12px" }}>waiting for output…</div>
        ) : (
          <pre className="logs">{props.logs.map((l) => `[${l.source}] ${l.message}`).join("\n")}</pre>
        )}
      </div>
    </div>
  );
}

function DebugView(props: {
  stopped: boolean;
  frames: StackFrame[];
  variables: Variable[];
  output: string;
  onSelectFrame: (id: number) => void;
}) {
  return (
    <div className="output">
      <div className="statusrow">
        <span className={`status ${props.stopped ? "exception" : "ok"}`}>{props.stopped ? "PAUSED" : "RUNNING"}</span>
        {props.stopped && props.frames[0] && <span className="meta">at {props.frames[0].name}:{props.frames[0].line}</span>}
      </div>

      <div className="panel">
        <div className="paneltitle">Variables</div>
        {props.variables.length === 0 ? (
          <div className="muted" style={{ padding: "10px 12px" }}>{props.stopped ? "no locals in scope" : "—"}</div>
        ) : (
          <table className="diff">
            <tbody>
              {props.variables.map((v) => (
                <tr key={v.name}>
                  <td className="dk">{v.name}</td>
                  <td className="dv">{v.value}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      <div className="panel">
        <div className="paneltitle">Call stack</div>
        {props.frames.length === 0 ? (
          <div className="muted" style={{ padding: "10px 12px" }}>—</div>
        ) : (
          <div className="callstack">
            {props.frames.map((f) => (
              <div key={f.id} className="frame" onClick={() => props.onSelectFrame(f.id)}>
                {f.name} <span className="muted">:{f.line}</span>
              </div>
            ))}
          </div>
        )}
      </div>

      {props.output && (
        <div className="panel">
          <div className="paneltitle">Output</div>
          <pre className="logs">{props.output}</pre>
        </div>
      )}
    </div>
  );
}

function Picker(props: { label: string; options: string[]; onPick: (v: string) => void; placeholder: string }) {
  return (
    <div className="picker">
      <span className="fieldlabel">{props.label}</span>
      <select defaultValue="" onChange={(e) => props.onPick(e.target.value)}>
        <option value="" disabled>{props.placeholder}</option>
        {props.options.map((o) => (
          <option key={o} value={o}>{o}</option>
        ))}
      </select>
    </div>
  );
}

function KeyValues(props: { title: string; pairs: Pair[]; onChange: (p: Pair[]) => void }) {
  const { title, pairs, onChange } = props;
  function update(i: number, patch: Partial<Pair>) {
    onChange(pairs.map((p, idx) => (idx === i ? { ...p, ...patch } : p)));
  }
  return (
    <div className="kv">
      <div className="kvhead">
        <span className="fieldlabel">{title}</span>
        <button onClick={() => onChange([...pairs, { key: "", value: "" }])}>+ add</button>
      </div>
      {pairs.map((p, i) => (
        <div className="kvrow" key={i}>
          <input placeholder="name" value={p.key} onChange={(e) => update(i, { key: e.target.value })} />
          <input placeholder="value" value={p.value} onChange={(e) => update(i, { value: e.target.value })} />
          <button onClick={() => onChange(pairs.filter((_, idx) => idx !== i))}>✕</button>
        </div>
      ))}
    </div>
  );
}

function AttachmentsEditor(props: { attachments: AttachmentSpec[]; onChange: (a: AttachmentSpec[]) => void }) {
  const { attachments, onChange } = props;
  function update(i: number, patch: Partial<AttachmentSpec>) {
    onChange(attachments.map((a, idx) => (idx === i ? { ...a, ...patch } : a)));
  }
  return (
    <div className="kv">
      <div className="kvhead">
        <span className="fieldlabel">Attachments</span>
        <button onClick={() => onChange([...attachments, { name: "", body: "", contentType: null }])}>+ add</button>
      </div>
      {attachments.map((a, i) => (
        <div className="attachrow" key={i}>
          <div className="attachrowtop">
            <input placeholder="name" value={a.name} onChange={(e) => update(i, { name: e.target.value })} />
            <input placeholder="content-type" value={a.contentType ?? ""} onChange={(e) => update(i, { contentType: e.target.value || null })} />
            <button onClick={() => onChange(attachments.filter((_, idx) => idx !== i))}>✕</button>
          </div>
          <textarea placeholder="content" value={a.body} onChange={(e) => update(i, { body: e.target.value })} spellCheck={false} />
        </div>
      ))}
    </div>
  );
}

function Assertions(props: { assertions: Assertion[]; onChange: (a: Assertion[]) => void }) {
  const { assertions, onChange } = props;
  function update(i: number, patch: Partial<Assertion>) {
    onChange(assertions.map((a, idx) => (idx === i ? { ...a, ...patch } : a)));
  }
  return (
    <div className="kv">
      <div className="kvhead">
        <span className="fieldlabel">Assertions</span>
        <button onClick={() => onChange([...assertions, { kind: "BODY_EQUALS", target: null, expected: "" }])}>+ add</button>
      </div>
      {assertions.map((a, i) => {
        const spec = ASSERTION_KINDS.find((k) => k.kind === a.kind);
        return (
          <div className="assertrow" key={i}>
            <select value={a.kind} onChange={(e) => update(i, { kind: e.target.value as AssertionKind })}>
              {ASSERTION_KINDS.map((k) => (
                <option key={k.kind} value={k.kind}>{k.label}</option>
              ))}
            </select>
            {spec?.needsTarget && (
              <input placeholder="name" value={a.target ?? ""} onChange={(e) => update(i, { target: e.target.value })} />
            )}
            <input placeholder="expected" value={a.expected} onChange={(e) => update(i, { expected: e.target.value })} />
            <button onClick={() => onChange(assertions.filter((_, idx) => idx !== i))}>✕</button>
          </div>
        );
      })}
    </div>
  );
}

function assertionLabel(a: Assertion): string {
  const spec = ASSERTION_KINDS.find((k) => k.kind === a.kind);
  const base = spec?.label ?? a.kind;
  return spec?.needsTarget ? `${base} ${a.target ?? ""} == ${a.expected}` : `${base} ${a.expected}`;
}

function CaseReportView(props: { report: CaseReport }) {
  const { report } = props;
  return (
    <div className="output">
      <div className="statusrow">
        <span className={`status ${report.passed ? "ok" : "exception"}`}>{report.passed ? "PASS" : "FAIL"}</span>
        <span className="meta">
          {report.name || "case"} · {report.assertions.filter((a) => a.passed).length}/{report.assertions.length} assertions ·
          run {report.result.status}
        </span>
      </div>

      <div className="panel">
        <div className="paneltitle">Assertions</div>
        <table className="diff asserttable">
          <tbody>
            {report.assertions.map((ar, i) => (
              <tr key={i} className={ar.passed ? "assertpass" : "assertfail"}>
                <td className="amark">{ar.passed ? "✓" : "✕"}</td>
                <td className="dk">{assertionLabel(ar.assertion)}</td>
                <td className="dv">{ar.passed ? "" : `got: ${ar.actual ?? "—"}`}</td>
              </tr>
            ))}
            {report.assertions.length === 0 && (
              <tr><td className="muted" style={{ padding: "8px 12px" }}>no assertions — add some to check output</td></tr>
            )}
          </tbody>
        </table>
      </div>

      <Output result={report.result} override="AUTO" setOverride={() => {}} />
    </div>
  );
}

function SuiteView(props: { reports: CaseReport[] }) {
  const passed = props.reports.filter((r) => r.passed).length;
  return (
    <div className="output">
      <div className="statusrow">
        <span className={`status ${passed === props.reports.length ? "ok" : "exception"}`}>
          {passed === props.reports.length ? "ALL PASS" : "FAILURES"}
        </span>
        <span className="meta">{passed}/{props.reports.length} cases passed</span>
      </div>
      <div className="panel">
        <div className="paneltitle">Cases</div>
        <table className="diff asserttable">
          <tbody>
            {props.reports.map((r, i) => (
              <tr key={i} className={r.passed ? "assertpass" : "assertfail"}>
                <td className="amark">{r.passed ? "✓" : "✕"}</td>
                <td className="dk">{r.name}</td>
                <td className="dv">{r.assertions.filter((a) => a.passed).length}/{r.assertions.length} · {r.result.status}</td>
              </tr>
            ))}
            {props.reports.length === 0 && (
              <tr><td className="muted" style={{ padding: "8px 12px" }}>no saved cases in the workspace</td></tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function Output(props: { result: RunResult; override: BodyType | "AUTO"; setOverride: (t: BodyType | "AUTO") => void }) {
  const { result, override, setOverride } = props;
  const type = override === "AUTO" ? result.body?.type ?? "TEXT" : override;
  const rendered = useMemo(() => (result.body ? renderBody(type, result.body.inline) : ""), [result.body, type]);

  return (
    <div className="output">
      <div className="statusrow">
        <span className={`status ${result.status.toLowerCase()}`}>{result.status}</span>
        {result.body && (
          <span className="meta">
            {result.body.contentType ?? "—"} · {result.body.size} bytes{result.body.truncated ? " · truncated" : ""}
          </span>
        )}
      </div>

      {result.exception && (
        <div className="panel exc">
          <div className="paneltitle">Exception</div>
          <div className="excmsg">
            <strong>{result.exception.type}</strong>: {result.exception.message}
            {result.exception.mappedLine != null && <span className="line"> @ line {result.exception.mappedLine}</span>}
          </div>
          {result.exception.stackTrace && <pre className="stack">{result.exception.stackTrace}</pre>}
        </div>
      )}

      {result.body && (
        <div className="panel">
          <div className="paneltitle">
            Output body
            <select className="override" value={override} onChange={(e) => setOverride(e.target.value as BodyType | "AUTO")}>
              <option value="AUTO">auto ({result.body.type})</option>
              <option value="XML">XML</option>
              <option value="JSON">JSON</option>
              <option value="TEXT">Text</option>
              <option value="BINARY">Hex</option>
            </select>
          </div>
          <pre className="bodyout">{rendered}</pre>
        </div>
      )}

      {result.attachments.length > 0 && (
        <div className="panel">
          <div className="paneltitle">Attachments ({result.attachments.length})</div>
          {result.attachments.map((a, i) => (
            <div className="attachout" key={i}>
              <div className="attachouthead">
                <span className="attachname">{a.name}</span>
                <span className="meta">{a.contentType ?? "—"} · {a.size} bytes{a.truncated ? " · truncated" : ""}</span>
              </div>
              <pre className="bodyout">{a.inline}</pre>
            </div>
          ))}
        </div>
      )}

      <Diff title="Headers" before={result.headersBefore} after={result.headersAfter} />
      <Diff title="Properties" before={result.propertiesBefore} after={result.propertiesAfter} />

      <div className="panel">
        <div className="paneltitle">Log ({result.logs.length})</div>
        {result.logs.length === 0 ? (
          <div className="muted">no log output</div>
        ) : (
          <pre className="logs">{result.logs.map((l) => `[${l.source}] ${l.message}`).join("\n")}</pre>
        )}
      </div>
    </div>
  );
}

function Diff(props: { title: string; before: Record<string, unknown>; after: Record<string, unknown> }) {
  const keys = Array.from(new Set([...Object.keys(props.before), ...Object.keys(props.after)])).sort();
  if (keys.length === 0) return null;
  return (
    <div className="panel">
      <div className="paneltitle">{props.title} (before → after)</div>
      <table className="diff">
        <tbody>
          {keys.map((k) => {
            const b = props.before[k];
            const a = props.after[k];
            const changed = String(b) !== String(a);
            const added = !(k in props.before);
            return (
              <tr key={k} className={added ? "added" : changed ? "changed" : ""}>
                <td className="dk">{k}</td>
                <td className="dv">{b === undefined ? "—" : String(b)}</td>
                <td className="arr">→</td>
                <td className="dv">{a === undefined ? "—" : String(a)}</td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
