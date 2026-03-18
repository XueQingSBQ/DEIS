<template>
  <div class="page-shell">
    <section class="page-hero panel">
      <div>
        <p class="eyebrow">Audit Center</p>
        <h2>业务审计中心</h2>
        <p class="panel-caption">按标识、操作类型和时间区间回溯业务行为，适合展示资源接入、绑定、删除和跨节点治理过程。</p>
      </div>
      <div class="page-hero-actions page-toolbar">
        <button class="button ghost" type="button" @click="applyTimePreset('24h')">最近 24 小时</button>
        <button class="button ghost" type="button" @click="applyTimePreset('7d')">最近 7 天</button>
        <button class="button ghost" type="button" @click="applyTimePreset('30d')">最近 30 天</button>
        <button class="button warm" type="button" :disabled="loading" @click="queryAudit">{{ loading ? "查询中..." : "查询审计" }}</button>
      </div>
    </section>

    <section class="summary-grid compact-summary-grid">
      <article class="summary-card">
        <span class="summary-label">总记录数</span>
        <strong class="summary-value">{{ auditPage?.total ?? 0 }}</strong>
        <small>当前筛选条件命中的总数</small>
      </article>
      <article class="summary-card">
        <span class="summary-label">当前页</span>
        <strong class="summary-value">{{ auditPage?.page ?? queryForm.page }}</strong>
        <small>每页 {{ queryForm.size }} 条</small>
      </article>
      <article class="summary-card">
        <span class="summary-label">成功记录</span>
        <strong class="summary-value">{{ successCount }}</strong>
        <small>当前页 SUCCESS 数量</small>
      </article>
      <article class="summary-card">
        <span class="summary-label">失败记录</span>
        <strong class="summary-value">{{ failureCount }}</strong>
        <small>{{ auditPage?.hasNext ? "后续仍有分页" : "当前已到最后一页" }}</small>
      </article>
    </section>

    <section class="results-grid audit-workspace-grid">
      <article class="panel page-card audit-filter-card">
        <div class="panel-head">
          <div>
            <p class="eyebrow">Filter</p>
            <h3>查询条件</h3>
          </div>
          <span class="status-pill" :class="loading ? 'status-warn' : 'status-good'">{{ loading ? "执行中" : "就绪" }}</span>
        </div>

        <div class="tool-cloud compact-cloud">
          <button
            v-for="option in operationOptions"
            :key="option.value || 'all'"
            class="tool-pill"
            :class="queryForm.operation === option.value ? 'tool-pill-active' : ''"
            type="button"
            @click="applyOperationPreset(option.value)"
          >
            {{ option.label }}
          </button>
        </div>

        <div class="form-grid audit-filter-grid">
          <label class="field-block field-block-wide">
            <span class="field-label">标识 identifier</span>
            <input v-model.trim="queryForm.identifier" class="text-input mono" type="text" placeholder="输入完整标识，可为空" />
          </label>

          <label class="field-block">
            <span class="field-label">操作类型</span>
            <select v-model="queryForm.operation" class="select-input">
              <option v-for="option in operationOptions" :key="option.value || 'select-all'" :value="option.value">
                {{ option.label }}
              </option>
            </select>
          </label>

          <label class="field-block">
            <span class="field-label">每页条数</span>
            <input v-model.number="queryForm.size" class="text-input mono" type="number" min="1" />
          </label>

          <label class="field-block">
            <span class="field-label">开始时间</span>
            <input v-model.trim="queryForm.startTime" class="text-input mono" type="text" placeholder="2026-03-01T00:00:00Z" />
          </label>

          <label class="field-block">
            <span class="field-label">结束时间</span>
            <input v-model.trim="queryForm.endTime" class="text-input mono" type="text" placeholder="2026-03-12T23:59:59Z" />
          </label>
        </div>

        <div class="audit-toolbar-inline">
          <button class="button warm" type="button" :disabled="loading" @click="queryAudit">{{ loading ? "查询中..." : "执行查询" }}</button>
          <button class="button ghost" type="button" :disabled="loading || queryForm.page <= 1" @click="goPrevPage">上一页</button>
          <button class="button ghost" type="button" :disabled="loading || !auditPage?.hasNext" @click="goNextPage">下一页</button>
          <button class="button ghost" type="button" :disabled="loading" @click="resetFilters">重置条件</button>
        </div>
      </article>

      <article class="panel page-card audit-overview-card">
        <div class="panel-head">
          <div>
            <p class="eyebrow">Overview</p>
            <h3>查询概况</h3>
          </div>
        </div>

        <div class="hint-card">
          <div class="hint-card-head">
            <strong>当前筛选快照</strong>
            <span class="status-pill status-muted">{{ currentOperationLabel }}</span>
          </div>
          <div class="kv-grid">
            <div class="kv-item">
              <span>目标标识</span>
              <strong class="mono-wrap">{{ queryForm.identifier || "全部标识" }}</strong>
            </div>
            <div class="kv-item">
              <span>时间区间</span>
              <strong>{{ timeRangeLabel }}</strong>
            </div>
            <div class="kv-item">
              <span>当前页码</span>
              <strong>{{ queryForm.page }}</strong>
            </div>
            <div class="kv-item">
              <span>分页状态</span>
              <strong>{{ auditPage?.hasNext ? "存在下一页" : "已是最后一页" }}</strong>
            </div>
          </div>
        </div>

        <div class="hint-card">
          <div class="hint-card-head">
            <strong>结果信号</strong>
            <span class="status-pill" :class="failureCount ? 'status-warn' : 'status-good'">{{ failureCount ? "存在失败" : "全部成功" }}</span>
          </div>
          <div class="chip-grid">
            <span class="status-pill status-good">SUCCESS {{ successCount }}</span>
            <span class="status-pill status-warn">FAIL {{ failureCount }}</span>
            <span class="status-pill status-muted">记录 {{ records.length }}</span>
          </div>
        </div>

        <div class="audit-tip-board">
          <strong>审计阅读建议</strong>
          <ul>
            <li v-if="!records.length">先做一次标识注册、资源绑定或删除操作，再回到本页查询，更容易看到完整链路。</li>
            <li v-else-if="failureCount">当前页存在失败记录，优先查看 FAIL 事件的 message、actor 和时间戳。</li>
            <li v-else>当前页审计记录全部成功，可用于展示系统主链路执行情况。</li>
            <li>如果要答辩演示，推荐用同一标识串联 REGISTER、UPLOAD_COMPLETE、BIND_COMPONENT、DELETE_IDENTIFIER 等操作。</li>
          </ul>
        </div>
      </article>
    </section>

    <section class="results-grid audit-result-grid">
      <article class="panel page-card">
        <div class="panel-head">
          <div>
            <p class="eyebrow">Timeline</p>
            <h3>审计时间线</h3>
          </div>
        </div>

        <div v-if="records.length" class="audit-timeline-list">
          <button
            v-for="record in records"
            :key="record.id"
            type="button"
            class="audit-event-card"
            :class="selectedRecord?.id === record.id ? 'audit-event-card-active' : ''"
            @click="selectedRecord = record"
          >
            <div class="audit-event-head">
              <div>
                <strong>{{ record.operation }}</strong>
                <p class="field-help mono">{{ record.identifier }}</p>
              </div>
              <span class="status-pill" :class="record.success ? 'status-good' : 'status-warn'">{{ record.success ? "SUCCESS" : "FAIL" }}</span>
            </div>
            <div class="kv-grid audit-event-kv">
              <div class="kv-item">
                <span>操作人</span>
                <strong>{{ record.actorUserId || "-" }}</strong>
              </div>
              <div class="kv-item">
                <span>角色</span>
                <strong>{{ record.actorRole || "-" }}</strong>
              </div>
              <div class="kv-item">
                <span>时间</span>
                <strong>{{ formatTime(record.createdAt) }}</strong>
              </div>
              <div class="kv-item">
                <span>记录 ID</span>
                <strong class="mono-wrap">{{ record.id }}</strong>
              </div>
            </div>
            <p class="field-help">{{ record.message || "无附加说明" }}</p>
          </button>
        </div>
        <div v-else class="panel-empty">暂无审计记录。可以先执行接入、绑定、删除等操作，再回到此页查询。</div>
      </article>

      <article class="panel page-card">
        <div class="panel-head">
          <div>
            <p class="eyebrow">Detail</p>
            <h3>选中记录详情</h3>
          </div>
        </div>

        <div v-if="selectedRecord" class="audit-detail-grid">
          <div class="hint-card">
            <div class="hint-card-head">
              <strong>基础信息</strong>
              <span class="status-pill" :class="selectedRecord.success ? 'status-good' : 'status-warn'">
                {{ selectedRecord.success ? "SUCCESS" : "FAIL" }}
              </span>
            </div>
            <div class="kv-grid">
              <div class="kv-item">
                <span>标识</span>
                <strong class="mono-wrap">{{ selectedRecord.identifier }}</strong>
              </div>
              <div class="kv-item">
                <span>操作类型</span>
                <strong>{{ selectedRecord.operation }}</strong>
              </div>
              <div class="kv-item">
                <span>操作人</span>
                <strong>{{ selectedRecord.actorUserId || "-" }}</strong>
              </div>
              <div class="kv-item">
                <span>角色</span>
                <strong>{{ selectedRecord.actorRole || "-" }}</strong>
              </div>
              <div class="kv-item">
                <span>创建时间</span>
                <strong>{{ formatTime(selectedRecord.createdAt) }}</strong>
              </div>
              <div class="kv-item">
                <span>记录 ID</span>
                <strong class="mono-wrap">{{ selectedRecord.id }}</strong>
              </div>
            </div>
          </div>

          <div class="hint-card">
            <div class="hint-card-head">
              <strong>审计说明</strong>
              <span class="status-pill status-muted">{{ selectedRecord.message ? "已记录" : "无说明" }}</span>
            </div>
            <p class="panel-caption">{{ selectedRecord.message || "当前记录没有附加 message，可直接查看原始 JSON。" }}</p>
          </div>

          <details class="management-raw-fold">
            <summary>查看原始审计记录</summary>
            <pre class="json-view management-json-view">{{ prettyJson(selectedRecord) }}</pre>
          </details>
        </div>
        <div v-else class="panel-empty">查询后点击左侧任意一条审计事件，这里会显示详情。</div>
      </article>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from "vue";

