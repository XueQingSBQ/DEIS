import type { CallToolResult } from "@modelcontextprotocol/sdk/types.js";

import { DrisApiError, type DrisApiEnvelope } from "../adapter/drisApiClient.js";

export function buildSuccessResult(summary: string, envelope: DrisApiEnvelope): CallToolResult {
  return {
    content: [
      {
        type: "text",
        text: `${summary}\n\n${prettyJson(envelope)}`
      }
    ],
    structuredContent: envelope
  };
}

export function buildErrorResult(action: string, error: unknown): CallToolResult {
  if (error instanceof DrisApiError) {
    const envelope = error.envelope ?? {
      ok: false,
      code: "HTTP_ERROR",
      message: error.message,
      status: error.status
    };

    return {
      isError: true,
      content: [
        {
          type: "text",
          text: `${action} failed\n\n${prettyJson(envelope)}`
        }
      ],
      structuredContent: envelope
    };
  }

  return {
    isError: true,
    content: [
      {
        type: "text",
        text: `${action} failed\n\n${prettyJson({
          ok: false,
          code: "UNEXPECTED_ERROR",
          message: error instanceof Error ? error.message : "Unexpected error"
        })}`
      }
    ],
    structuredContent: {
      ok: false,
      code: "UNEXPECTED_ERROR",
      message: error instanceof Error ? error.message : "Unexpected error"
    }
  };
}

export function prettyJson(value: unknown): string {
  return JSON.stringify(value, null, 2);
}
