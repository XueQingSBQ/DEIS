import type { EnvConfig } from "../config/env.js";
import { extractEnvelope } from "../executor/mcpResearchClient.js";
import type { BatchItemBase, SearchItem } from "../types/research.js";
import type { ResourcePreviewInfo, ResourcePreviewMap } from "./buildReport.js";

interface SearchResourceItem extends SearchItem {
  locator?: string | null;
}

interface ComponentBatchItem extends BatchItemBase {
  resource?: Record<string, unknown>;
}

interface ObjectBatchItem extends BatchItemBase {
  digitalObject?: Record<string, unknown>;
}

interface PreviewCandidate {
  identifier: string;
  name: string;
  locator: string;
  contentType: string;
  format: string;
  rank: number;
  score: number | null;
}

export interface ResourcePreviewCollection {
  previewMap: ResourcePreviewMap;
  attemptedCount: number;
}

const PREVIEW_ACCEPT = "text/csv,application/json,text/plain,text/html,application/xml,text/xml,*/*";
const DEFAULT_CONCURRENCY = 2;

function collectToolOutputs(outputs: Record<string, unknown>, toolName: string): unknown[] {
  const value = outputs[toolName];
  if (typeof value === "undefined") {
    return [];
  }
  return Array.isArray(value) ? value : [value];
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

function shortText(value: string, maxLength: number): string {
  const normalized = value.replace(/\s+/g, " ").trim();
  if (normalized.length <= maxLength) {
    return normalized;
  }
  return `${normalized.slice(0, Math.max(0, maxLength - 3))}...`;
}

function asEnvelope(output: unknown): Record<string, unknown> {
  return (extractEnvelope(output) as Record<string, unknown> | undefined) ?? {};
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

function readBatchItems<T extends BatchItemBase>(output: unknown): T[] {
  const data = readObject(asEnvelope(output).data);
  if (!data) {
    return [];
  }
  const items = data.items;
  return Array.isArray(items)
    ? (items.filter((item) => item && typeof item === "object") as T[])
    : [];
}

function mergeCandidate(target: PreviewCandidate, source: Partial<PreviewCandidate>): PreviewCandidate {
  return {
    identifier: target.identifier || source.identifier || "",
    name: target.name || source.name || "",
    locator: target.locator || source.locator || "",
    contentType: target.contentType || source.contentType || "",
    format: target.format || source.format || "",
    rank: Math.min(target.rank, source.rank ?? Number.MAX_SAFE_INTEGER),
    score: target.score ?? source.score ?? null
  };
}

function buildPreviewCandidates(outputs: Record<string, unknown>, limit: number): PreviewCandidate[] {
  const searchOutputs = collectToolOutputs(outputs, "search_global");
  const componentOutputs = collectToolOutputs(outputs, "batch_resolve_component_distributed");
  const objectOutputs = collectToolOutputs(outputs, "batch_resolve_object_distributed");

  const searchItems = searchOutputs.flatMap((output) => readSearchItems(output));
  const componentItems = componentOutputs.flatMap((output) => readBatchItems<ComponentBatchItem>(output));
  const objectItems = objectOutputs.flatMap((output) => readBatchItems<ObjectBatchItem>(output));

  const searchInfoByIdentifier = new Map<string, { name: string; rank: number; score: number | null }>();
  for (const [index, item] of searchItems.entries()) {
    const identifier = readString(item.identifier);
    if (!identifier) {
      continue;
    }
    if (!searchInfoByIdentifier.has(identifier)) {
      searchInfoByIdentifier.set(identifier, {
        name: readString(item.name),
        rank: index + 1,
        score: readNumber(item.finalScore)
      });
    }
  }

  const candidateByLocator = new Map<string, PreviewCandidate>();
  const upsertCandidate = (candidate: PreviewCandidate): void => {
    if (!candidate.locator || !/^https?:\/\//i.test(candidate.locator)) {
      return;
    }
    const existing = candidateByLocator.get(candidate.locator);
    candidateByLocator.set(candidate.locator, existing ? mergeCandidate(existing, candidate) : candidate);
  };

  for (const item of componentItems) {
    const resource = readObject(item.resource);
    if (!resource) {
      continue;
    }
    const identifier = readString(item.identifier);
    const searchInfo = searchInfoByIdentifier.get(identifier);
    upsertCandidate({
      identifier,
      name: searchInfo?.name || "",
      locator: readString(resource.locator),
      contentType: readString(resource.contentType),
      format: readString(resource.format),
      rank: searchInfo?.rank ?? Number.MAX_SAFE_INTEGER,
      score: searchInfo?.score ?? null
    });
  }

  for (const item of objectItems) {
    const digitalObject = readObject(item.digitalObject);
    const resource = readObject(digitalObject?.resource);
    if (!resource) {
      continue;
    }
    const identifier = readString(item.identifier);
    const searchInfo = searchInfoByIdentifier.get(identifier);
    upsertCandidate({
      identifier,
      name: searchInfo?.name || "",
      locator: readString(resource.locator),
      contentType: readString(resource.contentType),
      format: readString(resource.format),
      rank: searchInfo?.rank ?? Number.MAX_SAFE_INTEGER,
      score: searchInfo?.score ?? null
    });
  }

  return Array.from(candidateByLocator.values())
    .sort((left, right) => {
      if (left.rank !== right.rank) {
        return left.rank - right.rank;
      }
      const leftScore = left.score ?? -1;
      const rightScore = right.score ?? -1;
      if (leftScore !== rightScore) {
        return rightScore - leftScore;
      }
      return (left.name || left.identifier || left.locator).localeCompare(right.name || right.identifier || right.locator, "zh-CN");
    })
    .slice(0, limit);
}

function inferContentType(headerValue: string, locator: string, format: string): string {
  const normalizedHeader = headerValue.split(";")[0]?.trim().toLowerCase() || "";
  if (normalizedHeader) {
    return normalizedHeader;
  }
  const normalizedFormat = format.trim().toLowerCase();
  if (normalizedFormat.includes("csv")) {
    return "text/csv";
  }
  if (normalizedFormat.includes("json")) {
    return "application/json";
  }
  if (normalizedFormat.includes("html") || normalizedFormat.includes("arcgis")) {
    return "text/html";
  }
  const pathname = (() => {
    try {
      return new URL(locator).pathname.toLowerCase();
    } catch {
      return locator.toLowerCase();
    }
  })();
  if (pathname.endsWith(".csv")) {
    return "text/csv";
  }
  if (pathname.endsWith(".json")) {
    return "application/json";
  }
  if (pathname.endsWith(".xml")) {
    return "application/xml";
  }
  if (pathname.endsWith(".html") || pathname.endsWith(".htm")) {
    return "text/html";
  }
  if (pathname.endsWith(".txt") || pathname.endsWith(".md")) {
    return "text/plain";
  }
  if (pathname.endsWith(".pdf")) {
    return "application/pdf";
  }
  if (pathname.endsWith(".doc") || pathname.endsWith(".docx")) {
    return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
  }
  return "application/octet-stream";
}

function looksLikeZip(sample: Uint8Array): boolean {
  return sample.length >= 4 && sample[0] === 0x50 && sample[1] === 0x4b;
}

function looksLikeOleDocument(sample: Uint8Array): boolean {
  return sample.length >= 8
    && sample[0] === 0xd0
    && sample[1] === 0xcf
    && sample[2] === 0x11
    && sample[3] === 0xe0
    && sample[4] === 0xa1
    && sample[5] === 0xb1
    && sample[6] === 0x1a
    && sample[7] === 0xe1;
}

function isKnownBinaryLocator(locator: string): boolean {
  const lowerLocator = locator.toLowerCase();
  return /(\.pdf|\.doc|\.docx|\.xls|\.xlsx|\.ppt|\.pptx|\.zip|\.rar|\.7z|\.gz|\.tar)(\?|$)/.test(lowerLocator);
}

function isProbablyText(sample: Uint8Array): boolean {
  if (!sample.length) {
    return false;
  }
  if (looksLikeZip(sample) || looksLikeOleDocument(sample)) {
    return false;
  }
  let printable = 0;
  for (const byte of sample.slice(0, 128)) {
    if (byte === 0) {
      return false;
    }
    if ((byte >= 32 && byte <= 126) || byte === 9 || byte === 10 || byte === 13) {
      printable += 1;
    }
  }
  return printable / Math.min(sample.length, 128) >= 0.75;
}

function isTextual(contentType: string, locator: string, sample: Uint8Array): boolean {
  if (looksLikeZip(sample) || looksLikeOleDocument(sample)) {
    return false;
  }
  if (/(pdf|wordprocessingml|msword|officedocument|vnd\.ms-|zip)/.test(contentType)) {
    return false;
  }
  if (contentType.includes("application/octet-stream") && isKnownBinaryLocator(locator)) {
    return false;
  }
  if (isKnownBinaryLocator(locator)) {
    return false;
  }
  if (/^text\//.test(contentType)) {
    return true;
  }
  if (/(json|xml|csv|html)/.test(contentType)) {
    return true;
  }
  const lowerLocator = locator.toLowerCase();
  if (/(\.csv|\.json|\.xml|\.html|\.htm|\.txt|\.md)(\?|$)/.test(lowerLocator)) {
    return true;
  }
  return isProbablyText(sample);
}

function decodeText(sample: Uint8Array): string {
  return new TextDecoder("utf-8", { fatal: false }).decode(sample).replace(/^\uFEFF/, "");
}

function splitMeaningfulLines(text: string, limit: number): string[] {
  return text
    .split(/\r?\n/)
    .map((line) => shortText(line, 180))
    .filter(Boolean)
    .slice(0, limit);
}

function parseCsvPreview(text: string): { summary: string; lines: string[]; kind: string } {
  const rows = text.split(/\r?\n/).map((line) => line.trim()).filter(Boolean);
  const header = rows[0] || "";
  const sampleRows = rows.slice(1, 3);
  return {
    kind: "csv",
    summary: `CSV 头部 + 前 ${sampleRows.length} 行样本`,
    lines: [
      header ? `字段：${shortText(header, 180)}` : "",
      ...sampleRows.map((row, index) => `样本${index + 1}：${shortText(row, 180)}`)
    ].filter(Boolean)
  };
}

function parseJsonPreview(text: string): { summary: string; lines: string[]; kind: string } {
  try {
    const value = JSON.parse(text) as unknown;
    if (value && typeof value === "object" && !Array.isArray(value)) {
      const keys = Object.keys(value as Record<string, unknown>).slice(0, 8);
      return {
        kind: "json",
        summary: `JSON 对象，顶层键 ${keys.join("，") || "无"}`,
        lines: splitMeaningfulLines(JSON.stringify(value, null, 2), 4)
      };
    }
    if (Array.isArray(value)) {
      return {
        kind: "json",
        summary: `JSON 数组，样本长度 ${value.length}`,
        lines: splitMeaningfulLines(JSON.stringify(value.slice(0, 2), null, 2), 4)
      };
    }
  } catch {
    // ignore and fall back to text preview
  }
  return {
    kind: "json",
    summary: "JSON 文本片段",
    lines: splitMeaningfulLines(text, 4)
  };
}

function parseHtmlPreview(text: string): { summary: string; lines: string[]; kind: string } {
  const title = text.match(/<title[^>]*>([\s\S]*?)<\/title>/i)?.[1]?.replace(/\s+/g, " ").trim() || "";
  const description = text.match(/<meta[^>]+name=["']description["'][^>]+content=["']([\s\S]*?)["']/i)?.[1]?.replace(/\s+/g, " ").trim() || "";
  const bodyText = text.replace(/<script[\s\S]*?<\/script>/gi, " ").replace(/<style[\s\S]*?<\/style>/gi, " ").replace(/<[^>]+>/g, " ").replace(/\s+/g, " ").trim();
  const lines = [
    title ? `页面标题：${shortText(title, 180)}` : "",
    description ? `页面描述：${shortText(description, 180)}` : "",
    bodyText ? `页面摘要：${shortText(bodyText, 180)}` : ""
  ].filter(Boolean);
  return {
    kind: "html",
    summary: title ? `网页预览：${shortText(title, 80)}` : "网页内容预览",
    lines: lines.slice(0, 3)
  };
}

function parseXmlPreview(text: string): { summary: string; lines: string[]; kind: string } {
  const rootTag = text.match(/<([a-zA-Z0-9:_-]+)(\s|>)/)?.[1] || "unknown";
  return {
    kind: "xml",
    summary: `XML 结构预览，根标签 ${rootTag}`,
    lines: splitMeaningfulLines(text, 4)
  };
}

function parseTextPreview(text: string): { summary: string; lines: string[]; kind: string } {
  const lines = splitMeaningfulLines(text, 4);
  return {
    kind: "text",
    summary: `文本前 ${lines.length} 行预览`,
    lines
  };
}

function parsePreviewBody(sample: Uint8Array, contentType: string, locator: string): { summary: string; lines: string[]; kind: string } {
  if (!isTextual(contentType, locator, sample)) {
    const officeOrArchive = looksLikeZip(sample) || looksLikeOleDocument(sample) || isKnownBinaryLocator(locator);
    return {
      kind: "binary",
      summary: officeOrArchive
        ? "Office/压缩二进制资源，当前仅展示类型与定位信息"
        : `二进制资源（${contentType || "unknown"}），当前仅展示类型与定位信息`,
      lines: []
    };
  }

  const text = decodeText(sample);
  if (contentType.includes("csv")) {
    return parseCsvPreview(text);
  }
  if (contentType.includes("json")) {
    return parseJsonPreview(text);
  }
  if (contentType.includes("html")) {
    return parseHtmlPreview(text);
  }
  if (contentType.includes("xml")) {
    return parseXmlPreview(text);
  }
  if (/,[^\n]+,[^\n]+/.test(text.split(/\r?\n/)[0] || "")) {
    return parseCsvPreview(text);
  }
  return parseTextPreview(text);
}

async function readResponseSample(response: Response, maxBytes: number): Promise<Uint8Array> {
  if (!response.body) {
    return new Uint8Array((await response.arrayBuffer()).slice(0, maxBytes));
  }

  const reader = response.body.getReader();
  const chunks: Uint8Array[] = [];
  let total = 0;

  try {
    while (total < maxBytes) {
      const { done, value } = await reader.read();
      if (done || !value) {
        break;
      }
      const remaining = maxBytes - total;
      const chunk = value.length > remaining ? value.slice(0, remaining) : value;
      chunks.push(chunk);
      total += chunk.length;
      if (total >= maxBytes) {
        await reader.cancel().catch(() => undefined);
        break;
      }
    }
  } finally {
    reader.releaseLock();
  }

  const merged = new Uint8Array(total);
  let offset = 0;
  for (const chunk of chunks) {
    merged.set(chunk, offset);
    offset += chunk.length;
  }
  return merged;
}

async function fetchSinglePreview(candidate: PreviewCandidate, env: EnvConfig): Promise<ResourcePreviewInfo> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), env.RESOURCE_PREVIEW_TIMEOUT_MS);
  try {
    const response = await fetch(candidate.locator, {
      method: "GET",
      headers: {
        Accept: PREVIEW_ACCEPT,
        Range: `bytes=0-${Math.max(0, env.RESOURCE_PREVIEW_BYTES - 1)}`
      },
      signal: controller.signal
    });

    const contentType = inferContentType(response.headers.get("content-type") || candidate.contentType, candidate.locator, candidate.format);
    const contentLength = readNumber(response.headers.get("content-length")) ?? null;

    if (!response.ok) {
      const summary = response.status === 403
        ? "预览失败，目标站点拒绝抓取（403）"
        : response.status === 401
          ? "预览失败，目标站点要求认证（401）"
          : response.status === 404
            ? "预览失败，资源地址不存在（404）"
            : `预览失败，状态码 ${response.status}`;
      const detail = response.status === 403
        ? "资源预览失败：目标站点拒绝抓取（HTTP 403）"
        : response.status === 401
          ? "资源预览失败：目标站点要求认证（HTTP 401）"
          : response.status === 404
            ? "资源预览失败：目标地址不存在（HTTP 404）"
            : `资源预览失败：HTTP ${response.status}`;
      return {
        locator: candidate.locator,
        kind: "error",
        contentType,
        contentLength,
        summary,
        lines: [detail]
      };
    }

    const sample = await readResponseSample(response, env.RESOURCE_PREVIEW_BYTES);
    const parsed = parsePreviewBody(sample, contentType, candidate.locator);
    return {
      locator: candidate.locator,
      kind: parsed.kind,
      contentType,
      contentLength,
      summary: parsed.summary,
      lines: parsed.lines
    };
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    return {
      locator: candidate.locator,
      kind: "error",
      contentType: inferContentType(candidate.contentType, candidate.locator, candidate.format),
      contentLength: null,
      summary: `预览失败：${shortText(message, 80)}`,
      lines: [`资源预览失败：${shortText(message, 160)}`]
    };
  } finally {
    clearTimeout(timer);
  }
}

async function mapWithConcurrency<T, R>(items: T[], concurrency: number, worker: (item: T) => Promise<R>): Promise<R[]> {
  const results: R[] = new Array(items.length);
  let cursor = 0;

  async function runWorker(): Promise<void> {
    while (cursor < items.length) {
      const current = cursor;
      cursor += 1;
      results[current] = await worker(items[current]!);
    }
  }

  const workers = Array.from({ length: Math.min(concurrency, items.length) }, () => runWorker());
  await Promise.all(workers);
  return results;
}

export async function collectResourcePreviews(env: EnvConfig, outputs: Record<string, unknown>): Promise<ResourcePreviewCollection> {
  const candidates = buildPreviewCandidates(outputs, env.RESOURCE_PREVIEW_LIMIT);
  if (!candidates.length) {
    return {
      previewMap: new Map(),
      attemptedCount: 0
    };
  }

  const previews = await mapWithConcurrency(candidates, DEFAULT_CONCURRENCY, (candidate) => fetchSinglePreview(candidate, env));
  const previewMap: ResourcePreviewMap = new Map();
  for (const preview of previews) {
    previewMap.set(preview.locator, preview);
  }

  return {
    previewMap,
    attemptedCount: candidates.length
  };
}

