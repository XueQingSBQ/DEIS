import { randomUUID } from "node:crypto";

import express, { type Request, type Response } from "express";
import { StreamableHTTPServerTransport } from "@modelcontextprotocol/sdk/server/streamableHttp.js";

import type { EnvConfig } from "../config/env.js";
import { createMcpServer } from "./createMcpServer.js";

interface SessionState {
  server: ReturnType<typeof createMcpServer>;
  transport: StreamableHTTPServerTransport;
}

function isInitializeRequest(body: unknown): boolean {
  if (Array.isArray(body)) {
    return body.some((item) => isInitializeRequest(item));
  }

  return Boolean(
    body
      && typeof body === "object"
      && "method" in body
      && (body as { method?: unknown }).method === "initialize"
  );
}

function setCorsHeaders(res: Response, allowedOrigin: string): void {
  res.setHeader("Access-Control-Allow-Origin", allowedOrigin);
  res.setHeader("Access-Control-Allow-Headers", "content-type, authorization, mcp-session-id, mcp-protocol-version, last-event-id, x-request-id");
  res.setHeader("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
  res.setHeader("Access-Control-Expose-Headers", "mcp-session-id");
}

export function createHttpApp(env: EnvConfig) {
  const app = express();
  const sessions = new Map<string, SessionState>();

  app.use(express.json({ limit: "1mb" }));

  app.use((req, res, next) => {
    setCorsHeaders(res, env.MCP_ALLOWED_ORIGIN);
    if (req.method === "OPTIONS") {
      res.status(204).end();
      return;
    }
    next();
  });

  app.get("/health", (_req, res) => {
    res.json({
      ok: true,
      name: env.MCP_SERVER_NAME,
      version: env.MCP_SERVER_VERSION,
      route: env.MCP_ROUTE_PATH,
      drisBaseUrl: env.DRIS_BASE_URL,
      activeSessions: sessions.size
    });
  });

  app.post(env.MCP_ROUTE_PATH, async (req: Request, res: Response) => {
    const sessionIdHeader = req.headers["mcp-session-id"];
    const sessionId = Array.isArray(sessionIdHeader) ? sessionIdHeader[0] : sessionIdHeader;

    try {
      if (sessionId && sessions.has(sessionId)) {
        await sessions.get(sessionId)!.transport.handleRequest(req, res, req.body);
        return;
      }

      if (!sessionId && isInitializeRequest(req.body)) {
        const server = createMcpServer(env);
        let transport: StreamableHTTPServerTransport;

        transport = new StreamableHTTPServerTransport({
          sessionIdGenerator: () => randomUUID(),
          onsessioninitialized: (newSessionId) => {
            sessions.set(newSessionId, { server, transport });
            console.log(`[mcp] session initialized: ${newSessionId}`);
          }
        });

        transport.onclose = () => {
          const currentSessionId = transport.sessionId;
          if (!currentSessionId) {
            return;
          }

          const state = sessions.get(currentSessionId);
          if (state) {
            sessions.delete(currentSessionId);
            void state.server.close().catch((error) => {
              console.error(`[mcp] failed to close server for session ${currentSessionId}:`, error);
            });
            console.log(`[mcp] session closed: ${currentSessionId}`);
          }
        };

        transport.onerror = (error) => {
          console.error("[mcp] transport error:", error);
        };

        await server.connect(transport);
        await transport.handleRequest(req, res, req.body);
        return;
      }

      res.status(400).json({
        jsonrpc: "2.0",
        error: {
          code: -32000,
          message: "Bad Request: no valid MCP session was provided"
        },
        id: null
      });
    } catch (error) {
      console.error("[mcp] POST request handling failed:", error);
      if (!res.headersSent) {
        res.status(500).json({
          jsonrpc: "2.0",
          error: {
            code: -32603,
            message: "Internal server error"
          },
          id: null
        });
      }
    }
  });

  app.get(env.MCP_ROUTE_PATH, async (req: Request, res: Response) => {
    const sessionIdHeader = req.headers["mcp-session-id"];
    const sessionId = Array.isArray(sessionIdHeader) ? sessionIdHeader[0] : sessionIdHeader;

    if (!sessionId || !sessions.has(sessionId)) {
      res.status(400).send("Invalid or missing MCP session ID");
      return;
    }

    try {
      await sessions.get(sessionId)!.transport.handleRequest(req, res);
    } catch (error) {
      console.error("[mcp] GET request handling failed:", error);
      if (!res.headersSent) {
        res.status(500).send("Internal server error");
      }
    }
  });

  app.delete(env.MCP_ROUTE_PATH, async (req: Request, res: Response) => {
    const sessionIdHeader = req.headers["mcp-session-id"];
    const sessionId = Array.isArray(sessionIdHeader) ? sessionIdHeader[0] : sessionIdHeader;

    if (!sessionId || !sessions.has(sessionId)) {
      res.status(400).send("Invalid or missing MCP session ID");
      return;
    }

    try {
      await sessions.get(sessionId)!.transport.handleRequest(req, res);
    } catch (error) {
      console.error("[mcp] DELETE request handling failed:", error);
      if (!res.headersSent) {
        res.status(500).send("Internal server error");
      }
    }
  });

  async function shutdown(): Promise<void> {
    const entries = Array.from(sessions.entries());
    sessions.clear();

    await Promise.all(
      entries.map(async ([sessionId, state]) => {
        try {
          await state.transport.close();
          await state.server.close();
        } catch (error) {
          console.error(`[mcp] failed to close session ${sessionId}:`, error);
        }
      })
    );
  }

  return { app, shutdown };
}
