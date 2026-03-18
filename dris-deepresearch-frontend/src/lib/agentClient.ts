import type { ResearchReport, ToolExecutionStep } from "./types";

export interface AgentPlanStep {
  toolName: string;
  title: string;
  description: string;
}

export interface AgentPlan {
  plannerMode: string;
  templateId: string;
  title: string;
  rationale: string[];
  explicitIdentifiers: string[];
  focusedIdentifiers: string[];
  searchEnabled: boolean;
  steps: AgentPlanStep[];
}

export interface AgentRunRequest {
  question?: string;
  identifiersText?: string;
  anchorNodeCode?: string;
  searchMode?: "KEYWORD" | "SEMANTIC" | "HYBRID";
  strategy?: "ITERATIVE" | "RECURSIVE";
  limit?: number;
  ttl?: number;
  maxFanout?: number;
  perNodeLimit?: number;
  globalDeadlineMs?: number;
  hopTimeoutMs?: number;
  auditOperation?: string;
  auditStartTime?: string;
  auditEndTime?: string;
  auditPageSize?: number;
  mcpUrl?: string;
  llmModel?: string;
}

export interface AgentRunResponse {
  ok: boolean;
  plannerMode: string;
  mcpUrl: string;
  llmModel: string;
  plan: AgentPlan;
  steps: ToolExecutionStep[];
  report: ResearchReport;
  focusedIdentifiers: string[];
  durationMs: number;
}

export interface AgentHealthPayload {
  ok: boolean;
  plannerMode?: string;
  llmConfigured?: boolean;
  llmSummaryEnabled?: boolean;
  mcpServerUrl?: string;
  llmModel?: string | null;
}

export interface AgentModelsResponse {
  ok: boolean;
  configured: boolean;
  defaultModel: string | null;
  models: string[];
}

export interface AgentMetaEvent {
  type: "meta";
  plannerMode: string;
  llmModel: string;
  mcpUrl: string;
}

export interface AgentPlanEvent {
  type: "plan";
  plan: AgentPlan;
}

export interface AgentStepEvent {
  type: "step";
  step: ToolExecutionStep;
}

export interface AgentReportEvent {
  type: "report";
  report: ResearchReport;
}

export interface AgentSummaryDeltaEvent {
  type: "summary_delta";
  delta: string;
  accumulated: string;
}

export interface AgentDoneEvent {
  type: "done";
  focusedIdentifiers: string[];
  durationMs: number;
}

export interface AgentErrorEvent {
  type: "error";
  message: string;
}

export type AgentStreamEvent =
  | AgentMetaEvent
  | AgentPlanEvent
  | AgentStepEvent
  | AgentReportEvent
  | AgentSummaryDeltaEvent
  | AgentDoneEvent
  | AgentErrorEvent;

function normalizeBaseUrl(raw: string): string {
  const trimmed = raw.trim();
  if (!trimmed) {
    throw new Error("Orchestrator URL is required");
  }
  return trimmed.endsWith("/") ? trimmed.slice(0, -1) : trimmed;
}

async function readErrorMessage(response: Response): Promise<string> {
  const text = await response.text().catch(() => "");
  if (!text) {
    return `Agent request failed with status ${response.status}`;
  }

  try {
    const payload = JSON.parse(text) as { message?: string };
    return payload.message || `Agent request failed with status ${response.status}`;
  } catch {
    return text;
  }
}

function parseSseBlock(block: string): AgentStreamEvent | null {
  const normalized = block.trim();
  if (!normalized) {
    return null;
  }

  const dataLines = normalized
    .split("\n")
    .filter((line) => line.startsWith("data:"))
    .map((line) => line.slice(5).trimStart());

  if (!dataLines.length) {
    return null;
  }

  return JSON.parse(dataLines.join("\n")) as AgentStreamEvent;
}

export async function fetchOrchestratorHealth(baseUrl: string): Promise<{ ok: boolean; payload: AgentHealthPayload | null }> {
  const response = await fetch(`${normalizeBaseUrl(baseUrl)}/health`, {
    method: "GET"
  });
  const payload = (await response.json().catch(() => null)) as AgentHealthPayload | null;
  return { ok: response.ok, payload };
}

export async function fetchAgentModels(baseUrl: string): Promise<AgentModelsResponse> {
  const response = await fetch(`${normalizeBaseUrl(baseUrl)}/api/llm/models`, {
    method: "GET"
  });

  const payload = (await response.json().catch(() => null)) as AgentModelsResponse | { message?: string } | null;
  if (!response.ok || !payload || !("ok" in payload) || payload.ok !== true) {
    throw new Error((payload && "message" in payload && payload.message) || `Model list request failed with status ${response.status}`);
  }

  return payload as AgentModelsResponse;
}

export async function runAgentResearch(baseUrl: string, payload: AgentRunRequest): Promise<AgentRunResponse> {
  const response = await fetch(`${normalizeBaseUrl(baseUrl)}/api/research/run`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify(payload)
  });

  const body = (await response.json().catch(() => null)) as { ok?: boolean; message?: string } | null;
  if (!response.ok || !body?.ok) {
    throw new Error(body?.message || `Agent research request failed with status ${response.status}`);
  }

  return body as unknown as AgentRunResponse;
}

export async function runAgentResearchStream(
  baseUrl: string,
  payload: AgentRunRequest,
  onEvent: (event: AgentStreamEvent) => void | Promise<void>
): Promise<void> {
  const response = await fetch(`${normalizeBaseUrl(baseUrl)}/api/research/stream`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Accept: "text/event-stream"
    },
    body: JSON.stringify(payload)
  });

  if (!response.ok) {
    throw new Error(await readErrorMessage(response));
  }

  if (!response.body) {
    throw new Error("Streaming response body is unavailable in this browser");
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  let completed = false;

  while (true) {
    const { done, value } = await reader.read();
    buffer += decoder.decode(value ?? new Uint8Array(), { stream: !done });
    buffer = buffer.replace(/\r\n/g, "\n");

    let boundary = buffer.indexOf("\n\n");
    while (boundary >= 0) {
      const block = buffer.slice(0, boundary);
      buffer = buffer.slice(boundary + 2);
      const event = parseSseBlock(block);
      if (event) {
        await onEvent(event);
        if (event.type === "error") {
          throw new Error(event.message);
        }
        if (event.type === "done") {
          completed = true;
        }
      }
      boundary = buffer.indexOf("\n\n");
    }

    if (done) {
      break;
    }
  }

  const finalEvent = parseSseBlock(buffer);
  if (finalEvent) {
    await onEvent(finalEvent);
    if (finalEvent.type === "error") {
      throw new Error(finalEvent.message);
    }
    if (finalEvent.type === "done") {
      completed = true;
    }
  }

  if (!completed) {
    throw new Error("Research stream ended before completion");
  }
}
