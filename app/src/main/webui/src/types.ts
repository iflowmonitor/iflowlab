export type Status = "OK" | "EXCEPTION" | "TIMEOUT";
export type BodyType = "XML" | "JSON" | "TEXT" | "BINARY";

export interface BodyView {
  type: BodyType;
  contentType: string | null;
  size: number;
  inline: string;
  truncated: boolean;
}

export interface LogLine {
  level: string;
  source: string;
  message: string;
}

export interface ExceptionInfo {
  type: string;
  message: string;
  mappedLine: number | null;
  stackTrace: string | null;
}

export interface AttachmentView {
  name: string;
  contentType: string | null;
  size: number;
  inline: string;
  truncated: boolean;
}

export interface AttachmentSpec {
  name: string;
  body: string;
  contentType: string | null;
}

export interface RunResult {
  status: Status;
  body: BodyView | null;
  headersBefore: Record<string, unknown>;
  headersAfter: Record<string, unknown>;
  propertiesBefore: Record<string, unknown>;
  propertiesAfter: Record<string, unknown>;
  logs: LogLine[];
  attachments: AttachmentView[];
  exception: ExceptionInfo | null;
}

export type EngineKind = "groovy" | "xslt";

export interface RunRequest {
  script: string;
  body: string;
  contentType: string | null;
  headers: Record<string, unknown>;
  properties: Record<string, unknown>;
  timeoutMs?: number;
  kind?: EngineKind;
  attachments?: AttachmentSpec[];
}

export interface WorkspaceInfo {
  root: string;
  scripts: string[];
  messages: string[];
  cases: string[];
}

export interface MessageFixture {
  name: string;
  body: string;
  contentType: string | null;
  headers: Record<string, unknown>;
  properties: Record<string, unknown>;
  attachments: AttachmentSpec[];
}

// --- run-cases / assertions (slice 3) ---
export type AssertionKind =
  | "STATUS"
  | "BODY_EQUALS"
  | "BODY_CONTAINS"
  | "BODY_TYPE"
  | "HEADER"
  | "PROPERTY";

export interface Assertion {
  kind: AssertionKind;
  target: string | null;
  expected: string;
}

export interface MessageSpec {
  body: string;
  contentType: string | null;
  headers: Record<string, unknown>;
  properties: Record<string, unknown>;
}

export interface RunCase {
  name: string;
  script: string;
  message: MessageSpec;
  assertions: Assertion[];
}

export interface AssertionResult {
  assertion: Assertion;
  passed: boolean;
  actual: string | null;
}

export interface CaseReport {
  name: string;
  passed: boolean;
  result: RunResult;
  assertions: AssertionResult[];
}

export interface Finding {
  line: number;
  column: number;
  endColumn: number;
  severity: "ERROR" | "WARNING" | "INFO";
  rule: string;
  message: string;
}
