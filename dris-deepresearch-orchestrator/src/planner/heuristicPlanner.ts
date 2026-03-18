import type {
  PlanStep,
  PlannerMode,
  ResearchFormState,
  ResearchPlan,
  ResearchRunRequest,
  SearchMode,
  SearchStrategy,
  TemplateId
} from "../types/research.js";

const IDENTIFIER_PATTERN = /\b[0-9A-Za-z]{16,}\b/g;
const FULL_NODE_CODE_PATTERN = /\b\d{4}(?:ROOT|PROA|CITA|CNTA|CITB|CNTB)\b/gi;
const REGION_CODE_PATTERN = /\b\d{4}\b/g;
const QUESTION_TOKEN_PATTERN = /[\p{L}\p{N}\u4e00-\u9fff]+/gu;
const SEARCH_STOPWORDS = new Set([
  "查找", "检索", "查询", "搜索", "帮我", "请", "生成", "整理", "分析", "报告",
  "相关", "重点", "类型", "行业", "区域", "地区", "节点", "范围", "对象", "数字对象",
  "资源", "数据", "信息", "内容", "方面", "北京", "beijing", "region", "area", "node",
  "under", "related", "about", "focus", "focused", "type", "types", "within", "across",
  "with", "from", "into", "that", "this", "these", "those", "show", "find", "search",
  "research", "report", "summary", "overview"
]);
const DATASET_HINTS = ["dataset", "data set", "数据集", "dataset type", "dataset 类型"];
const DEVICE_HINTS = ["device", "设备"];
const RESOURCE_HINTS = ["resource", "资源"];
const SERVICE_HINTS = ["service", "api", "服务"];
const TOOL_HINTS = ["tool", "工具"];
const DOWNLOAD_HINTS = ["download", "downloadable", "下载", "可下载"];
const FORMAT_HINTS: Array<[string, string[]]> = [
  ["csv", ["csv"]],
  ["json", ["json"]],
  ["xml", ["xml"]],
  ["html", ["html"]],
  ["pdf", ["pdf"]],
  ["doc", ["doc", "docx", "document", "文档"]],
  ["xls", ["xls", "xlsx", "excel", "spreadsheet", "表格"]]
];

type SearchFilterMap = Record<string, string>;

interface SearchIntent {
  naturalQuery: string;
  keywords: string[];
  filters: SearchFilterMap;
  anchorNodeCode: string;
}

function unique(values: string[]): string[] {
  return Array.from(new Set(values.map((item) => item.trim()).filter(Boolean)));
}

function clamp(value: number, min: number, max: number): number {
  return Math.max(min, Math.min(max, value));
}

function safeLower(value?: string): string {
  return (value ?? "").trim().toLowerCase();
}

function containsAny(question: string, fragments: string[]): boolean {
  const normalized = safeLower(question);
  return fragments.some((fragment) => normalized.includes(safeLower(fragment)));
}

function tokenizeQuestion(question: string): string[] {
  return unique((question.match(QUESTION_TOKEN_PATTERN) ?? []).map((item) => item.trim()));
}

function normalizeAnchorNodeCode(anchorNodeCode?: string): string {
  return (anchorNodeCode ?? "").trim().toUpperCase();
}

function inferAnchorNodeCode(question: string, anchorNodeCode?: string): string {
  const explicit = normalizeAnchorNodeCode(anchorNodeCode);
  if (explicit) {
    return explicit;
  }

  const direct = question.match(FULL_NODE_CODE_PATTERN);
  if (direct && direct.length) {
    return direct[0].toUpperCase();
  }

  const regionMatches = question.match(REGION_CODE_PATTERN) ?? [];
  for (const raw of regionMatches) {
    const code = Number(raw);
    if (!Number.isFinite(code)) {
      continue;
    }
    if (code >= 1900 && code <= 2099) {
      continue;
    }
    return `${raw}ROOT`;
  }
  return "";
}

function addFilterIfMentioned(filters: SearchFilterMap, key: string, value: string, question: string, hints: string[]): void {
  if (filters[key]) {
    return;
  }
  if (containsAny(question, hints)) {
    filters[key] = value;
  }
}

function inferFiltersFromQuestion(question: string): SearchFilterMap {
  const filters: SearchFilterMap = {};
  addFilterIfMentioned(filters, "entityType", "dataset", question, DATASET_HINTS);
  addFilterIfMentioned(filters, "entityType", "device", question, DEVICE_HINTS);
  addFilterIfMentioned(filters, "componentType", "resource", question, RESOURCE_HINTS);
  addFilterIfMentioned(filters, "componentType", "service", question, SERVICE_HINTS);
  addFilterIfMentioned(filters, "componentType", "tool", question, TOOL_HINTS);
  addFilterIfMentioned(filters, "accessMethod", "download", question, DOWNLOAD_HINTS);
  for (const [format, hints] of FORMAT_HINTS) {
    addFilterIfMentioned(filters, "format", format, question, hints);
  }
  return filters;
}

