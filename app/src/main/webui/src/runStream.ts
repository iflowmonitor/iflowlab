import type { LogLine, RunRequest, RunResult } from "./types";

/**
 * Runs a script over the /run/stream WebSocket, invoking onLog for each log line
 * as it is produced and resolving with the final result envelope (slice 10).
 */
export function runScriptStreaming(req: RunRequest, onLog: (line: LogLine) => void): Promise<RunResult> {
  return new Promise((resolve, reject) => {
    const proto = location.protocol === "https:" ? "wss" : "ws";
    const ws = new WebSocket(`${proto}://${location.host}/run/stream`);
    let settled = false;
    ws.onopen = () => ws.send(JSON.stringify(req));
    ws.onmessage = (ev) => {
      let frame: { type: string; line?: LogLine; result?: RunResult; message?: string };
      try {
        frame = JSON.parse(ev.data);
      } catch {
        return;
      }
      if (frame.type === "log" && frame.line) {
        onLog(frame.line);
      } else if (frame.type === "result" && frame.result) {
        settled = true;
        resolve(frame.result);
        ws.close();
      } else if (frame.type === "error") {
        settled = true;
        reject(new Error(frame.message ?? "run stream error"));
        ws.close();
      }
    };
    ws.onerror = () => {
      if (!settled) reject(new Error("run stream socket error"));
    };
    ws.onclose = () => {
      if (!settled) reject(new Error("run stream closed before a result"));
    };
  });
}
