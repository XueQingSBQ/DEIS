import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";

import { buildDrisRequestContext, DrisApiClient } from "../adapter/drisApiClient.js";
import { buildErrorResult, buildSuccessResult } from "../adapter/responseMappers.js";
import {
  apiEnvelopeOutputSchema,
  auditQueryInputSchema,
  downloadPresignInputSchema,
  identifierSchema,
  identifiersSchema,
  searchGlobalInputSchema
} from "../types/toolSchemas.js";

export function registerTools(server: McpServer, client: DrisApiClient): void {
  server.registerTool(
    "resolve_metadata",
    {
      title: "Resolve Identifier Metadata",
      description: "Resolve identifier metadata from the DRIS backend.",
      inputSchema: { identifier: identifierSchema },
      outputSchema: apiEnvelopeOutputSchema,
      annotations: {
        title: "Resolve Identifier Metadata",
        readOnlyHint: true,
        destructiveHint: false,
        idempotentHint: true,
        openWorldHint: false
      }
    },
    async ({ identifier }, extra) => {
      try {
        const envelope = await client.resolveMetadata(identifier, buildDrisRequestContext(extra));
        return buildSuccessResult(`Resolved metadata for ${identifier}`, envelope);
      } catch (error) {
        return buildErrorResult(`Resolving metadata for ${identifier}`, error);
      }
    }
  );

  server.registerTool(
    "resolve_object",
    {
      title: "Resolve Digital Object",
      description: "Resolve a full digital object by identifier.",
      inputSchema: { identifier: identifierSchema },
      outputSchema: apiEnvelopeOutputSchema,
      annotations: {
        title: "Resolve Digital Object",
        readOnlyHint: true,
        destructiveHint: false,
        idempotentHint: true,
        openWorldHint: false
      }
    },
    async ({ identifier }, extra) => {
      try {
        const envelope = await client.resolveObject(identifier, buildDrisRequestContext(extra));
        return buildSuccessResult(`Resolved digital object for ${identifier}`, envelope);
      } catch (error) {
        return buildErrorResult(`Resolving digital object for ${identifier}`, error);
      }
    }
  );

  server.registerTool(
    "resolve_component",
    {
      title: "Resolve Resource Component",
      description: "Resolve the resource component bound to an identifier.",
      inputSchema: { identifier: identifierSchema },
      outputSchema: apiEnvelopeOutputSchema,
      annotations: {
        title: "Resolve Resource Component",
        readOnlyHint: true,
        destructiveHint: false,
        idempotentHint: true,
        openWorldHint: false
      }
    },
    async ({ identifier }, extra) => {
      try {
        const envelope = await client.resolveComponent(identifier, buildDrisRequestContext(extra));
        return buildSuccessResult(`Resolved component for ${identifier}`, envelope);
      } catch (error) {
        return buildErrorResult(`Resolving component for ${identifier}`, error);
      }
    }
  );

  server.registerTool(
    "batch_resolve_metadata_distributed",
    {
      title: "Batch Resolve Metadata Distributed",
      description: "Cross-node batch metadata resolution using the backend distributed resolver.",
      inputSchema: { identifiers: identifiersSchema },
      outputSchema: apiEnvelopeOutputSchema,
      annotations: {
        title: "Batch Resolve Metadata Distributed",
        readOnlyHint: true,
        destructiveHint: false,
        idempotentHint: true,
        openWorldHint: false
      }
    },
    async ({ identifiers }, extra) => {
      try {
        const envelope = await client.batchResolveMetadataDistributed(identifiers, buildDrisRequestContext(extra));
        return buildSuccessResult(`Distributed metadata resolution completed for ${identifiers.length} identifiers`, envelope);
      } catch (error) {
        return buildErrorResult("Distributed metadata batch resolution", error);
      }
    }
  );

  server.registerTool(
    "batch_resolve_object_distributed",
    {
      title: "Batch Resolve Objects Distributed",
      description: "Cross-node batch digital object resolution.",
      inputSchema: { identifiers: identifiersSchema },
      outputSchema: apiEnvelopeOutputSchema,
      annotations: {
        title: "Batch Resolve Objects Distributed",
        readOnlyHint: true,
        destructiveHint: false,
        idempotentHint: true,
        openWorldHint: false
      }
    },
    async ({ identifiers }, extra) => {
      try {
        const envelope = await client.batchResolveObjectDistributed(identifiers, buildDrisRequestContext(extra));
        return buildSuccessResult(`Distributed object resolution completed for ${identifiers.length} identifiers`, envelope);
      } catch (error) {
        return buildErrorResult("Distributed object batch resolution", error);
      }
    }
  );

  server.registerTool(
    "batch_resolve_component_distributed",
    {
      title: "Batch Resolve Components Distributed",
      description: "Cross-node batch component or resource resolution.",
      inputSchema: { identifiers: identifiersSchema },
      outputSchema: apiEnvelopeOutputSchema,
      annotations: {
        title: "Batch Resolve Components Distributed",
        readOnlyHint: true,
        destructiveHint: false,
        idempotentHint: true,
        openWorldHint: false
      }
    },
    async ({ identifiers }, extra) => {
      try {
        const envelope = await client.batchResolveComponentDistributed(identifiers, buildDrisRequestContext(extra));
        return buildSuccessResult(`Distributed component resolution completed for ${identifiers.length} identifiers`, envelope);
      } catch (error) {
        return buildErrorResult("Distributed component batch resolution", error);
      }
    }
  );

  server.registerTool(
    "search_global",
    {
      title: "Global Search",
      description: "Distributed search across DRIS nodes.",
      inputSchema: searchGlobalInputSchema,
      outputSchema: apiEnvelopeOutputSchema,
      annotations: {
        title: "Global Search",
        readOnlyHint: true,
        destructiveHint: false,
        idempotentHint: true,
        openWorldHint: false
      }
    },
    async (args, extra) => {
      try {
        const envelope = await client.searchGlobal(args, buildDrisRequestContext(extra));
        return buildSuccessResult(`Global search completed for query: ${args.naturalQuery}`, envelope);
      } catch (error) {
        return buildErrorResult("Global search", error);
      }
    }
  );

  server.registerTool(
    "create_download_presign",
    {
      title: "Create Download Presign",
      description: "Create a distributed presigned download URL for the resource bound to an identifier.",
      inputSchema: downloadPresignInputSchema,
      outputSchema: apiEnvelopeOutputSchema,
      annotations: {
        title: "Create Download Presign",
        readOnlyHint: true,
        destructiveHint: false,
        idempotentHint: true,
        openWorldHint: false
      }
    },
    async (args, extra) => {
      try {
        const envelope = await client.createDownloadPresign(args, buildDrisRequestContext(extra));
        const data = envelope.data && typeof envelope.data === "object"
          ? { ...(envelope.data as Record<string, unknown>), identifier: args.identifier }
          : { identifier: args.identifier };
        return buildSuccessResult(`Created download URL for ${args.identifier}`, { ...envelope, data });
      } catch (error) {
        return buildErrorResult(`Creating download URL for ${args.identifier}`, error);
      }
    }
  );

  server.registerTool(
    "query_audit_logs",
    {
      title: "Query Audit Logs",
      description: "Query identifier audit history with paging and time filters.",
      inputSchema: auditQueryInputSchema,
      outputSchema: apiEnvelopeOutputSchema,
      annotations: {
        title: "Query Audit Logs",
        readOnlyHint: true,
        destructiveHint: false,
        idempotentHint: true,
        openWorldHint: false
      }
    },
    async (args, extra) => {
      try {
        const envelope = await client.queryAuditLogs(args, buildDrisRequestContext(extra));
        return buildSuccessResult("Audit log query completed", envelope);
      } catch (error) {
        return buildErrorResult("Audit log query", error);
      }
    }
  );

  server.registerTool(
    "get_node_routes",
    {
      title: "Get Node Routes",
      description: "List current cross-node forwarding routes from the backend topology service.",
      inputSchema: {},
      outputSchema: apiEnvelopeOutputSchema,
      annotations: {
        title: "Get Node Routes",
        readOnlyHint: true,
        destructiveHint: false,
        idempotentHint: true,
        openWorldHint: false
      }
    },
    async (_args, extra) => {
      try {
        const envelope = await client.getNodeRoutes(buildDrisRequestContext(extra));
        return buildSuccessResult("Fetched node forwarding routes", envelope);
      } catch (error) {
        return buildErrorResult("Fetching node routes", error);
      }
    }
  );
}
