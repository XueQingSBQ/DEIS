<template>
  <div class="page-shell">
    <section class="page-hero panel">
      <div>
        <p class="eyebrow">Search + Resolve</p>
        <h2>跨节点搜索与批量解析</h2>
        <p class="panel-caption">把自然语言检索、跨节点链路追踪和批量解析收敛到同一工作台，适合联调和答辩演示。</p>
      </div>
      <div class="page-hero-actions page-toolbar">
        <button class="button ghost" type="button" @click="applyPreset('energy')">能源数据集</button>
        <button class="button ghost" type="button" @click="applyPreset('service')">服务接口</button>
        <button class="button ghost" type="button" @click="applyPreset('image')">图像资源</button>
        <button class="button warm" type="button" :disabled="running" @click="runSearch">{{ running ? "检索中..." : "执行全局搜索" }}</button>
      </div>
    </section>

    <section class="summary-grid compact-summary-grid">
      <article class="summary-card">
        <span class="summary-label">锚点节点</span>
        <strong class="summary-value">{{ currentNode.label }}</strong>
        <small class="mono">{{ searchForm.anchorNodeCode }}</small>
      </article>
      <article class="summary-card">
        <span class="summary-label">搜索方式</span>
        <strong class="summary-value">{{ modeLabel }}</strong>
        <small>{{ strategyLabel }} / TTL {{ searchForm.ttl }}</small>
      </article>
      <article class="summary-card">
        <span class="summary-label">命中结果</span>
        <strong class="summary-value">{{ searchItems.length }}</strong>
        <small>访问 {{ visitedNodes.length }} / 失败 {{ failedNodes.length }}</small>
      </article>
      <article class="summary-card">
        <span class="summary-label">待解析标识</span>
        <strong class="summary-value">{{ selectedIdentifiers.length }}</strong>
        <small>{{ resolveCards.length ? `已返回 ${resolveCards.length} 条解析结果` : "可继续批量解析" }}</small>
      </article>
    </section>

    <section class="results-grid search-workspace-grid">
      <article class="panel page-card search-workbench-card">
        <div class="panel-head">
          <div>
            <p class="eyebrow">Workbench</p>
            <h3>检索工作台</h3>
          </div>
          <span class="status-pill" :class="running ? 'status-warn' : 'status-good'">{{ running ? "执行中" : "就绪" }}</span>
        </div>

        <div class="tool-cloud compact-cloud">
          <button class="tool-pill" :class="presetName === 'energy' ? 'tool-pill-active' : ''" type="button" @click="applyPreset('energy')">能源 dataset</button>
          <button class="tool-pill" :class="presetName === 'service' ? 'tool-pill-active' : ''" type="button" @click="applyPreset('service')">服务接口</button>
          <button class="tool-pill" :class="presetName === 'image' ? 'tool-pill-active' : ''" type="button" @click="applyPreset('image')">图像资源</button>
        </div>

        <label class="field-block field-block-wide">
          <span class="field-label">自然语言检索问题</span>
          <textarea
            v-model.trim="searchForm.naturalQuery"
            class="text-area search-query-input"
            rows="5"
            placeholder="例如：检索北京区域下与能源行业相关、重点为 dataset 类型的数字对象，并给出可访问资源。"
          ></textarea>
        </label>

        <div class="search-primary-grid">
          <label class="field-block">
            <span class="field-label">搜索模式</span>
            <select v-model="searchForm.mode" class="select-input">
              <option value="KEYWORD">关键词</option>
              <option value="SEMANTIC">语义</option>
              <option value="HYBRID">混合</option>
            </select>
          </label>
          <label class="field-block">
            <span class="field-label">传播策略</span>
            <select v-model="searchForm.strategy" class="select-input">
              <option value="ITERATIVE">迭代</option>
              <option value="RECURSIVE">递归</option>
            </select>
          </label>
          <label class="field-block">
            <span class="field-label">返回上限</span>
            <input v-model.number="searchForm.limit" class="text-input mono" type="number" min="1" />
          </label>
          <label class="field-block">
            <span class="field-label">锚点节点</span>
            <input v-model.trim="searchForm.anchorNodeCode" class="text-input mono" type="text" />
          </label>
        </div>

        <details class="control-disclosure search-advanced-fold">
          <summary>
            <span>高级参数</span>
            <span class="disclosure-meta">每节点上限、超时、全局截止时间</span>
          </summary>
          <div class="disclosure-body">
            <div class="form-grid">
              <label class="field-block">
                <span class="field-label">perNodeLimit</span>
                <input v-model.number="searchForm.perNodeLimit" class="text-input mono" type="number" min="1" />
              </label>
              <label class="field-block">
                <span class="field-label">globalDeadlineMs</span>
                <input v-model.number="searchForm.globalDeadlineMs" class="text-input mono" type="number" min="100" />
              </label>
              <label class="field-block">
                <span class="field-label">hopTimeoutMs</span>
                <input v-model.number="searchForm.hopTimeoutMs" class="text-input mono" type="number" min="100" />
              </label>
              <label class="field-block">
                <span class="field-label">ttl</span>
                <input v-model.number="searchForm.ttl" class="text-input mono" type="number" min="1" />
              </label>
            </div>
          </div>
        </details>

        <div class="action-row compact-actions">
          <button class="button warm" type="button" :disabled="running" @click="runSearch">{{ running ? "检索中..." : "开始检索" }}</button>
          <button class="button ghost" type="button" :disabled="!searchItems.length" @click="clearSearch">清空结果</button>
        </div>
      </article>

      <article class="panel page-card search-trace-card">
        <div class="panel-head">
          <div>
            <p class="eyebrow">Trace</p>
            <h3>链路摘要</h3>
          </div>
          <span class="status-pill status-muted mono">{{ searchResult?.traceId || "未生成 traceId" }}</span>
        </div>

        <div class="search-brief-grid">
          <div class="hint-card">
            <div class="hint-card-head">
              <strong>查询概况</strong>
              <span class="status-pill status-muted">{{ modeLabel }}</span>
            </div>
            <div class="kv-grid">
              <div class="kv-item">
                <span>当前节点</span>
                <strong>{{ currentNode.label }}</strong>
              </div>
              <div class="kv-item">
                <span>传播策略</span>
                <strong>{{ strategyLabel }}</strong>
              </div>
              <div class="kv-item">
                <span>返回上限</span>
                <strong>{{ searchForm.limit }}</strong>
              </div>
              <div class="kv-item">
                <span>全局截止</span>
                <strong>{{ searchForm.globalDeadlineMs }} ms</strong>
              </div>
            </div>
          </div>

          <div class="hint-card">
            <div class="hint-card-head">
              <strong>访问路径</strong>
              <span class="status-pill" :class="failedNodes.length ? 'status-warn' : 'status-good'">{{ failedNodes.length ? "存在失败节点" : "链路正常" }}</span>
            </div>
            <div v-if="visitedNodes.length" class="search-node-flow">
              <span v-for="node in visitedNodes" :key="node" class="status-pill status-good mono">{{ node }}</span>
            </div>
            <div v-else class="panel-empty compact-empty">执行搜索后显示访问路径。</div>
          </div>
        </div>

        <div class="trace-group">
          <strong>失败节点</strong>
          <div v-if="failedNodes.length" class="chip-grid">
            <span v-for="node in failedNodes" :key="node" class="status-pill status-warn mono">{{ node }}</span>
          </div>
          <div v-else class="panel-empty compact-empty">当前没有失败节点。</div>
        </div>

        <div class="search-tip-board">
          <strong>调优建议</strong>
          <ul>
            <li v-if="!searchItems.length">若结果为 0，优先收缩自然语言关键词，再调整模式为“关键词”或切换锚点节点。</li>
            <li v-else-if="averageScore < 0.12">当前平均相关度较低，建议缩短问题描述、补充行业/类型关键词。</li>
            <li v-else>当前命中质量可接受，可直接勾选目标标识执行批量解析。</li>
            <li>若需要做方法对比，保持同一问题分别使用“迭代 / 递归”执行即可。</li>
          </ul>
        </div>
      </article>
    </section>

    <section class="results-grid search-result-grid">
      <article class="panel page-card">
        <div class="panel-head">
          <div>
            <p class="eyebrow">Hits</p>
            <h3>命中结果</h3>
          </div>
          <div class="action-row compact-actions compact-head-actions search-batch-actions">
            <button class="button ghost" type="button" :disabled="!searchItems.length" @click="toggleSelectAll">{{ allSelected ? "取消全选" : "全选结果" }}</button>
            <button class="button ghost" type="button" :disabled="!selectedIdentifiers.length || running" @click="batchResolve('metadata')">批量元数据解析</button>
            <button class="button ghost" type="button" :disabled="!selectedIdentifiers.length || running" @click="batchResolve('object')">批量数字对象解析</button>
            <button class="button ghost" type="button" :disabled="!selectedIdentifiers.length || running" @click="batchResolve('component')">批量资源解析</button>
          </div>
        </div>

        <div v-if="searchItems.length" class="result-card-list">
          <label
            v-for="(item, index) in searchItems"
            :key="item.identifier"
            class="result-card selectable-card search-hit-card"
            :class="selectedIdentifiers.includes(item.identifier) ? 'search-hit-selected' : ''"
          >
            <div class="result-card-head">
              <input v-model="selectedIdentifiers" type="checkbox" :value="item.identifier" />
              <div class="search-hit-main">
                <div class="search-hit-title-row">
                  <strong>{{ item.name || item.identifier }}</strong>
                  <span class="status-pill" :class="scorePillClass(item.finalScore)">#{{ Number(index) + 1 }} / {{ scoreText(item.finalScore) }}</span>
                </div>
                <p class="field-help mono">{{ item.identifier }}</p>
              </div>
            </div>

            <div class="search-score-bar">
              <span class="search-score-bar-fill" :style="{ width: scoreWidth(item.finalScore) }"></span>
            </div>

            <p class="field-help">{{ item.description || "无描述信息，建议继续执行元数据解析。" }}</p>

            <div class="metrics-strip compact-chip-strip">
              <div class="metric-chip"><span>类型</span><strong>{{ item.componentType || "-" }}</strong></div>
              <div class="metric-chip"><span>来源</span><strong>{{ item.source || "-" }}</strong></div>
              <div class="metric-chip"><span>行业</span><strong>{{ item.industry || "-" }}</strong></div>
              <div class="metric-chip"><span>分类</span><strong>{{ item.dataCategory || "-" }}</strong></div>
            </div>

            <div class="kv-grid search-hit-kv">
              <div class="kv-item">
                <span>所有者</span>
                <strong>{{ item.owner || "-" }}</strong>
              </div>
              <div class="kv-item">
                <span>资源定位</span>
                <strong class="mono-wrap">{{ truncate(item.locator || "-", 88) }}</strong>
              </div>
            </div>
          </label>
        </div>
        <div v-else class="panel-empty">先执行一次全局搜索。若结果为 0，可缩短问题描述、切换搜索模式，或更换锚点节点后重试。</div>
      </article>

      <article class="panel page-card">
        <div class="panel-head">
          <div>
            <p class="eyebrow">Resolve</p>
            <h3>批量解析结果</h3>
          </div>
          <span class="status-pill" :class="resolveCards.length ? 'status-good' : 'status-muted'">{{ resolveCards.length ? "已返回" : "未执行" }}</span>
        </div>

        <div class="metrics-strip">
          <div class="metric-chip"><span>记录数</span><strong>{{ resolveSummary.recordCount }}</strong></div>
          <div class="metric-chip"><span>键数</span><strong>{{ resolveSummary.keyCount }}</strong></div>
          <div class="metric-chip"><span>当前类型</span><strong>{{ resolveModeLabel }}</strong></div>
        </div>

        <div v-if="resolveCards.length" class="result-card-list">
          <article v-for="card in resolveCards" :key="`${resolveModeLabel}-${card.identifier}`" class="result-card resolve-result-card">
            <div class="search-hit-title-row">
              <strong>{{ card.title }}</strong>
              <div class="chip-grid">
                <span class="status-pill" :class="card.success === false ? 'status-warn' : 'status-good'">{{ card.success === false ? "失败" : "成功" }}</span>
                <span v-if="card.nodeCode" class="status-pill status-muted mono">{{ card.nodeCode }}</span>
              </div>
            </div>
            <p class="field-help mono">{{ card.identifier }}</p>
            <div class="kv-grid search-resolve-kv">
              <div v-for="field in card.fields" :key="`${card.identifier}-${field.label}`" class="kv-item">
                <span>{{ field.label }}</span>
                <strong :class="field.mono ? 'mono-wrap' : ''">{{ field.value }}</strong>
              </div>
            </div>
            <details class="management-raw-fold">
              <summary>查看原始响应</summary>
              <pre class="json-view management-json-view">{{ prettyJson(card.raw) }}</pre>
            </details>
          </article>
        </div>
        <div v-else class="panel-empty">先勾选命中结果，再执行批量元数据解析、数字对象解析或资源解析。</div>
      </article>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from "vue";

