export type StepStatus = "pending" | "running" | "success" | "error" | "skipped";

export interface ResearchTemplate {
  id: string;
  title: string;
  subtitle: string;
  description: string;
  expectedOutput: string;
  requiresQuestion: boolean;
  requiresIdentifiers: boolean;
}

export interface ResearchFormState {
  mcpUrl: string;
  templateId: string;
  question: string;
  identifiersText: string;
  anchorNodeCode: string;
  searchMode: "KEYWORD" | "SEMANTIC" | "HYBRID";
  strategy: "ITERATIVE" | "RECURSIVE";
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

export interface ConnectionSnapshot {
  status: "idle" | "connecting" | "connected" | "error";
  healthStatus: "unknown" | "checking" | "up" | "down";
  healthMessage: string;
  tools: string[];
  error: string;
  lastConnectedAt: string;
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
  markdown: string;
}

export interface ResearchRunResult {
  report: ResearchReport;
  outputs: Record<string, unknown>;
  focusedIdentifiers: string[];
}

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
