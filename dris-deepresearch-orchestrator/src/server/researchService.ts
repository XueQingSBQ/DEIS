import { performance } from "node:perf_hooks";

import type { EnvConfig } from "../config/env.js";
import { McpResearchClient } from "../executor/mcpResearchClient.js";
import { executeResearchPlan } from "../executor/runPlan.js";
import { isLlmConfigured, resolveModel } from "../llm/openaiCompatibleClient.js";
import { normalizeResearchForm } from "../planner/heuristicPlanner.js";
import { createPlannedResearch } from "../planner/plannerService.js";
import { buildResearchReport, prependExecutiveSummary } from "../report/buildReport.js";
import { collectResourcePreviews } from "../report/resourcePreview.js";
import { generateExecutiveSummary, generateExecutiveSummaryStream } from "../report/llmSummary.js";
import type {
  ResearchRunRequest,
  ResearchRunResponse,
  ResearchStreamEvent,
  ToolExecutionStep
} from "../types/research.js";

export interface ResearchEventHooks {
  onEvent?: (event: ResearchStreamEvent) => void | Promise<void>;
}

async function emitEvent(hooks: ResearchEventHooks | undefined, event: ResearchStreamEvent): Promise<void> {
  await hooks?.onEvent?.(event);
}

function buildSummaryStep(
  id: string,
  status: ToolExecutionStep["status"],
  llmModel: string,
  message: string,
  startedAt: string,
  error?: string
): ToolExecutionStep {
  const endedAt = new Date().toISOString();
  return {
    id,
    toolName: "llm_executive_summary",
    title: "Generate executive summary",
    description: "Use the configured LLM to compress the structured report into a short executive summary.",
    status,
    startedAt,
    endedAt,
    durationMs: Math.max(0, new Date(endedAt).getTime() - new Date(startedAt).getTime()),
    summary: message,
    error,
    args: {
      llmModel: llmModel || "inherit"
    }
  };
}

function buildResourcePreviewStep(
  id: string,
  status: ToolExecutionStep["status"],
  message: string,
  startedAt: string,
  args?: Record<string, unknown>,
  error?: string
): ToolExecutionStep {
  const endedAt = new Date().toISOString();
  return {
    id,
    toolName: "resource_content_preview",
    title: "Fetch lightweight resource preview",
    description: "Fetch small samples from resolved resource locators to expose actual data content without downloading whole files.",
    status,
    startedAt,
    endedAt,
    durationMs: Math.max(0, new Date(endedAt).getTime() - new Date(startedAt).getTime()),
    summary: message,
    error,
    args
  };
}

