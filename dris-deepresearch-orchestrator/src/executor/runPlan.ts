import { performance } from "node:perf_hooks";

import type {
  ResearchFormState,
  ResearchPlan,
  SearchItem,
  ToolEnvelope,
  ToolExecutionStep
} from "../types/research.js";
import { extractEnvelope, McpResearchClient, summarizeToolResult } from "./mcpResearchClient.js";

const IDENTIFIER_PATTERN = /[0-9A-Za-z]{16,}/;

function nowIso(): string {
  return new Date().toISOString();
}

function unique(values: string[]): string[] {
  return Array.from(new Set(values.filter((item) => item.trim())));
}

function cloneStep(step: ToolExecutionStep): ToolExecutionStep {
  return JSON.parse(JSON.stringify(step)) as ToolExecutionStep;
}

function appendToolOutput(outputs: Record<string, unknown>, toolName: string, value: unknown): void {
  const existing = outputs[toolName];
  if (typeof existing === "undefined") {
    outputs[toolName] = value;
    return;
  }

  if (Array.isArray(existing)) {
    existing.push(value);
    return;
  }

  outputs[toolName] = [existing, value];
}

function asEnvelope(output: unknown): ToolEnvelope {
  return extractEnvelope(output) ?? {};
}

function readObject(value: unknown): Record<string, unknown> | undefined {
  return value && typeof value === "object" && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : undefined;
}

function readString(value: unknown): string {
  return typeof value === "string" ? value.trim() : "";
}

function looksLikeIdentifier(value: string): boolean {
  return IDENTIFIER_PATTERN.test(value);
}

function extractIdentifierCandidate(item: Record<string, unknown>): string {
  const directKeys = [
    "identifier",
    "identifierValue",
    "digitalObjectIdentifier",
    "objectIdentifier",
    "pid",
    "id"
  ];
  for (const key of directKeys) {
    const value = readString(item[key]);
    if (looksLikeIdentifier(value)) {
      return value;
    }
  }

  const nestedKeys = ["metadata", "digitalObject", "object", "resource"];
  for (const key of nestedKeys) {
    const nested = readObject(item[key]);
    if (!nested) {
      continue;
    }
    const value = readString(nested.identifier);
    if (looksLikeIdentifier(value)) {
      return value;
    }
  }

  return "";
}

function readSearchItems(output: unknown): SearchItem[] {
  const data = readObject(asEnvelope(output).data);
  if (!data) {
    return [];
  }

  const items = data.items ?? data.results;
  if (!Array.isArray(items)) {
    return [];
  }

  return items.filter((item) => item && typeof item === "object") as SearchItem[];
}

function selectIdentifiers(explicitIdentifiers: string[], searchItems: SearchItem[], limit: number): string[] {
  const fromSearch = searchItems
    .map((item) => extractIdentifierCandidate(item as unknown as Record<string, unknown>))
    .filter(Boolean)
    .slice(0, limit);
  return unique([...explicitIdentifiers, ...fromSearch]);
}

function buildAuditArgs(form: ResearchFormState, identifier?: string): Record<string, unknown> {
  return {
    identifier: identifier || undefined,
    operation: form.auditOperation || undefined,
    startTime: form.auditStartTime || undefined,
    endTime: form.auditEndTime || undefined,
    page: 1,
    size: form.auditPageSize
  };
}

export interface ResearchExecutionHooks {
  onStep?: (step: ToolExecutionStep) => void | Promise<void>;
}

async function emitStep(hooks: ResearchExecutionHooks | undefined, step: ToolExecutionStep): Promise<void> {
  await hooks?.onStep?.(cloneStep(step));
}

async function appendSyntheticStep(
  steps: ToolExecutionStep[],
  step: Omit<ToolExecutionStep, "id">,
  hooks?: ResearchExecutionHooks
): Promise<void> {
  const nextStep: ToolExecutionStep = {
    ...step,
    id: `${step.toolName}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
  };
  steps.push(nextStep);
  await emitStep(hooks, nextStep);
}

async function executeTool(
  client: McpResearchClient,
  outputs: Record<string, unknown>,
  steps: ToolExecutionStep[],
  toolName: string,
  title: string,
  description: string,
  args: Record<string, unknown>,
  hooks?: ResearchExecutionHooks
): Promise<unknown> {
  const step: ToolExecutionStep = {
    id: `${toolName}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    title,
    toolName,
    description,
    status: "running",
    startedAt: nowIso(),
    args
  };
  steps.push(step);
  await emitStep(hooks, step);

  const started = performance.now();
  try {
    const result = await client.callTool(toolName, args);
    step.status = "success";
    step.endedAt = nowIso();
    step.durationMs = Math.round(performance.now() - started);
    step.result = result;
    step.summary = summarizeToolResult(toolName, result);
    appendToolOutput(outputs, toolName, result);
    await emitStep(hooks, step);
    return result;
  } catch (error) {
    step.status = "error";
    step.endedAt = nowIso();
    step.durationMs = Math.round(performance.now() - started);
    step.error = error instanceof Error ? error.message : String(error);
    await emitStep(hooks, step);
    throw error;
  }
}

export interface ExecutedResearchRun {
  steps: ToolExecutionStep[];
  outputs: Record<string, unknown>;
  focusedIdentifiers: string[];
  availableTools: string[];
}