import { asJsonBody, requestEnvelope } from "../../shared/api/http";
import { showToast } from "../../shared/feedback/toast";
import { useConsoleRuntime } from "../../shared/composables/useConsoleRuntime";
import { formatNumber, prettyJson, truncate } from "../../shared/utils/format";

type ResolveField = {
  label: string;
  value: string;
  mono?: boolean;
};

type ResolveCard = {
  identifier: string;
  title: string;
  success?: boolean;
  nodeCode?: string;
  fields: ResolveField[];
  raw: unknown;
};

const { currentBackendProxyBase, currentNode } = useConsoleRuntime();

const presetName = ref<"energy" | "service" | "image">("energy");
const running = ref(false);
const selectedIdentifiers = ref<string[]>([]);
const searchResult = ref<any | null>(null);
const resolveOutput = ref<any | null>(null);
const resolveMode = ref<"metadata" | "object" | "component" | null>(null);
const searchForm = reactive({
  naturalQuery: "针对北京区域 1100 下与能源行业 energy 相关、重点为 dataset 类型的对象做检索和总结",
  mode: "HYBRID",
  strategy: "RECURSIVE",
  anchorNodeCode: currentNode.value.code,
  limit: 6,
  offset: 0,
  ttl: 4,
  maxFanout: 8,
  perNodeLimit: 20,
  globalDeadlineMs: 5000,
  hopTimeoutMs: 1500
});

