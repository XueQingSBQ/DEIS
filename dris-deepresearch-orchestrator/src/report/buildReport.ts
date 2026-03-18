import type {
  AuditRecord,
  BatchItemBase,
  EvidenceCard,
  ResearchFormState,
  ResearchPlan,
  ResearchReport,
  SearchItem,
  SummarySection,
  ToolEnvelope
} from "../types/research.js";
import { extractEnvelope } from "../executor/mcpResearchClient.js";

interface MetadataBatchItem extends BatchItemBase {
  metadata?: Record<string, unknown>;
}

interface ObjectBatchItem extends BatchItemBase {
  digitalObject?: Record<string, unknown>;
}

interface ComponentBatchItem extends BatchItemBase {
  resource?: Record<string, unknown>;
}

interface SearchResourceItem extends SearchItem {
  componentType?: string | null;
  storageType?: string | null;
  accessMethod?: string | null;
  contentType?: string | null;
  format?: string | null;
}

interface ResourceProfile {
  key: string;
  identifier: string;
  name: string;
  description: string;
  owner: string;
  industry: string;
  dataCategory: string;
  registrationType: string;
  entityType: string;
  sourceNodeCode: string;
  targetNodeCode: string;
  resolvedByNodeCode: string;
  updateTime: string;
  searchScore: number | null;
  searchRank: number;
  metadataStatus: string;
  objectStatus: string;
  revision: number | null;
  resourceBound: boolean;
  componentType: string;
  storageType: string;
  accessMethod: string;
  contentType: string;
  format: string;
  locator: string;
  size: number | null;
  auditCount: number;
  auditFailureCount: number;
  lastAuditOperation: string;
  lastAuditTime: string;
  hasMetadata: boolean;
  hasObject: boolean;
  hasComponent: boolean;
}

interface BatchStats {
  total: number;
  successCount: number;
  failureCount: number;
  partial: boolean;
  mode: string;
}

interface ResourceLandscape {
  searchItems: SearchResourceItem[];
  profiles: ResourceProfile[];
  auditRecords: AuditRecord[];
  visitedNodeCodes: string[];
  failedNodes: string[];
  metadataStats: BatchStats;
  objectStats: BatchStats;
  componentStats: BatchStats;
  routeCount: number;
}

interface RelevanceView {
  total: number;
  rankByKey: Map<string, number>;
}

export interface ResourcePreviewInfo {
  locator: string;
  kind: string;
  contentType: string;
  contentLength: number | null;
  summary: string;
  lines: string[];
}

export type ResourcePreviewMap = Map<string, ResourcePreviewInfo>;

const UNKNOWN_TEXT = "-";
const IDENTIFIER_PATTERN = /[0-9A-Za-z]{16,}/;

function nowIso(): string {
  return new Date().toISOString();
}

