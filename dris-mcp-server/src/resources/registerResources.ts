import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";

import { buildDrisRequestContext, DrisApiClient } from "../adapter/drisApiClient.js";
import { prettyJson } from "../adapter/responseMappers.js";

export function registerResources(server: McpServer, client: DrisApiClient): void {
  server.registerResource(
    "dris-capabilities",
    "dris://system/capabilities",
    {
      title: "DRIS MCP Capabilities",
      description: "Overview of tools and interaction patterns exposed by this MCP server.",
      mimeType: "application/json"
    },
    async () => ({
      contents: [
        {
          uri: "dris://system/capabilities",
          mimeType: "application/json",
          text: prettyJson({
            transport: "streamable-http",
            authentication: {
              mode: "authorization-header-pass-through",
              fallback: "DRIS_AUTH_TOKEN environment variable"
            },
            tools: [
              "resolve_metadata",
              "resolve_object",
              "resolve_component",
              "batch_resolve_metadata_distributed",
              "batch_resolve_object_distributed",
              "batch_resolve_component_distributed",
              "search_global",
              "query_audit_logs",
              "get_node_routes"
            ],
            resources: [
              "dris://system/capabilities",
              "dris://node/topology"
            ],
            prompts: [
              "cross_node_batch_analysis",
              "identifier_provenance_analysis"
            ]
          })
        }
      ]
    })
  );

  server.registerResource(
    "dris-node-topology",
    "dris://node/topology",
    {
      title: "DRIS Node Topology",
      description: "Live topology snapshot from the DRIS backend.",
      mimeType: "application/json"
    },
    async (_uri, extra) => {
      const context = buildDrisRequestContext(extra);
      const [routes, descendants] = await Promise.all([
        client.getNodeRoutes(context),
        client.getNodeDirectDescendants(context)
      ]);

      return {
        contents: [
          {
            uri: "dris://node/topology",
            mimeType: "application/json",
            text: prettyJson({
              fetchedAt: new Date().toISOString(),
              routes: routes.data,
              directDescendants: descendants.data
            })
          }
        ]
      };
    }
  );
}
