<template>
  <div class="page-shell">
    <section class="page-hero panel">
      <div>
        <p class="eyebrow">Overview</p>
        <h2>系统总览</h2>
        <p class="panel-caption">先看节点图、路由和服务状态，再跳转到具体业务页。</p>
      </div>
      <div class="page-hero-actions">
        <button class="button ghost" type="button" :disabled="loading" @click="loadOverview">{{ loading ? '刷新中...' : '刷新总览' }}</button>
      </div>
    </section>

    <section class="summary-grid compact-summary-grid">
      <article class="summary-card">
        <span class="summary-label">当前节点</span>
        <strong class="summary-value">{{ currentNode.label }}</strong>
        <small>{{ currentNode.code }} / {{ currentNode.level }}</small>
      </article>
      <article class="summary-card">
        <span class="summary-label">直连节点数</span>
        <strong class="summary-value">{{ statuses.length }}</strong>
        <small>当前节点相邻节点状态</small>
      </article>
      <article class="summary-card">
        <span class="summary-label">路由记录数</span>
        <strong class="summary-value">{{ routes.length }}</strong>
        <small>当前节点本地路由表快照</small>
      </article>
      <article class="summary-card">
        <span class="summary-label">服务状态</span>
        <strong class="summary-value">{{ serviceHealthLabel }}</strong>
        <small>MCP / 编排 / 认证公共检查</small>
      </article>
    </section>

    <section class="panel page-card graph-card">
      <div class="panel-head">
        <div>
          <p class="eyebrow">Graph</p>
          <h3>实验节点图</h3>
        </div>
      </div>
      <NodeTopologyGraph :nodes="nodeOptions" :current-node-code="currentNode.code" :status-items="statuses" :route-items="routes" />
      <div class="metrics-strip graph-legend">
        <div class="metric-chip"><span>当前选中</span><strong>{{ currentNode.code }}</strong></div>
        <div class="metric-chip"><span>直连在线</span><strong>{{ statuses.filter((item) => item.online).length }}</strong></div>
        <div class="metric-chip"><span>路由覆盖</span><strong>{{ routes.length }}</strong></div>
      </div>
    </section>

    <section class="results-grid two-column-grid">
      <article class="panel page-card">
        <div class="panel-head">
          <div>
            <p class="eyebrow">Health</p>
            <h3>公共服务状态</h3>
          </div>
        </div>
        <div class="entity-list">
          <div class="entity-list-item">
            <div>
              <strong>MCP</strong>
              <p class="field-help mono">{{ state.mcpUrl }}</p>
            </div>
            <span class="status-pill" :class="mcpHealth ? 'status-good' : 'status-warn'">{{ mcpHealth ? '可达' : '未知' }}</span>
          </div>
          <div class="entity-list-item">
            <div>
              <strong>编排服务</strong>
              <p class="field-help mono">{{ state.orchestratorUrl }}</p>
            </div>
            <span class="status-pill" :class="orchestratorHealth ? 'status-good' : 'status-warn'">{{ orchestratorHealth ? '可达' : '未知' }}</span>
          </div>
          <div class="entity-list-item">
            <div>
              <strong>认证中心</strong>
              <p class="field-help mono">{{ state.authBaseUrl }}</p>
            </div>
            <span class="status-pill" :class="authPublicKey ? 'status-good' : 'status-warn'">{{ authPublicKey ? '可达' : '未知' }}</span>
          </div>
        </div>
      </article>

      <article class="panel page-card">
        <div class="panel-head">
          <div>
            <p class="eyebrow">Status</p>
            <h3>直连节点状态</h3>
          </div>
        </div>
        <div v-if="statuses.length" class="table-wrap">
          <table class="data-table">
            <thead>
              <tr>
                <th>节点</th>
                <th>状态</th>
                <th>最后心跳</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in statuses" :key="item.nodeCode">
                <td>{{ item.nodeName }}<br /><small>{{ item.nodeCode }}</small></td>
                <td>{{ item.relationStatus }} / {{ item.healthStatus }}</td>
                <td>{{ formatTime(item.lastSeenAt) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div v-else class="panel-empty">当前节点没有可展示的相邻节点状态。</div>
      </article>
    </section>

    <section class="panel page-card">
      <div class="panel-head">
        <div>
          <p class="eyebrow">Routes</p>
          <h3>路由与指标</h3>
        </div>
      </div>
      <div class="metrics-strip">
        <div class="metric-chip">
          <span>路由数</span>
          <strong>{{ routes.length }}</strong>
        </div>
        <div class="metric-chip">
          <span>ops 模块</span>
          <strong>{{ metrics ? Object.keys(metrics).length : 0 }}</strong>
        </div>
      </div>
      <pre class="json-view">{{ prettyJson(metrics) }}</pre>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";

import NodeTopologyGraph from "../../shared/components/NodeTopologyGraph.vue";
import { requestEnvelope, requestJson } from "../../shared/api/http";
import { useConsoleRuntime } from "../../shared/composables/useConsoleRuntime";
import { formatTime, prettyJson } from "../../shared/utils/format";

const { currentNode, currentBackendProxyBase, nodeOptions, state, authProxyBase } = useConsoleRuntime();

const loading = ref(false);
const statuses = ref<any[]>([]);
const routes = ref<any[]>([]);
const metrics = ref<Record<string, Record<string, number>> | null>(null);
const mcpHealth = ref<any | null>(null);
const orchestratorHealth = ref<any | null>(null);
const authPublicKey = ref<any | null>(null);

const serviceHealthLabel = computed(() => {
  const okCount = [mcpHealth.value, orchestratorHealth.value, authPublicKey.value].filter(Boolean).length;
  return `${okCount}/3 可达`;
});

async function loadOverview(): Promise<void> {
  loading.value = true;
  try {
    const [statusResp, routeResp, metricResp, mcpResp, orchestratorResp, authResp] = await Promise.all([
      requestEnvelope<any[]>(`${currentBackendProxyBase.value}/api/v2/node/adjacent/status`),
      requestEnvelope<any[]>(`${currentBackendProxyBase.value}/api/v2/node/routes`),
      requestEnvelope<Record<string, Record<string, number>>>(`${currentBackendProxyBase.value}/api/v2/ops/metrics`),
      requestJson<any>(`${new URL("/health", state.mcpUrl.replace(/\/mcp$/, "/")).toString()}`),
      requestJson<any>(`${state.orchestratorUrl.replace(/\/$/, "")}/health`),
      requestEnvelope<any>(`${authProxyBase}/auth/public-key`)
    ]);
    statuses.value = statusResp.data ?? [];
    routes.value = routeResp.data ?? [];
    metrics.value = metricResp.data ?? null;
    mcpHealth.value = mcpResp;
    orchestratorHealth.value = orchestratorResp;
    authPublicKey.value = authResp.data ?? null;
  } finally {
    loading.value = false;
  }
}

watch(() => currentNode.value.code, () => {
  void loadOverview();
});

onMounted(() => {
  void loadOverview();
});
</script>


