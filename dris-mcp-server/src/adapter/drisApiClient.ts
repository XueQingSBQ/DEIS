import type { RequestHandlerExtra } from "@modelcontextprotocol/sdk/shared/protocol.js";

export interface DrisApiEnvelope<T = unknown> extends Record<string, unknown> {
  ok: boolean;
  code?: string;
  message?: string;
  data?: T;
  status?: number;
}

export interface DrisRequestContext {
  authorization?: string;
  requestId?: string;
  signal?: AbortSignal;
}

export interface QueryAuditLogsParams {
  identifier?: string;
  operation?: string;
  startTime?: string;
  endTime?: string;
  page?: number;
  size?: number;
}

export interface DownloadPresignParams {
  identifier: string;
  expiresInSeconds?: number;
}

export interface SearchGlobalParams {
  naturalQuery: string;
  keywords?: string[];
  fields?: string[];
  filters?: Record<string, string>;
  limit?: number;
  offset?: number;
  mode?: string;
  semanticWeight?: number;
  strategy?: string;
  anchorNodeCode?: string;
  ttl?: number;
  maxFanout?: number;
  perNodeLimit?: number;
  globalDeadlineMs?: number;
  hopTimeoutMs?: number;
  traceId?: string;
}

export class DrisApiError extends Error {
  readonly status: number;
  readonly envelope?: DrisApiEnvelope;
  readonly rawBody?: unknown;

  constructor(message: string, status: number, envelope?: DrisApiEnvelope, rawBody?: unknown) {
    super(message);
    this.name = "DrisApiError";
    this.status = status;
    this.envelope = envelope;
    this.rawBody = rawBody;
  }
}

function getHeaderValue(headers: Record<string, string | string[] | undefined> | undefined, name: string): string | undefined {
  if (!headers) {
    return undefined;
  }

  const value = headers[name.toLowerCase()] ?? headers[name];
  if (Array.isArray(value)) {
    return value[0];
  }

  return typeof value === "string" && value.trim() ? value : undefined;
}

function buildQueryString(params: Record<string, string | number | undefined>): string {
  const query = new URLSearchParams();

  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== "") {
      query.set(key, String(value));
    }
  }

  const text = query.toString();
  return text ? `?${text}` : "";
}

type ExtraWithRequestInfo = Pick<RequestHandlerExtra<any, any>, "requestInfo" | "requestId" | "signal">;

export function buildDrisRequestContext(extra: ExtraWithRequestInfo): DrisRequestContext {
  const headers = extra.requestInfo?.headers;

  return {
    authorization: getHeaderValue(headers, "authorization"),
    requestId: getHeaderValue(headers, "x-request-id") ?? String(extra.requestId),
    signal: extra.signal
  };
}

export class DrisApiClient {
  private readonly baseUrl: string;
  private readonly defaultAuthorization?: string;

  constructor(options: { baseUrl: string; defaultAuthorization?: string }) {
    this.baseUrl = options.baseUrl.replace(/\/$/, "");
    this.defaultAuthorization = options.defaultAuthorization;
  }

  async resolveMetadata(identifier: string, context: DrisRequestContext): Promise<DrisApiEnvelope> {
    return this.request("GET", `/api/v2/identifiers/${encodeURIComponent(identifier)}/metadata`, undefined, context);
  }

  async resolveObject(identifier: string, context: DrisRequestContext): Promise<DrisApiEnvelope> {
    return this.request("GET", `/api/v2/identifiers/${encodeURIComponent(identifier)}/object`, undefined, context);
  }

  async resolveComponent(identifier: string, context: DrisRequestContext): Promise<DrisApiEnvelope> {
    return this.request("GET", `/api/v2/identifiers/${encodeURIComponent(identifier)}/component`, undefined, context);
  }

  async batchResolveMetadataDistributed(identifiers: string[], context: DrisRequestContext): Promise<DrisApiEnvelope> {
    return this.request("POST", "/api/v2/identifiers/resolve/batch/distributed", identifiers, context);
  }

  async batchResolveObjectDistributed(identifiers: string[], context: DrisRequestContext): Promise<DrisApiEnvelope> {
    return this.request("POST", "/api/v2/identifiers/resolve/batch/object/distributed", identifiers, context);
  }

  async batchResolveComponentDistributed(identifiers: string[], context: DrisRequestContext): Promise<DrisApiEnvelope> {
    return this.request("POST", "/api/v2/identifiers/resolve/batch/component/distributed", identifiers, context);
  }

  async searchGlobal(payload: SearchGlobalParams, context: DrisRequestContext): Promise<DrisApiEnvelope> {
    return this.request("POST", "/api/v2/search/global", payload, context);
  }

  async queryAuditLogs(params: QueryAuditLogsParams, context: DrisRequestContext): Promise<DrisApiEnvelope> {
    return this.request(
      "GET",
      `/api/v2/audit/identifier-history${buildQueryString({
        identifier: params.identifier,
        operation: params.operation,
        startTime: params.startTime,
        endTime: params.endTime,
        page: params.page,
        size: params.size
      })}`,
      undefined,
      context
    );
  }

  async createDownloadPresign(payload: DownloadPresignParams, context: DrisRequestContext): Promise<DrisApiEnvelope> {
    return this.request("POST", "/api/v2/downloads/presign", payload, context);
  }

  async getNodeRoutes(context: DrisRequestContext): Promise<DrisApiEnvelope> {
    return this.request("GET", "/api/v2/node/routes", undefined, context);
  }

  async getNodeDirectDescendants(context: DrisRequestContext): Promise<DrisApiEnvelope> {
    return this.request("GET", "/api/v2/node/direct-descendants", undefined, context);
  }

  private async request(
    method: "GET" | "POST",
    path: string,
    body: unknown,
    context: DrisRequestContext
  ): Promise<DrisApiEnvelope> {
    const headers = new Headers();
    headers.set("accept", "application/json");

    if (body !== undefined) {
      headers.set("content-type", "application/json");
    }

    const authorization = context.authorization ?? this.defaultAuthorization;
    if (authorization) {
      headers.set("authorization", authorization);
    }

    if (context.requestId) {
      headers.set("x-request-id", context.requestId);
    }

    let response: Response;
    try {
      response = await fetch(`${this.baseUrl}${path}`, {
        method,
        headers,
        body: body === undefined ? undefined : JSON.stringify(body),
        signal: context.signal
      });
    } catch (error) {
      throw new DrisApiError(
        error instanceof Error ? error.message : "Failed to call DRIS backend",
        502,
        undefined,
        undefined
      );
    }

    const payload = await parseBody(response);
    const envelope = isApiEnvelope(payload)
      ? {
          ok: payload.ok,
          code: typeof payload.code === "string" ? payload.code : undefined,
          message: typeof payload.message === "string" ? payload.message : undefined,
          data: payload.data,
          status: response.status
        }
      : {
          ok: response.ok,
          data: payload,
          status: response.status
        };

    if (!response.ok || !envelope.ok) {
      throw new DrisApiError(
        envelope.message ?? `DRIS backend request failed with status ${response.status}`,
        response.status,
        envelope,
        payload
      );
    }

    return envelope;
  }
}

async function parseBody(response: Response): Promise<unknown> {
  const contentType = response.headers.get("content-type") ?? "";

  if (contentType.includes("application/json")) {
    return response.json();
  }

  const text = await response.text();
  return text ? text : undefined;
}

function isApiEnvelope(value: unknown): value is DrisApiEnvelope {
  return Boolean(
    value
      && typeof value === "object"
      && "ok" in value
      && typeof (value as { ok?: unknown }).ok === "boolean"
  );
}
