export type StepStatus = "pending" | "running" | "success" | "error" | "skipped";
export type TemplateId = "discovery_overview" | "identifier_compare" | "audit_trace" | "anchor_search";
export type SearchMode = "KEYWORD" | "SEMANTIC" | "HYBRID";
export type SearchStrategy = "ITERATIVE" | "RECURSIVE";
export type PlannerMode = "heuristic" | "llm";

export interface ResearchRunRequest {
  question?: string;
  identifiersText?: string;
  anchorNodeCode?: string;
  searchMode?: SearchMode;
  strategy?: SearchStrategy;
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

export interface ResearchFormState {
  question: string;
  identifiersText: string;
  anchorNodeCode: string;
  searchMode: SearchMode;
  strategy: SearchStrategy;
  limit: number;
  ttl: number;
  maxFanout: number;
  perNodeLimit: number;
  globalDeadlineMs: number;
  hopTimeoutMs: number;
  auditOperation: string;
  auditStartTime: string;
  auditEndTime: string;
  auditPageSize: number;
  mcpUrl: string;
  llmModel: string;
}

export interface PlanStep {
  toolName: string;
  title: string;
  description: string;
}

export interface ResearchPlan {
  plannerMode: PlannerMode;
  templateId: TemplateId;
  title: string;
  rationale: string[];
  extractedQuestion: string;
  explicitIdentifiers: string[];
  focusedIdentifiers: string[];
  searchEnabled: boolean;
  searchArgs?: Record<string, unknown>;
  auditIdentifiers: string[];
  steps: PlanStep[];
}

export interface ToolExecutionStep {
  id: string;
  title: string;
  toolName: string;
  description: string;
  status: StepStatus;
  startedAt?: string;
  endedAt?: string;
  durationMs?: number;
  args?: Record<string, unknown>;
  summary?: string;
  error?: string;
  result?: unknown;
}

export interface EvidenceCard {
  title: string;
  subtitle?: string;
  tone?: "neutral" | "good" | "warn" | "bad";
  metrics?: Array<{ label: string; value: string }>;
  bullets?: string[];
}

export interface SummarySection {
  title: string;
  lines: string[];
}

export interface ResearchReport {
  title: string;
  templateTitle: string;
  question: string;
  focusedIdentifiers: string[];
  summarySections: SummarySection[];
  evidenceCards: EvidenceCard[];
  generatedAt: string;
  narrativeMarkdown?: string;
  multimodalMarkdown?: string;
  markdown: string;
}

export interface ResearchRunResponse {
  ok: boolean;
  plannerMode: PlannerMode;
  mcpUrl: string;
  llmModel: string;
  plan: ResearchPlan;
  steps: ToolExecutionStep[];
  report: ResearchReport;
  focusedIdentifiers: string[];
  durationMs: number;
}

export interface ResearchStreamBaseEvent {
  type: "meta" | "plan" | "step" | "report" | "summary_delta" | "done" | "error";
}

export interface ResearchMetaEvent extends ResearchStreamBaseEvent {
  type: "meta";
  plannerMode: PlannerMode;
  llmModel: string;
  mcpUrl: string;
}

export interface ResearchPlanEvent extends ResearchStreamBaseEvent {
  type: "plan";
  plan: ResearchPlan;
}

export interface ResearchStepEvent extends ResearchStreamBaseEvent {
  type: "step";
  step: ToolExecutionStep;
}

export interface ResearchReportEvent extends ResearchStreamBaseEvent {
  type: "report";
  report: ResearchReport;
}

export interface ResearchSummaryDeltaEvent extends ResearchStreamBaseEvent {
  type: "summary_delta";
  delta: string;
  accumulated: string;
}

export interface ResearchDoneEvent extends ResearchStreamBaseEvent {
  type: "done";
  focusedIdentifiers: string[];
  durationMs: number;
}

export interface ResearchErrorEvent extends ResearchStreamBaseEvent {
  type: "error";
  message: string;
}

export type ResearchStreamEvent =
  | ResearchMetaEvent
  | ResearchPlanEvent
  | ResearchStepEvent
  | ResearchReportEvent
  | ResearchSummaryDeltaEvent
  | ResearchDoneEvent
  | ResearchErrorEvent;

export interface ToolEnvelope<T = unknown> {
  ok?: boolean;
  code?: string;
  message?: string;
  status?: number;
  data?: T;
}

export interface SearchItem {
  identifier?: string;
  name?: string | null;
  description?: string | null;
  owner?: string | null;
  industry?: string | null;
  dataCategory?: string | null;
  registrationType?: string | null;
  entityType?: string | null;
  updateTime?: string | null;
  source?: string | null;
  finalScore?: number | null;
}

export interface BatchItemBase {
  identifier?: string;
  targetNodeCode?: string;
  resolvedByNodeCode?: string;
  success?: boolean;
  code?: string;
  message?: string;
}

export interface AuditRecord {
  id?: number;
  identifier?: string;
  operation?: string;
  actorUserId?: string;
  actorRole?: string;
  success?: boolean;
  message?: string;
  createdAt?: string;
}
