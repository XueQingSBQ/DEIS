<template>
  <div class="page-shell">
    <section class="page-hero panel">
      <div>
        <p class="eyebrow">Search + Resolve</p>
        <h2>跨节点搜索与批量解析</h2>
        <p class="panel-caption">
          将当前节点精准检索与全局联邦检索拆开，便于同时做业务演示和检索效果验证。
        </p>
      </div>
      <div class="page-hero-actions page-toolbar">
        <button class="button ghost" type="button" @click="applyPreset('notice')">通知公告</button>
        <button class="button ghost" type="button" @click="applyPreset('case')">地方案例</button>
        <button class="button ghost" type="button" @click="applyPreset('standard')">规范文件</button>
        <button class="button warm" type="button" :disabled="running" @click="runSearch">
          {{ running ? "检索中..." : runButtonLabel }}
        </button>
      </div>
    </section>

    <section class="summary-grid compact-summary-grid">
      <article class="summary-card">
        <span class="summary-label">当前节点</span>
        <strong class="summary-value">{{ currentNode.label }}</strong>
        <small class="mono">{{ currentNode.code }}</small>
      </article>
      <article class="summary-card">
        <span class="summary-label">检索范围</span>
        <strong class="summary-value">{{ searchScopeLabel }}</strong>
        <small>{{ modeLabel }}<template v-if="isGlobalScope"> / {{ strategyLabel }}</template></small>
      </article>
      <article class="summary-card">
        <span class="summary-label">命中结果</span>
        <strong class="summary-value">{{ searchItems.length }}</strong>
        <small>访问 {{ visitedNodes.length }} / 失败 {{ failedNodes.length }}</small>
      </article>
      <article class="summary-card">
        <span class="summary-label">已选标识</span>
        <strong class="summary-value">{{ selectedIdentifiers.length }}</strong>
        <small>{{ resolveCards.length ? `已返回 ${resolveCards.length} 条解析结果` : "可继续执行批量解析" }}</small>
      </article>
    </section>

    <section class="results-grid search-workspace-grid">
      <article class="panel page-card search-workbench-card">
        <div class="panel-head">
          <div>
            <p class="eyebrow">Workbench</p>
            <h3>检索工作台</h3>
          </div>
          <span class="status-pill" :class="running ? 'status-warn' : 'status-good'">
            {{ running ? "执行中" : "就绪" }}
          </span>
        </div>

        <div class="tool-cloud compact-cloud">
          <button
            class="tool-pill"
            :class="searchScope === 'local' ? 'tool-pill-active' : ''"
            type="button"
            @click="setSearchScope('local')"
          >
            当前节点精准检索
          </button>
          <button
            class="tool-pill"
            :class="searchScope === 'global' ? 'tool-pill-active' : ''"
            type="button"
            @click="setSearchScope('global')"
          >
            全局联邦检索
          </button>
          <button
            class="tool-pill"
            :class="presetName === 'notice' ? 'tool-pill-active' : ''"
            type="button"
            @click="applyPreset('notice')"
          >
            通知公告
          </button>
          <button
            class="tool-pill"
            :class="presetName === 'case' ? 'tool-pill-active' : ''"
            type="button"
            @click="applyPreset('case')"
          >
            地方案例
          </button>
          <button
            class="tool-pill"
            :class="presetName === 'standard' ? 'tool-pill-active' : ''"
            type="button"
            @click="applyPreset('standard')"
          >
            规范文件
          </button>
        </div>

        <label class="field-block field-block-wide">
          <span class="field-label">自然语言问题</span>
          <textarea
            v-model.trim="searchForm.naturalQuery"
            class="text-area search-query-input"
            rows="4"
            placeholder="例如：检索与新干县山洪沟治理相关的地方案例和建设进展"
          ></textarea>
        </label>

        <label class="field-block field-block-wide">
          <span class="field-label">关键词（空格分隔，建议填写）</span>
          <input
            v-model.trim="searchForm.keywordText"
            class="text-input"
            type="text"
            placeholder="例如：新干县 山洪沟治理 地方案例"
          />
        </label>

        <div class="search-primary-grid">
          <label class="field-block">
            <span class="field-label">检索模式</span>
            <select v-model="searchForm.mode" class="select-input">
              <option value="KEYWORD">关键词</option>
              <option value="HYBRID">混合</option>
              <option value="SEMANTIC">语义</option>
            </select>
          </label>

          <label class="field-block" v-if="isGlobalScope">
            <span class="field-label">锚点节点</span>
            <select v-model="searchForm.anchorNodeCode" class="select-input">
              <option v-for="node in nodeOptions" :key="node.code" :value="node.code">
                {{ node.label }}（{{ node.code }}）
              </option>
            </select>
          </label>

          <label class="field-block" v-else>
            <span class="field-label">执行节点</span>
            <input :value="`${currentNode.label}（${currentNode.code}）`" class="text-input" type="text" readonly />
          </label>
        </div>

        <details class="control-disclosure search-advanced-fold">
          <summary>
            <span>高级参数</span>
            <span class="disclosure-meta">
              <template v-if="isGlobalScope">每节点上限、TTL、链路超时</template>
              <template v-else>本地返回上限与偏移</template>
            </span>
          </summary>
          <div class="disclosure-body">
            <div class="form-grid">
              <label class="field-block">
                <span class="field-label">limit</span>
                <input v-model.number="searchForm.limit" class="text-input mono" type="number" min="1" max="50" />
              </label>
              <label class="field-block">
                <span class="field-label">offset</span>
                <input v-model.number="searchForm.offset" class="text-input mono" type="number" min="0" />
              </label>
              <label class="field-block" v-if="isGlobalScope">
                <span class="field-label">perNodeLimit</span>
                <input v-model.number="searchForm.perNodeLimit" class="text-input mono" type="number" min="1" max="200" />
              </label>
              <label class="field-block" v-if="isGlobalScope">
                <span class="field-label">ttl</span>
                <input v-model.number="searchForm.ttl" class="text-input mono" type="number" min="1" max="16" />
              </label>
              <label class="field-block" v-if="isGlobalScope">
                <span class="field-label">hopTimeoutMs</span>
                <input v-model.number="searchForm.hopTimeoutMs" class="text-input mono" type="number" min="100" />
              </label>
              <label class="field-block" v-if="isGlobalScope">
                <span class="field-label">globalDeadlineMs</span>
                <input v-model.number="searchForm.globalDeadlineMs" class="text-input mono" type="number" min="100" />
              </label>
            </div>
          </div>
        </details>

        <div class="action-row compact-actions">
          <button class="button warm" type="button" :disabled="running" @click="runSearch">
            {{ running ? "检索中..." : runButtonLabel }}
          </button>
          <button class="button ghost" type="button" :disabled="!searchItems.length" @click="clearSearch">清空结果</button>
        </div>
      </article>

      <article class="panel page-card search-trace-card">
        <div class="panel-head">
          <div>
            <p class="eyebrow">Trace</p>
            <h3>链路摘要</h3>
          </div>
          <span class="status-pill status-muted mono">{{ traceLabel }}</span>
        </div>

        <div class="search-brief-grid">
          <div class="hint-card">
            <div class="hint-card-head">
              <strong>查询概况</strong>
              <span class="status-pill status-muted">{{ searchScopeLabel }}</span>
            </div>
            <div class="kv-grid">
              <div class="kv-item">
                <span>当前节点</span>
                <strong>{{ currentNode.label }}</strong>
              </div>
              <div class="kv-item">
                <span>模式</span>
                <strong>{{ modeLabel }}</strong>
              </div>
              <div class="kv-item" v-if="isGlobalScope">
                <span>传播策略</span>
                <strong>{{ strategyLabel }}</strong>
              </div>
              <div class="kv-item">
                <span>返回上限</span>
                <strong>{{ searchForm.limit }}</strong>
              </div>
            </div>
          </div>

          <div class="hint-card">
            <div class="hint-card-head">
              <strong>访问路径</strong>
              <span class="status-pill" :class="failedNodes.length ? 'status-warn' : 'status-good'">
                {{ failedNodes.length ? "存在失败节点" : "链路正常" }}
              </span>
            </div>
            <div v-if="visitedNodes.length" class="search-node-flow">
              <span v-for="node in visitedNodes" :key="node" class="status-pill status-good mono">{{ node }}</span>
            </div>
            <div v-else class="panel-empty compact-empty">执行检索后显示访问路径。</div>
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
          <strong>检索建议</strong>
          <ul>
            <li v-if="!searchItems.length">没有命中时，优先缩短问题描述，并在关键词框里填入 2 到 4 个核心词。</li>
            <li v-else-if="searchScope === 'global'">全局模式展示的是跨节点融合结果，请同时关注“关键词分”和“融合分”。</li>
            <li v-else>当前是本地精准检索，优先以“关键词分”和“本地分”判断结果是否足够准确。</li>
            <li>做答辩演示时，建议先本地精准检索，再切全局联邦检索，故事线更清楚。</li>
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
            <button class="button ghost" type="button" :disabled="!searchItems.length" @click="toggleSelectAll">
              {{ allSelected ? "取消全选" : "全选结果" }}
            </button>
            <button class="button ghost" type="button" :disabled="!selectedIdentifiers.length || running" @click="batchResolve('metadata')">
              批量元数据解析
            </button>
            <button class="button ghost" type="button" :disabled="!selectedIdentifiers.length || running" @click="batchResolve('object')">
              批量数字对象解析
            </button>
            <button class="button ghost" type="button" :disabled="!selectedIdentifiers.length || running" @click="batchResolve('component')">
              批量资源解析
            </button>
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
                  <span class="status-pill" :class="scorePillClass(primaryScoreValue(item))">
                    #{{ Number(index) + 1 }} / {{ primaryScoreText(item) }}
                  </span>
                </div>
                <p class="field-help mono">{{ item.identifier }}</p>
              </div>
            </div>

            <div class="search-score-bar">
              <span class="search-score-bar-fill" :style="{ width: scoreWidth(progressScore(item)) }"></span>
            </div>

            <p class="field-help">{{ item.description || "暂无描述，建议继续执行元数据解析。" }}</p>

            <div class="metrics-strip compact-chip-strip">
              <div class="metric-chip"><span>来源</span><strong>{{ item.source || "-" }}</strong></div>
              <div class="metric-chip"><span>行业</span><strong>{{ item.industry || "-" }}</strong></div>
              <div class="metric-chip"><span>分类</span><strong>{{ item.dataCategory || "-" }}</strong></div>
              <div class="metric-chip"><span>关键词分</span><strong>{{ scoreNumberText(item.keywordScore) }}</strong></div>
              <div class="metric-chip"><span>本地分</span><strong>{{ scoreNumberText(item.localScore) }}</strong></div>
              <div class="metric-chip" v-if="isGlobalScope"><span>融合分</span><strong>{{ scoreNumberText(item.finalScore) }}</strong></div>
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
        <div v-else class="panel-empty">
          先执行一次检索。若结果为 0，优先缩短问题描述，并在关键词框中补充更明确的词。
        </div>
      </article>

      <article class="panel page-card">
        <div class="panel-head">
          <div>
            <p class="eyebrow">Resolve</p>
            <h3>批量解析结果</h3>
          </div>
          <span class="status-pill" :class="resolveCards.length ? 'status-good' : 'status-muted'">
            {{ resolveCards.length ? "已返回" : "未执行" }}
          </span>
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
                <span class="status-pill" :class="card.success === false ? 'status-warn' : 'status-good'">
                  {{ card.success === false ? "失败" : "成功" }}
                </span>
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