import { buildQuery, requestEnvelope } from "../../shared/api/http";
import { showToast } from "../../shared/feedback/toast";
import { useConsoleRuntime } from "../../shared/composables/useConsoleRuntime";
import { formatTime, prettyJson } from "../../shared/utils/format";

const { currentBackendProxyBase } = useConsoleRuntime();

const operationOptions = [
  { value: "", label: "全部操作" },
  { value: "REGISTER", label: "REGISTER" },
  { value: "UPLOAD_COMPLETE", label: "UPLOAD_COMPLETE" },
  { value: "BIND_COMPONENT", label: "BIND_COMPONENT" },
  { value: "DELETE_IDENTIFIER", label: "DELETE_IDENTIFIER" },
  { value: "UPDATE_METADATA", label: "UPDATE_METADATA" }
];

const loading = ref(false);
const auditPage = ref<any | null>(null);
const selectedRecord = ref<any | null>(null);
const queryForm = reactive({
  identifier: "",
  operation: "",
  startTime: "",
  endTime: "",
  page: 1,
  size: 10
});

const records = computed(() => auditPage.value?.records ?? []);
const successCount = computed(() => records.value.filter((item: any) => item.success).length);
const failureCount = computed(() => records.value.filter((item: any) => !item.success).length);
const currentOperationLabel = computed(() => operationOptions.find((item) => item.value === queryForm.operation)?.label ?? "全部操作");
const timeRangeLabel = computed(() => {
  if (queryForm.startTime && queryForm.endTime) {
    return `${queryForm.startTime} ~ ${queryForm.endTime}`;
  }
  if (queryForm.startTime) {
    return `${queryForm.startTime} 起`;
  }
  if (queryForm.endTime) {
    return `截止 ${queryForm.endTime}`;
  }
  return "未限定";
});