function isYearLike(token: string): boolean {
  if (!/^\d{4}$/.test(token)) {
    return false;
  }
  const value = Number(token);
  return value >= 1900 && value <= 2099;
}

function tokenBelongsToFilter(token: string, filters: SearchFilterMap): boolean {
  const normalized = safeLower(token);
  if (!normalized) {
    return false;
  }
  if (filters.entityType === "dataset" && DATASET_HINTS.some((hint) => normalized === safeLower(hint))) {
    return true;
  }
  if (filters.entityType === "device" && DEVICE_HINTS.some((hint) => normalized === safeLower(hint))) {
    return true;
  }
  if (filters.componentType === "resource" && RESOURCE_HINTS.some((hint) => normalized === safeLower(hint))) {
    return true;
  }
  if (filters.componentType === "service" && SERVICE_HINTS.some((hint) => normalized === safeLower(hint))) {
    return true;
  }
  if (filters.componentType === "tool" && TOOL_HINTS.some((hint) => normalized === safeLower(hint))) {
    return true;
  }
  if (filters.accessMethod === "download" && DOWNLOAD_HINTS.some((hint) => normalized === safeLower(hint))) {
    return true;
  }
  if (filters.format) {
    for (const [format, hints] of FORMAT_HINTS) {
      if (filters.format !== format) {
        continue;
      }
      if (hints.some((hint) => normalized === safeLower(hint))) {
        return true;
      }
    }
  }
  return false;
}

function buildSearchKeywords(question: string, filters: SearchFilterMap, anchorNodeCode: string): string[] {
  const keywords: string[] = [];
  const regionCode = anchorNodeCode ? anchorNodeCode.slice(0, 4).toLowerCase() : "";
  for (const token of tokenizeQuestion(question)) {
    const normalized = safeLower(token);
    if (!normalized) {
      continue;
    }
    if (SEARCH_STOPWORDS.has(normalized)) {
      continue;
    }
    if (anchorNodeCode && normalized === safeLower(anchorNodeCode)) {
      continue;
    }
    if (regionCode && normalized === regionCode) {
      continue;
    }
    if (/^\d+$/.test(normalized) && (regionCode === normalized || isYearLike(normalized))) {
      continue;
    }
    if (tokenBelongsToFilter(normalized, filters)) {
      continue;
    }
    keywords.push(token);
  }
  return unique(keywords).slice(0, 8);
}

function buildSearchIntent(form: ResearchFormState): SearchIntent {
  const anchorNodeCode = inferAnchorNodeCode(form.question, form.anchorNodeCode);
  const filters = inferFiltersFromQuestion(form.question);
  const keywords = buildSearchKeywords(form.question, filters, anchorNodeCode);
  const naturalQuery = keywords.length ? keywords.slice(0, 4).join(" ") : form.question.trim();
  return {
    naturalQuery: naturalQuery || form.question.trim(),
    keywords,
    filters,
    anchorNodeCode
  };
}

export function parseIdentifiers(raw: string): string[] {
  return unique(
    raw
      .split(/[\s,;\n\r]+/)
      .map((item) => item.trim())
      .filter(Boolean)
  );
}

export function extractIdentifiersFromQuestion(question: string): string[] {
  return unique(Array.from(question.matchAll(IDENTIFIER_PATTERN)).map((match) => match[0] ?? ""));
}

function normalizeSearchMode(value?: string): SearchMode {
  return value === "KEYWORD" || value === "SEMANTIC" ? value : "HYBRID";
}

function normalizeStrategy(value?: string): SearchStrategy {
  return value === "ITERATIVE" ? value : "RECURSIVE";
}