const searchItems = computed(() => searchResult.value?.items ?? []);
const visitedNodes = computed(() => searchResult.value?.visitedNodeCodes ?? []);
const failedNodes = computed(() => searchResult.value?.failedNodes ?? []);
const allSelected = computed(() => searchItems.value.length > 0 && selectedIdentifiers.value.length === searchItems.value.length);
const modeLabel = computed(() => modeText(searchForm.mode));
const strategyLabel = computed(() => strategyText(searchForm.strategy));
const averageScore = computed(() => {
  const scores = searchItems.value
    .map((item: any) => Number(item.finalScore))
    .filter((score: number) => !Number.isNaN(score));
  if (!scores.length) {
    return 0;
  }
  return scores.reduce((sum: number, current: number) => sum + current, 0) / scores.length;
});
const resolveModeLabel = computed(() => {
  if (resolveMode.value === "metadata") {
    return "元数据";
  }
  if (resolveMode.value === "object") {
    return "数字对象";
  }
  if (resolveMode.value === "component") {
    return "资源组件";
  }
  return "未执行";
});
const resolveSummary = computed(() => {
  if (resolveOutput.value == null) {
    return { recordCount: 0, keyCount: 0 };
  }
  if (Array.isArray(resolveOutput.value)) {
    return { recordCount: resolveOutput.value.length, keyCount: 0 };
  }
  const items = extractResolveItems(resolveOutput.value);
  return {
    recordCount: items.length,
    keyCount: typeof resolveOutput.value === "object" ? Object.keys(resolveOutput.value).length : 0
  };
});
const resolveCards = computed<ResolveCard[]>(() => {
  const items = extractResolveItems(resolveOutput.value);
  return items.map((item: any) => buildResolveCard(item, resolveMode.value));
});

