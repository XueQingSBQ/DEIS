import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { StreamableHTTPClientTransport } from "@modelcontextprotocol/sdk/client/streamableHttp.js";

import type { ToolEnvelope } from "../types/research.js";

export function extractEnvelope(result: unknown): ToolEnvelope | undefined {
  if (!result || typeof result !== "object") {
    return undefined;
  }

  const structuredContent = (result as { structuredContent?: unknown }).structuredContent;
  if (!structuredContent || typeof structuredContent !== "object") {
    return undefined;
  }

  return structuredContent as ToolEnvelope;
}

export function summarizeToolResult(toolName: string, result: unknown): string {
  const envelope = extractEnvelope(result);
  if (!envelope) {
    return `${toolName} completed`;
  }

  const data = envelope.data;
  if (toolName === "search_global" && data && typeof data === "object") {
    const items = (data as { items?: unknown[] }).items;
    const count = Array.isArray(items) ? items.length : 0;
    const partial = Boolean((data as { partial?: unknown }).partial);
    return `Search returned ${count} items${partial ? " (partial)" : ""}`;
  }

  if (toolName.startsWith("batch_resolve_") && data && typeof data === "object") {
    const total = Number((data as { total?: unknown }).total ?? 0);
    const successCount = Number((data as { successCount?: unknown }).successCount ?? 0);
    const failureCount = Number((data as { failureCount?: unknown }).failureCount ?? 0);
    return `Resolved ${successCount}/${total}, failures ${failureCount}`;
  }

  if (toolName === "query_audit_logs" && data && typeof data === "object") {
    const total = Number((data as { total?: unknown }).total ?? 0);
    const hasNext = Boolean((data as { hasNext?: unknown }).hasNext);
    return `Loaded ${total} audit records${hasNext ? " (more available)" : ""}`;
  }

  if (toolName === "get_node_routes" && Array.isArray(data)) {
    return `Loaded ${data.length} route records`;
  }

  return envelope.message || envelope.code || `${toolName} completed`;
}

export class McpResearchClient {
  private client: Client | null = null;
  private transport: StreamableHTTPClientTransport | null = null;
  private currentUrl = "";

  async connect(mcpUrl: string): Promise<void> {
    const url = new URL(mcpUrl.trim()).toString();
    if (this.client && this.transport && this.currentUrl === url) {
      return;
    }

    await this.disconnect();

    const client = new Client(
      {
        name: "dris-deepresearch-orchestrator",
        version: "0.1.0"
      },
      {
        capabilities: {}
      }
    );

    const transport = new StreamableHTTPClientTransport(new URL(url), {
      requestInit: {
        headers: {
          "x-request-id": `orchestrator-${Date.now()}`
        }
      }
    });

    await client.connect(transport);
    this.client = client;
    this.transport = transport;
    this.currentUrl = url;
  }

  async disconnect(): Promise<void> {
    if (this.transport) {
      try {
        await this.transport.terminateSession();
      } catch {
        // ignore termination failures during shutdown
      }

      try {
        await this.transport.close();
      } catch {
        // ignore close failures during shutdown
      }
    }

    this.client = null;
    this.transport = null;
    this.currentUrl = "";
  }

  async listTools(): Promise<string[]> {
    if (!this.client) {
      throw new Error("MCP client is not connected");
    }

    const response = await this.client.listTools();
    return response.tools.map((tool) => tool.name);
  }

  async callTool(toolName: string, args: Record<string, unknown>): Promise<unknown> {
    if (!this.client) {
      throw new Error("MCP client is not connected");
    }

    return this.client.callTool({
      name: toolName,
      arguments: args
    });
  }
}
