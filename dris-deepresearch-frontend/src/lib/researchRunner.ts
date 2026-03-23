import { extractEnvelope, summarizeToolResult, type BrowserMcpClient } from "./mcpClient";
import { getTemplateById } from "./researchTemplates";
import type {
  AuditRecord,
  BatchItemBase,
  EvidenceCard,
  ResearchFormState,
  ResearchReport,
  ResearchRunResult,
  SearchItem,
  SummarySection,
  ToolEnvelope,
  ToolExecutionStep
} from "./types";

function nowIso(): string {
  return new Date().toISOString();
}

function cloneStep(step: ToolExecutionStep): ToolExecutionStep {
  return JSON.parse(JSON.stringify(step)) as ToolExecutionStep;
}

function unique(values: string[]): string[] {
  return Array.from(new Set(values.filter((item) => item.trim())));
}

function clamp(value: number, min: number, max: number): number {
  return Math.max(min, Math.min(max, value));
}

export function parseIdentifiers(raw: string): string[] {
  return unique(
    raw
      .split(/[\s,;\n\r]+/)
      .map((item) => item.trim())
      .filter(Boolean)
  );
}

function collectToolOutputs(outputs: Record<string, unknown>, toolName: string): unknown[] {
  const value = outputs[toolName];
  if (typeof value === "undefined") {
    return [];
  }
  return Array.isArray(value) ? value : [value];
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

function readSearchItems(output: unknown): SearchItem[] {
  const data = asEnvelope(output).data;
  if (!data || typeof data !== "object") {
    return [];
  }

  const items = (data as { items?: unknown }).items;
  if (!Array.isArray(items)) {
    return [];
  }

  return items.filter((item) => item && typeof item === "object") as SearchItem[];
}

function readVisitedNodeCodes(output: unknown): string[] {
  const data = asEnvelope(output).data;
  if (!data || typeof data !== "object") {
    return [];
  }

  const visited = (data as { visitedNodeCodes?: unknown }).visitedNodeCodes;
  return Array.isArray(visited) ? visited.map((item) => String(item)) : [];
}

function readFailedNodes(output: unknown): string[] {
  const data = asEnvelope(output).data;
  if (!data || typeof data !== "object") {
    return [];
  }

  const failed = (data as { failedNodes?: unknown }).failedNodes;
  return Array.isArray(failed) ? failed.map((item) => JSON.stringify(item)) : [];
}

function readBatchItems<T extends BatchItemBase>(output: unknown): T[] {
  const data = asEnvelope(output).data;
  if (!data || typeof data !== "object") {
    return [];
  }

  const items = (data as { items?: unknown }).items;
  if (!Array.isArray(items)) {
    return [];
  }

  return items.filter((item) => item && typeof item === "object") as T[];
}

function readBatchStats(output: unknown): { total: number; successCount: number; failureCount: number; partial: boolean; mode: string } {
  const data = asEnvelope(output).data;
  if (!data || typeof data !== "object") {
    return { total: 0, successCount: 0, failureCount: 0, partial: false, mode: "unknown" };
  }

  return {
    total: Number((data as { total?: unknown }).total ?? 0),
    successCount: Number((data as { successCount?: unknown }).successCount ?? 0),
    failureCount: Number((data as { failureCount?: unknown }).failureCount ?? 0),
    partial: Boolean((data as { partial?: unknown }).partial),
    mode: String((data as { mode?: unknown }).mode ?? "unknown")
  };
}

function readAuditRecords(output: unknown): AuditRecord[] {
  const data = asEnvelope(output).data;
  if (!data || typeof data !== "object") {
    return [];
  }

  const records = (data as { records?: unknown }).records;
  if (!Array.isArray(records)) {
    return [];
  }

  return records.filter((item) => item && typeof item === "object") as AuditRecord[];
}

function readNodeRoutes(output: unknown): Array<Record<string, unknown>> {
  const data = asEnvelope(output).data;
  if (!Array.isArray(data)) {
    return [];
  }
  return data.filter((item) => item && typeof item === "object") as Array<Record<string, unknown>>;
}

function readIdentifierFromMetadata(output: unknown): string {
  const data = asEnvelope(output).data;
  if (!data || typeof data !== "object") {
    return "";
  }
  return String((data as { identifier?: unknown }).identifier ?? "").trim();
}

function buildSearchArgs(form: ResearchFormState): Record<string, unknown> {
  return {
    naturalQuery: form.question.trim(),
    limit: clamp(form.limit, 1, 20),
    offset: 0,
    mode: form.searchMode,
    strategy: form.strategy,
    anchorNodeCode: form.anchorNodeCode.trim() || undefined,
    ttl: clamp(form.ttl, 1, 16),
    maxFanout: clamp(form.maxFanout, 1, 64),
    perNodeLimit: clamp(form.perNodeLimit, 1, 100),
    globalDeadlineMs: clamp(form.globalDeadlineMs, 500, 120000),
    hopTimeoutMs: clamp(form.hopTimeoutMs, 200, 30000),
    traceId: `deepresearch-${Date.now()}`
  };
}

function buildAuditArgs(form: ResearchFormState, identifier?: string): Record<string, unknown> {
  return {
    identifier: identifier || undefined,
    operation: form.auditOperation.trim() || undefined,
    startTime: form.auditStartTime || undefined,
    endTime: form.auditEndTime || undefined,
    page: 1,
    size: clamp(form.auditPageSize, 1, 20)
  };
}

async function executeTool(
  client: BrowserMcpClient,
  outputs: Record<string, unknown>,
  onStepUpdate: (step: ToolExecutionStep) => void,
  toolName: string,
  title: string,
  description: string,
  args: Record<string, unknown>
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

  onStepUpdate(cloneStep(step));
  const started = performance.now();

  try {
    const result = await client.callTool(toolName, args);
    step.result = result;
    step.status = "success";
    step.endedAt = nowIso();
    step.durationMs = Math.round(performance.now() - started);
    step.summary = summarizeToolResult(toolName, result);
    appendToolOutput(outputs, toolName, result);
    onStepUpdate(cloneStep(step));
    return result;
  } catch (error) {
    step.status = "error";
    step.endedAt = nowIso();
    step.durationMs = Math.round(performance.now() - started);
    step.error = error instanceof Error ? error.message : String(error);
    onStepUpdate(cloneStep(step));
    throw error;
  }
}

function selectIdentifiers(explicitIdentifiers: string[], searchItems: SearchItem[], limit: number): string[] {
  const fromSearch = searchItems
    .map((item) => String(item.identifier ?? "").trim())
    .filter(Boolean)
    .slice(0, limit);
  return unique([...explicitIdentifiers, ...fromSearch]);
}

function buildSummarySections(
  form: ResearchFormState,
  focusedIdentifiers: string[],
  outputs: Record<string, unknown>
): SummarySection[] {
  const sections: SummarySection[] = [];
  const routes = collectToolOutputs(outputs, "get_node_routes").flatMap((output) => readNodeRoutes(output));
  const searchOutput = collectToolOutputs(outputs, "search_global")[0];
  const searchItems = searchOutput ? readSearchItems(searchOutput) : [];
  const visitedNodes = searchOutput ? readVisitedNodeCodes(searchOutput) : [];
  const failedNodes = searchOutput ? readFailedNodes(searchOutput) : [];
  const metadataOutput = collectToolOutputs(outputs, "batch_resolve_metadata_distributed")[0];
  const objectOutput = collectToolOutputs(outputs, "batch_resolve_object_distributed")[0];
  const componentOutput = collectToolOutputs(outputs, "batch_resolve_component_distributed")[0];
  const singleMetadata = collectToolOutputs(outputs, "resolve_metadata")[0];
  const singleObject = collectToolOutputs(outputs, "resolve_object")[0];
  const singleComponent = collectToolOutputs(outputs, "resolve_component")[0];
  const auditRecords = collectToolOutputs(outputs, "query_audit_logs").flatMap((output) => readAuditRecords(output));

  sections.push({
    title: "Research Scope",
    lines: [
      `Template: ${getTemplateById(form.templateId).title}`,
      `Question: ${form.question.trim() || "No natural-language question was provided."}`,
      `Focused identifiers: ${focusedIdentifiers.length ? focusedIdentifiers.join(", ") : "None"}`,
      `Search strategy: ${form.strategy}, mode: ${form.searchMode}${form.anchorNodeCode.trim() ? `, anchor: ${form.anchorNodeCode.trim()}` : ""}`
    ]
  });

  if (searchItems.length || visitedNodes.length) {
    sections.push({
      title: "Search and Routing",
      lines: [
        `Global search returned ${searchItems.length} items; visited nodes: ${visitedNodes.length ? visitedNodes.join(" -> ") : "not reported"}.`,
        failedNodes.length ? `Failed nodes or forward errors were reported: ${failedNodes.length}.` : "No failed nodes were reported in the distributed search trace.",
        routes.length ? `Topology endpoint currently exposes ${routes.length} route records for research context.` : "Topology endpoint did not return explicit route rows in this run."
      ]
    });
  }

  if (metadataOutput || singleMetadata) {
    const stats = metadataOutput ? readBatchStats(metadataOutput) : { total: focusedIdentifiers.length || 1, successCount: readIdentifierFromMetadata(singleMetadata) ? 1 : 0, failureCount: 0, partial: false, mode: "single" };
    sections.push({
      title: "Metadata Resolution",
      lines: [
        `Metadata resolution mode: ${stats.mode}. Success ${stats.successCount}/${stats.total}, failures ${stats.failureCount}.`,
        stats.partial ? "Metadata results are partial and should be explained as a cross-node limitation in the demo." : "Metadata results are complete for the current request set."
      ]
    });
  }

  if (objectOutput || singleObject || componentOutput || singleComponent) {
    const objectStats = objectOutput ? readBatchStats(objectOutput) : { total: focusedIdentifiers.length || 1, successCount: singleObject ? 1 : 0, failureCount: 0, partial: false, mode: "single" };
    const componentStats = componentOutput ? readBatchStats(componentOutput) : { total: focusedIdentifiers.length || 1, successCount: singleComponent ? 1 : 0, failureCount: 0, partial: false, mode: "single" };
    sections.push({
      title: "Object and Resource Evidence",
      lines: [
        `Digital object resolution: ${objectStats.successCount}/${objectStats.total} successful.` ,
        `Resource/component resolution: ${componentStats.successCount}/${componentStats.total} successful.`,
        componentStats.failureCount > 0 ? "Some identifiers have missing or unresolved resource evidence." : "No component-level failures were reported in this run."
      ]
    });
  }

  if (auditRecords.length) {
    const latest = auditRecords[0];
    const failureCount = auditRecords.filter((record) => record.success === false).length;
    sections.push({
      title: "Audit Observations",
      lines: [
        `Audit query returned ${auditRecords.length} records in the current page; failure entries: ${failureCount}.`,
        latest ? `Latest visible operation: ${latest.operation || "unknown"} at ${latest.createdAt || "unknown time"}.` : "No latest operation could be inferred.",
        failureCount > 0 ? "Audit evidence contains unsuccessful operations and should be highlighted in the governance discussion." : "Current audit page shows only successful operations or no explicit failures."
      ]
    });
  }

  if (!sections.length) {
    sections.push({
      title: "Research Scope",
      lines: ["The workflow completed without producing structured outputs that the report builder recognizes."]
    });
  }

  return sections;
}

function buildEvidenceCards(focusedIdentifiers: string[], outputs: Record<string, unknown>): EvidenceCard[] {
  const cards: EvidenceCard[] = [];
  const searchOutput = collectToolOutputs(outputs, "search_global")[0];
  const searchItems = searchOutput ? readSearchItems(searchOutput).slice(0, 4) : [];
  const metadataOutput = collectToolOutputs(outputs, "batch_resolve_metadata_distributed")[0];
  const metadataItems = metadataOutput ? readBatchItems<BatchItemBase & { metadata?: Record<string, unknown> }>(metadataOutput).slice(0, 4) : [];
  const objectOutput = collectToolOutputs(outputs, "batch_resolve_object_distributed")[0];
  const objectItems = objectOutput ? readBatchItems<BatchItemBase & { digitalObject?: Record<string, unknown> }>(objectOutput).slice(0, 4) : [];
  const componentOutput = collectToolOutputs(outputs, "batch_resolve_component_distributed")[0];
  const componentItems = componentOutput ? readBatchItems<BatchItemBase & { resource?: Record<string, unknown> }>(componentOutput).slice(0, 4) : [];
  const auditRecords = collectToolOutputs(outputs, "query_audit_logs").flatMap((output) => readAuditRecords(output)).slice(0, 5);

  cards.push({
    title: "Focused Identifiers",
    subtitle: `${focusedIdentifiers.length} identifier(s) selected for this run`,
    tone: focusedIdentifiers.length ? "good" : "warn",
    bullets: focusedIdentifiers.length ? focusedIdentifiers : ["No identifier was selected"]
  });

  for (const [index, item] of searchItems.entries()) {
    cards.push({
      title: item.name || item.identifier || "Search hit",
      subtitle: `Search evidence from ${item.source || "unknown node"}`,
      tone: "neutral",
      metrics: [
        { label: "Identifier", value: String(item.identifier ?? "-") },
        { label: "Owner", value: String(item.owner ?? "-") },
        { label: "Entity", value: String(item.entityType ?? "-") },
        { label: "匹配度", value: `第 ${index + 1}/${searchItems.length}` }
      ]
    });
  }

  for (const item of metadataItems) {
    const metadata = item.metadata ?? {};
    cards.push({
      title: String(metadata.name ?? item.identifier ?? "Metadata evidence"),
      subtitle: `Resolved by ${item.resolvedByNodeCode || item.targetNodeCode || "unknown node"}`,
      tone: item.success === false ? "bad" : "good",
      metrics: [
        { label: "Identifier", value: String(item.identifier ?? "-") },
        { label: "Owner", value: String(metadata.owner ?? "-") },
        { label: "Industry", value: String(metadata.industry ?? "-") },
        { label: "Status", value: String(metadata.status ?? item.code ?? "-") }
      ]
    });
  }

  for (const item of objectItems) {
    const digitalObject = item.digitalObject ?? {};
    cards.push({
      title: String(item.identifier ?? "Digital object"),
      subtitle: `Object state from ${item.resolvedByNodeCode || item.targetNodeCode || "unknown node"}`,
      tone: item.success === false ? "warn" : "good",
      metrics: [
        { label: "Status", value: String(digitalObject.status ?? item.code ?? "-") },
        { label: "Revision", value: String(digitalObject.revision ?? "-") },
        { label: "Resource bound", value: digitalObject.resource ? "yes" : "no" },
        { label: "Node", value: String(item.resolvedByNodeCode ?? item.targetNodeCode ?? "-") }
      ]
    });
  }

  for (const item of componentItems) {
    const resource = item.resource ?? {};
    cards.push({
      title: String(item.identifier ?? "Resource evidence"),
      subtitle: `Component resolution from ${item.resolvedByNodeCode || item.targetNodeCode || "unknown node"}`,
      tone: item.success === false ? "warn" : "neutral",
      metrics: [
        { label: "Storage", value: String(resource.storageType ?? "-") },
        { label: "Access", value: String(resource.accessMethod ?? "-") },
        { label: "Format", value: String(resource.format ?? "-") },
        { label: "Content", value: String(resource.contentType ?? "-") }
      ]
    });
  }

  for (const record of auditRecords) {
    cards.push({
      title: record.operation || "Audit record",
      subtitle: record.createdAt || "Unknown time",
      tone: record.success === false ? "bad" : "neutral",
      metrics: [
        { label: "Identifier", value: String(record.identifier ?? "-") },
        { label: "Actor", value: String(record.actorUserId ?? "-") },
        { label: "Role", value: String(record.actorRole ?? "-") },
        { label: "Success", value: record.success === false ? "false" : "true" }
      ],
      bullets: record.message ? [record.message] : undefined
    });
  }

  return cards.slice(0, 16);
}

function buildMarkdownReport(report: Omit<ResearchReport, "markdown">): string {
  const lines: string[] = [];
  lines.push(`# ${report.title}`);
  lines.push("");
  lines.push(`- Generated at: ${report.generatedAt}`);
  lines.push(`- Template: ${report.templateTitle}`);
  lines.push(`- Question: ${report.question || "N/A"}`);
  lines.push(`- Focused identifiers: ${report.focusedIdentifiers.length ? report.focusedIdentifiers.join(", ") : "N/A"}`);
  lines.push("");

  for (const section of report.summarySections) {
    lines.push(`## ${section.title}`);
    lines.push("");
    for (const line of section.lines) {
      lines.push(`- ${line}`);
    }
    lines.push("");
  }

  if (report.evidenceCards.length) {
    lines.push("## Evidence Cards");
    lines.push("");
    for (const card of report.evidenceCards) {
      lines.push(`### ${card.title}`);
      if (card.subtitle) {
        lines.push(`- ${card.subtitle}`);
      }
      for (const metric of card.metrics ?? []) {
        lines.push(`- ${metric.label}: ${metric.value}`);
      }
      for (const bullet of card.bullets ?? []) {
        lines.push(`- ${bullet}`);
      }
      lines.push("");
    }
  }

  return lines.join("\n");
}

function buildReport(form: ResearchFormState, focusedIdentifiers: string[], outputs: Record<string, unknown>): ResearchReport {
  const template = getTemplateById(form.templateId);
  const summarySections = buildSummarySections(form, focusedIdentifiers, outputs);
  const evidenceCards = buildEvidenceCards(focusedIdentifiers, outputs);
  const baseReport = {
    title: `DRIS DeepResearch - ${template.title}`,
    templateTitle: template.title,
    question: form.question.trim(),
    focusedIdentifiers,
    summarySections,
    evidenceCards,
    generatedAt: nowIso()
  };

  return {
    ...baseReport,
    markdown: buildMarkdownReport(baseReport)
  };
}

export async function runResearchWorkflow(
  client: BrowserMcpClient,
  form: ResearchFormState,
  onStepUpdate: (step: ToolExecutionStep) => void
): Promise<ResearchRunResult> {
  const template = getTemplateById(form.templateId);
  const explicitIdentifiers = parseIdentifiers(form.identifiersText);
  const outputs: Record<string, unknown> = {};

  if (template.requiresQuestion && !form.question.trim()) {
    throw new Error("This template requires a research question.");
  }

  if (template.requiresIdentifiers && explicitIdentifiers.length === 0) {
    throw new Error("This template requires at least one identifier.");
  }

  let focusedIdentifiers = [...explicitIdentifiers];

  await executeTool(client, outputs, onStepUpdate, "get_node_routes", "Load node routes", "Fetch current cross-node routing context before running the research flow.", {});

  if (template.id === "discovery_overview" || template.id === "anchor_search") {
    const searchResult = await executeTool(
      client,
      outputs,
      onStepUpdate,
      "search_global",
      template.id === "anchor_search" ? "Anchor search" : "Global search",
      template.id === "anchor_search"
        ? "Run a global search from the selected anchor node to prove the search path can be constrained."
        : "Run a distributed search to discover candidate identifiers for deeper analysis.",
      buildSearchArgs(form)
    );

    focusedIdentifiers = selectIdentifiers(explicitIdentifiers, readSearchItems(searchResult), clamp(form.limit, 1, 8));
    if (!focusedIdentifiers.length) {
      throw new Error("Search finished but no identifier could be selected for the next research step.");
    }
  }

  if (template.id === "discovery_overview" || template.id === "identifier_compare" || template.id === "anchor_search") {
    await executeTool(
      client,
      outputs,
      onStepUpdate,
      "batch_resolve_metadata_distributed",
      "Batch metadata resolution",
      "Resolve identifier metadata across nodes to build the study set baseline.",
      { identifiers: focusedIdentifiers }
    );

    await executeTool(
      client,
      outputs,
      onStepUpdate,
      "batch_resolve_object_distributed",
      "Batch object resolution",
      "Resolve digital object snapshots for the selected identifier set.",
      { identifiers: focusedIdentifiers }
    );

    await executeTool(
      client,
      outputs,
      onStepUpdate,
      "batch_resolve_component_distributed",
      "Batch component resolution",
      "Resolve resource or component evidence for each selected identifier.",
      { identifiers: focusedIdentifiers }
    );

    for (const identifier of focusedIdentifiers.slice(0, 3)) {
      await executeTool(
        client,
        outputs,
        onStepUpdate,
        "query_audit_logs",
        `Audit trace - ${identifier}`,
        "Query paginated audit history to show traceability and governance evidence.",
        buildAuditArgs(form, identifier)
      );
    }
  }

  if (template.id === "audit_trace") {
    const identifier = explicitIdentifiers[0];
    focusedIdentifiers = [identifier!];

    await executeTool(
      client,
      outputs,
      onStepUpdate,
      "resolve_metadata",
      "Resolve metadata",
      "Resolve metadata for the focused identifier.",
      { identifier }
    );

    await executeTool(
      client,
      outputs,
      onStepUpdate,
      "resolve_object",
      "Resolve digital object",
      "Resolve the digital object snapshot for the focused identifier.",
      { identifier }
    );

    await executeTool(
      client,
      outputs,
      onStepUpdate,
      "resolve_component",
      "Resolve resource component",
      "Resolve the resource component to attach storage and access evidence.",
      { identifier }
    );

    await executeTool(
      client,
      outputs,
      onStepUpdate,
      "query_audit_logs",
      "Query audit history",
      "Query audit history for the focused identifier.",
      buildAuditArgs(form, identifier)
    );
  }

  const report = buildReport(form, focusedIdentifiers, outputs);
  return {
    report,
    outputs,
    focusedIdentifiers
  };
}