export function normalizeResearchForm(request: ResearchRunRequest, defaultMcpUrl: string): ResearchFormState {
  return {
    question: (request.question ?? "").trim(),
    identifiersText: (request.identifiersText ?? "").trim(),
    anchorNodeCode: (request.anchorNodeCode ?? "").trim(),
    searchMode: normalizeSearchMode(request.searchMode),
    strategy: normalizeStrategy(request.strategy),
    limit: clamp(request.limit ?? 5, 1, 20),
    ttl: clamp(request.ttl ?? 4, 1, 16),
    maxFanout: clamp(request.maxFanout ?? 8, 1, 64),
    perNodeLimit: clamp(request.perNodeLimit ?? 20, 1, 100),
    globalDeadlineMs: clamp(request.globalDeadlineMs ?? 5000, 500, 20000),
    hopTimeoutMs: clamp(request.hopTimeoutMs ?? 1500, 200, 10000),
    auditOperation: (request.auditOperation ?? "").trim(),
    auditStartTime: request.auditStartTime ?? "",
    auditEndTime: request.auditEndTime ?? "",
    auditPageSize: clamp(request.auditPageSize ?? 5, 1, 20),
    mcpUrl: (request.mcpUrl ?? defaultMcpUrl).trim() || defaultMcpUrl,
    llmModel: (request.llmModel ?? "").trim()
  };
}

export function buildSearchArgs(form: ResearchFormState): Record<string, unknown> {
  const intent = buildSearchIntent(form);
  const searchArgs: Record<string, unknown> = {
    naturalQuery: intent.naturalQuery,
    limit: form.limit,
    offset: 0,
    mode: form.searchMode,
    strategy: form.strategy,
    anchorNodeCode: intent.anchorNodeCode || undefined,
    ttl: form.ttl,
    maxFanout: form.maxFanout,
    perNodeLimit: form.perNodeLimit,
    globalDeadlineMs: form.globalDeadlineMs,
    hopTimeoutMs: form.hopTimeoutMs,
    traceId: `agent-${Date.now()}`
  };
  if (intent.keywords.length) {
    searchArgs.keywords = intent.keywords;
  }
  if (Object.keys(intent.filters).length) {
    searchArgs.filters = intent.filters;
  }
  return searchArgs;
}

export function chooseTemplateHeuristically(form: ResearchFormState, identifiers: string[]): { templateId: TemplateId; rationale: string[] } {
  const rationale: string[] = [];
  const question = form.question.trim();
  const hasQuestion = Boolean(question);
  const hasAnchor = Boolean(form.anchorNodeCode);
  const compareIntent = containsAny(question, ["compare", "difference", "versus", "vs", "duibi", "bijiao", "compare two", "compare these", "比较", "对比", "差异"]);
  const auditIntent = containsAny(question, ["audit", "history", "trace", "provenance", "operation", "timeline", "review", "审计", "日志", "轨迹", "追溯", "历史", "操作"]);

  if (hasAnchor) {
    rationale.push(`Anchor node ${form.anchorNodeCode} was provided, so the search should start from a constrained node.`);
    return { templateId: "anchor_search", rationale };
  }

  if (identifiers.length > 1 && compareIntent) {
    rationale.push(`Multiple identifiers were provided and the question contains comparison intent.`);
    return { templateId: "identifier_compare", rationale };
  }

  if (identifiers.length >= 1 && auditIntent) {
    rationale.push(`An identifier is already known and the question emphasizes audit or provenance analysis.`);
    return { templateId: "audit_trace", rationale };
  }

  if (identifiers.length > 1) {
    rationale.push(`Multiple explicit identifiers were provided, so direct comparison is cheaper than discovery search.`);
    return { templateId: "identifier_compare", rationale };
  }

  if (identifiers.length === 1 && !hasQuestion) {
    rationale.push(`A single explicit identifier was provided without a broader discovery question.`);
    return { templateId: "audit_trace", rationale };
  }

  if (hasQuestion) {
    rationale.push(`A natural-language research question was provided and discovery search is needed to select study targets.`);
    return { templateId: "discovery_overview", rationale };
  }

  rationale.push(`No strong signal was found, so the planner falls back to discovery overview.`);
  return { templateId: "discovery_overview", rationale };
}