export async function executeResearchPlan(
  client: McpResearchClient,
  form: ResearchFormState,
  plan: ResearchPlan,
  hooks?: ResearchExecutionHooks
): Promise<ExecutedResearchRun> {
  const outputs: Record<string, unknown> = {};
  const steps: ToolExecutionStep[] = [];
  const availableTools = await client.listTools();

  const plannerStep: ToolExecutionStep = {
    id: `agent-plan-${Date.now()}`,
    toolName: "agent_plan",
    title: plan.title,
    description: "The lightweight agent selected a bounded research plan before tool execution.",
    status: "success",
    startedAt: nowIso(),
    endedAt: nowIso(),
    durationMs: 0,
    args: {
      templateId: plan.templateId,
      rationale: plan.rationale,
      explicitIdentifiers: plan.explicitIdentifiers,
      searchEnabled: plan.searchEnabled
    },
    summary: `Planner selected ${plan.templateId} with ${plan.steps.length} step(s).`
  };
  steps.push(plannerStep);
  await emitStep(hooks, plannerStep);

  await executeTool(
    client,
    outputs,
    steps,
    "get_node_routes",
    "Load node routes",
    "Fetch current cross-node routing context before any research execution.",
    {},
    hooks
  );

  let focusedIdentifiers = [...plan.explicitIdentifiers];
  if (plan.searchEnabled && plan.searchArgs) {
    const searchResult = await executeTool(
      client,
      outputs,
      steps,
      "search_global",
      plan.templateId === "anchor_search" ? "Anchor search" : "Global search",
      plan.templateId === "anchor_search"
        ? "Search from the given anchor node to discover identifiers for deeper analysis."
        : "Run distributed search to discover identifiers for deeper analysis.",
      plan.searchArgs,
      hooks
    );

    const searchItems = readSearchItems(searchResult);
    focusedIdentifiers = selectIdentifiers(plan.explicitIdentifiers, searchItems, Math.min(form.limit, 8));
    if (!focusedIdentifiers.length) {
      await appendSyntheticStep(
        steps,
        {
          toolName: "identifier_selection",
          title: "Select resolvable identifiers",
          description: "Convert search candidates into downstream resolution targets.",
          status: "skipped",
          startedAt: nowIso(),
          endedAt: nowIso(),
          durationMs: 0,
          summary: searchItems.length
            ? "Search returned candidate resources, but no resolvable identifier field was exposed. Downstream resolution was skipped and the report will be built from search evidence only."
            : "Search returned no candidates, so downstream resolution was skipped."
        },
        hooks
      );
    }
  }

  if (plan.templateId === "identifier_compare" || plan.templateId === "discovery_overview" || plan.templateId === "anchor_search") {
    if (focusedIdentifiers.length) {
      await executeTool(
        client,
        outputs,
        steps,
        "batch_resolve_metadata_distributed",
        "Batch metadata resolution",
        "Resolve metadata across nodes for the selected identifiers.",
        { identifiers: focusedIdentifiers },
        hooks
      );
      await executeTool(
        client,
        outputs,
        steps,
        "batch_resolve_object_distributed",
        "Batch object resolution",
        "Resolve digital object snapshots across nodes for the selected identifiers.",
        { identifiers: focusedIdentifiers },
        hooks
      );
      await executeTool(
        client,
        outputs,
        steps,
        "batch_resolve_component_distributed",
        "Batch component resolution",
        "Resolve resource or component evidence across nodes for the selected identifiers.",
        { identifiers: focusedIdentifiers },
        hooks
      );

      const auditIdentifiers = focusedIdentifiers.slice(0, 3);
      for (const identifier of auditIdentifiers) {
        await executeTool(
          client,
          outputs,
          steps,
          "query_audit_logs",
          `Audit trace - ${identifier}`,
          "Query paginated audit history for a focused identifier.",
          buildAuditArgs(form, identifier),
          hooks
        );
      }
      plan.auditIdentifiers = auditIdentifiers;
    } else {
      plan.auditIdentifiers = [];
    }
  } else {
    const identifier = focusedIdentifiers[0];
    if (!identifier) {
      throw new Error("Audit trace plan requires at least one focused identifier.");
    }

    focusedIdentifiers = [identifier];
    await executeTool(
      client,
      outputs,
      steps,
      "resolve_metadata",
      "Resolve metadata",
      "Resolve metadata for the focused identifier.",
      { identifier },
      hooks
    );
    await executeTool(
      client,
      outputs,
      steps,
      "resolve_object",
      "Resolve digital object",
      "Resolve the digital object snapshot for the focused identifier.",
      { identifier },
      hooks
    );
    await executeTool(
      client,
      outputs,
      steps,
      "resolve_component",
      "Resolve resource component",
      "Resolve the resource evidence for the focused identifier.",
      { identifier },
      hooks
    );
    await executeTool(
      client,
      outputs,
      steps,
      "query_audit_logs",
      "Query audit history",
      "Query recent audit history for the focused identifier.",
      buildAuditArgs(form, identifier),
      hooks
    );
    plan.auditIdentifiers = [identifier];
  }

  plan.focusedIdentifiers = focusedIdentifiers;
  return {
    steps,
    outputs,
    focusedIdentifiers,
    availableTools
  };
}
