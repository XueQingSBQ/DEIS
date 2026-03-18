<template>
  <div class="shell">
    <div class="backdrop backdrop-a"></div>
    <div class="backdrop backdrop-b"></div>

    <header class="panel hero-panel">
      <div class="hero-copy">
        <p class="eyebrow">DRIS + MCP + DeepResearch</p>
        <h1>跨节点研究工作台</h1>
        <p class="header-copy">
          基于现有 MCP 工具链，先返回结构化报告，再流式补充 LLM 执行摘要，减少等待感并保留完整证据链。
        </p>
        <div class="hero-tags">
          <span class="hero-tag">先报告后摘要</span>
          <span class="hero-tag">流式事件时间线</span>
          <span class="hero-tag">跨节点资源证据</span>
        </div>
      </div>

      <div class="hero-status">
        <div class="connection-badge" :class="`state-${connection.status}`">
          <strong>{{ connectionStatusLabel(connection.status) }}</strong>
          <span>{{ targetLabel }}</span>
          <small>{{ connection.healthMessage || '尚未检查' }}</small>
        </div>

        <dl class="hero-stats">
          <div>
            <dt>执行方式</dt>
            <dd>{{ executionMode === 'agent' ? '智能体编排' : '手动流程' }}</dd>
          </div>
          <div>
            <dt>规划模式</dt>
            <dd>{{ plannerModeLabel }}</dd>
          </div>
          <div>
            <dt>标识输入</dt>
            <dd>{{ filledIdentifierCount }}</dd>
          </div>
          <div>
            <dt>最近耗时</dt>
            <dd>{{ lastDurationText }}</dd>
          </div>
        </dl>

        <div class="header-actions">
          <button class="button ghost" type="button" :disabled="healthBusy" @click="checkHealthAction">
            {{ healthBusy ? '检查中...' : executionMode === 'agent' ? '检查编排服务' : '检查 MCP' }}
          </button>
          <button class="button warm" type="button" :disabled="!report" @click="exportReport">导出报告</button>
        </div>
      </div>
    </header>

    <main class="workspace">
      <aside>
        <section class="panel control-panel">
          <div class="panel-head">
            <div>
              <p class="eyebrow">研究配置</p>
              <h2>输入与执行</h2>
            </div>
            <span class="pill" :class="isRunning ? 'pill-live' : failedStepCount ? 'pill-warn' : 'pill-still'">
              {{ isRunning ? '执行中' : report ? '已完成' : '待执行' }}
            </span>
          </div>

          <div class="control-stack">
            <section class="control-block emphasis-block">
              <div class="section-title-row">
                <h3>执行方式</h3>
                <span class="section-note">Agent 更适合展示</span>
              </div>
              <div class="segmented-control">
                <button class="segment" type="button" :class="executionMode === 'agent' ? 'segment-active' : ''" @click="executionMode = 'agent'">智能体</button>
                <button class="segment" type="button" :class="executionMode === 'manual' ? 'segment-active' : ''" @click="executionMode = 'manual'">手动</button>
              </div>
              <p class="field-help">
                {{ executionMode === 'agent' ? '直接调用编排层并接收流式事件。' : '前端直接驱动 MCP 工具，适合固定模板联调。' }}
              </p>
            </section>

            <section class="control-block service-stack">
              <div v-if="executionMode === 'agent'">
                <label class="field-label">编排服务地址</label>
                <input v-model.trim="orchestratorUrl" class="text-input mono" type="text" placeholder="http://127.0.0.1:38083" />
              </div>

              <div>
                <label class="field-label">MCP 地址</label>
                <input v-model.trim="form.mcpUrl" class="text-input mono" type="text" placeholder="http://127.0.0.1:38082/mcp" />
              </div>

              <div v-if="executionMode === 'agent'" class="agent-config-grid">
                <div>
                  <label class="field-label">LLM 模型</label>
                  <select v-if="agentModels.length" v-model="agentModelInput" class="select-input">
                    <option value="">沿用后端默认</option>
                    <option v-for="model in agentModels" :key="model" :value="model">{{ model }}</option>
                  </select>
                  <input v-else v-model.trim="agentModelInput" class="text-input" type="text" placeholder="例如 gpt-5.4-medium" />
                </div>
                <button class="button ghost small" type="button" :disabled="agentModelsBusy" @click="loadAgentModelsAction()">
                  {{ agentModelsBusy ? '加载中...' : '刷新模型' }}
                </button>
              </div>

              <div v-if="executionMode === 'manual'">
                <label class="field-label">研究模板</label>
                <select v-model="form.templateId" class="select-input">
                  <option v-for="item in templates" :key="item.id" :value="item.id">{{ item.title }} / {{ item.subtitle }}</option>
                </select>
                <div class="template-card compact-card">
                  <p class="template-subtitle">{{ activeTemplate.subtitle }}</p>
                  <h3>{{ activeTemplate.title }}</h3>
                  <p>{{ activeTemplate.description }}</p>
                </div>
              </div>
            </section>

            <section class="control-block service-stack">
              <div>
                <label class="field-label">自然语言问题</label>
                <textarea v-model="form.question" class="text-area" placeholder="例如：总结新能源车辆相关数据资源的共享覆盖情况、资源访问证据与近期审计风险。"></textarea>
              </div>

              <div>
                <label class="field-label">标识列表</label>
                <textarea v-model="form.identifiersText" class="text-area mono" placeholder="支持换行、空格或逗号分隔。"></textarea>
              </div>

              <div class="compact-grid">
                <div>
                  <label class="field-label">锚点节点</label>
                  <input v-model.trim="form.anchorNodeCode" class="text-input mono" type="text" placeholder="如 1100CNTA" />
                </div>
                <div>
                  <label class="field-label">搜索模式</label>
                  <select v-model="form.searchMode" class="select-input">
                    <option value="KEYWORD">关键词</option>
                    <option value="SEMANTIC">语义</option>
                    <option value="HYBRID">混合</option>
                  </select>
                </div>
                <div>
                  <label class="field-label">跨节点策略</label>
                  <select v-model="form.strategy" class="select-input">
                    <option value="RECURSIVE">递归</option>
                    <option value="ITERATIVE">迭代</option>
                  </select>
                </div>
                <div>
                  <label class="field-label">候选上限</label>
                  <input v-model.number="form.limit" class="text-input" type="number" min="1" max="20" />
                </div>
              </div>
            </section>

            <div class="action-row">
              <button class="button primary-block" type="button" :disabled="isRunning" @click="runResearchAction">
                {{ isRunning ? '执行中...' : executionMode === 'agent' ? '开始智能研究' : '执行手动流程' }}
              </button>
              <button class="button ghost" type="button" :disabled="isRunning" @click="clearWorkspace">清空结果</button>
            </div>
          </div>
        </section>
      </aside>

      <section class="results-stage">
        <div class="stage-summary">
          <article class="panel overview-card overview-primary">
            <div class="panel-head">
              <div>
                <p class="eyebrow">运行概览</p>
                <h2>研究快照</h2>
              </div>
              <span class="pill" :class="isRunning ? 'pill-live' : 'pill-still'">{{ isRunning ? '流式更新中' : '等待执行' }}</span>
            </div>

            <p class="overview-lead">{{ form.question.trim() || '尚未填写研究问题' }}</p>

            <div class="summary-grid">
              <article class="summary-metric">
                <span class="summary-label">搜索策略</span>
                <p class="summary-value">{{ searchProfileLabel }}</p>
              </article>
              <article class="summary-metric">
                <span class="summary-label">焦点标识</span>
                <p class="summary-value">{{ report?.focusedIdentifiers.length || lastAgentPlan?.focusedIdentifiers.length || 0 }} 个</p>
              </article>
              <article class="summary-metric">
                <span class="summary-label">成功步骤</span>
                <p class="summary-value">{{ completedStepCount }} / {{ steps.length }}</p>
              </article>
              <article class="summary-metric">
                <span class="summary-label">可见工具</span>
                <p class="summary-value">{{ visibleTools.length }}</p>
              </article>
            </div>

            <article v-if="agentSummaryLines.length" class="snapshot-card stream-card">
              <div class="section-title-row">
                <h3>流式执行摘要</h3>
                <span class="section-note">摘要在结构化报告之后逐步补齐</span>
              </div>
              <ul class="agent-rationale">
                <li v-for="line in agentSummaryLines" :key="line">{{ line }}</li>
              </ul>
            </article>

            <article v-if="executionMode === 'agent' && lastAgentPlan" class="snapshot-card">
              <div class="section-title-row">
                <h3>智能体规划</h3>
                <span class="section-note">最近一次执行</span>
              </div>
              <div class="agent-brief-grid">
                <div>
                  <span class="field-label">规划模式</span>
                  <p class="agent-brief-value">{{ plannerModeLabel }}</p>
                </div>
                <div>
                  <span class="field-label">模型</span>
                  <p class="agent-brief-value">{{ currentModelLabel }}</p>
                </div>
              </div>
              <div v-if="lastAgentPlan.rationale.length" class="agent-rationale">
                <h4>规划理由</h4>
                <ul>
                  <li v-for="line in lastAgentPlan.rationale" :key="line">{{ translateUiText(line) }}</li>
                </ul>
              </div>
            </article>
          </article>

          <article class="panel overview-card overview-side">
            <div class="panel-head">
              <div>
                <p class="eyebrow">状态</p>
                <h2>连接与工具</h2>
              </div>
            </div>
            <div class="status-list">
              <div class="status-row"><span>目标服务</span><strong>{{ targetLabel }}</strong></div>
              <div class="status-row"><span>连接状态</span><strong>{{ connectionStatusLabel(connection.status) }}</strong></div>
              <div class="status-row"><span>健康信息</span><strong>{{ connection.healthMessage || '尚未检查' }}</strong></div>
              <div class="status-row"><span>最近连接</span><strong>{{ connection.lastConnectedAt ? formatTime(connection.lastConnectedAt) : '暂无记录' }}</strong></div>
              <div class="status-row"><span>当前模型</span><strong>{{ currentModelLabel }}</strong></div>
            </div>
            <div v-if="visibleTools.length" class="tool-cloud">
              <span v-for="tool in visibleTools" :key="tool" class="tool-pill mono">{{ tool }}</span>
            </div>
            <div v-if="runtimeError" class="runtime-error">{{ runtimeError }}</div>
          </article>
        </div>

        <div class="results-grid">
          <ExecutionTimeline :steps="steps" :running="isRunning" />
          <InsightPanel :report="report" />
        </div>
      </section>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from "vue";
