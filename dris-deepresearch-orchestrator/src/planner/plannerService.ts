import type { EnvConfig } from "../config/env.js";
import { isLlmConfigured } from "../llm/openaiCompatibleClient.js";
import type { ResearchFormState, ResearchPlan } from "../types/research.js";
import { createResearchPlan } from "./heuristicPlanner.js";
import { createLlmResearchPlan } from "./llmPlanner.js";

export async function createPlannedResearch(env: EnvConfig, form: ResearchFormState): Promise<ResearchPlan> {
  if (env.PLANNER_MODE === "heuristic") {
    return createResearchPlan(form);
  }

  if (env.PLANNER_MODE === "llm") {
    return createLlmResearchPlan(env, form);
  }

  if (isLlmConfigured(env, form.llmModel)) {
    try {
      return await createLlmResearchPlan(env, form);
    } catch {
      return createResearchPlan(form);
    }
  }

  return createResearchPlan(form);
}
