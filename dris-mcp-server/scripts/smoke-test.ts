import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { StreamableHTTPClientTransport } from "@modelcontextprotocol/sdk/client/streamableHttp.js";

interface ApiEnvelope {
  ok?: boolean;
  data?: unknown;
}

interface SearchItem {
  identifier?: string;
}

const mcpUrl = new URL(process.env.MCP_SERVER_URL ?? "http://127.0.0.1:38081/mcp");
const explicitIdentifier = (process.env.SMOKE_IDENTIFIER ?? "").trim();
const searchQuery = (process.env.SMOKE_QUERY ?? "owner-demo").trim();

function summarize(result: unknown): string {
  return JSON.stringify(result, null, 2);
}

function readEnvelope(result: unknown): ApiEnvelope | undefined {
  if (!result || typeof result !== "object") {
    return undefined;
  }
  const structuredContent = (result as { structuredContent?: unknown }).structuredContent;
  if (!structuredContent || typeof structuredContent !== "object") {
    return undefined;
  }
  return structuredContent as ApiEnvelope;
}

function inferIdentifierFromSearch(result: unknown): string {
  const envelope = readEnvelope(result);
  const data = envelope?.data;
  if (!data || typeof data !== "object") {
    return "";
  }

  const items = (data as { items?: unknown }).items;
  if (!Array.isArray(items) || items.length === 0) {
    return "";
  }

  for (const item of items) {
    if (item && typeof item === "object") {
      const identifier = ((item as SearchItem).identifier ?? "").trim();
      if (identifier) {
        return identifier;
      }
    }
  }

  return "";
}

async function main(): Promise<void> {
  const client = new Client(
    {
      name: "dris-mcp-smoke-client",
      version: "0.1.0"
    },
    {
      capabilities: {}
    }
  );

  const transport = new StreamableHTTPClientTransport(mcpUrl, {
    requestInit: {
      headers: {
        "x-request-id": `smoke-${Date.now()}`
      }
    }
  });

  await client.connect(transport);

  try {
    const tools = await client.listTools();
    console.log("[smoke] tools:");
    console.log(summarize(tools.tools.map((tool) => tool.name)));

    const nodeRoutes = await client.callTool({
      name: "get_node_routes",
      arguments: {}
    });
    console.log("[smoke] get_node_routes:");
    console.log(summarize(nodeRoutes));

    const globalSearch = await client.callTool({
      name: "search_global",
      arguments: {
        naturalQuery: searchQuery,
        strategy: "RECURSIVE",
        mode: "KEYWORD",
        limit: 3,
        offset: 0
      }
    });
    console.log(`[smoke] search_global (${searchQuery}):`);
    console.log(summarize(globalSearch));

    const resolvedIdentifier = explicitIdentifier || inferIdentifierFromSearch(globalSearch);
    if (resolvedIdentifier && !explicitIdentifier) {
      console.log(`[smoke] auto-selected identifier from search result: ${resolvedIdentifier}`);
    }

    const auditLogs = await client.callTool({
      name: "query_audit_logs",
      arguments: {
        identifier: resolvedIdentifier || undefined,
        page: 1,
        size: 3
      }
    });
    console.log("[smoke] query_audit_logs:");
    console.log(summarize(auditLogs));

    if (resolvedIdentifier) {
      const metadata = await client.callTool({
        name: "resolve_metadata",
        arguments: {
          identifier: resolvedIdentifier
        }
      });
      console.log("[smoke] resolve_metadata:");
      console.log(summarize(metadata));

      const digitalObject = await client.callTool({
        name: "resolve_object",
        arguments: {
          identifier: resolvedIdentifier
        }
      });
      console.log("[smoke] resolve_object:");
      console.log(summarize(digitalObject));
    } else {
      console.log("[smoke] resolve checks skipped: no explicit identifier and search returned no usable identifier");
    }
  } finally {
    try {
      await transport.terminateSession();
    } catch {
      // ignore termination failures for smoke runs
    }
    await transport.close();
  }
}

main().catch((error) => {
  console.error("[smoke] failed:", error);
  process.exit(1);
});