import ExecutionTimeline from "../../components/ExecutionTimeline.vue";
import InsightPanel from "../../components/InsightPanel.vue";
import { fetchAgentModels, fetchOrchestratorHealth, runAgentResearchStream, type AgentHealthPayload, type AgentPlan, type AgentStreamEvent } from "../../lib/agentClient";
import { BrowserMcpClient, fetchHealth } from "../../lib/mcpClient";
import { runResearchWorkflow } from "../../lib/researchRunner";
import { getTemplateById, researchTemplates } from "../../lib/researchTemplates";
import type { ConnectionSnapshot, ResearchFormState, ResearchReport, ToolExecutionStep } from "../../lib/types";
import { connectionStatusLabel, translateUiText } from "../../lib/uiText";
import { useStatusToast } from "../../shared/composables/useStatusToast";
import { useConsoleRuntime } from "../../shared/composables/useConsoleRuntime";
import { showToast } from "../../shared/feedback/toast";

type ExecutionMode = "manual" | "agent";

const { state: consoleRuntime } = useConsoleRuntime();
const client = new BrowserMcpClient();
const templates = researchTemplates;
const form = reactive<ResearchFormState>({
  mcpUrl: consoleRuntime.mcpUrl,
  templateId: templates[0]!.id,
  question: "结合跨节点搜索结果，总结新能源车辆数据资源的共享覆盖情况、资源访问证据与近期审计风险。",
  identifiersText: "",
  anchorNodeCode: "",
  searchMode: "KEYWORD",
  strategy: "RECURSIVE",
  limit: 5,
  ttl: 4,
  maxFanout: 8,
  perNodeLimit: 20,
  globalDeadlineMs: 5000,
  hopTimeoutMs: 1500,
  auditOperation: "",
  auditStartTime: "",
  auditEndTime: "",
  auditPageSize: 5
});
const executionMode = ref<ExecutionMode>("agent");
const orchestratorUrl = ref(consoleRuntime.orchestratorUrl);
const agentModelInput = ref("");
const connection = reactive<ConnectionSnapshot>({ status: "idle", healthStatus: "unknown", healthMessage: "", tools: [], error: "", lastConnectedAt: "" });
const steps = ref<ToolExecutionStep[]>([]);
const report = ref<ResearchReport | null>(null);
const isRunning = ref(false);
const runtimeError = ref("");
const connectionBusy = ref(false);
const healthBusy = ref(false);
const agentModelsBusy = ref(false);
const agentModels = ref<string[]>([]);
const agentHealth = ref<AgentHealthPayload | null>(null);
const lastAgentPlan = ref<AgentPlan | null>(null);
const lastPlannerMode = ref("");
const lastLlmModel = ref("");
const lastDurationMs = ref<number | null>(null);
const streamingSummaryText = ref("");

