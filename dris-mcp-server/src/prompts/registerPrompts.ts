import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";

export function registerPrompts(server: McpServer): void {
  server.registerPrompt(
    "cross_node_batch_analysis",
    {
      title: "Cross-Node Batch Analysis",
      description: "Guide the model to analyze identifiers with distributed batch resolution and route topology.",
      argsSchema: {
        identifiers: z.string().min(1).describe("Comma-separated identifiers"),
        resolveType: z.enum(["metadata", "object", "component"]).default("metadata")
      }
    },
    async ({ identifiers, resolveType }) => ({
      messages: [
        {
          role: "user",
          content: {
            type: "text",
            text: [
              "You are analyzing DRIS cross-node batch resolution.",
              `Identifiers: ${identifiers}`,
              `Preferred resolve type: ${resolveType}`,
              "First read the node topology using get_node_routes.",
              "Then call the matching distributed batch tool.",
              "Summarize success ratio, partial failures, likely forwarding path characteristics, and follow-up recommendations."
            ].join("\n")
          }
        }
      ]
    })
  );

  server.registerPrompt(
    "identifier_provenance_analysis",
    {
      title: "Identifier Provenance Analysis",
      description: "Guide the model to analyze identifier state together with audit history.",
      argsSchema: {
        identifier: z.string().min(1).describe("Target identifier"),
        startTime: z.string().optional().describe("Optional ISO-8601 start time"),
        endTime: z.string().optional().describe("Optional ISO-8601 end time")
      }
    },
    async ({ identifier, startTime, endTime }) => ({
      messages: [
        {
          role: "user",
          content: {
            type: "text",
            text: [
              "Analyze the lifecycle and provenance of a DRIS identifier.",
              `Identifier: ${identifier}`,
              startTime ? `Start time: ${startTime}` : undefined,
              endTime ? `End time: ${endTime}` : undefined,
              "Resolve metadata first, then query audit logs for the same identifier.",
              "Explain current state, recent operations, suspicious changes, and whether the resource or object view matches the audit trail."
            ]
              .filter(Boolean)
              .join("\n")
          }
        }
      ]
    })
  );
}
