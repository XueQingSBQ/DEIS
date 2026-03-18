import type { EnvConfig } from "../config/env.js";

interface ChatMessage {
  role: "system" | "user" | "assistant";
  content: string;
}

interface ChatCompletionResponse {
  choices?: Array<{
    message?: {
      content?: string | Array<{ type?: string; text?: string }>;
    };
  }>;
}

interface ChatCompletionStreamChunk {
  choices?: Array<{
    delta?: {
      content?: string | Array<{ type?: string; text?: string }>;
    };
    finish_reason?: string | null;
  }>;
}

interface ModelsResponse {
  data?: Array<{
    id?: string;
    supported_endpoint_types?: string[];
  }>;
}

export function resolveModel(env: EnvConfig, overrideModel?: string): string {
  const model = (overrideModel ?? "").trim() || env.LLM_MODEL || "";
  return model;
}

export function isLlmConfigured(env: EnvConfig, overrideModel?: string): boolean {
  return Boolean(env.LLM_API_KEY && resolveModel(env, overrideModel));
}

function normalizeContent(content: string | Array<{ type?: string; text?: string }> | undefined): string {
  if (typeof content === "string") {
    return content;
  }
  if (Array.isArray(content)) {
    return content
      .map((item) => (typeof item?.text === "string" ? item.text : ""))
      .join("\n")
      .trim();
  }
  return "";
}

async function buildLlmErrorMessage(response: Response): Promise<string> {
  const payload = (await response.json().catch(() => null)) as { error?: { message?: string } } | null;
  if (payload?.error?.message) {
    return payload.error.message;
  }
  return `LLM request failed with status ${response.status}`;
}

function createAbortController(env: EnvConfig): { controller: AbortController; timer: NodeJS.Timeout } {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), env.LLM_TIMEOUT_MS);
  return { controller, timer };
}