const activeTemplate = computed(() => getTemplateById(form.templateId));
const targetLabel = computed(() => executionMode.value === "agent" ? "DeepResearch 编排服务" : "MCP 工具网关");
const plannerModeLabel = computed(() => translateUiText(lastPlannerMode.value || agentHealth.value?.plannerMode || "heuristic"));
const currentModelLabel = computed(() => lastLlmModel.value || agentModelInput.value || agentHealth.value?.llmModel || "沿用默认");
const filledIdentifierCount = computed(() => parseIdentifiers(form.identifiersText).length);
const completedStepCount = computed(() => steps.value.filter((item) => item.status === "success").length);
const failedStepCount = computed(() => steps.value.filter((item) => item.status === "error").length);
const lastDurationText = computed(() => (lastDurationMs.value == null ? "-" : `${lastDurationMs.value} ms`));
const searchProfileLabel = computed(() => `${form.strategy === "ITERATIVE" ? "迭代" : "递归"} / ${form.searchMode === "SEMANTIC" ? "语义" : form.searchMode === "HYBRID" ? "混合" : "关键词"}`);
const visibleTools = computed(() => connection.tools.length ? connection.tools : lastAgentPlan.value?.steps.map((step) => step.toolName) ?? []);
const agentSummaryLines = computed(() => streamingSummaryText.value.split(/\r?\n/).map((line) => line.replace(/^\s*[-*•]\s*/, "").trim()).filter(Boolean).slice(0, 6));
useStatusToast(runtimeError, "Research");

