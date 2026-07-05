import { useEffect, useMemo, useState } from "react";
import Editor from "@monaco-editor/react";
import { getMessage, getScript, getWorkspace, runScript } from "./api";
import { renderBody } from "./format";
import type { BodyType, RunResult, WorkspaceInfo } from "./types";

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
  const [script, setScript] = useState(SAMPLE_SCRIPT);
  const [body, setBody] = useState("hello world");
  const [contentType, setContentType] = useState("text/plain");
  const [headers, setHeaders] = useState<Pair[]>([]);
  const [properties, setProperties] = useState<Pair[]>([]);
  const [result, setResult] = useState<RunResult | null>(null);
  const [running, setRunning] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [override, setOverride] = useState<BodyType | "AUTO">("AUTO");

  useEffect(() => {
    getWorkspace().then(setWorkspace).catch(() => setWorkspace(null));
  }, []);

  async function loadScript(path: string) {
    if (!path) return;
    try {
      setScript(await getScript(path));
    } catch (e) {
      setError(String(e));
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
    } catch (e) {
      setError(String(e));
    }
  }

  async function run() {
    setRunning(true);
    setError(null);
    try {
      const r = await runScript({
        script,
        body,
        contentType: contentType || null,
        headers: toRecord(headers),
        properties: toRecord(properties),
      });
      setResult(r);
      setOverride("AUTO");
    } catch (e) {
      setError(String(e));
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
        <div className="ws" title={workspace?.root}>
          {workspace ? `workspace: ${workspace.root}` : "no workspace"}
        </div>
        <button className="run" onClick={run} disabled={running}>
          {running ? "Running…" : "▶ Run"}
        </button>
      </header>

      <div className="columns">
        <section className="left">
          <Picker
            label="Script"
            options={workspace?.scripts ?? []}
            onPick={loadScript}
            placeholder="open a .groovy from workspace…"
          />
          <div className="editor">
            <Editor
              language="groovy"
              theme="vs-dark"
              value={script}
              onChange={(v) => setScript(v ?? "")}
              options={{
                minimap: { enabled: false },
                fontSize: 13,
                scrollBeyondLastLine: false,
                automaticLayout: true,
              }}
            />
          </div>

          <Picker
            label="Message"
            options={workspace?.messages ?? []}
            onPick={loadMessage}
            placeholder="load a message fixture…"
          />
          <label className="fieldlabel">Body</label>
          <textarea className="body" value={body} onChange={(e) => setBody(e.target.value)} spellCheck={false} />
          <label className="fieldlabel">Content-Type</label>
          <input className="ct" value={contentType} onChange={(e) => setContentType(e.target.value)} />

          <KeyValues title="Headers" pairs={headers} onChange={setHeaders} />
          <KeyValues title="Properties" pairs={properties} onChange={setProperties} />
        </section>

        <section className="right">
          {error && <div className="banner err">{error}</div>}
          {!result && !error && <div className="placeholder">Run a script to see its output.</div>}
          {result && <Output result={result} override={override} setOverride={setOverride} />}
        </section>
      </div>
    </div>
  );
}

function Picker(props: { label: string; options: string[]; onPick: (v: string) => void; placeholder: string }) {
  return (
    <div className="picker">
      <span className="fieldlabel">{props.label}</span>
      <select defaultValue="" onChange={(e) => props.onPick(e.target.value)}>
        <option value="" disabled>
          {props.placeholder}
        </option>
        {props.options.map((o) => (
          <option key={o} value={o}>
            {o}
          </option>
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

function Output(props: { result: RunResult; override: BodyType | "AUTO"; setOverride: (t: BodyType | "AUTO") => void }) {
  const { result, override, setOverride } = props;
  const type = override === "AUTO" ? result.body?.type ?? "TEXT" : override;
  const rendered = useMemo(
    () => (result.body ? renderBody(type, result.body.inline) : ""),
    [result.body, type],
  );

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

      <Diff title="Headers" before={result.headersBefore} after={result.headersAfter} />
      <Diff title="Properties" before={result.propertiesBefore} after={result.propertiesAfter} />

      <div className="panel">
        <div className="paneltitle">Log ({result.logs.length})</div>
        {result.logs.length === 0 ? (
          <div className="muted">no log output</div>
        ) : (
          <pre className="logs">
            {result.logs.map((l) => `[${l.source}] ${l.message}`).join("\n")}
          </pre>
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