async function runResearchInternal(
  env: EnvConfig,
  request: ResearchRunRequest,
  hooks?: ResearchEventHooks
): Promise<ResearchRunResponse> {
  const started = performance.now();
  const form = normalizeResearchForm(request, env.MCP_SERVER_URL);
  const plan = await createPlannedResearch(env, form);
  const llmModel = resolveModel(env, form.llmModel);

  await emitEvent(hooks, {
    type: "meta",
    plannerMode: plan.plannerMode,
    llmModel,
    mcpUrl: form.mcpUrl
  });
  await emitEvent(hooks, {
    type: "plan",
    plan
  });

  const client = new McpResearchClient();

  try {
    await client.connect(form.mcpUrl);
    const executed = await executeResearchPlan(client, form, plan, {
      onStep: async (step) => {
        await emitEvent(hooks, {
          type: "step",
          step
        });
      }
    });

    let report = buildResearchReport(form, plan, executed.outputs);
    const streamingMode = Boolean(hooks?.onEvent);

    if (streamingMode) {
      await emitEvent(hooks, {
        type: "report",
        report
      });
    }

    if (env.RESOURCE_PREVIEW_ENABLED) {
      const previewStartedAt = new Date().toISOString();
      const previewStepId = `resource-preview-${Date.now()}`;
      await emitEvent(hooks, {
        type: "step",
        step: {
          id: previewStepId,
          toolName: "resource_content_preview",
          title: "Fetch lightweight resource preview",
          description: "Fetch small samples from resolved resource locators to expose actual data content without downloading whole files.",
          status: "running",
          startedAt: previewStartedAt,
          args: {
            limit: env.RESOURCE_PREVIEW_LIMIT,
            timeoutMs: env.RESOURCE_PREVIEW_TIMEOUT_MS,
            maxBytes: env.RESOURCE_PREVIEW_BYTES
          }
        }
      });

      try {
        const previewResult = await collectResourcePreviews(env, executed.outputs);
        report = buildResearchReport(form, plan, executed.outputs, previewResult.previewMap);
        const previewStep = buildResourcePreviewStep(
          previewStepId,
          "success",
          previewResult.attemptedCount
            ? `Collected lightweight previews for ${previewResult.previewMap.size}/${previewResult.attemptedCount} resource locators.`
            : "No previewable HTTP resource locator was available in this run.",
          previewStartedAt,
          {
            limit: env.RESOURCE_PREVIEW_LIMIT,
            timeoutMs: env.RESOURCE_PREVIEW_TIMEOUT_MS,
            maxBytes: env.RESOURCE_PREVIEW_BYTES,
            attemptedCount: previewResult.attemptedCount,
            previewCount: previewResult.previewMap.size
          }
        );
        executed.steps.push(previewStep);
        await emitEvent(hooks, {
          type: "step",
          step: previewStep
        });

        if (streamingMode && previewResult.previewMap.size > 0) {
          await emitEvent(hooks, {
            type: "report",
            report
          });
        }
      } catch (error) {
        const previewStep = buildResourcePreviewStep(
          previewStepId,
          "error",
          "Resource preview collection failed; kept structured report.",
          previewStartedAt,
          {
            limit: env.RESOURCE_PREVIEW_LIMIT,
            timeoutMs: env.RESOURCE_PREVIEW_TIMEOUT_MS,
            maxBytes: env.RESOURCE_PREVIEW_BYTES
          },
          error instanceof Error ? error.message : String(error)
        );
        executed.steps.push(previewStep);
        await emitEvent(hooks, {
          type: "step",
          step: previewStep
        });
      }
    }

    if (env.LLM_SUMMARY_ENABLED && isLlmConfigured(env, form.llmModel)) {
      const summaryStartedAt = new Date().toISOString();
      const summaryStepId = `llm-summary-${Date.now()}`;
      await emitEvent(hooks, {
        type: "step",
        step: {
          id: summaryStepId,
          toolName: "llm_executive_summary",
          title: "Generate executive summary",
          description: "Use the configured LLM to compress the structured report into a short executive summary.",
          status: "running",
          startedAt: summaryStartedAt,
          args: {
            llmModel: llmModel || "inherit"
          }
        }
      });

      try {
        const executiveSummary = streamingMode
          ? await generateExecutiveSummaryStream(
              env,
              plan,
              report,
              executed.steps,
              async (delta, accumulated) => {
                await emitEvent(hooks, {
                  type: "summary_delta",
                  delta,
                  accumulated
                });
              },
              form.llmModel
            )
          : await generateExecutiveSummary(env, plan, report, executed.steps, form.llmModel);

        report = prependExecutiveSummary(report, executiveSummary);

        const summaryStep = buildSummaryStep(
          summaryStepId,
          "success",
          llmModel,
          `Generated executive summary with ${llmModel || "configured model"}.`,
          summaryStartedAt
        );
        executed.steps.push(summaryStep);
        await emitEvent(hooks, {
          type: "step",
          step: summaryStep
        });

        if (streamingMode) {
          await emitEvent(hooks, {
            type: "report",
            report
          });
        }
      } catch (error) {
        const summaryStep = buildSummaryStep(
          summaryStepId,
          "error",
          llmModel,
          "Executive summary generation failed; kept structured report.",
          summaryStartedAt,
          error instanceof Error ? error.message : String(error)
        );
        executed.steps.push(summaryStep);
        await emitEvent(hooks, {
          type: "step",
          step: summaryStep
        });
      }
    }

    const response: ResearchRunResponse = {
      ok: true,
      plannerMode: plan.plannerMode,
      mcpUrl: form.mcpUrl,
      llmModel,
      plan,
      steps: executed.steps,
      report,
      focusedIdentifiers: executed.focusedIdentifiers,
      durationMs: Math.round(performance.now() - started)
    };

    if (!streamingMode) {
      await emitEvent(hooks, {
        type: "report",
        report
      });
    }
    await emitEvent(hooks, {
      type: "done",
      focusedIdentifiers: response.focusedIdentifiers,
      durationMs: response.durationMs
    });

    return response;
  } finally {
    await client.disconnect();
  }
}

export async function runResearch(env: EnvConfig, request: ResearchRunRequest): Promise<ResearchRunResponse> {
  return runResearchInternal(env, request);
}

export async function streamResearch(
  env: EnvConfig,
  request: ResearchRunRequest,
  hooks: ResearchEventHooks
): Promise<ResearchRunResponse> {
  return runResearchInternal(env, request, hooks);
}
