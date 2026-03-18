<template>
  <div class="page-shell">
    <section class="page-hero panel">
      <div>
        <p class="eyebrow">MCP</p>
        <h2>MCP 工具联调台</h2>
        <p class="panel-caption">基于浏览器侧 MCP Client 做连接、健康检查、工具选择、参数模板填充和结果查看。</p>
      </div>
      <div class="page-hero-actions page-toolbar">
        <button class="button ghost" type="button" :disabled="connecting" @click="checkHealth">健康检查</button>
        <button class="button ghost" type="button" :disabled="!connected" @click="disconnectAction">断开连接</button>
        <button class="button warm" type="button" :disabled="connecting" @click="connectAction">{{ connecting ? '连接中...' : connected ? '重连 MCP' : '连接 MCP' }}</button>
      </div>
    </section>

    <section class="summary-grid compact-summary-grid">
      <article class="summary-card">
        <span class="summary-label">连接状态</span>
        <strong class="summary-value">{{ connected ? '已连接' : '未连接' }}</strong>
        <small>{{ statusMessage || '等待连接 MCP' }}</small>
      </article>
      <article class="summary-card">
        <span class="summary-label">工具数</span>
        <strong class="summary-value">{{ tools.length }}</strong>
        <small>{{ selectedTool || '尚未选择工具' }}</small>
      </article>
      <article class="summary-card">
        <span class="summary-label">健康检查</span>
        <strong class="summary-value">{{ healthState }}</strong>
        <small>{{ healthSummary }}</small>
      </article>
      <article class="summary-card">
        <span class="summary-label">调用摘要</span>
        <strong class="summary-value">{{ invocationSummary || '-' }}</strong>
        <small>{{ resultPayload ? '最近一次调用已返回结果' : '尚未调用工具' }}</small>
      </article>
    </section>

    <section class="results-grid two-column-grid">
      <article class="panel page-card">
        <div class="panel-head">
          <div>
            <p class="eyebrow">Connection</p>
            <h3>连接与工具列表</h3>
          </div>
        </div>
        <label class="field-block field-block-wide">
          <span class="field-label">MCP URL</span>
          <input v-model.trim="mcpUrl" class="text-input mono" type="text" />
        </label>
        <div class="tool-cloud compact-cloud">
          <button
            v-for="tool in tools"
            :key="tool"
            class="tool-pill"
            type="button"
            :class="selectedTool === tool ? 'tool-pill-active' : ''"
            @click="selectTool(tool)"
          >
            {{ tool }}
          </button>
        </div>
        <div v-if="healthPayload" class="hint-card">
          <div class="hint-card-head">
            <strong>健康检查结果</strong>
            <span class="status-pill" :class="healthOk ? 'status-good' : 'status-warn'">{{ healthState }}</span>
          </div>
          <pre class="json-view compact-json">{{ prettyJson(healthPayload) }}</pre>
        </div>
      </article>

      <article class="panel page-card">
        <div class="panel-head">
          <div>
            <p class="eyebrow">Invoke</p>
            <h3>工具调用</h3>
          </div>
        </div>
        <div class="entity-list">
          <div class="entity-list-item">
            <div>
              <strong>当前工具</strong>
              <p class="field-help mono">{{ selectedTool || '-' }}</p>
            </div>
          </div>
          <div class="entity-list-item">
            <div>
              <strong>参数模板</strong>
              <p class="field-help">根据工具名自动填充默认请求体，可直接修改后调用。</p>
            </div>
          </div>
        </div>
        <label class="field-block field-block-wide">
          <span class="field-label">arguments(JSON)</span>
          <textarea v-model.trim="argsText" class="text-area mono" rows="12"></textarea>
        </label>
        <div class="action-row compact-actions">
          <button class="button warm" type="button" :disabled="!selectedTool || invoking || !connected" @click="invokeTool">{{ invoking ? '调用中...' : '调用工具' }}</button>
          <button class="button ghost" type="button" :disabled="!selectedTool" @click="resetArgs">重置参数模板</button>
          <button class="button ghost" type="button" :disabled="!selectedTool" @click="copyArgs">复制参数</button>
        </div>
      </article>
    </section>

    <section class="results-grid two-column-grid">
      <article class="panel page-card">
        <div class="panel-head">
          <div>
            <p class="eyebrow">Summary</p>
            <h3>调用摘要</h3>
          </div>
        </div>
        <div class="entity-list">
          <div class="entity-list-item"><div><strong>工具</strong><p class="field-help mono">{{ selectedTool || '-' }}</p></div></div>
          <div class="entity-list-item"><div><strong>结果摘要</strong><p class="field-help">{{ invocationSummary || '-' }}</p></div></div>
        </div>
        <pre class="json-view">{{ prettyJson(resultEnvelope) }}</pre>
      </article>

      <article class="panel page-card">
        <div class="panel-head">
          <div>
            <p class="eyebrow">Result</p>
            <h3>原始响应</h3>
          </div>
        </div>
        <pre class="json-view">{{ prettyJson(resultPayload) }}</pre>
      </article>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';

