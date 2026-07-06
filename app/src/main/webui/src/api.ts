import type { CaseReport, Finding, MessageFixture, RunCase, RunRequest, RunResult, WorkspaceInfo } from "./types";

export async function runScript(req: RunRequest): Promise<RunResult> {
  const res = await fetch("/run", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(req),
  });
  if (!res.ok) {
    throw new Error(`Run failed: HTTP ${res.status}`);
  }
  return res.json();
}

export async function getWorkspace(): Promise<WorkspaceInfo> {
  const res = await fetch("/workspace");
  if (!res.ok) throw new Error(`workspace: HTTP ${res.status}`);
  return res.json();
}

export async function openWorkspace(path: string): Promise<WorkspaceInfo> {
  const res = await fetch("/workspace/open", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ path }),
  });
  if (!res.ok) {
    const msg = await res.json().catch(() => null);
    throw new Error(msg?.error ?? `open workspace: HTTP ${res.status}`);
  }
  return res.json();
}

export async function getScript(path: string): Promise<string> {
  const res = await fetch(`/workspace/script?path=${encodeURIComponent(path)}`);
  if (!res.ok) throw new Error(`script: HTTP ${res.status}`);
  return res.text();
}

export async function getMessage(name: string): Promise<MessageFixture> {
  const res = await fetch(`/workspace/message?name=${encodeURIComponent(name)}`);
  if (!res.ok) throw new Error(`message: HTTP ${res.status}`);
  return res.json();
}

export interface SaveMessage {
  name: string;
  body: string;
  contentType: string | null;
  headers: Record<string, unknown>;
  properties: Record<string, unknown>;
  attachments: import("./types").AttachmentSpec[];
}

export async function saveMessage(dto: SaveMessage): Promise<MessageFixture> {
  const res = await fetch("/workspace/message", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(dto),
  });
  if (!res.ok) throw new Error(`save: HTTP ${res.status}`);
  return res.json();
}

export async function getCase(name: string): Promise<RunCase> {
  const res = await fetch(`/workspace/case?name=${encodeURIComponent(name)}`);
  if (!res.ok) throw new Error(`case: HTTP ${res.status}`);
  return res.json();
}

export async function saveCase(runCase: RunCase): Promise<RunCase> {
  const res = await fetch("/workspace/case", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(runCase),
  });
  if (!res.ok) throw new Error(`save case: HTTP ${res.status}`);
  return res.json();
}

export async function runCase(runCase: RunCase): Promise<CaseReport> {
  const res = await fetch("/case/run", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(runCase),
  });
  if (!res.ok) throw new Error(`run case: HTTP ${res.status}`);
  return res.json();
}

export async function runAllCases(): Promise<CaseReport[]> {
  const res = await fetch("/case/run-all", { method: "POST" });
  if (!res.ok) throw new Error(`run all: HTTP ${res.status}`);
  return res.json();
}

export async function lintScript(script: string): Promise<Finding[]> {
  const res = await fetch("/lint", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ script }),
  });
  if (!res.ok) throw new Error(`lint: HTTP ${res.status}`);
  return res.json();
}