function modeText(value: string): string {
  if (value === "KEYWORD") {
    return "关键词匹配";
  }
  if (value === "SEMANTIC") {
    return "语义匹配";
  }
  return "混合匹配";
}

function strategyText(value: string): string {
  return value === "ITERATIVE" ? "迭代传播" : "递归传播";
}

function displayValue(value: unknown): string {
  if (value == null || value === "") {
    return "-";
  }
  return String(value);
}

function extractResolveItems(payload: any): any[] {
  if (!payload) {
    return [];
  }
  if (Array.isArray(payload)) {
    return payload;
  }
  if (Array.isArray(payload.items)) {
    return payload.items;
  }
  if (Array.isArray(payload.records)) {
    return payload.records;
  }
  if (Array.isArray(payload.data)) {
    return payload.data;
  }
  return [];
}

function buildResolveCard(item: any, kind: "metadata" | "object" | "component" | null): ResolveCard {
  const identifier = displayValue(item?.identifier);
  const nodeCode = item?.resolvedByNodeCode ? String(item.resolvedByNodeCode) : undefined;
  if (kind === "metadata") {
    const metadata = item?.metadata ?? item;
    return {
      identifier,
      title: metadata?.name || identifier,
      success: item?.success,
      nodeCode,
      fields: [
        { label: "行业", value: displayValue(metadata?.industry) },
        { label: "分类", value: displayValue(metadata?.dataCategory) },
        { label: "所有者", value: displayValue(metadata?.owner) },
        { label: "实体类型", value: displayValue(metadata?.entityType) },
        { label: "应用场景", value: displayValue(metadata?.applicationScenario) },
        { label: "描述", value: displayValue(metadata?.description) }
      ],
      raw: item
    };
  }
  if (kind === "object") {
    const object = item?.digitalObject ?? item;
    const metadata = object?.metadata;
    const resource = item?.resource ?? object?.resource;
    return {
      identifier,
      title: metadata?.name || identifier,
      success: item?.success,
      nodeCode,
      fields: [
        { label: "对象状态", value: displayValue(object?.status) },
        { label: "行业", value: displayValue(metadata?.industry) },
        { label: "分类", value: displayValue(metadata?.dataCategory) },
        { label: "资源类型", value: displayValue(resource?.componentType) },
        { label: "存储方式", value: displayValue(resource?.storageType) },
        { label: "资源定位", value: displayValue(resource?.locator), mono: true }
      ],
      raw: item
    };
  }
  if (kind === "component") {
    const resource = item?.resource ?? item?.component ?? item;
    return {
      identifier,
      title: resource?.componentType ? `${resource.componentType} 资源` : identifier,
      success: item?.success,
      nodeCode,
      fields: [
        { label: "资源类型", value: displayValue(resource?.componentType) },
        { label: "存储方式", value: displayValue(resource?.storageType) },
        { label: "访问方式", value: displayValue(resource?.accessMethod) },
        { label: "内容类型", value: displayValue(resource?.contentType) },
        { label: "格式", value: displayValue(resource?.format) },
        { label: "资源定位", value: displayValue(resource?.locator), mono: true }
      ],
      raw: item
    };
  }
  return {
    identifier,
    title: identifier,
    success: item?.success,
    nodeCode,
    fields: [
      { label: "说明", value: "当前响应结构未识别，保留原始响应查看。" }
    ],
    raw: item
  };
}