function buildPlanSteps(templateId: TemplateId): PlanStep[] {
  const common: PlanStep[] = [
    {
      toolName: "get_node_routes",
      title: "Load node routes",
      description: "Fetch current cross-node routing context before any research execution."
    }
  ];

  if (templateId === "discovery_overview" || templateId === "anchor_search") {
    return [
      ...common,
      {
        toolName: "search_global",
        title: templateId === "anchor_search" ? "Anchor search" : "Global search",
        description: templateId === "anchor_search"
          ? "Search from the given anchor node to discover identifiers for deeper analysis."
          : "Run distributed search to discover identifiers for deeper analysis."
      },
      {
        toolName: "batch_resolve_metadata_distributed",
        title: "Batch metadata resolution",
        description: "Resolve metadata across nodes for the selected identifiers."
      },
      {
        toolName: "batch_resolve_object_distributed",
        title: "Batch object resolution",
        description: "Resolve digital object snapshots across nodes."
      },
      {
        toolName: "batch_resolve_component_distributed",
        title: "Batch component resolution",
        description: "Resolve resource or component evidence across nodes."
      },
      {
        toolName: "query_audit_logs",
        title: "Audit trace",
        description: "Query recent audit history for the selected identifiers."
      }
    ];
  }

  if (templateId === "identifier_compare") {
    return [
      ...common,
      {
        toolName: "batch_resolve_metadata_distributed",
        title: "Batch metadata resolution",
        description: "Resolve metadata for the explicit identifier set."
      },
      {
        toolName: "batch_resolve_object_distributed",
        title: "Batch object resolution",
        description: "Resolve digital object snapshots for the explicit identifier set."
      },
      {
        toolName: "batch_resolve_component_distributed",
        title: "Batch component resolution",
        description: "Resolve resource evidence for the explicit identifier set."
      },
      {
        toolName: "query_audit_logs",
        title: "Audit trace",
        description: "Query recent audit history for the compared identifiers."
      }
    ];
  }

  return [
    ...common,
    {
      toolName: "resolve_metadata",
      title: "Resolve metadata",
      description: "Resolve metadata for the focused identifier."
    },
    {
      toolName: "resolve_object",
      title: "Resolve digital object",
      description: "Resolve the digital object snapshot for the focused identifier."
    },
    {
      toolName: "resolve_component",
      title: "Resolve resource component",
      description: "Resolve the resource evidence for the focused identifier."
    },
    {
      toolName: "query_audit_logs",
      title: "Query audit history",
      description: "Query recent audit history for the focused identifier."
    }
  ];
}

function planTitle(templateId: TemplateId): string {
  if (templateId === "audit_trace") {
    return "Audit-focused deep research";
  }
  if (templateId === "identifier_compare") {
    return "Identifier comparison deep research";
  }
  if (templateId === "anchor_search") {
    return "Anchor-constrained discovery research";
  }
  return "Discovery-oriented deep research";
}

function describeSearchArgs(searchArgs?: Record<string, unknown>): string[] {
  if (!searchArgs) {
    return [];
  }
  const notes: string[] = [];
  const keywords = Array.isArray(searchArgs.keywords) ? (searchArgs.keywords as string[]) : [];
  if (keywords.length) {
    notes.push(`Search keywords were rewritten to: ${keywords.join(", ")}.`);
  }
  const filters = searchArgs.filters && typeof searchArgs.filters === "object"
    ? Object.entries(searchArgs.filters as Record<string, unknown>)
        .map(([key, value]) => `${key}=${String(value)}`)
    : [];
  if (filters.length) {
    notes.push(`Search filters inferred from the question: ${filters.join(", ")}.`);
  }
  const anchorNodeCode = typeof searchArgs.anchorNodeCode === "string" ? searchArgs.anchorNodeCode.trim() : "";
  if (anchorNodeCode) {
    notes.push(`Search will start from anchor node ${anchorNodeCode}.`);
  }
  return notes;
}

export function buildPlanFromTemplate(
  form: ResearchFormState,
  templateId: TemplateId,
  explicitIdentifiers: string[],
  rationale: string[],
  plannerMode: PlannerMode
): ResearchPlan {
  const searchEnabled = templateId === "discovery_overview" || templateId === "anchor_search";
  const searchArgs = searchEnabled ? buildSearchArgs(form) : undefined;
  const nextRationale = [...rationale];
  if (explicitIdentifiers.length) {
    nextRationale.push(`The planner extracted ${explicitIdentifiers.length} explicit identifier(s) from input.`);
  }
  if (form.auditOperation) {
    nextRationale.push(`Audit operation filter '${form.auditOperation}' will be applied where audit logs are queried.`);
  }
  nextRationale.push(...describeSearchArgs(searchArgs));

  return {
    plannerMode,
    templateId,
    title: planTitle(templateId),
    rationale: nextRationale,
    extractedQuestion: form.question,
    explicitIdentifiers,
    focusedIdentifiers: [...explicitIdentifiers],
    searchEnabled,
    searchArgs,
    auditIdentifiers: [...explicitIdentifiers],
    steps: buildPlanSteps(templateId)
  };
}

export function createResearchPlan(form: ResearchFormState): ResearchPlan {
  const explicitIdentifiers = unique([
    ...parseIdentifiers(form.identifiersText),
    ...extractIdentifiersFromQuestion(form.question)
  ]);
  const choice = chooseTemplateHeuristically(form, explicitIdentifiers);
  return buildPlanFromTemplate(form, choice.templateId, explicitIdentifiers, choice.rationale, "heuristic");
}