type SearchScope = "local" | "global";
type PresetName = "notice" | "case" | "standard";

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

const DEFAULT_FIELDS = ["name", "description", "applicationScenario", "additionalAttributes"];

const { currentBackendProxyBase, currentNode, nodeOptions } = useConsoleRuntime();

const presetName = ref<PresetName>("case");
const searchScope = ref<SearchScope>("local");
const running = ref(false);
const selectedIdentifiers = ref<string[]>([]);
const searchResult = ref<any | null>(null);
const resolveOutput = ref<any | null>(null);
const resolveMode = ref<"metadata" | "object" | "component" | null>(null);

const searchForm = reactive({
  naturalQuery: "检索与新干县山洪沟治理相关的地方案例和建设进展",
  keywordText: "新干县 山洪沟治理 地方案例",
  mode: "KEYWORD",
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

const isGlobalScope = computed(() => searchScope.value === "global");
const rootNodeCode = computed(() => nodeOptions.value.find((item) => item.level === "COUNTRY")?.code ?? currentNode.value.code);
const searchItems = computed(() => extractSearchItems(searchResult.value));
const visitedNodes = computed(() => {
  if (searchScope.value === "local") {
    return searchItems.value.length ? [currentNode.value.code] : [];
  }
  return Array.isArray(searchResult.value?.visitedNodeCodes) ? searchResult.value.visitedNodeCodes : [];
});
const failedNodes = computed(() => {
  if (searchScope.value === "local") {
    return [];
  }
  return Array.isArray(searchResult.value?.failedNodes) ? searchResult.value.failedNodes : [];
});
const allSelected = computed(() => searchItems.value.length > 0 && selectedIdentifiers.value.length === searchItems.value.length);
const modeLabel = computed(() => modeText(searchForm.mode));
const strategyLabel = computed(() => strategyText(searchForm.strategy));
const searchScopeLabel = computed(() => (searchScope.value === "local" ? "当前节点精准检索" : "全局联邦检索"));
const runButtonLabel = computed(() => (searchScope.value === "local" ? "执行当前节点精准检索" : "执行全局联邦检索"));
const traceLabel = computed(() => {
  if (searchScope.value === "local") {
    return `local@${currentNode.value.code}`;
  }
  return searchResult.value?.traceId || "未生成 traceId";
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
    return "关键词";
  }
  if (value === "SEMANTIC") {
    return "语义";
  }
  return "混合";
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

function extractSearchItems(payload: any): any[] {
  if (!payload) {
    return [];
  }
  if (Array.isArray(payload)) {
    return payload;
  }
  if (Array.isArray(payload.items)) {
    return payload.items;
  }
  if (Array.isArray(payload.data)) {
    return payload.data;
  }
  return [];
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
    fields: [{ label: "说明", value: "当前响应结构未识别，保留原始响应查看。" }],
    raw: item
  };
}

function setSearchScope(scope: SearchScope): void {
  searchScope.value = scope;
  if (scope === "local") {
    searchForm.anchorNodeCode = currentNode.value.code;
  } else {
    searchForm.anchorNodeCode = rootNodeCode.value;
  }
}

function applyPreset(type: PresetName): void {
  presetName.value = type;
  if (type === "notice") {
    searchForm.naturalQuery = "检索与通知公告、督导检查相关的山洪灾害防治文档";
    searchForm.keywordText = "山洪灾害 通知公告 督导检查";
    searchForm.mode = "KEYWORD";
    searchForm.strategy = "RECURSIVE";
  } else if (type === "case") {
    searchForm.naturalQuery = "检索与新干县山洪沟治理相关的地方案例和建设进展";
    searchForm.keywordText = "新干县 山洪沟治理 地方案例";
    searchForm.mode = "KEYWORD";
    searchForm.strategy = "ITERATIVE";
  } else {
    searchForm.naturalQuery = "检索与规范文件、设计导则、监测预警系统相关的标准文档";
    searchForm.keywordText = "规范文件 设计导则 监测预警";
    searchForm.mode = "KEYWORD";
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

function parseKeywords(keywordText: string, naturalQuery: string): string[] {
  const source = keywordText.trim() || naturalQuery.trim();
  if (!source) {
    return [];
  }
  const chunks = source
    .split(/[\s,，;；、|]+/)
    .map((item) => item.trim())
    .filter((item) => item.length > 0);
  return Array.from(new Set(chunks));
}

function buildSearchPayload(): Record<string, unknown> {
  const keywords = parseKeywords(searchForm.keywordText, searchForm.naturalQuery);
  return {
    naturalQuery: searchForm.naturalQuery,
    keywords,
    fields: DEFAULT_FIELDS,
    filters: {},
    limit: searchForm.limit,
    offset: searchForm.offset,
    mode: searchForm.mode,
    semanticWeight: searchForm.mode === "SEMANTIC" ? 1 : searchForm.mode === "HYBRID" ? 0.3 : 0
  };
}

function scorePillClass(score?: number): string {
  if (typeof score !== "number" || Number.isNaN(score) || score <= 0) {
    return "status-muted";
  }
  if (score >= 1) {
    return "status-good";
  }
  if (score >= 0.2) {
    return "status-warn";
  }
  return "status-muted";
}

function scoreNumberText(score?: number): string {
  if (typeof score !== "number" || Number.isNaN(score)) {
    return "-";
  }
  return formatNumber(score, 3);
}

function primaryScoreValue(item: any): number {
  const keywordScore = Number(item?.keywordScore);
  const localScore = Number(item?.localScore);
  const finalScore = Number(item?.finalScore);
  if (!Number.isNaN(keywordScore) && keywordScore > 0) {
    return keywordScore;
  }
  if (!Number.isNaN(localScore) && localScore > 0) {
    return localScore;
  }
  if (!Number.isNaN(finalScore) && finalScore > 0) {
    return finalScore;
  }
  return 0;
}

function primaryScoreText(item: any): string {
  if (isGlobalScope.value) {
    const keywordScore = Number(item?.keywordScore);
    if (!Number.isNaN(keywordScore) && keywordScore > 0) {
      return `关键词 ${scoreNumberText(keywordScore)}`;
    }
    return `融合 ${scoreNumberText(item?.finalScore)}`;
  }
  return `相关 ${scoreNumberText(item?.keywordScore || item?.localScore || item?.finalScore)}`;
}

function progressScore(item: any): number {
  const localScore = Number(item?.localScore);
  if (!Number.isNaN(localScore) && localScore > 0) {
    return Math.max(0.06, Math.min(localScore, 1));
  }
  const finalScore = Number(item?.finalScore);
  if (!Number.isNaN(finalScore) && finalScore > 0) {
    return Math.max(0.06, Math.min(finalScore, 1));
  }
  const keywordScore = Number(item?.keywordScore);
  if (!Number.isNaN(keywordScore) && keywordScore > 0) {
    return Math.max(0.06, Math.min(Math.log10(keywordScore + 1) / 2, 1));
  }
  return 0.06;
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
    const payload = buildSearchPayload();
    if (searchScope.value === "local") {
      const response = await requestEnvelope<any>(`${currentBackendProxyBase.value}/api/v2/search`, {
        method: "POST",
        body: asJsonBody(payload)
      });
      searchResult.value = response.data;
      selectedIdentifiers.value = extractSearchItems(response.data).slice(0, 3).map((item: any) => item.identifier).filter(Boolean);
      showToast({
        title: "Search",
        message: `当前节点精准检索完成，返回 ${searchItems.value.length} 条结果。`,
        tone: "success"
      });
    } else {
      const response = await requestEnvelope<any>(`${currentBackendProxyBase.value}/api/v2/search/global`, {
        method: "POST",
        body: asJsonBody({
          ...payload,
          strategy: searchForm.strategy,
          anchorNodeCode: searchForm.anchorNodeCode,
          ttl: searchForm.ttl,
          maxFanout: searchForm.maxFanout,
          perNodeLimit: searchForm.perNodeLimit,
          globalDeadlineMs: searchForm.globalDeadlineMs,
          hopTimeoutMs: searchForm.hopTimeoutMs
        })
      });
      searchResult.value = response.data;
      selectedIdentifiers.value = extractSearchItems(response.data).slice(0, 3).map((item: any) => item.identifier).filter(Boolean);
      showToast({
        title: "Search",
        message: `全局联邦检索完成，返回 ${searchItems.value.length} 条结果。`,
        tone: "success"
      });
    }
    resolveOutput.value = null;
    resolveMode.value = null;
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
  if (searchScope.value === "local") {
    searchForm.anchorNodeCode = currentNode.value.code;
  }
});
</script>