function applyPreset(type: "energy" | "service" | "image"): void {
  presetName.value = type;
  if (type === "energy") {
    searchForm.naturalQuery = "针对北京区域 1100 下与能源行业 energy 相关、重点为 dataset 类型的对象做检索和总结";
    searchForm.mode = "HYBRID";
    searchForm.strategy = "RECURSIVE";
  } else if (type === "service") {
    searchForm.naturalQuery = "查找与能源统计分析相关的 service 类型资源，优先返回可调用接口和节点分布";
    searchForm.mode = "KEYWORD";
    searchForm.strategy = "ITERATIVE";
  } else {
    searchForm.naturalQuery = "检索与设备巡检图片、图像采集和视觉监测相关的 image 类型资源";
    searchForm.mode = "HYBRID";
    searchForm.strategy = "RECURSIVE";
  }
}

function clearSearch(): void {
  searchResult.value = null;
  resolveOutput.value = null;
  resolveMode.value = null;
  selectedIdentifiers.value = [];
}

function toggleSelectAll(): void {
  selectedIdentifiers.value = allSelected.value ? [] : searchItems.value.map((item: any) => item.identifier).filter(Boolean);
}

function scorePillClass(score?: number): string {
  if (typeof score !== "number" || Number.isNaN(score)) {
    return "status-muted";
  }
  if (score >= 0.55) {
    return "status-good";
  }
  if (score >= 0.2) {
    return "status-warn";
  }
  return "status-muted";
}

