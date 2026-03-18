import { z } from "zod";

import type { EnvConfig } from "../config/env.js";
import { createChatCompletion, extractJsonBlock } from "../llm/openaiCompatibleClient.js";
import type { ResearchFormState, ResearchPlan, TemplateId } from "../types/research.js";
import {
  buildPlanFromTemplate,
  chooseTemplateHeuristically,
  extractIdentifiersFromQuestion,
  parseIdentifiers
} from "./heuristicPlanner.js";

const plannerSchema = z.object({
  templateId: z.enum(["discovery_overview", "identifier_compare", "audit_trace", "anchor_search"]),
  rationale: z.array(z.string()).min(1).max(6),
  explicitIdentifiers: z.array(z.string()).max(12).optional()
});

function unique(values: string[]): string[] {
  return Array.from(new Set(values.map((item) => item.trim()).filter(Boolean)));
}

export async function createLlmResearchPlan(env: EnvConfig, form: ResearchFormState): Promise<ResearchPlan> {
  const heuristicIdentifiers = unique([
    ...parseIdentifiers(form.identifiersText),
    ...extractIdentifiersFromQuestion(form.question)
  ]);
  const heuristicChoice = chooseTemplateHeuristically(form, heuristicIdentifiers);

  const systemPrompt = [
    "You are a domain planner for a DRIS DeepResearch system.",
    "Choose exactly one bounded research template.",
    "Available templates: discovery_overview, identifier_compare, audit_trace, anchor_search.",
    "Rules:",
    "- Use anchor_search when anchorNodeCode is present.",
    "- Use identifier_compare when multiple identifiers are already known and the question is comparative.",
    "- Use audit_trace when the user is focused on history, provenance, audit, or a single known identifier.",
    "- Use discovery_overview for broad exploratory questions.",
    "Return only JSON."
  ].join("\n");

  const userPrompt = JSON.stringify(
    {
      question: form.question,
      identifiersText: form.identifiersText,
      extractedIdentifiers: heuristicIdentifiers,
      anchorNodeCode: form.anchorNodeCode,
      searchMode: form.searchMode,
      strategy: form.strategy,
      auditOperation: form.auditOperation,
      heuristicFallback: heuristicChoice
    },
    null,
    2
  );

  const completion = await createChatCompletion(
    env,
    [
      { role: "system", content: systemPrompt },
      { role: "user", content: userPrompt }
    ],
    form.llmModel
  );

  const parsed = plannerSchema.parse(JSON.parse(extractJsonBlock(completion)));
  const explicitIdentifiers = unique([...(parsed.explicitIdentifiers ?? []), ...heuristicIdentifiers]);
  const rationale = parsed.rationale.length ? parsed.rationale : heuristicChoice.rationale;

  return buildPlanFromTemplate(form, parsed.templateId as TemplateId, explicitIdentifiers, rationale, "llm");
}
