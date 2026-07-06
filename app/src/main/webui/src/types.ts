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

export interface RunResult {
  status: Status;
  body: BodyView | null;
  headersBefore: Record<string, unknown>;
  headersAfter: Record<string, unknown>;
  propertiesBefore: Record<string, unknown>;
  propertiesAfter: Record<string, unknown>;
  logs: LogLine[];
  exception: ExceptionInfo | null;
}

export interface RunRequest {
  script: string;
  body: string;
  contentType: string | null;
  headers: Record<string, unknown>;
  properties: Record<string, unknown>;
  timeoutMs?: number;
}

export interface WorkspaceInfo {
  root: string;
  scripts: string[];
  messages: string[];
}

export interface MessageFixture {
  name: string;
  body: string;
  contentType: string | null;
  headers: Record<string, unknown>;
  properties: Record<string, unknown>;
}