function scoreText(score?: number): string {
  if (typeof score !== "number" || Number.isNaN(score)) {
    return "-";
  }
  return formatNumber(score, 3);
}

function scoreWidth(score?: number): string {
  if (typeof score !== "number" || Number.isNaN(score)) {
    return "6%";
  }
  const normalized = Math.max(0.06, Math.min(score, 1));
  return `${normalized * 100}%`;
}

async function runSearch(): Promise<void> {
  running.value = true;
  try {
    const response = await requestEnvelope<any>(`${currentBackendProxyBase.value}/api/v2/search/global`, {
      method: "POST",
      body: asJsonBody(searchForm)
    });
    searchResult.value = response.data;
    selectedIdentifiers.value = (response.data?.items ?? []).slice(0, 3).map((item: any) => item.identifier).filter(Boolean);
    resolveOutput.value = null;
    resolveMode.value = null;
    showToast({
      title: "Search",
      message: `全局搜索完成，返回 ${searchItems.value.length} 条结果。`,
      tone: "success"
    });
  } catch (error) {
    showToast({
      title: "Search",
      message: error instanceof Error ? error.message : String(error),
      tone: "error"
    });
    throw error;
  } finally {
    running.value = false;
  }
}

async function batchResolve(kind: "metadata" | "object" | "component"): Promise<void> {
  if (!selectedIdentifiers.value.length) {
    return;
  }
  running.value = true;
  try {
    const endpoint = kind === "metadata"
      ? "/api/v2/identifiers/resolve/batch/distributed"
      : kind === "object"
        ? "/api/v2/identifiers/resolve/batch/object/distributed"
        : "/api/v2/identifiers/resolve/batch/component/distributed";
    const response = await requestEnvelope<any>(`${currentBackendProxyBase.value}${endpoint}`, {
      method: "POST",
      body: asJsonBody(selectedIdentifiers.value)
    });
    resolveMode.value = kind;
    resolveOutput.value = response.data;
    showToast({
      title: "Resolve",
      message: `批量${kind === "metadata" ? "元数据" : kind === "object" ? "数字对象" : "资源"}解析完成。`,
      tone: "success"
    });
  } catch (error) {
    showToast({
      title: "Resolve",
      message: error instanceof Error ? error.message : String(error),
      tone: "error"
    });
    throw error;
  } finally {
    running.value = false;
  }
}

watch(() => currentNode.value.code, () => {
  searchForm.anchorNodeCode = currentNode.value.code;
});
</script>
