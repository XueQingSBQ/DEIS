import { config as loadDotEnv } from "dotenv";
import { z } from "zod";

loadDotEnv();

const envSchema = z.object({
  MCP_PORT: z.coerce.number().int().positive().default(38081),
  MCP_ROUTE_PATH: z.string().min(1).default("/mcp"),
  MCP_SERVER_NAME: z.string().min(1).default("dris-mcp-server"),
  MCP_SERVER_VERSION: z.string().min(1).default("0.1.0"),
  MCP_ALLOWED_ORIGIN: z.string().optional().default("*"),
  DRIS_BASE_URL: z.string().url().default("http://127.0.0.1:8080"),
  DRIS_AUTH_TOKEN: z.string().optional().transform((value) => value?.trim() || undefined)
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
  DRIS_BASE_URL: parsed.data.DRIS_BASE_URL.replace(/\/$/, "")
};

export type EnvConfig = typeof env;