import { BrowserMcpClient, extractEnvelope, fetchHealth, summarizeToolResult } from '../../lib/mcpClient';
import { useStatusToast } from '../../shared/composables/useStatusToast';
import { useConsoleRuntime } from '../../shared/composables/useConsoleRuntime';
import { prettyJson } from '../../shared/utils/format';

const client = new BrowserMcpClient();
const { state } = useConsoleRuntime();

const mcpUrl = ref(state.mcpUrl);
const connecting = ref(false);
const invoking = ref(false);
const connected = ref(false);
const tools = ref<string[]>([]);
const selectedTool = ref('');
const argsText = ref(`{
  "identifier": ""
}`);
const resultPayload = ref<unknown>(null);
const healthPayload = ref<unknown>(null);
const healthOk = ref<boolean | null>(null);
const statusMessage = ref('');
const invocationSummary = ref('');

useStatusToast(statusMessage, 'MCP 联调');

const defaultToolArgs: Record<string, string> = {
  resolve_metadata: `{
  "identifier": ""
}`,
  resolve_object: `{
  "identifier": ""
}`,
  resolve_component: `{
  "identifier": ""
}`,
  batch_resolve_metadata_distributed: `{
  "identifiers": []
}`,
  batch_resolve_object_distributed: `{
  "identifiers": []
}`,
  batch_resolve_component_distributed: `{
  "identifiers": []
}`,
  search_global: `{
  "naturalQuery": "energy dataset",
  "mode": "HYBRID",
  "strategy": "RECURSIVE",
  "anchorNodeCode": "1100RTAA",
  "limit": 5,
  "offset": 0,
  "ttl": 4,
  "maxFanout": 8,
  "perNodeLimit": 20,
  "globalDeadlineMs": 5000,
  "hopTimeoutMs": 1500
}`,
  query_audit_logs: `{
  "identifier": "",
  "page": 1,
  "size": 10
}`,
  get_node_routes: `{}
`
};

const resultEnvelope = computed(() => extractEnvelope(resultPayload.value));
const healthState = computed(() => healthOk.value == null ? '未检查' : healthOk.value ? '可达' : '异常');
const healthSummary = computed(() => {
  if (healthOk.value == null) {
    return '尚未执行 health 检查';
  }
  return healthOk.value ? 'MCP 服务 health 正常返回' : 'health 返回异常';
});

async function copyText(value: string, successMessage: string): Promise<void> {
  if (!value) {
    return;
  }
  try {
    await navigator.clipboard.writeText(value);
    statusMessage.value = successMessage;
  } catch {
    statusMessage.value = '复制失败，请手动复制。';
  }
}

async function checkHealth(): Promise<void> {
  try {
    const result = await fetchHealth(mcpUrl.value);
    healthOk.value = result.ok;
    healthPayload.value = result.payload;
    statusMessage.value = result.ok ? 'MCP health 检查通过。' : 'MCP health 返回异常。';
  } catch (error) {
    healthOk.value = false;
    healthPayload.value = { error: error instanceof Error ? error.message : String(error) };
    statusMessage.value = error instanceof Error ? error.message : String(error);
  }
}

async function connectAction(): Promise<void> {
  connecting.value = true;
  statusMessage.value = '';
  try {
    state.mcpUrl = mcpUrl.value;
    await client.connect(mcpUrl.value);
    tools.value = await client.listTools();
    connected.value = true;
    statusMessage.value = `已连接 MCP，工具数 ${tools.value.length}`;
    if (!selectedTool.value && tools.value.length) {
      selectTool(tools.value[0]!);
    }
  } catch (error) {
    connected.value = false;
    statusMessage.value = error instanceof Error ? error.message : String(error);
  } finally {
    connecting.value = false;
  }
}

async function disconnectAction(): Promise<void> {
  await client.disconnect();
  connected.value = false;
  statusMessage.value = 'MCP 连接已断开。';
}

function selectTool(tool: string): void {
  selectedTool.value = tool;
  argsText.value = defaultToolArgs[tool] ?? '{}';
}

function resetArgs(): void {
  if (!selectedTool.value) {
    return;
  }
  argsText.value = defaultToolArgs[selectedTool.value] ?? '{}';
}

async function copyArgs(): Promise<void> {
  await copyText(argsText.value, '当前参数模板已复制到剪贴板。');
}

async function invokeTool(): Promise<void> {
  if (!selectedTool.value) {
    return;
  }
  invoking.value = true;
  statusMessage.value = '';
  try {
    resultPayload.value = await client.callTool(selectedTool.value, JSON.parse(argsText.value || '{}'));
    invocationSummary.value = summarizeToolResult(selectedTool.value, resultPayload.value);
    statusMessage.value = `${selectedTool.value} 调用完成`;
  } catch (error) {
    invocationSummary.value = '';
    statusMessage.value = error instanceof Error ? error.message : String(error);
  } finally {
    invoking.value = false;
  }
}
</script>

