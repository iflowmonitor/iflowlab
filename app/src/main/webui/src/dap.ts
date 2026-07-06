import type { DebugProtocol } from "@vscode/debugprotocol";

/**
 * A thin DAP client over WebSocket (R4): request/response correlation by seq,
 * plus an event dispatcher. No VS Code runtime — just the wire protocol, typed
 * with @vscode/debugprotocol.
 */
export class DapClient {
  private ws: WebSocket;
  private seq = 1;
  private pending = new Map<number, (body: unknown) => void>();
  private listeners = new Map<string, ((body: unknown) => void)[]>();
  private ready: Promise<void>;

  constructor(url: string) {
    this.ws = new WebSocket(url);
    this.ready = new Promise((resolve, reject) => {
      this.ws.onopen = () => resolve();
      this.ws.onerror = () => reject(new Error("debug socket error"));
    });
    this.ws.onmessage = (ev) => this.receive(ev.data as string);
  }

  open(): Promise<void> {
    return this.ready;
  }

  private receive(raw: string) {
    const msg = JSON.parse(raw) as DebugProtocol.ProtocolMessage;
    if (msg.type === "response") {
      const res = msg as DebugProtocol.Response;
      const cb = this.pending.get(res.request_seq);
      if (cb) {
        this.pending.delete(res.request_seq);
        cb(res.body);
      }
    } else if (msg.type === "event") {
      const evt = msg as DebugProtocol.Event;
      (this.listeners.get(evt.event) ?? []).forEach((cb) => cb(evt.body));
    }
  }

  request<T = unknown>(command: string, args?: unknown): Promise<T> {
    const seq = this.seq++;
    const message = { seq, type: "request", command, arguments: args ?? {} };
    return new Promise<T>((resolve) => {
      this.pending.set(seq, (body) => resolve(body as T));
      this.ws.send(JSON.stringify(message));
    });
  }

  on(event: string, cb: (body: unknown) => void): void {
    const arr = this.listeners.get(event) ?? [];
    arr.push(cb);
    this.listeners.set(event, arr);
  }

  close(): void {
    try {
      this.ws.close();
    } catch {
      /* ignore */
    }
  }
}

export interface StackFrame {
  id: number;
  name: string;
  line: number;
}

export interface Variable {
  name: string;
  value: string;
}
