import type { EnvConfig } from "../config/env.js";
import { createChatCompletion } from "../llm/openaiCompatibleClient.js";
import type { ResearchPlan, ResearchReport, ToolExecutionStep } from "../types/research.js";

function buildNarrativeMessages(
  plan: ResearchPlan,
  report: ResearchReport,
  steps: ToolExecutionStep[]
): Array<{ role: "system" | "user"; content: string }> {
  const systemPrompt = [
    "You write a factual domain research report, not a system operation report.",
    "Your main task is to analyze the real contents of the retrieved files and organize them into a thematic survey or research note.",
    "Focus on document themes, technical points, policy background, implementation clues, relationships among documents, and substantive conclusions.",
    "If related images, figures, screenshots, or derived visual materials are provided, treat them as part of the source evidence and incorporate their content into the analysis.",
    "When using image evidence, explain what the images supplement or confirm in the source documents, rather than merely listing image identifiers or URLs.",
    "Do not center the report on identifiers, routing, node topology, audit logs, component resolution, or platform governance.",
    "Those system fields may be mentioned briefly only as supporting evidence when necessary, but they must not dominate the report.",
    "Use only the supplied evidence and resource previews.",
    "Write in Simplified Chinese unless the user's question clearly uses another language.",
    "When multiple files are present, distinguish core normative documents, supporting technical documents, and local practice or progress materials.",
    "Explain how the files relate to each other, such as upper-level requirements vs. implementation guidelines, or technical specifications vs. local execution examples.",
    "If the evidence is insufficient, state the limitation clearly, but still summarize what can be reliably inferred from the file contents.",
    "Produce Markdown with the following structure:",
    "# 标题",
    "## 研究背景",
    "## 核心文件与内容概览",
    "## 重点内容分析",
    "## 文件关系与体系梳理",
    "## 结论与建议",
    "The tone should resemble a formal research note, literature-style survey, or thematic report.",
    "Avoid generic filler. Do not invent facts, sources, or conclusions."
  ].join("\n");

  const userPrompt = JSON.stringify(
    {
      plan: {
        templateId: plan.templateId,
        rationale: plan.rationale,
        focusedIdentifiers: plan.focusedIdentifiers
      },
      report: {
        title: report.title,
        question: report.question,
        summarySections: report.summarySections,
        evidenceCards: report.evidenceCards.slice(0, 10),
        multimodalMarkdown: report.multimodalMarkdown,
        markdown: report.markdown
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

export async function generateLongformNarrative(
  env: EnvConfig,
  plan: ResearchPlan,
  report: ResearchReport,
  steps: ToolExecutionStep[],
  overrideModel?: string
): Promise<string> {
  const completion = await createChatCompletion(env, buildNarrativeMessages(plan, report, steps), overrideModel);
  const text = completion.trim();
  if (!text || text.length < 120) {
    throw new Error("LLM returned too little narrative content");
  }
  return text;
}