function parseIdentifiers(raw: string): string[] {
  return Array.from(new Set(raw.split(/[\s,;，；]+/).map((item) => item.trim()).filter(Boolean)));
}

function normalizeAgentModels(models: string[], defaultModel?: string | null): string[] {
  const merged = Array.from(new Set([...(defaultModel ? [defaultModel] : []), ...models].map((item) => item.trim()).filter(Boolean)));
  return merged.sort((left, right) => left.localeCompare(right, "zh-CN"));
}

function syncAgentHealth(payload: AgentHealthPayload | null): void {
  agentHealth.value = payload;
  if (payload?.plannerMode) {
    lastPlannerMode.value = payload.plannerMode;
  }
  if (!agentModelInput.value && payload?.llmModel) {
    agentModelInput.value = payload.llmModel;
  }
}

function upsertStep(step: ToolExecutionStep): void {
  const index = steps.value.findIndex((item) => item.id === step.id);
  if (index >= 0) {
    steps.value.splice(index, 1, step);
  } else {
    steps.value.push(step);
  }
}

function resetRunState(): void {
  steps.value = [];
  report.value = null;
  lastAgentPlan.value = null;
  lastLlmModel.value = "";
  lastDurationMs.value = null;
  streamingSummaryText.value = "";
  runtimeError.value = "";
}

