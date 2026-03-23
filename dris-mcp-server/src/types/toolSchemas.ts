import { z } from "zod";

export const identifierSchema = z.string().min(1).describe("DRIS identifier");

export const identifiersSchema = z.array(identifierSchema).min(1).max(200).describe("Identifier list");

export const apiEnvelopeOutputSchema = {
  ok: z.boolean(),
  code: z.string().optional(),
  message: z.string().optional(),
  status: z.number().int().optional(),
  data: z.any().optional()
};

export const searchGlobalInputSchema = {
  naturalQuery: z.string().min(1).describe("Natural language search query"),
  keywords: z.array(z.string()).optional().describe("Optional keyword list"),
  fields: z.array(z.string()).optional().describe("Search fields to emphasize"),
  filters: z.record(z.string(), z.string()).optional().describe("Exact-match filters"),
  limit: z.number().int().min(1).max(200).optional().describe("Global result limit"),
  offset: z.number().int().min(0).optional().describe("Result offset"),
  mode: z.enum(["KEYWORD", "SEMANTIC", "HYBRID"]).optional().describe("Search mode"),
  semanticWeight: z.number().min(0).max(1).optional().describe("Semantic weight when using HYBRID mode"),
  strategy: z.enum(["ITERATIVE", "RECURSIVE"]).optional().describe("Cross-node routing strategy"),
  anchorNodeCode: z.string().optional().describe("Optional anchor node code"),
  ttl: z.number().int().min(1).max(16).optional().describe("Cross-node TTL"),
  maxFanout: z.number().int().min(1).max(64).optional().describe("Maximum fan-out per hop"),
  perNodeLimit: z.number().int().min(1).max(200).optional().describe("Per-node result cap"),
  globalDeadlineMs: z.number().int().min(100).optional().describe("Overall deadline in milliseconds"),
  hopTimeoutMs: z.number().int().min(100).optional().describe("Per-hop timeout in milliseconds"),
  traceId: z.string().optional().describe("Caller-defined trace ID")
};

export const auditQueryInputSchema = {
  identifier: z.string().optional().describe("Filter by identifier"),
  operation: z.string().optional().describe("Filter by operation type"),
  startTime: z.string().optional().describe("Inclusive start time, ISO-8601"),
  endTime: z.string().optional().describe("Inclusive end time, ISO-8601"),
  page: z.number().int().min(1).optional().default(1).describe("Page number, starting from 1"),
  size: z.number().int().min(1).max(100).optional().default(20).describe("Page size")
};

export const downloadPresignInputSchema = {
  identifier: identifierSchema,
  expiresInSeconds: z.number().int().min(60).max(3600).optional().default(600).describe("Presigned URL expiration in seconds")
};