export async function createChatCompletion(env: EnvConfig, messages: ChatMessage[], overrideModel?: string): Promise<string> {
  if (!env.LLM_API_KEY) {
    throw new Error("LLM API key is not configured");
  }

  const model = resolveModel(env, overrideModel);
  if (!model) {
    throw new Error("No LLM model is configured");
  }

  const { controller, timer } = createAbortController(env);
  let accumulated = "";
  try {
    const response = await fetch(`${env.LLM_BASE_URL}/chat/completions`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${env.LLM_API_KEY}`
      },
      body: JSON.stringify({
        model,
        temperature: 0.2,
        messages
      }),
      signal: controller.signal
    });

    if (!response.ok) {
      throw new Error(await buildLlmErrorMessage(response));
    }

    const payload = (await response.json().catch(() => null)) as ChatCompletionResponse | null;
    const content = normalizeContent(payload?.choices?.[0]?.message?.content);
    if (!content) {
      throw new Error("LLM returned an empty completion");
    }
    return content;
  } finally {
    clearTimeout(timer);
  }
}

function parseSseBlocks(buffer: string): { blocks: string[]; rest: string } {
  const normalized = buffer.replace(/\r\n/g, "\n");
  const blocks: string[] = [];
  let rest = normalized;
  let boundary = rest.indexOf("\n\n");
  while (boundary >= 0) {
    blocks.push(rest.slice(0, boundary));
    rest = rest.slice(boundary + 2);
    boundary = rest.indexOf("\n\n");
  }
  return { blocks, rest };
}

function extractSseDataLines(block: string): string[] {
  return block
    .split("\n")
    .filter((line) => line.startsWith("data:"))
    .map((line) => line.slice(5).trimStart())
    .filter(Boolean);
}

export async function createChatCompletionStream(
  env: EnvConfig,
  messages: ChatMessage[],
  onDelta: (delta: string, accumulated: string) => void | Promise<void>,
  overrideModel?: string
): Promise<string> {
  if (!env.LLM_API_KEY) {
    throw new Error("LLM API key is not configured");
  }

  const model = resolveModel(env, overrideModel);
  if (!model) {
    throw new Error("No LLM model is configured");
  }

  const { controller, timer } = createAbortController(env);
  let accumulated = "";
  try {
    const response = await fetch(`${env.LLM_BASE_URL}/chat/completions`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Accept: "text/event-stream",
        Authorization: `Bearer ${env.LLM_API_KEY}`
      },
      body: JSON.stringify({
        model,
        temperature: 0.2,
        stream: true,
        messages
      }),
      signal: controller.signal
    });

    if (!response.ok) {
      throw new Error(await buildLlmErrorMessage(response));
    }

    if (!response.body) {
      throw new Error("LLM streaming response body is unavailable");
    }

    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = "";

    while (true) {
      const { done, value } = await reader.read();
      buffer += decoder.decode(value ?? new Uint8Array(), { stream: !done });
      const parsed = parseSseBlocks(buffer);
      buffer = parsed.rest;

      for (const block of parsed.blocks) {
        for (const dataLine of extractSseDataLines(block)) {
          if (dataLine === "[DONE]") {
            continue;
          }
          const chunk = JSON.parse(dataLine) as ChatCompletionStreamChunk;
          const delta = normalizeContent(chunk.choices?.[0]?.delta?.content);
          if (!delta) {
            continue;
          }
          accumulated += delta;
          await onDelta(delta, accumulated);
        }
      }

      if (done) {
        break;
      }
    }

    const trailing = extractSseDataLines(buffer);
    for (const dataLine of trailing) {
      if (dataLine === "[DONE]") {
        continue;
      }
      const chunk = JSON.parse(dataLine) as ChatCompletionStreamChunk;
      const delta = normalizeContent(chunk.choices?.[0]?.delta?.content);
      if (!delta) {
        continue;
      }
      accumulated += delta;
      await onDelta(delta, accumulated);
    }

    if (!accumulated.trim()) {
      throw new Error("LLM returned an empty streamed completion");
    }

    return accumulated;
  } catch (error) {
    if (accumulated.trim()) {
      return accumulated;
    }
    if (!controller.signal.aborted) {
      try {
        const fallback = await createChatCompletion(env, messages, overrideModel);
        await onDelta(fallback, fallback);
        return fallback;
      } catch {
        throw error;
      }
    }
    throw error;
  } finally {
    clearTimeout(timer);
  }
}

export async function listCompatibleModels(env: EnvConfig): Promise<string[]> {
  if (!env.LLM_API_KEY) {
    throw new Error("LLM API key is not configured");
  }

  const { controller, timer } = createAbortController(env);
  try {
    const response = await fetch(`${env.LLM_BASE_URL}/models`, {
      method: "GET",
      headers: {
        Authorization: `Bearer ${env.LLM_API_KEY}`
      },
      signal: controller.signal
    });

    const payload = (await response.json().catch(() => null)) as ModelsResponse | { error?: { message?: string } } | null;
    if (!response.ok) {
      const message = payload && typeof payload === "object" && "error" in payload
        ? payload.error?.message || `Model list request failed with status ${response.status}`
        : `Model list request failed with status ${response.status}`;
      throw new Error(message);
    }

    const responsePayload = payload as ModelsResponse | null;
    const models = (responsePayload?.data ?? [])
      .map((item) => String(item.id ?? "").trim())
      .filter(Boolean);
    return Array.from(new Set(models));
  } finally {
    clearTimeout(timer);
  }
}

export function extractJsonBlock(text: string): string {
  const fencedMatch = text.match(/```json\s*([\s\S]*?)```/i);
  if (fencedMatch?.[1]) {
    return fencedMatch[1].trim();
  }

  const firstBrace = text.indexOf("{");
  const lastBrace = text.lastIndexOf("}");
  if (firstBrace >= 0 && lastBrace > firstBrace) {
    return text.slice(firstBrace, lastBrace + 1).trim();
  }

  throw new Error("Could not find a JSON object in the LLM response");
}




