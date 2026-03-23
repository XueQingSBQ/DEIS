import express from "express";
import { z } from "zod";

import type { EnvConfig } from "../config/env.js";
import { isLlmConfigured, listCompatibleModels } from "../llm/openaiCompatibleClient.js";
import type { ResearchStreamEvent } from "../types/research.js";
import { runResearch, streamResearch } from "./researchService.js";

const requestSchema = z.object({
  question: z.string().optional(),
  identifiersText: z.string().optional(),
  anchorNodeCode: z.string().optional(),
  searchMode: z.enum(["KEYWORD", "SEMANTIC", "HYBRID"]).optional(),
  strategy: z.enum(["ITERATIVE", "RECURSIVE"]).optional(),
  limit: z.number().int().min(1).max(20).optional(),
  ttl: z.number().int().min(1).max(16).optional(),
  maxFanout: z.number().int().min(1).max(64).optional(),
  perNodeLimit: z.number().int().min(1).max(100).optional(),
  globalDeadlineMs: z.number().int().min(500).max(120000).optional(),
  hopTimeoutMs: z.number().int().min(200).max(30000).optional(),
  auditOperation: z.string().optional(),
  auditStartTime: z.string().optional(),
  auditEndTime: z.string().optional(),
  auditPageSize: z.number().int().min(1).max(20).optional(),
  mcpUrl: z.string().url().optional(),
  llmModel: z.string().optional()
});

function formatValidationError(error: z.ZodError): string {
  return error.issues.map((issue) => `${issue.path.join(".") || "body"}: ${issue.message}`).join("; ");
}

function writeSseEvent(res: express.Response, event: ResearchStreamEvent): void {
  res.write(`event: ${event.type}\n`);
  res.write(`data: ${JSON.stringify(event)}\n\n`);
}

export function createHttpApp(env: EnvConfig) {
  const app = express();
  app.use(express.json({ limit: "1mb" }));

  app.use((req, res, next) => {
    res.setHeader("Access-Control-Allow-Origin", env.CORS_ORIGIN);
    res.setHeader("Access-Control-Allow-Headers", "content-type, authorization, x-request-id");
    res.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
    if (req.method === "OPTIONS") {
      res.status(204).end();
      return;
    }
    next();
  });

  app.get("/health", (_req, res) => {
    res.json({
      ok: true,
      plannerMode: env.PLANNER_MODE,
      llmConfigured: isLlmConfigured(env),
      llmSummaryEnabled: env.LLM_SUMMARY_ENABLED,
      mcpServerUrl: env.MCP_SERVER_URL,
      llmModel: env.LLM_MODEL ?? null
    });
  });

  app.get("/api/llm/models", async (_req, res) => {
    if (!env.LLM_API_KEY) {
      res.json({
        ok: true,
        configured: false,
        defaultModel: env.LLM_MODEL ?? null,
        models: []
      });
      return;
    }

    try {
      const models = await listCompatibleModels(env);
      res.json({
        ok: true,
        configured: isLlmConfigured(env),
        defaultModel: env.LLM_MODEL ?? null,
        models
      });
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      res.status(502).json({
        ok: false,
        code: "LLM_MODELS_FAILED",
        message
      });
    }
  });

  app.post("/api/research/run", async (req, res) => {
    const parsed = requestSchema.safeParse(req.body ?? {});
    if (!parsed.success) {
      res.status(400).json({
        ok: false,
        code: "INVALID_REQUEST",
        message: formatValidationError(parsed.error)
      });
      return;
    }

    try {
      const response = await runResearch(env, parsed.data);
      res.json(response);
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      res.status(500).json({
        ok: false,
        code: "RESEARCH_RUN_FAILED",
        message
      });
    }
  });

  app.post("/api/research/stream", async (req, res) => {
    const parsed = requestSchema.safeParse(req.body ?? {});
    if (!parsed.success) {
      res.status(400).json({
        ok: false,
        code: "INVALID_REQUEST",
        message: formatValidationError(parsed.error)
      });
      return;
    }

    res.status(200);
    res.setHeader("Content-Type", "text/event-stream; charset=utf-8");
    res.setHeader("Cache-Control", "no-cache, no-transform");
    res.setHeader("Connection", "keep-alive");
    res.setHeader("X-Accel-Buffering", "no");
    res.flushHeaders?.();
    res.write(": stream-open\n\n");

    let clientGone = false;
    req.on("aborted", () => {
      clientGone = true;
    });
    res.on("close", () => {
      clientGone = true;
    });

    try {
      await streamResearch(env, parsed.data, {
        onEvent: async (event) => {
          if (clientGone || res.writableEnded || res.destroyed) {
            return;
          }
          writeSseEvent(res, event);
        }
      });
    } catch (error) {
      if (!clientGone && !res.writableEnded && !res.destroyed) {
        writeSseEvent(res, {
          type: "error",
          message: error instanceof Error ? error.message : String(error)
        });
      }
    } finally {
      if (!res.writableEnded && !res.destroyed) {
        res.end();
      }
    }
  });

  return app;
}