function applyOperationPreset(operation: string): void {
  queryForm.operation = operation;
  queryForm.page = 1;
}

function applyTimePreset(preset: "24h" | "7d" | "30d"): void {
  const now = new Date();
  const start = new Date(now);
  if (preset === "24h") {
    start.setHours(start.getHours() - 24);
  } else if (preset === "7d") {
    start.setDate(start.getDate() - 7);
  } else {
    start.setDate(start.getDate() - 30);
  }
  queryForm.startTime = start.toISOString();
  queryForm.endTime = now.toISOString();
  queryForm.page = 1;
}

function resetFilters(): void {
  queryForm.identifier = "";
  queryForm.operation = "";
  queryForm.startTime = "";
  queryForm.endTime = "";
  queryForm.page = 1;
  queryForm.size = 10;
}

async function queryAudit(): Promise<void> {
  loading.value = true;
  try {
    const query = buildQuery(queryForm);
    const response = await requestEnvelope<any>(`${currentBackendProxyBase.value}/api/v2/audit/identifier-history${query}`);
    auditPage.value = response.data;
    selectedRecord.value = response.data?.records?.[0] ?? null;
    showToast({
      title: "Audit",
      message: `审计查询完成，当前页 ${response.data?.records?.length ?? 0} 条记录。`,
      tone: "success"
    });
  } catch (error) {
    showToast({
      title: "Audit",
      message: error instanceof Error ? error.message : String(error),
      tone: "error"
    });
    throw error;
  } finally {
    loading.value = false;
  }
}

async function goPrevPage(): Promise<void> {
  if (queryForm.page <= 1) {
    return;
  }
  queryForm.page -= 1;
  await queryAudit();
}

async function goNextPage(): Promise<void> {
  if (!auditPage.value?.hasNext) {
    return;
  }
  queryForm.page += 1;
  await queryAudit();
}
</script>