function rebuildMarkdown(next: ResearchReport): string {
  const lines: string[] = [
    `# ${next.title}`,
    "",
    `- 生成时间: ${next.generatedAt}`,
    `- 模板: ${next.templateTitle}`,
    `- 问题: ${next.question || "N/A"}`,
    `- 焦点标识: ${next.focusedIdentifiers.length ? next.focusedIdentifiers.join(", ") : "N/A"}`,
    ""
  ];
  for (const section of next.summarySections) {
    lines.push(`## ${section.title}`, "");
    for (const line of section.lines) {
      lines.push(`- ${line}`);
    }
    lines.push("");
  }
  return lines.join("\n");
}

function applyStreamingSummary(text: string): void {
  if (!report.value || !agentSummaryLines.value.length) {
    return;
  }
  const next: ResearchReport = {
    ...report.value,
    summarySections: [
      { title: "LLM 执行摘要（生成中）", lines: [...agentSummaryLines.value] },
      ...report.value.summarySections.filter((section) => !section.title.startsWith("LLM 执行摘要"))
    ]
  };
  report.value = { ...next, markdown: rebuildMarkdown(next) };
}

async function loadAgentModelsAction(silent = false): Promise<void> {
  if (executionMode.value !== "agent") {
    return;
  }
  agentModelsBusy.value = true;
  if (!silent) {
    runtimeError.value = "";
  }
  try {
    const payload = await fetchAgentModels(orchestratorUrl.value);
    agentModels.value = normalizeAgentModels(payload.models, payload.defaultModel);
    if (!agentModelInput.value && payload.defaultModel) {
      agentModelInput.value = payload.defaultModel;
    }
    if (!silent) {
      showToast({
        title: "Research",
        message: `模型列表已刷新，共 ${agentModels.value.length} 个模型。`,
        tone: "success"
      });
    }
  } catch (error) {
    if (!silent) {
      runtimeError.value = error instanceof Error ? error.message : String(error);
    }
  } finally {
    agentModelsBusy.value = false;
  }
}

async function checkHealthAction(): Promise<void> {
  runtimeError.value = "";
  healthBusy.value = true;
  connection.healthStatus = "checking";
  try {
    if (executionMode.value === "agent") {
      const response = await fetchOrchestratorHealth(orchestratorUrl.value);
      syncAgentHealth(response.payload);
      connection.healthStatus = response.ok ? "up" : "down";
      connection.healthMessage = response.ok ? "编排服务可达" : "编排服务返回异常";
      if (response.ok) {
        await loadAgentModelsAction(true);
      }
    } else {
      const response = await fetchHealth(form.mcpUrl);
      connection.healthStatus = response.ok ? "up" : "down";
      connection.healthMessage = response.ok ? "MCP 健康接口可达" : "MCP 健康接口返回异常";
    }
    showToast({
      title: "Health",
      message: connection.healthMessage || "健康检查完成。",
      tone: connection.healthStatus === "up" ? "success" : "error"
    });
  } catch (error) {
    connection.healthStatus = "down";
    connection.healthMessage = error instanceof Error ? error.message : String(error);
    runtimeError.value = connection.healthMessage;
  } finally {
    healthBusy.value = false;
  }
}

async function connectAction(): Promise<void> {
  connectionBusy.value = true;
  connection.status = "connecting";
  connection.error = "";
  try {
    if (executionMode.value === "agent") {
      const response = await fetchOrchestratorHealth(orchestratorUrl.value);
      syncAgentHealth(response.payload);
      if (!response.ok) {
        throw new Error("编排服务不可用");
      }
      connection.status = "connected";
      connection.healthStatus = "up";
      connection.healthMessage = "编排服务可达";
      connection.lastConnectedAt = new Date().toISOString();
      await loadAgentModelsAction(true);
    } else {
      await client.connect(form.mcpUrl);
      connection.tools = await client.listTools();
      connection.status = "connected";
      connection.healthStatus = "up";
      connection.healthMessage = "MCP 已连接";
      connection.lastConnectedAt = new Date().toISOString();
    }
    showToast({
      title: "Connect",
      message: connection.healthMessage || "连接成功。",
      tone: "success"
    });
  } catch (error) {
    connection.status = "error";
    connection.error = error instanceof Error ? error.message : String(error);
    runtimeError.value = connection.error;
  } finally {
    connectionBusy.value = false;
  }
}

