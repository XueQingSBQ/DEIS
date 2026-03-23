import type { EnvConfig } from "../config/env.js";
import { createChatCompletion, createChatCompletionStream } from "../llm/openaiCompatibleClient.js";
import type { ResearchPlan, ResearchReport, ToolExecutionStep } from "../types/research.js";

function buildSummaryMessages(
  plan: ResearchPlan,
  report: ResearchReport,
  steps: ToolExecutionStep[]
): Array<{ role: "system" | "user"; content: string }> {
  const systemPrompt = [
    "You summarize domain research results, not platform operation status.",
    "Focus on the substantive contents of the retrieved files: what they discuss, how they relate, and what conclusions can be drawn.",
    "Do not center the summary on identifiers, routing, node topology, audit logs, or system governance.",
    "You may mention system evidence only briefly if it affects confidence or completeness.",
    "Write a concise executive summary as 2 to 5 bullet lines.",
    "Each line must start with '- ' and stay factual.",
    "Respond in the same language as the user's question when possible; otherwise default to Simplified Chinese.",
    "Do not invent facts. Use only the structured evidence provided."
  ].join("\n");

  const userPrompt = JSON.stringify(
    {
      plan: {
        plannerMode: plan.plannerMode,
        templateId: plan.templateId,
        rationale: plan.rationale,
        focusedIdentifiers: plan.focusedIdentifiers
      },
      report: {
        title: report.title,
        templateTitle: report.templateTitle,
        question: report.question,
        summarySections: report.summarySections,
        evidenceCards: report.evidenceCards.slice(0, 8)
      },
      executedSteps: steps.map((step) => ({
        toolName: step.toolName,
        status: step.status,
        summary: step.summary,
        error: step.error
      }))
    },
    null,
    2
  );

  return [
    { role: "system", content: systemPrompt },
    { role: "user", content: userPrompt }
  ];
}

function normalizeSummaryLines(text: string): string[] {
  const bulletLines = text
    .split(/\r?\n/)
    .map((line) => line.replace(/^\s*[-*•]\s*/, "").trim())
    .filter(Boolean);

  if (bulletLines.length) {
    return bulletLines.slice(0, 5);
  }

  const fallbackLines = text
    .split(/[。！？!?]\s*/)
    .map((line) => line.trim())
    .filter(Boolean);

  return fallbackLines.slice(0, 5);
}

export async function generateExecutiveSummary(
  env: EnvConfig,
  plan: ResearchPlan,
  report: ResearchReport,
  steps: ToolExecutionStep[],
  overrideModel?: string
): Promise<string[]> {
  const completion = await createChatCompletion(env, buildSummaryMessages(plan, report, steps), overrideModel);
  const lines = normalizeSummaryLines(completion);
  if (lines.length < 2) {
    throw new Error("LLM returned too little summary content");
  }
  return lines;
}

export async function generateExecutiveSummaryStream(
  env: EnvConfig,
  plan: ResearchPlan,
  report: ResearchReport,
  steps: ToolExecutionStep[],
  onDelta: (delta: string, accumulated: string) => void | Promise<void>,
  overrideModel?: string
): Promise<string[]> {
  const completion = await createChatCompletionStream(env, buildSummaryMessages(plan, report, steps), onDelta, overrideModel);
  const lines = normalizeSummaryLines(completion);
  if (lines.length < 2) {
    throw new Error("LLM returned too little summary content");
  }
  return lines;
}
