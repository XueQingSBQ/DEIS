import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";

import { DrisApiClient } from "../adapter/drisApiClient.js";
import type { EnvConfig } from "../config/env.js";
import { registerPrompts } from "../prompts/registerPrompts.js";
import { registerResources } from "../resources/registerResources.js";
import { registerTools } from "../tools/registerTools.js";

export function createMcpServer(env: EnvConfig): McpServer {
  const server = new McpServer(
    {
      name: env.MCP_SERVER_NAME,
      version: env.MCP_SERVER_VERSION
    },
    {
      capabilities: {
        logging: {}
      }
    }
  );

  const client = new DrisApiClient({
    baseUrl: env.DRIS_BASE_URL,
    defaultAuthorization: env.DRIS_AUTH_TOKEN
  });

  registerTools(server, client);
  registerResources(server, client);
  registerPrompts(server);

  return server;
}