async function handleAgentStreamEvent(event: AgentStreamEvent): Promise<void> {
  if (event.type === "meta") {
    lastPlannerMode.value = event.plannerMode;
    lastLlmModel.value = event.llmModel;
    connection.status = "connected";
    connection.healthStatus = "up";
    connection.healthMessage = `规划 ${translateUiText(event.plannerMode)} / 模型 ${translateUiText(event.llmModel || "inherit")}`;
    return;
  }
  if (event.type === "plan") {
    lastAgentPlan.value = event.plan;
    connection.tools = event.plan.steps.map((step) => step.toolName);
    return;
  }
  if (event.type === "step") {
    upsertStep(event.step);
    return;
  }
  if (event.type === "report") {
    report.value = event.report;
    if (event.report.summarySections[0]?.title?.startsWith("LLM 执行摘要")) {
      streamingSummaryText.value = "";
    }
    return;
  }
  if (event.type === "summary_delta") {
    streamingSummaryText.value = event.accumulated;
    applyStreamingSummary(event.accumulated);
    return;
  }
  if (event.type === "done") {
    lastDurationMs.value = event.durationMs;
    connection.lastConnectedAt = new Date().toISOString();
    return;
  }
  throw new Error(event.message);
}

async function runResearchAction(): Promise<void> {
  resetRunState();
  isRunning.value = true;
  try {
    if (executionMode.value === "agent") {
      connection.status = "connecting";
      connection.healthStatus = "checking";
      connection.healthMessage = "正在提交研究请求...";
      await runAgentResearchStream(orchestratorUrl.value, {
        question: form.question,
        identifiersText: form.identifiersText,
        anchorNodeCode: form.anchorNodeCode,
        searchMode: form.searchMode,
        strategy: form.strategy,
        limit: form.limit,
        ttl: form.ttl,
        maxFanout: form.maxFanout,
        perNodeLimit: form.perNodeLimit,
        globalDeadlineMs: form.globalDeadlineMs,
        hopTimeoutMs: form.hopTimeoutMs,
        auditOperation: form.auditOperation,
        auditStartTime: form.auditStartTime,
        auditEndTime: form.auditEndTime,
        auditPageSize: form.auditPageSize,
        mcpUrl: form.mcpUrl,
        llmModel: agentModelInput.value || undefined
      }, handleAgentStreamEvent);
    } else {
      const started = performance.now();
      await connectAction();
      if (connection.status !== "connected") {
        throw new Error(connection.error || "MCP 连接失败");
      }
      const result = await runResearchWorkflow(client, form, upsertStep);
      report.value = result.report;
      lastDurationMs.value = Math.round(performance.now() - started);
    }
    showToast({
      title: "Research",
      message: `研究执行完成，耗时 ${lastDurationMs.value ?? 0} ms。`,
      tone: "success"
    });
  } catch (error) {
    connection.status = "error";
    connection.error = error instanceof Error ? error.message : String(error);
    runtimeError.value = connection.error;
  } finally {
    isRunning.value = false;
  }
}

function clearWorkspace(): void {
  resetRunState();
  showToast({
    title: "Research",
    message: "研究结果已清空。",
    tone: "info"
  });
}

function exportReport(): void {
  if (!report.value) {
    return;
  }
  const blob = new Blob([report.value.markdown], { type: "text/markdown;charset=utf-8" });
  const href = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = href;
  anchor.download = `dris-研究报告-${Date.now()}.md`;
  anchor.click();
  URL.revokeObjectURL(href);
  showToast({
    title: "Research",
    message: "报告已导出。",
    tone: "success"
  });
}

function formatTime(value: string): string {
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? value : parsed.toLocaleString("zh-CN", { hour12: false });
}

onMounted(() => {
  void checkHealthAction();
  if (executionMode.value === "agent") {
    void loadAgentModelsAction(true);
  }
});

onBeforeUnmount(() => {
  void client.disconnect();
});
</script>

<style scoped>
.compact-card {
  margin-top: 12px;
}

.stream-card {
  border-color: rgba(23, 106, 98, 0.14);
  background: linear-gradient(180deg, rgba(233, 247, 244, 0.92), rgba(255, 255, 255, 0.82));
}
</style>


