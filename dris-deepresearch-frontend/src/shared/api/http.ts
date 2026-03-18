import { getAccessToken } from "../auth/session";

export interface ApiEnvelope<T> {
  ok: boolean;
  code: string;
  message?: string;
  data: T;
}

function buildHeaders(init?: RequestInit): Headers {
  const headers = new Headers(init?.headers ?? {});
  if (!headers.has("Accept")) {
    headers.set("Accept", "application/json");
  }
  const accessToken = getAccessToken();
  if (accessToken && !headers.has("Authorization")) {
    headers.set("Authorization", `Bearer ${accessToken}`);
  }
  if (init?.body && !headers.has("Content-Type") && !(init.body instanceof FormData)) {
    headers.set("Content-Type", "application/json");
  }
  return headers;
}

export async function requestEnvelope<T>(url: string, init?: RequestInit): Promise<ApiEnvelope<T>> {
  const response = await fetch(url, {
    ...init,
    headers: buildHeaders(init)
  });
  const text = await response.text();
  const payload = text ? (JSON.parse(text) as ApiEnvelope<T>) : ({ ok: response.ok, code: response.ok ? "OK" : "EMPTY", data: null as T } as ApiEnvelope<T>);
  if (!response.ok || !payload.ok) {
    throw new Error(payload.message || payload.code || `HTTP ${response.status}`);
  }
  return payload;
}

export async function requestOptionalEnvelope<T>(url: string, init?: RequestInit): Promise<ApiEnvelope<T> | null> {
  try {
    return await requestEnvelope<T>(url, init);
  } catch {
    return null;
  }
}

export async function requestJson<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, {
    ...init,
    headers: buildHeaders(init)
  });
  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`);
  }
  return (await response.json()) as T;
}

export function asJsonBody(value: unknown): string {
  return JSON.stringify(value);
}

export function buildQuery(params: Record<string, string | number | boolean | null | undefined>): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value === null || typeof value === "undefined" || value === "") {
      continue;
    }
    search.set(key, String(value));
  }
  const output = search.toString();
  return output ? `?${output}` : "";
}