function collectToolOutputs(outputs: Record<string, unknown>, toolName: string): unknown[] {
  const value = outputs[toolName];
  if (typeof value === "undefined") {
    return [];
  }
  return Array.isArray(value) ? value : [value];
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

function preferText(...values: Array<string | undefined | null>): string {
  for (const value of values) {
    if (typeof value === "string" && value.trim()) {
      return value.trim();
    }
  }
  return "";
}

function preferHigherNumber(current: number | null, next: number | null): number | null {
  if (next === null) {
    return current;
  }
  if (current === null) {
    return next;
  }
  return Math.max(current, next);
}

function unique(values: string[]): string[] {
  return Array.from(new Set(values.map((item) => item.trim()).filter(Boolean)));
}

function normalizeKeyHint(value: string): string {
  const normalized = value.replace(/[^0-9A-Za-z_-]+/g, "-").replace(/-+/g, "-").replace(/^-|-$/g, "");
  return normalized || "item";
}

function buildSyntheticKey(prefix: string, index: number, hint: string): string {
  return `${prefix}:${index}:${normalizeKeyHint(hint).slice(0, 24)}`;
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

function readSearchItems(output: unknown): SearchResourceItem[] {
  const data = readObject(asEnvelope(output).data);
  if (!data) {
    return [];
  }
  const items = data.items ?? data.results;
  return Array.isArray(items)
    ? (items.filter((item) => item && typeof item === "object") as SearchResourceItem[])
    : [];
}

function readVisitedNodeCodes(output: unknown): string[] {
  const data = readObject(asEnvelope(output).data);
  if (!data) {
    return [];
  }
  const visited = data.visitedNodeCodes;
  return Array.isArray(visited) ? unique(visited.map((item) => String(item))) : [];
}

function readFailedNodes(output: unknown): string[] {
  const data = readObject(asEnvelope(output).data);
  if (!data) {
    return [];
  }
  const failed = data.failedNodes;
  return Array.isArray(failed)
    ? failed.map((item) => (typeof item === "string" ? item : JSON.stringify(item)))
    : [];
}

function readBatchItems<T extends BatchItemBase>(output: unknown): T[] {
  const data = readObject(asEnvelope(output).data);
  if (!data) {
    return [];
  }
  const items = data.items;
  return Array.isArray(items) ? (items.filter((item) => item && typeof item === "object") as T[]) : [];
}

function readBatchStats(output: unknown): BatchStats {
  const data = readObject(asEnvelope(output).data);
  if (!data) {
    return { total: 0, successCount: 0, failureCount: 0, partial: false, mode: "unknown" };
  }
  return {
    total: Number(data.total ?? 0),
    successCount: Number(data.successCount ?? 0),
    failureCount: Number(data.failureCount ?? 0),
    partial: Boolean(data.partial),
    mode: String(data.mode ?? "unknown")
  };
}

function readAuditRecords(output: unknown): AuditRecord[] {
  const data = readObject(asEnvelope(output).data);
  if (!data) {
    return [];
  }
  const records = data.records ?? data.items;
  return Array.isArray(records) ? (records.filter((item) => item && typeof item === "object") as AuditRecord[]) : [];
}

function readNodeRoutes(output: unknown): Array<Record<string, unknown>> {
  const data = asEnvelope(output).data;
  return Array.isArray(data) ? (data.filter((item) => item && typeof item === "object") as Array<Record<string, unknown>>) : [];
}

function templateTitle(templateId: ResearchPlan["templateId"]): string {
  if (templateId === "identifier_compare") {
    return "标识对比研究";
  }
  if (templateId === "audit_trace") {
    return "审计追踪研究";
  }
  if (templateId === "anchor_search") {
    return "锚点约束资源发现";
  }
  return "主题资源概览";
}

function createEmptyProfile(key: string): ResourceProfile {
  return {
    key,
    identifier: "",
    name: "",
    description: "",
    owner: "",
    industry: "",
    dataCategory: "",
    registrationType: "",
    entityType: "",
    sourceNodeCode: "",
    targetNodeCode: "",
    resolvedByNodeCode: "",
    updateTime: "",
    searchScore: null,
    searchRank: Number.MAX_SAFE_INTEGER,
    metadataStatus: "",
    objectStatus: "",
    revision: null,
    resourceBound: false,
    componentType: "",
    storageType: "",
    accessMethod: "",
    contentType: "",
    format: "",
    locator: "",
    size: null,
    auditCount: 0,
    auditFailureCount: 0,
    lastAuditOperation: "",
    lastAuditTime: "",
    hasMetadata: false,
    hasObject: false,
    hasComponent: false
  };
}

function resolveProfileKey(map: Map<string, ResourceProfile>, identifier: string, fallbackKey: string): string {
  if (identifier && map.has(identifier)) {
    return identifier;
  }
  if (identifier) {
    return identifier;
  }
  return fallbackKey;
}

function getOrCreateProfile(map: Map<string, ResourceProfile>, key: string): ResourceProfile {
  const existing = map.get(key);
  if (existing) {
    return existing;
  }
  const created = createEmptyProfile(key);
  map.set(key, created);
  return created;
}

function mergeItemBase(profile: ResourceProfile, item: BatchItemBase): void {
  profile.identifier = preferText(profile.identifier, readString(item.identifier));
  profile.targetNodeCode = preferText(profile.targetNodeCode, readString(item.targetNodeCode));
  profile.resolvedByNodeCode = preferText(profile.resolvedByNodeCode, readString(item.resolvedByNodeCode));
}

function mergeMetadataRecord(profile: ResourceProfile, metadata: Record<string, unknown>, fallbackCode: string): void {
  profile.identifier = preferText(profile.identifier, readString(metadata.identifier));
  profile.name = preferText(profile.name, readString(metadata.name));
  profile.description = preferText(profile.description, readString(metadata.description));
  profile.owner = preferText(profile.owner, readString(metadata.owner));
  profile.industry = preferText(profile.industry, readString(metadata.industry));
  profile.dataCategory = preferText(profile.dataCategory, readString(metadata.dataCategory));
  profile.registrationType = preferText(profile.registrationType, readString(metadata.registrationType));
  profile.entityType = preferText(profile.entityType, readString(metadata.entityType));
  profile.updateTime = preferText(profile.updateTime, readString(metadata.updateTime), readString(metadata.createTime));
  profile.metadataStatus = preferText(profile.metadataStatus, readString(metadata.status), fallbackCode);
  profile.hasMetadata = true;
}

function mergeResourceRecord(profile: ResourceProfile, resource: Record<string, unknown>): void {
  profile.componentType = preferText(profile.componentType, readString(resource.componentType));
  profile.storageType = preferText(profile.storageType, readString(resource.storageType));
  profile.accessMethod = preferText(profile.accessMethod, readString(resource.accessMethod));
  profile.contentType = preferText(profile.contentType, readString(resource.contentType));
  profile.format = preferText(profile.format, readString(resource.format));
  profile.locator = preferText(profile.locator, readString(resource.locator));
  profile.size = preferHigherNumber(profile.size, readNumber(resource.size));
  profile.resourceBound = true;
}

function mergeSearchItem(profile: ResourceProfile, item: SearchResourceItem, index: number): void {
  const raw = item as Record<string, unknown>;
  profile.identifier = preferText(profile.identifier, extractIdentifierCandidate(raw), readString(item.identifier));
  profile.name = preferText(profile.name, readString(item.name));
  profile.description = preferText(profile.description, readString(item.description));
  profile.owner = preferText(profile.owner, readString(item.owner));
  profile.industry = preferText(profile.industry, readString(item.industry));
  profile.dataCategory = preferText(profile.dataCategory, readString(item.dataCategory));
  profile.registrationType = preferText(profile.registrationType, readString(item.registrationType));
  profile.entityType = preferText(profile.entityType, readString(item.entityType));
  profile.sourceNodeCode = preferText(profile.sourceNodeCode, readString(item.source));
  profile.updateTime = preferText(profile.updateTime, readString(item.updateTime));
  profile.componentType = preferText(profile.componentType, readString(item.componentType));
  profile.storageType = preferText(profile.storageType, readString(item.storageType));
  profile.accessMethod = preferText(profile.accessMethod, readString(item.accessMethod));
  profile.contentType = preferText(profile.contentType, readString(item.contentType));
  profile.format = preferText(profile.format, readString(item.format));
  profile.searchScore = preferHigherNumber(profile.searchScore, readNumber(item.finalScore));
  profile.searchRank = Math.min(profile.searchRank, index + 1);
}

function mergeObjectItem(profile: ResourceProfile, item: ObjectBatchItem): void {
  mergeItemBase(profile, item);
  const digitalObject = readObject(item.digitalObject);
  if (!digitalObject) {
    return;
  }
  profile.hasObject = true;
  profile.objectStatus = preferText(profile.objectStatus, readString(digitalObject.status), readString(item.code));
  profile.revision = preferHigherNumber(profile.revision, readNumber(digitalObject.revision));

  const metadata = readObject(digitalObject.metadata);
  if (metadata) {
    mergeMetadataRecord(profile, metadata, readString(item.code));
  }

  const resource = readObject(digitalObject.resource);
  if (resource) {
    mergeResourceRecord(profile, resource);
  }
}

function mergeComponentItem(profile: ResourceProfile, item: ComponentBatchItem): void {
  mergeItemBase(profile, item);
  const resource = readObject(item.resource);
  if (!resource) {
    return;
  }
  profile.hasComponent = true;
  mergeResourceRecord(profile, resource);
}

function mergeAuditRecord(profile: ResourceProfile, record: AuditRecord): void {
  profile.auditCount += 1;
  if (record.success === false) {
    profile.auditFailureCount += 1;
  }
  const createdAt = readString(record.createdAt);
  if (createdAt && (!profile.lastAuditTime || createdAt > profile.lastAuditTime)) {
    profile.lastAuditTime = createdAt;
    profile.lastAuditOperation = readString(record.operation);
  }
}

function resolveNodeCode(profile: ResourceProfile): string {
  return preferText(profile.resolvedByNodeCode, profile.targetNodeCode, profile.sourceNodeCode);
}

function classifyResource(profile: ResourceProfile): string {
  const componentType = profile.componentType.toLowerCase();
  const accessMethod = profile.accessMethod.toLowerCase();
  const storageType = profile.storageType.toLowerCase();
  const locator = profile.locator.toLowerCase();

  if (componentType === "service" || accessMethod.includes("api") || accessMethod.includes("rpc") || accessMethod.includes("grpc")) {
    return "服务/API";
  }
  if (componentType === "tool" || accessMethod.includes("tool") || accessMethod.includes("invoke")) {
    return "工具能力";
  }
  if (
    accessMethod.includes("download")
      || accessMethod.includes("get")
      || accessMethod.includes("http")
      || accessMethod.includes("https")
      || storageType.includes("cos")
      || storageType.includes("s3")
      || locator.startsWith("http")
  ) {
    return "可下载数据";
  }
  if (profile.locator) {
    return "可定位资源";
  }
  return "待确认";
}

function profileCompleteness(profile: ResourceProfile): number {
  let score = 0;
  if (profile.hasMetadata) {
    score += 1;
  }
  if (profile.hasObject) {
    score += 1;
  }
  if (profile.hasComponent) {
    score += 1;
  }
  if (profile.locator) {
    score += 1;
  }
  return score;
}

function compareProfiles(left: ResourceProfile, right: ResourceProfile): number {
  const leftScore = left.searchScore ?? -1;
  const rightScore = right.searchScore ?? -1;
  if (rightScore !== leftScore) {
    return rightScore - leftScore;
  }

  const leftCompleteness = profileCompleteness(left);
  const rightCompleteness = profileCompleteness(right);
  if (rightCompleteness !== leftCompleteness) {
    return rightCompleteness - leftCompleteness;
  }

  if (left.searchRank !== right.searchRank) {
    return left.searchRank - right.searchRank;
  }

  return preferText(left.name, left.identifier, left.key).localeCompare(preferText(right.name, right.identifier, right.key));
}

function countBy(values: string[]): Array<[string, number]> {
  const counts = new Map<string, number>();
  for (const value of values.map((item) => item.trim()).filter(Boolean)) {
    counts.set(value, (counts.get(value) ?? 0) + 1);
  }
  return Array.from(counts.entries()).sort((left, right) => right[1] - left[1] || left[0].localeCompare(right[0]));
}

function formatDistribution(values: string[], limit: number): string {
  const top = countBy(values).slice(0, limit);
  if (!top.length) {
    return "未形成明显分布";
  }
  return top.map(([label, count]) => `${label}(${count})`).join("，");
}

function shortText(value: string, maxLength: number): string {
  if (!value || value.length <= maxLength) {
    return value;
  }
  return `${value.slice(0, Math.max(0, maxLength - 1))}...`;
}

function shortLocator(locator: string): string {
  if (!locator) {
    return "";
  }
  if (locator.length <= 72) {
    return locator;
  }
  return `${locator.slice(0, 69)}...`;
}

function formatScore(score: number | null): string {
  return score === null ? UNKNOWN_TEXT : score.toFixed(3);
}

function buildRelevanceView(profiles: ResourceProfile[]): RelevanceView {
  const ranked = profiles
    .filter((profile) => profile.searchScore !== null || profile.searchRank < Number.MAX_SAFE_INTEGER)
    .slice()
    .sort(compareProfiles);
  const rankByKey = new Map<string, number>();
  ranked.forEach((profile, index) => {
    rankByKey.set(profile.key, index + 1);
  });
  return {
    total: ranked.length,
    rankByKey
  };
}

function resolveRelevanceRank(profile: ResourceProfile, relevanceView: RelevanceView): number | null {
  return relevanceView.rankByKey.get(profile.key) ?? null;
}

function resolveRelevanceLevel(rank: number, total: number): string {
  if (total <= 1) {
    return "高";
  }
  const ratio = (rank - 1) / Math.max(total - 1, 1);
  if (ratio <= 0.1) {
    return "很高";
  }
  if (ratio <= 0.35) {
    return "较高";
  }
  if (ratio <= 0.7) {
    return "中等";
  }
  return "一般";
}

function formatRelevance(profile: ResourceProfile, relevanceView: RelevanceView): string {
  const rank = resolveRelevanceRank(profile, relevanceView);
  if (rank === null || relevanceView.total <= 0) {
    return UNKNOWN_TEXT;
  }
  return `${resolveRelevanceLevel(rank, relevanceView.total)}（第 ${rank}/${relevanceView.total}）`;
}

function formatBatchStats(label: string, stats: BatchStats): string {
  return `${label} ${stats.successCount}/${stats.total} 成功，失败 ${stats.failureCount}，模式 ${stats.mode}${stats.partial ? "，结果为部分返回" : ""}`;
}

function buildLandscape(outputs: Record<string, unknown>): ResourceLandscape {
  const searchOutputs = collectToolOutputs(outputs, "search_global");
  const metadataOutputs = collectToolOutputs(outputs, "batch_resolve_metadata_distributed");
  const objectOutputs = collectToolOutputs(outputs, "batch_resolve_object_distributed");
  const componentOutputs = collectToolOutputs(outputs, "batch_resolve_component_distributed");
  const auditOutputs = collectToolOutputs(outputs, "query_audit_logs");
  const routeOutputs = collectToolOutputs(outputs, "get_node_routes");

  const searchItems = searchOutputs.flatMap((output) => readSearchItems(output));
  const metadataItems = metadataOutputs.flatMap((output) => readBatchItems<MetadataBatchItem>(output));
  const objectItems = objectOutputs.flatMap((output) => readBatchItems<ObjectBatchItem>(output));
  const componentItems = componentOutputs.flatMap((output) => readBatchItems<ComponentBatchItem>(output));
  const auditRecords = auditOutputs.flatMap((output) => readAuditRecords(output));
  const visitedNodeCodes = unique(searchOutputs.flatMap((output) => readVisitedNodeCodes(output)));
  const failedNodes = searchOutputs.flatMap((output) => readFailedNodes(output));
  const routeCount = routeOutputs.flatMap((output) => readNodeRoutes(output)).length;

  const profiles = new Map<string, ResourceProfile>();

  searchItems.forEach((item, index) => {
    const raw = item as Record<string, unknown>;
    const identifier = extractIdentifierCandidate(raw);
    const key = resolveProfileKey(
      profiles,
      identifier,
      buildSyntheticKey("search", index, preferText(readString(item.name), readString(item.description), readString(item.source), "item"))
    );
    const profile = getOrCreateProfile(profiles, key);
    mergeSearchItem(profile, item, index);
  });

  metadataItems.forEach((item, index) => {
    const identifier = preferText(readString(item.identifier), extractIdentifierCandidate(item as unknown as Record<string, unknown>));
    const key = resolveProfileKey(profiles, identifier, buildSyntheticKey("metadata", index, preferText(identifier, "item")));
    const profile = getOrCreateProfile(profiles, key);
    mergeItemBase(profile, item);
    const metadata = readObject(item.metadata);
    if (metadata) {
      mergeMetadataRecord(profile, metadata, readString(item.code));
    }
  });

  objectItems.forEach((item, index) => {
    const key = resolveProfileKey(profiles, readString(item.identifier), buildSyntheticKey("object", index, preferText(readString(item.identifier), "item")));
    const profile = getOrCreateProfile(profiles, key);
    mergeObjectItem(profile, item);
  });

  componentItems.forEach((item, index) => {
    const key = resolveProfileKey(profiles, readString(item.identifier), buildSyntheticKey("component", index, preferText(readString(item.identifier), "item")));
    const profile = getOrCreateProfile(profiles, key);
    mergeComponentItem(profile, item);
  });

  auditRecords.forEach((record, index) => {
    const identifier = readString(record.identifier);
    if (!identifier) {
      return;
    }
    const key = resolveProfileKey(profiles, identifier, buildSyntheticKey("audit", index, identifier));
    const profile = getOrCreateProfile(profiles, key);
    profile.identifier = preferText(profile.identifier, identifier);
    mergeAuditRecord(profile, record);
  });

  return {
    searchItems,
    profiles: Array.from(profiles.values()).sort(compareProfiles),
    auditRecords,
    visitedNodeCodes,
    failedNodes,
    metadataStats: metadataOutputs[0] ? readBatchStats(metadataOutputs[0]) : { total: 0, successCount: 0, failureCount: 0, partial: false, mode: "unknown" },
    objectStats: objectOutputs[0] ? readBatchStats(objectOutputs[0]) : { total: 0, successCount: 0, failureCount: 0, partial: false, mode: "unknown" },
    componentStats: componentOutputs[0] ? readBatchStats(componentOutputs[0]) : { total: 0, successCount: 0, failureCount: 0, partial: false, mode: "unknown" },
    routeCount
  };
}

function buildResourceHighlights(profiles: ResourceProfile[], limit: number): string[] {
  const relevanceView = buildRelevanceView(profiles);
  return profiles.slice(0, limit).map((profile, index) => {
    const label = preferText(profile.name, profile.identifier, `候选资源${index + 1}`);
    const parts = [
      `节点=${preferText(resolveNodeCode(profile), "未知")}`,
      `行业=${preferText(profile.industry, "未标注")}`,
      `访问=${classifyResource(profile)}`,
      `格式=${preferText(profile.format, profile.contentType, "未标注")}`
    ];
    const relevance = formatRelevance(profile, relevanceView);
    if (relevance !== UNKNOWN_TEXT) {
      parts.push(`匹配=${relevance}`);
    }
    return `${index + 1}. ${label} | ${parts.join(" | ")}`;
  });
}

function buildPreviewHighlights(profiles: ResourceProfile[], previewMap?: ResourcePreviewMap, limit = 5): string[] {
  if (!previewMap?.size) {
    return [];
  }

  const lines: string[] = [];
  for (const profile of profiles) {
    if (!profile.locator) {
      continue;
    }
    const preview = previewMap.get(profile.locator);
    if (!preview) {
      continue;
    }
    const label = preferText(profile.name, profile.identifier, `候选资源${lines.length + 1}`);
    const detail = preview.lines[0] ? `；${shortText(preview.lines[0], 120)}` : "";
    lines.push(`${lines.length + 1}. ${label}：${preview.summary}${detail}`);
    if (lines.length >= limit) {
      break;
    }
  }
  return lines;
}

function buildSummarySections(
  form: ResearchFormState,
  plan: ResearchPlan,
  outputs: Record<string, unknown>,
  previewMap?: ResourcePreviewMap
): SummarySection[] {
  const landscape = buildLandscape(outputs);
  const profiles = landscape.profiles;
  const searchableProfiles = profiles.filter((profile) => profile.searchScore !== null || profile.searchRank < Number.MAX_SAFE_INTEGER);
  const resolvableProfiles = profiles.filter((profile) => Boolean(profile.identifier));
  const downloadableProfiles = profiles.filter((profile) => classifyResource(profile) === "可下载数据");
  const serviceProfiles = profiles.filter((profile) => classifyResource(profile) === "服务/API");
  const toolProfiles = profiles.filter((profile) => classifyResource(profile) === "工具能力");
  const locatedProfiles = profiles.filter((profile) => Boolean(profile.locator));
  const auditedProfiles = profiles.filter((profile) => profile.auditCount > 0);

  const sections: SummarySection[] = [];
  sections.push({
    title: "研究目标",
    lines: [
      form.question
        ? `研究问题：${form.question}`
        : "未提供自然语言问题，本报告按现有标识和资源证据生成主题概览。",
      `报告模板：${templateTitle(plan.templateId)}；规划模式：${plan.plannerMode}。`,
      "报告中心从标识卡片调整为资源画像，重点展示主题相关数据资源、节点分布与可用性证据。",
      `规划依据：${plan.rationale.join(" ") || "无"}`
    ]
  });

  sections.push({
    title: "发现范围",
    lines: [
      `全网检索返回 ${landscape.searchItems.length} 条候选结果，形成 ${searchableProfiles.length || profiles.length} 个候选资源画像。`,
      `可直接用于后续解析的标识数量为 ${resolvableProfiles.length}；最终报告共汇总 ${profiles.length} 个资源画像。`,
      `访问节点：${landscape.visitedNodeCodes.length ? landscape.visitedNodeCodes.join("，") : "仅本地或未返回节点轨迹"}。`,
      landscape.failedNodes.length ? `失败节点：${landscape.failedNodes.join("；")}` : "本次未报告失败节点。"
    ]
  });

  sections.push({
    title: "资源格局",
    lines: [
      `具备定位信息的资源 ${locatedProfiles.length} 个，可下载数据 ${downloadableProfiles.length} 个，服务/API ${serviceProfiles.length} 个，工具能力 ${toolProfiles.length} 个。`,
      `行业分布：${formatDistribution(profiles.map((profile) => profile.industry), 4)}。`,
      `格式分布：${formatDistribution(profiles.map((profile) => preferText(profile.format, profile.contentType)), 4)}。`,
      `节点分布：${formatDistribution(profiles.map((profile) => resolveNodeCode(profile)), 4)}。`
    ]
  });

  sections.push({
    title: "重点资源画像",
    lines: buildResourceHighlights(profiles, 5).length
      ? buildResourceHighlights(profiles, 5)
      : ["当前没有形成可展示的资源画像，建议扩大检索范围或补充样本数据。"]
  });

  const previewHighlights = buildPreviewHighlights(profiles, previewMap, 5);
  if (previewHighlights.length) {
    sections.push({
      title: "资源内容样本",
      lines: previewHighlights
    });
  }

  sections.push({
    title: "跨节点证据",
    lines: [
      `节点路由记录 ${landscape.routeCount} 条，说明当前编排拿到了跨节点拓扑快照。`,
      formatBatchStats("元数据批量解析：", landscape.metadataStats),
      formatBatchStats("数字对象批量解析：", landscape.objectStats),
      formatBatchStats("资源组件批量解析：", landscape.componentStats)
    ]
  });

  const auditOperationDistribution = formatDistribution(landscape.auditRecords.map((record) => readString(record.operation)), 4);
  const auditFailures = landscape.auditRecords.filter((record) => record.success === false).length;
  const latestAuditRecord = landscape.auditRecords
    .slice()
    .sort((left, right) => readString(right.createdAt).localeCompare(readString(left.createdAt)))[0];
  sections.push({
    title: "审计与治理信号",
    lines: [
      `审计记录共 ${landscape.auditRecords.length} 条，涉及 ${auditedProfiles.length} 个资源画像，失败记录 ${auditFailures} 条。`,
      `操作类型分布：${auditOperationDistribution}。`,
      latestAuditRecord
        ? `最近一次审计：${preferText(readString(latestAuditRecord.operation), "未知操作")} @ ${preferText(readString(latestAuditRecord.createdAt), "未知时间")}。`
        : "当前没有可用审计记录。"
    ]
  });

  const conclusionLines: string[] = [];
  if (profiles.length) {
    conclusionLines.push(`当前主题下已经发现 ${profiles.length} 个候选资源，其中 ${locatedProfiles.length} 个具备明确定位信息，可优先进入共享验证或人工复核。`);
    if (downloadableProfiles.length) {
      conclusionLines.push(`其中 ${downloadableProfiles.length} 个资源可按可下载数据理解，适合继续做样例下载、共享链路验证或报告引用。`);
    }
    if (serviceProfiles.length || toolProfiles.length) {
      conclusionLines.push(`除文件型数据外，还识别到 ${serviceProfiles.length} 个服务/API 和 ${toolProfiles.length} 个工具型资源，可用于数据与能力一体化展示。`);
    }
  } else {
    conclusionLines.push("当前证据不足以形成有效资源画像，建议先扩充样本数据、向量索引或检索提示词。");
  }
  sections.push({
    title: "结论",
    lines: conclusionLines
  });

  return sections;
}

function toneForProfile(profile: ResourceProfile): EvidenceCard["tone"] {
  if (!profile.identifier) {
    return "warn";
  }
  if (profile.hasComponent || profile.locator) {
    return "good";
  }
  if (profile.hasMetadata || profile.hasObject) {
    return "neutral";
  }
  return "warn";
}

function buildProfileBullets(profile: ResourceProfile, previewMap?: ResourcePreviewMap): string[] {
  const bullets: string[] = [];
  if (profile.description) {
    bullets.push(shortText(profile.description, 120));
  }
  const preview = profile.locator ? previewMap?.get(profile.locator) : undefined;
  if (preview) {
    bullets.push(`内容预览：${shortText(preview.summary, 120)}`);
    for (const line of preview.lines.slice(0, 2)) {
      bullets.push(shortText(line, 140));
    }
  }
  const ownerLine = [profile.owner ? `所有者 ${profile.owner}` : "", profile.updateTime ? `更新时间 ${profile.updateTime}` : ""]
    .filter(Boolean)
    .join("，");
  if (ownerLine) {
    bullets.push(ownerLine);
  }
  if (profile.locator) {
    bullets.push(`资源定位：${shortLocator(profile.locator)}`);
  }
  if (profile.auditCount) {
    bullets.push(`审计记录 ${profile.auditCount} 条，失败 ${profile.auditFailureCount} 条。`);
  }
  return bullets.slice(0, 5);
}

function buildEvidenceCards(plan: ResearchPlan, outputs: Record<string, unknown>, previewMap?: ResourcePreviewMap): EvidenceCard[] {
  const landscape = buildLandscape(outputs);
  const relevanceView = buildRelevanceView(landscape.profiles);
  const cards: EvidenceCard[] = [];

  for (const profile of landscape.profiles.slice(0, 10)) {
    cards.push({
      title: preferText(profile.name, profile.identifier, "候选资源"),
      subtitle: `资源画像 | ${preferText(resolveNodeCode(profile), "未知节点")} | ${classifyResource(profile)}`,
      tone: toneForProfile(profile),
      metrics: [
        { label: "标识", value: preferText(profile.identifier, "未暴露") },
        { label: "行业", value: preferText(profile.industry, UNKNOWN_TEXT) },
        { label: "分类", value: preferText(profile.dataCategory, profile.entityType, UNKNOWN_TEXT) },
        { label: "访问", value: preferText(profile.accessMethod, classifyResource(profile)) },
        { label: "格式", value: preferText(profile.format, profile.contentType, UNKNOWN_TEXT) },
        { label: "匹配度", value: formatRelevance(profile, relevanceView) }
      ],
      bullets: buildProfileBullets(profile, previewMap)
    });
  }

  cards.push({
    title: "跨节点发现概览",
    subtitle: `模板 ${templateTitle(plan.templateId)}`,
    tone: landscape.failedNodes.length ? "warn" : "good",
    metrics: [
      { label: "候选结果", value: String(landscape.searchItems.length) },
      { label: "资源画像", value: String(landscape.profiles.length) },
      { label: "访问节点", value: landscape.visitedNodeCodes.length ? landscape.visitedNodeCodes.join("，") : UNKNOWN_TEXT },
      { label: "路由记录", value: String(landscape.routeCount) },
      { label: "元数据解析", value: `${landscape.metadataStats.successCount}/${landscape.metadataStats.total}` },
      { label: "资源解析", value: `${landscape.componentStats.successCount}/${landscape.componentStats.total}` }
    ],
    bullets: landscape.failedNodes.length ? [`失败节点：${landscape.failedNodes.join("；")}`] : undefined
  });

  if (landscape.auditRecords.length) {
    const latestAuditRecord = landscape.auditRecords
      .slice()
      .sort((left, right) => readString(right.createdAt).localeCompare(readString(left.createdAt)))[0];
    cards.push({
      title: "审计治理概览",
      subtitle: "按标识关联的业务审计证据",
      tone: landscape.auditRecords.some((record) => record.success === false) ? "warn" : "neutral",
      metrics: [
        { label: "记录总数", value: String(landscape.auditRecords.length) },
        { label: "涉及资源", value: String(landscape.profiles.filter((profile) => profile.auditCount > 0).length) },
        { label: "失败记录", value: String(landscape.auditRecords.filter((record) => record.success === false).length) },
        { label: "最新操作", value: latestAuditRecord ? preferText(readString(latestAuditRecord.operation), UNKNOWN_TEXT) : UNKNOWN_TEXT }
      ],
      bullets: latestAuditRecord
        ? [
            `最近审计时间：${preferText(readString(latestAuditRecord.createdAt), UNKNOWN_TEXT)}`,
            `最近审计标识：${preferText(readString(latestAuditRecord.identifier), UNKNOWN_TEXT)}`
          ]
        : undefined
    });
  }

  return cards.slice(0, 16);
}

function buildMarkdownReport(report: Omit<ResearchReport, "markdown">): string {
  const lines: string[] = [];
  lines.push(`# ${report.title}`);
  lines.push("");
  lines.push(`- 生成时间: ${report.generatedAt}`);
  lines.push(`- 报告模板: ${report.templateTitle}`);
  lines.push(`- 研究问题: ${report.question || "N/A"}`);
  lines.push(`- 聚焦标识: ${report.focusedIdentifiers.length ? report.focusedIdentifiers.join(", ") : "N/A"}`);
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
    lines.push("## 证据卡片");
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

export function buildResearchReport(
  form: ResearchFormState,
  plan: ResearchPlan,
  outputs: Record<string, unknown>,
  previewMap?: ResourcePreviewMap
): ResearchReport {
  const baseReport = {
    title: form.question
      ? `DRIS DeepResearch 研究报告 - ${shortText(form.question, 28)}`
      : `DRIS DeepResearch 研究报告 - ${templateTitle(plan.templateId)}`,
    templateTitle: templateTitle(plan.templateId),
    question: form.question,
    focusedIdentifiers: plan.focusedIdentifiers,
    summarySections: buildSummarySections(form, plan, outputs, previewMap),
    evidenceCards: buildEvidenceCards(plan, outputs, previewMap),
    generatedAt: nowIso()
  };

  return {
    ...baseReport,
    markdown: buildMarkdownReport(baseReport)
  };
}

export function prependExecutiveSummary(report: ResearchReport, lines: string[]): ResearchReport {
  const summaryLines = lines.map((line) => line.trim()).filter(Boolean);
  if (!summaryLines.length) {
    return report;
  }

  const nextReport: ResearchReport = {
    ...report,
    summarySections: [
      {
        title: "LLM 执行摘要",
        lines: summaryLines
      },
      ...report.summarySections
    ]
  };

  const { markdown: _ignored, ...withoutMarkdown } = nextReport;
  return {
    ...nextReport,
    markdown: buildMarkdownReport(withoutMarkdown)
  };
}

