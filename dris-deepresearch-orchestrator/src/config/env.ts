import { config as loadDotEnv } from "dotenv";
import { z } from "zod";

loadDotEnv();

const booleanFromEnv = z.preprocess((value) => {
  if (typeof value === "string") {
    const normalized = value.trim().toLowerCase();
    if (["true", "1", "yes", "on"].includes(normalized)) {
      return true;
    }
    if (["false", "0", "no", "off", ""].includes(normalized)) {
      return false;
    }
  }
  return value;
}, z.boolean());

const envSchema = z.object({
  PORT: z.coerce.number().int().positive().default(38083),
  HOST: z.string().min(1).default("127.0.0.1"),
  CORS_ORIGIN: z.string().min(1).default("*"),
  MCP_SERVER_URL: z.string().url().default("http://127.0.0.1:38082/mcp"),
  PLANNER_MODE: z.enum(["heuristic", "llm", "auto"]).default("auto"),
  LLM_BASE_URL: z.string().url().optional().default("https://api.openai.com/v1"),
  LLM_API_KEY: z.string().optional().transform((value) => value?.trim() || undefined),
  LLM_MODEL: z.string().optional().transform((value) => value?.trim() || undefined),
  LLM_TIMEOUT_MS: z.coerce.number().int().min(1000).max(120000).default(30000),
  LLM_SUMMARY_ENABLED: booleanFromEnv.default(true),
  RESOURCE_PREVIEW_ENABLED: booleanFromEnv.default(true),
  RESOURCE_PREVIEW_LIMIT: z.coerce.number().int().min(0).max(10).default(3),
  RESOURCE_PREVIEW_TIMEOUT_MS: z.coerce.number().int().min(500).max(20000).default(2500),
  RESOURCE_PREVIEW_BYTES: z.coerce.number().int().min(256).max(32768).default(4096)
});

const parsed = envSchema.safeParse(process.env);
if (!parsed.success) {
  const message = parsed.error.issues
    .map((issue) => `${issue.path.join(".") || "env"}: ${issue.message}`)
    .join("; ");
  throw new Error(`Invalid environment configuration: ${message}`);
}

export const env = {
  ...parsed.data,
  MCP_SERVER_URL: parsed.data.MCP_SERVER_URL.replace(/\/$/, ""),
  LLM_BASE_URL: parsed.data.LLM_BASE_URL.replace(/\/$/, "")
};

export type EnvConfig = typeof env;
