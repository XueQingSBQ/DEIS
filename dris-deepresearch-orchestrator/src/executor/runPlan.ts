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
const RELATED_IMAGE_LIMIT = 24;

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

function readNumber(value: unknown): number | null {
  if (typeof value === "number" && Number.isFinite(value)) {
    return value;
  }
  if (typeof value === "string" && value.trim()) {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : null;
  }
  return null;
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

interface MetadataBatchItem {
  identifier?: string;
  metadata?: Record<string, unknown>;
}

interface SearchCandidate {
  identifier: string;
  name: string;
  description: string;
  entityType: string;
  finalScore: number | null;
}

function readBatchMetadataItems(output: unknown): MetadataBatchItem[] {
  const data = readObject(asEnvelope(output).data);
  if (!data) {
    return [];
  }
  const items = data.items;
  return Array.isArray(items)
    ? (items.filter((item) => item && typeof item === "object") as MetadataBatchItem[])
    : [];
}

function readSingleMetadataItems(output: unknown): MetadataBatchItem[] {
  const data = readObject(asEnvelope(output).data);
  if (!data) {
    return [];
  }
  const identifier = readString(data.identifier);
  if (!identifier) {
    return [];
  }
  return [
    {
      identifier,
      metadata: data
    }
  ];
}

function readStringArray(value: unknown): string[] {
  if (Array.isArray(value)) {
    return value.map((item) => readString(item)).filter(Boolean);
  }
  const single = readString(value);
  if (!single) {
    return [];
  }
  return single
    .split(/[,\s]+/)
    .map((item) => item.trim())
    .filter(Boolean);
}

function collectResolvedMetadataItems(outputs: Record<string, unknown>): MetadataBatchItem[] {
  return [
    ...((collectToolOutputs(outputs, "batch_resolve_metadata_distributed") ?? []).flatMap((output) =>
      readBatchMetadataItems(output)
    )),
    ...((collectToolOutputs(outputs, "resolve_metadata") ?? []).flatMap((output) => readSingleMetadataItems(output)))
  ];
}

function collectToolOutputs(outputs: Record<string, unknown>, toolName: string): unknown[] {
  const value = outputs[toolName];
  if (typeof value === "undefined") {
    return [];
  }
  return Array.isArray(value) ? value : [value];
}

function extractRelatedImageIdentifiers(
  outputs: Record<string, unknown>,
  focusedIdentifiers: string[],
  limit: number
): string[] {
  const focused = new Set(focusedIdentifiers);
  const ordered = collectResolvedMetadataItems(outputs)
    .filter((item) => {
      const identifier = readString(item.identifier);
      return identifier && focused.has(identifier);
    })
    .flatMap((item) => {
      const metadata = readObject(item.metadata);
      const additionalAttributes = readObject(metadata?.additionalAttributes);
      return readStringArray(additionalAttributes?.derivedImageIdentifiers);
    });
  return unique(ordered).slice(0, limit);
}

function extractParentDocumentIdentifiers(
  outputs: Record<string, unknown>,
  focusedIdentifiers: string[],
  limit: number
): string[] {
  const focused = new Set(focusedIdentifiers);
  const ordered = collectResolvedMetadataItems(outputs)
    .filter((item) => {
      const identifier = readString(item.identifier);
      return identifier && focused.has(identifier);
    })
    .flatMap((item) => {
      const metadata = readObject(item.metadata);
      const additionalAttributes = readObject(metadata?.additionalAttributes);
      return readStringArray(additionalAttributes?.sourceDocumentIdentifier);
    });
  return unique(ordered).slice(0, limit);
}

function normalizeText(value: string): string {
  return value.trim().toLowerCase();
}

function querySuggestsDocumentFocus(question: string): boolean {
  const text = normalizeText(question);
  if (!text) {
    return false;
  }
  return [
    "技术文件",
    "规范",
    "指南",
    "要求",
    "办法",
    "方案",
    "报告",
    "文档",
    "文件",
    "标准"
  ].some((hint) => text.includes(hint));
}

function toSearchCandidate(item: SearchItem): SearchCandidate | null {
  const raw = item as unknown as Record<string, unknown>;
  const identifier = extractIdentifierCandidate(raw);
  if (!identifier) {
    return null;
  }
  return {
    identifier,
    name: readString(item.name),
    description: readString(item.description),
    entityType: normalizeText(readString(item.entityType)),
    finalScore: readNumber(item.finalScore)
  };
}

function candidateLooksLikeDocument(candidate: SearchCandidate): boolean {
  const entityType = candidate.entityType;
  const text = normalizeText(`${candidate.name} ${candidate.description}`);
  return entityType === "document"
    || entityType === "file"
    || entityType === "dataset"
    || ["pdf", "doc", "docx", "指南", "技术要求", "方法", "规范", "文件"].some((hint) => text.includes(normalizeText(hint)));
}

function candidateLooksLikeImage(candidate: SearchCandidate): boolean {
  const entityType = candidate.entityType;
  const text = normalizeText(`${candidate.name} ${candidate.description}`);
  return entityType === "image"
    || ["附图", "图片", "图像", "示意图", "卫星图", "jpeg", "jpg", "png"].some((hint) => text.includes(normalizeText(hint)));
}

function filterSearchCandidates(question: string, searchItems: SearchItem[], limit: number): SearchCandidate[] {
  const candidates = searchItems
    .map((item) => toSearchCandidate(item))
    .filter((item): item is SearchCandidate => Boolean(item));
  if (!candidates.length) {
    return [];
  }

  const documentFocused = querySuggestsDocumentFocus(question);
  const topScore = candidates.reduce((max, item) => Math.max(max, item.finalScore ?? 0), 0);
  const strongFloor = topScore > 0 ? Math.max(topScore * 0.45, 0.12) : 0.12;
  const weakFloor = topScore > 0 ? Math.max(topScore * 0.2, 0.05) : 0.05;

  const filtered = candidates.filter((candidate, index) => {
    const score = candidate.finalScore ?? 0;
    const isDocument = candidateLooksLikeDocument(candidate);
    const isImage = candidateLooksLikeImage(candidate);

    if (documentFocused) {
      if (isDocument) {
        return score >= weakFloor || index < 4;
      }
      if (isImage) {
        return score >= strongFloor && index < 2;
      }
    }

    if (score >= strongFloor) {
      return true;
    }
    if (isDocument && score >= weakFloor) {
      return true;
    }
    return index < 2 && score >= weakFloor;
  });

  const prioritized = filtered.sort((left, right) => {
    const leftDocument = candidateLooksLikeDocument(left) ? 1 : 0;
    const rightDocument = candidateLooksLikeDocument(right) ? 1 : 0;
    if (leftDocument !== rightDocument && documentFocused) {
      return rightDocument - leftDocument;
    }
    return (right.finalScore ?? 0) - (left.finalScore ?? 0);
  });

  return prioritized.slice(0, Math.max(1, limit));
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
  hooks?: ResearchExecutionHooks,
  outputKey?: string
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
    appendToolOutput(outputs, outputKey ?? toolName, result);
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

async function enrichRelatedImages(
  client: McpResearchClient,
  outputs: Record<string, unknown>,
  steps: ToolExecutionStep[],
  form: ResearchFormState,
  focusedIdentifiers: string[],
  availableTools: string[],
  hooks?: ResearchExecutionHooks
): Promise<void> {
  const relatedImageIdentifiers = extractRelatedImageIdentifiers(outputs, focusedIdentifiers, RELATED_IMAGE_LIMIT);
  if (!relatedImageIdentifiers.length) {
    await appendSyntheticStep(
      steps,
      {
        toolName: "related_image_resolution",
        title: "Resolve related images",
        description: "Collect image identifiers linked from parent document metadata for multimodal report generation.",
        status: "skipped",
        startedAt: nowIso(),
        endedAt: nowIso(),
        durationMs: 0,
        summary: "No related image identifiers were exposed by the resolved parent metadata."
      },
      hooks
    );
    return;
  }

  await executeTool(
    client,
    outputs,
    steps,
    "batch_resolve_metadata_distributed",
    "Resolve related image metadata",
    "Resolve metadata for images linked from the focused parent documents.",
    { identifiers: relatedImageIdentifiers },
    hooks,
    "related_image_batch_resolve_metadata"
  );

  await executeTool(
    client,
    outputs,
    steps,
    "batch_resolve_component_distributed",
    "Resolve related image components",
    "Resolve image components so the report can reference image resource locators and formats.",
    { identifiers: relatedImageIdentifiers },
    hooks,
    "related_image_batch_resolve_component"
  );

  if (availableTools.includes("create_download_presign")) {
    for (const identifier of relatedImageIdentifiers.slice(0, Math.min(form.limit * 2, 8))) {
      await executeTool(
        client,
        outputs,
        steps,
        "create_download_presign",
        `Create image download URL - ${identifier}`,
        "Create a temporary download URL for a related image so the report can expose multimodal evidence.",
        { identifier, expiresInSeconds: 900 },
        hooks,
        "related_image_create_download_presign"
      );
    }
  }
}

async function enrichSourceDocumentsFromImages(
  client: McpResearchClient,
  outputs: Record<string, unknown>,
  steps: ToolExecutionStep[],
  form: ResearchFormState,
  focusedIdentifiers: string[],
  availableTools: string[],
  hooks?: ResearchExecutionHooks
): Promise<string[]> {
  const parentIdentifiers = extractParentDocumentIdentifiers(outputs, focusedIdentifiers, Math.max(form.limit, 8));
  if (!parentIdentifiers.length) {
    return focusedIdentifiers;
  }

  await executeTool(
    client,
    outputs,
    steps,
    "batch_resolve_metadata_distributed",
    "Resolve source document metadata",
    "Resolve original source document metadata traced back from image hits.",
    { identifiers: parentIdentifiers },
    hooks
  );
  await executeTool(
    client,
    outputs,
    steps,
    "batch_resolve_object_distributed",
    "Resolve source document objects",
    "Resolve original source document digital objects traced back from image hits.",
    { identifiers: parentIdentifiers },
    hooks
  );
  await executeTool(
    client,
    outputs,
    steps,
    "batch_resolve_component_distributed",
    "Resolve source document components",
    "Resolve original source document resources traced back from image hits.",
    { identifiers: parentIdentifiers },
    hooks
  );

  if (availableTools.includes("create_download_presign")) {
    for (const identifier of parentIdentifiers.slice(0, Math.min(form.limit, 4))) {
      await executeTool(
        client,
        outputs,
        steps,
        "create_download_presign",
        `Create source document download URL - ${identifier}`,
        "Create a download URL for the original source document traced from an image hit.",
        { identifier, expiresInSeconds: 900 },
        hooks
      );
    }
  }

  return unique([...parentIdentifiers, ...focusedIdentifiers]);
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
    const filteredCandidates = filterSearchCandidates(form.question, searchItems, Math.min(form.limit, 8));
    focusedIdentifiers = selectIdentifiers(
      plan.explicitIdentifiers,
      filteredCandidates.map((candidate) => ({
        identifier: candidate.identifier,
        name: candidate.name,
        description: candidate.description,
        entityType: candidate.entityType,
        finalScore: candidate.finalScore
      })),
      Math.min(form.limit, 8)
    );

    if (searchItems.length > filteredCandidates.length) {
      await appendSyntheticStep(
        steps,
        {
          toolName: "material_filter",
          title: "Filter research materials",
          description: "Keep only high-relevance search candidates before fetching full document contents for the LLM.",
          status: "success",
          startedAt: nowIso(),
          endedAt: nowIso(),
          durationMs: 0,
          summary: `Filtered search candidates from ${searchItems.length} to ${filteredCandidates.length} before downstream document analysis.`
        },
        hooks
      );
    }

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
      focusedIdentifiers = await enrichSourceDocumentsFromImages(
        client,
        outputs,
        steps,
        form,
        focusedIdentifiers,
        availableTools,
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

      if (availableTools.includes("create_download_presign")) {
        for (const identifier of focusedIdentifiers.slice(0, Math.min(form.limit, 6))) {
          await executeTool(
            client,
            outputs,
            steps,
            "create_download_presign",
            `Create download URL - ${identifier}`,
            "Create a downloadable URL for the resolved resource so the research flow can inspect actual file contents.",
            { identifier, expiresInSeconds: 900 },
            hooks
          );
        }
      }

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
      await enrichRelatedImages(client, outputs, steps, form, focusedIdentifiers, availableTools, hooks);
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
    focusedIdentifiers = await enrichSourceDocumentsFromImages(
      client,
      outputs,
      steps,
      form,
      focusedIdentifiers,
      availableTools,
      hooks
    );
    const tracedParentIdentifiers = focusedIdentifiers.filter((item) => item !== identifier);
    if (tracedParentIdentifiers.length) {
      await executeTool(
        client,
        outputs,
        steps,
        "batch_resolve_metadata_distributed",
        "Resolve traced source document metadata",
        "Resolve original source document metadata so the report can analyze the parent file, not just the image hit.",
        { identifiers: tracedParentIdentifiers },
        hooks
      );
    }
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
    if (tracedParentIdentifiers.length) {
      await executeTool(
        client,
        outputs,
        steps,
        "batch_resolve_object_distributed",
        "Resolve traced source document objects",
        "Resolve original source document digital objects traced from image hits.",
        { identifiers: tracedParentIdentifiers },
        hooks
      );
    }
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
    if (tracedParentIdentifiers.length) {
      await executeTool(
        client,
        outputs,
        steps,
        "batch_resolve_component_distributed",
        "Resolve traced source document components",
        "Resolve original source document resources traced from image hits.",
        { identifiers: tracedParentIdentifiers },
        hooks
      );
    }
    if (availableTools.includes("create_download_presign")) {
      await executeTool(
        client,
        outputs,
        steps,
        "create_download_presign",
        "Create download URL",
        "Create a downloadable URL for the resolved resource so the research flow can inspect actual file contents.",
        { identifier, expiresInSeconds: 900 },
        hooks
      );
      for (const parentIdentifier of tracedParentIdentifiers.slice(0, Math.min(form.limit, 4))) {
        await executeTool(
          client,
          outputs,
          steps,
          "create_download_presign",
          `Create source document download URL - ${parentIdentifier}`,
          "Create a download URL for the original source document traced from an image hit.",
          { identifier: parentIdentifier, expiresInSeconds: 900 },
          hooks
        );
      }
    }
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
    plan.auditIdentifiers = [focusedIdentifiers[0] || identifier];
    await enrichRelatedImages(client, outputs, steps, form, focusedIdentifiers, availableTools, hooks);
  }

  plan.focusedIdentifiers = focusedIdentifiers;
  return {
    steps,
    outputs,
    focusedIdentifiers,
    availableTools
  };
}
