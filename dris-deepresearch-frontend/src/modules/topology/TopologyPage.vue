<template>
  <div class="page-shell">
    <section class="page-hero panel">
      <div>
        <p class="eyebrow">Topology</p>
        <h2>{{ texts.pageTitle }}</h2>
        <p class="panel-caption">{{ texts.pageCaption }}</p>
      </div>
      <div class="page-hero-actions">
        <button class="button ghost" type="button" :disabled="loading" @click="loadTopology">{{ loading ? texts.refreshing : texts.refresh }}</button>
      </div>
    </section>

    <section class="panel page-card graph-card">
      <div class="panel-head">
        <div>
          <p class="eyebrow">Graph</p>
          <h3>{{ texts.graphTitle }}</h3>
        </div>
      </div>
      <NodeTopologyGraph :nodes="nodeOptions" :current-node-code="currentNode.code" :status-items="statuses" :route-items="routes" />
      <div class="metrics-strip graph-legend">
        <div class="metric-chip"><span>{{ texts.currentNode }}</span><strong>{{ currentNode.code }}</strong></div>
        <div class="metric-chip"><span>{{ texts.directNodes }}</span><strong>{{ descendants.length }}</strong></div>
        <div class="metric-chip"><span>{{ texts.onlineNeighbors }}</span><strong>{{ statuses.filter((item) => item.direction === 'CHILD' && item.online).length }}</strong></div>
      </div>
    </section>

    <section class="results-grid topology-grid">
      <article class="panel page-card">
        <div class="panel-head">
          <div>
            <p class="eyebrow">Children</p>
            <h3>{{ texts.childrenTitle }}</h3>
          </div>
        </div>
        <div class="table-wrap">
          <table class="data-table">
            <thead>
              <tr>
                <th>{{ texts.tableNode }}</th>
                <th>{{ texts.tableLevel }}</th>
                <th>{{ texts.tableRelation }}</th>
                <th>{{ texts.tableDrirp }}</th>
                <th>{{ texts.tableLastSeen }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in descendants" :key="item.childCode">
                <td>{{ item.childNodeName }}<br /><small>{{ item.childCode }}</small></td>
                <td>{{ item.childLevel }}</td>
                <td>{{ item.relationStatus }} / {{ item.healthStatus }}</td>
                <td><small class="mono">{{ item.childDrirpUrl || '-' }}</small></td>
                <td>{{ formatTime(item.lastSeenAt) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </article>

      <article class="panel page-card">
        <div class="panel-head">
          <div>
            <p class="eyebrow">Register</p>
            <h3>{{ texts.registerTitle }}</h3>
          </div>
        </div>
        <div class="form-grid">
          <label class="field-block">
            <span class="field-label">{{ texts.fieldChildName }}</span>
            <input v-model.trim="registerForm.childNodeName" class="text-input" type="text" :placeholder="childNodeNamePlaceholder" />
            <small class="field-help">{{ texts.childNameHelp }}</small>
          </label>
          <label class="field-block">
            <span class="field-label">{{ texts.fieldRegion }}</span>
            <select v-model="registerForm.regionCode" class="select-input mono" :disabled="regionLocked || selectableRegions.length === 0">
              <option v-for="item in selectableRegions" :key="item.code" :value="item.code">
                {{ item.name }} ({{ item.code }})
              </option>
            </select>
            <small class="field-help">{{ regionHelperText }}</small>
          </label>
          <label class="field-block">
            <span class="field-label">{{ texts.fieldOrgCode }}</span>
            <input
              v-model.trim="registerForm.orgCode"
              class="text-input mono"
              type="text"
              maxlength="4"
              :placeholder="orgCodePlaceholder"
            />
            <small class="field-help">{{ texts.orgCodeHelp }}</small>
          </label>
          <label class="field-block field-block-wide">
            <span class="field-label">{{ texts.fieldNodeCodePreview }}</span>
            <div class="mini-note">
              <strong>{{ childLevelLabel }} 路 {{ selectedRegionName }}</strong>
              <span class="mono">{{ registerForm.regionCode || '----' }} + {{ registerForm.orgCode || '____' }} = {{ nodeCodePreview }}</span>
              <small>{{ registerScopeSummary }}</small>
            </div>
          </label>
          <label class="field-block field-block-wide">
            <span class="field-label">{{ texts.fieldChildDrirp }}</span>
            <input v-model.trim="registerForm.childDrirpUrl" class="text-input mono" type="text" :placeholder="drirpPlaceholder" />
          </label>
        </div>
        <p class="field-help">{{ texts.drirpHelp }}</p>
        <div class="action-row compact-actions">
          <button class="button warm" type="button" :disabled="isCountyNode || submitting" @click="registerChild">
            {{ registerButtonLabel }}
          </button>
          <button class="button ghost" type="button" @click="notifyShutdown">{{ texts.shutdownButton }}</button>
        </div>
        <p v-if="statusMessage" class="field-help">{{ statusMessage }}</p>
        <div v-if="registerResult" class="compact-card">
          <div class="panel-head compact-card-head">
            <div>
              <h4>{{ texts.registerResultTitle }}</h4>
              <p class="field-help">{{ texts.registerResultHelp }}</p>
            </div>
            <div class="action-row compact-actions config-actions">
              <button class="button ghost" type="button" @click="downloadBootstrapJson">{{ texts.downloadJson }}</button>
              <button class="button ghost" type="button" @click="downloadBootstrapYaml">{{ texts.downloadYaml }}</button>
              <button class="button ghost" type="button" @click="copyBootstrapYaml">{{ texts.copyYaml }}</button>
            </div>
          </div>
          <pre class="json-view">{{ prettyJson(registerResult) }}</pre>
        </div>
      </article>
    </section>

    <section class="results-grid two-column-grid">
      <article class="panel page-card">
        <div class="panel-head">
          <div>
            <p class="eyebrow">Routes</p>
            <h3>{{ texts.routesTitle }}</h3>
          </div>
        </div>
        <div class="table-wrap table-wrap-tall">
          <table class="data-table">
            <thead>
              <tr>
                <th>{{ texts.routeTarget }}</th>
                <th>{{ texts.routeNextHop }}</th>
                <th>{{ texts.routeHopCount }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="route in routes" :key="route.targetCode">
                <td>{{ route.targetNodeName }}<br /><small>{{ route.targetCode }}</small></td>
                <td>{{ route.nextHopCode }}<br /><small>{{ route.nextHopDrirpUrl }}</small></td>
                <td>{{ route.hopCount }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </article>

      <article class="panel page-card">
        <div class="panel-head">
          <div>
            <p class="eyebrow">Tracking</p>
            <h3>{{ texts.trackingTitle }}</h3>
          </div>
        </div>
        <div v-if="trackedStatus" class="tracking-card">
          <p><strong>{{ trackedStatus.nodeName }}</strong> / {{ trackedStatus.nodeCode }}</p>
          <p>{{ trackedStatus.relationStatus }} / {{ trackedStatus.healthStatus }}</p>
          <p class="field-help">{{ texts.trackingLastSeen }}{{ formatTime(trackedStatus.lastSeenAt) }}</p>
          <p class="field-help mono">{{ trackedStatus.drirpUrl || '-' }}</p>
        </div>
        <div v-else class="panel-empty">{{ texts.trackingEmpty }}</div>
      </article>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';

import NodeTopologyGraph from '../../shared/components/NodeTopologyGraph.vue';
import { asJsonBody, requestEnvelope } from '../../shared/api/http';
import { useStatusToast } from '../../shared/composables/useStatusToast';
import { useConsoleRuntime } from '../../shared/composables/useConsoleRuntime';
import { findRegionByCode, listProvinceCities, provinceRegions, type RegionCatalogItem } from '../../shared/constants/regionCatalog';
import { formatTime, prettyJson } from '../../shared/utils/format';

const texts = {
  pageTitle: '\u8282\u70b9\u62d3\u6251\u4e0e\u6ce8\u518c',
  pageCaption: '\u7236\u8282\u70b9\u4fa7\u5c55\u793a\u8def\u7531\u3001\u5b50\u8282\u70b9\u72b6\u6001\uff0c\u5e76\u63d0\u4f9b\u4e0b\u7ea7\u8282\u70b9\u6ce8\u518c\u5165\u53e3\u3002',
  refresh: '\u5237\u65b0\u62d3\u6251',
  refreshing: '\u5237\u65b0\u4e2d...',
  graphTitle: '\u8282\u70b9\u62d3\u6251\u56fe',
  currentNode: '\u5f53\u524d\u8282\u70b9',
  directNodes: '\u76f4\u8fde\u8282\u70b9',
  onlineNeighbors: '\u5728\u7ebf\u76f4\u8fde',
  childrenTitle: '\u76f4\u8fde\u4e0b\u7ea7\u8282\u70b9',
  tableNode: '\u8282\u70b9',
  tableLevel: '\u5c42\u7ea7',
  tableRelation: '\u5173\u7cfb',
  tableDrirp: 'DRIRP \u5730\u5740',
  tableLastSeen: '\u6700\u540e\u5fc3\u8df3',
  registerTitle: '\u6ce8\u518c\u4e0b\u7ea7\u8282\u70b9',
  fieldChildName: '\u5b50\u8282\u70b9\u540d\u79f0',
  fieldRegion: '\u5730\u533a',
  fieldOrgCode: '\u7ec4\u7ec7\u7f16\u7801',
  fieldNodeCodePreview: '\u8282\u70b9\u7f16\u7801\u9884\u89c8',
  fieldChildDrirp: '\u5b50\u8282\u70b9 DRIRP \u5730\u5740',
  childNameHelp: '\u5efa\u8bae\u586b\u5199\u90e8\u7f72\u53ef\u8bc6\u522b\u7684\u8282\u70b9\u540d\u79f0\uff0c\u4f8b\u5982 node3-city-a\u3002',
  orgCodeHelp: '\u754c\u9762\u4e2d\u6587\u663e\u793a\uff0c\u63d0\u4ea4\u65f6\u4ecd\u6620\u5c04\u4e3a orgCode\uff0c\u8981\u6c42 4 \u4f4d\u5927\u5199\u5b57\u6bcd\uff0c\u4e14\u4e0d\u542b I \u548c O\u3002',
  drirpHelp: '\u8fd9\u91cc\u586b\u5199\u7684\u662f\u5b50\u8282\u70b9\u8282\u70b9\u95f4\u901a\u4fe1\u5730\u5740\uff0c\u4f8b\u5982 grpc://127.0.0.1:19202\uff0c\u4e0d\u662f HTTP \u540e\u7aef\u5730\u5740\u3002',
  countyCannotRegister: '\u53bf\u7ea7\u8282\u70b9\u4e0d\u53ef\u6ce8\u518c\u4e0b\u7ea7',
  submitting: '\u63d0\u4ea4\u4e2d...',
  registerPrefix: '\u6ce8\u518c',
  shutdownButton: '\u53d1\u9001\u4e0b\u7ebf\u901a\u77e5',
  registerResultTitle: '\u542f\u52a8\u914d\u7f6e\u7ed3\u679c',
  registerResultHelp: 'JSON \u4fdd\u5b58\u7236\u8282\u70b9\u6ce8\u518c\u54cd\u5e94\uff0cYAML \u7247\u6bb5\u53ef\u76f4\u63a5\u4f5c\u4e3a\u5b50\u8282\u70b9\u542f\u52a8\u914d\u7f6e\u53c2\u8003\u3002',
  downloadJson: '\u4e0b\u8f7d JSON',
  downloadYaml: '\u4e0b\u8f7d YAML \u7247\u6bb5',
  copyYaml: '\u590d\u5236 YAML',
  routesTitle: '\u8def\u7531\u8868',
  routeTarget: '\u76ee\u6807\u8282\u70b9',
  routeNextHop: '\u4e0b\u4e00\u8df3',
  routeHopCount: '\u8df3\u6570',
  trackingTitle: '\u6ce8\u518c\u540e\u8ddf\u8e2a',
  trackingLastSeen: '\u6700\u540e\u5fc3\u8df3\uff1a',
  trackingEmpty: '\u6ce8\u518c\u540e\u4f1a\u5728\u8fd9\u91cc\u81ea\u52a8\u8ddf\u8e2a\u76ee\u6807\u5b50\u8282\u70b9\u4ece PENDING/UNKNOWN \u5230 ACTIVE/ACTIVE \u7684\u72b6\u6001\u53d8\u5316\u3002',
  registerSuccess: '\u7236\u8282\u70b9\u5df2\u751f\u6210 bootstrap \u914d\u7f6e\uff0c\u8bf7\u4e0b\u8f7d\u540e\u5199\u5165\u5b50\u8282\u70b9\u914d\u7f6e\uff1b\u6ce8\u610f childDrirpUrl \u5e94\u4e3a grpc://...\uff0c\u4e0d\u662f HTTP \u5730\u5740\u3002',
  shutdownSent: '\u5df2\u53d1\u9001\u4e0b\u7ebf\u901a\u77e5\u3002',
  yamlCopied: '\u542f\u52a8 YAML \u7247\u6bb5\u5df2\u590d\u5236\u5230\u526a\u8d34\u677f\u3002',
  yamlCopyFailed: '\u590d\u5236\u5931\u8d25\uff0c\u8bf7\u76f4\u63a5\u4e0b\u8f7d YAML \u7247\u6bb5\u3002'
} as const;

const { currentNode, currentBackendProxyBase, nodeOptions } = useConsoleRuntime();

const loading = ref(false);
const submitting = ref(false);
const statusMessage = ref('');
const descendants = ref<any[]>([]);
const statuses = ref<any[]>([]);
const routes = ref<any[]>([]);
const registerResult = ref<any | null>(null);
const trackedStatus = ref<any | null>(null);
const trackingChildCode = ref('');
let trackingTimer: number | null = null;

useStatusToast(statusMessage, '节点拓扑');

const registerForm = reactive({
  childNodeName: '',
  regionCode: currentNode.value.code.slice(0, 4),
  orgCode: '',
  childDrirpUrl: ''
});

const isCountyNode = computed(() => currentNode.value.level === 'COUNTY');
const currentRegionCode = computed(() => currentNode.value.code.slice(0, 4));
const childLevel = computed(() => currentNode.value.level === 'COUNTRY'
  ? 'PROVINCE'
  : currentNode.value.level === 'PROVINCE'
    ? 'CITY'
    : currentNode.value.level === 'CITY'
      ? 'COUNTY'
      : '');
const childLevelLabel = computed(() => childLevel.value === 'PROVINCE'
  ? '\u7701\u7ea7\u8282\u70b9'
  : childLevel.value === 'CITY'
    ? '\u5e02\u7ea7\u8282\u70b9'
    : childLevel.value === 'COUNTY'
      ? '\u53bf\u7ea7\u8282\u70b9'
      : '\u4e0b\u7ea7\u8282\u70b9');
const childNodeNamePlaceholder = computed(() => childLevel.value === 'PROVINCE'
  ? 'node2-province'
  : childLevel.value === 'CITY'
    ? 'node3-city-a'
    : 'node4-county-a');
const orgCodePlaceholder = computed(() => childLevel.value === 'PROVINCE'
  ? 'PRAA'
  : childLevel.value === 'CITY'
    ? 'CTAA'
    : 'CNTA');
const drirpPlaceholder = computed(() => currentNode.value.level === 'COUNTRY'
  ? 'grpc://127.0.0.1:19201'
  : currentNode.value.level === 'PROVINCE'
    ? 'grpc://127.0.0.1:19202'
    : 'grpc://127.0.0.1:19203');
const bootstrapConfig = computed(() => registerResult.value?.bootstrapConfig ?? null);
const effectiveChildCode = computed(() => {
  const regionCode = registerResult.value?.regionCode ?? registerForm.regionCode;
  const orgCode = registerResult.value?.orgCode ?? registerForm.orgCode;
  return `${regionCode || ''}${orgCode || ''}`;
});
const selectableRegions = computed(() => {
  if (currentNode.value.level === 'COUNTRY') {
    return provinceRegions;
  }
  if (currentNode.value.level === 'PROVINCE') {
    return listProvinceCities(currentRegionCode.value);
  }
  if (currentNode.value.level === 'CITY') {
    const currentRegion = resolveRegion(currentRegionCode.value);
    return currentRegion ? [currentRegion] : [];
  }
  return [] as RegionCatalogItem[];
});
const regionLocked = computed(() => currentNode.value.level === 'CITY');
const selectedRegion = computed(() => resolveRegion(registerForm.regionCode));
const selectedRegionName = computed(() => selectedRegion.value?.name ?? (registerForm.regionCode || '----'));
const nodeCodePreview = computed(() => `${registerForm.regionCode || '----'}${registerForm.orgCode || '____'}`);
const registerScopeSummary = computed(() => {
  if (currentNode.value.level === 'COUNTRY') {
    return '\u56fd\u5bb6\u7ea7\u8282\u70b9\u53ef\u4ece\u5168\u56fd\u7701\u7ea7\u5730\u533a\u4e2d\u9009\u62e9\uff0c\u751f\u6210\u65b0\u7684\u7701\u7ea7\u8282\u70b9\u7f16\u7801\u3002';
  }
  if (currentNode.value.level === 'PROVINCE') {
    const provinceName = resolveRegion(currentRegionCode.value)?.name ?? currentRegionCode.value;
    return `\u5e02\u7ea7\u8282\u70b9\u5730\u533a\u4ec5\u9650\u7236\u7701 ${provinceName} \u4e0b\u7684\u5730\u533a\u7801\u3002`;
  }
  if (currentNode.value.level === 'CITY') {
    return `\u53bf\u7ea7\u8282\u70b9\u6cbf\u7528\u7236\u5e02\u5730\u533a\u7801 ${currentRegionCode.value}\uff0c\u4e0d\u5141\u8bb8\u6539\u6210\u5176\u4ed6\u5730\u533a\u3002`;
  }
  return '\u53bf\u7ea7\u8282\u70b9\u4e0d\u80fd\u7ee7\u7eed\u6ce8\u518c\u4e0b\u7ea7\u8282\u70b9\u3002';
});
const regionHelperText = computed(() => {
  if (currentNode.value.level === 'COUNTRY') {
    return '\u56fd\u5bb6\u7ea7\u6ce8\u518c\u7701\u7ea7\u8282\u70b9\u65f6\uff0c\u4ece\u7701\u7ea7\u884c\u653f\u533a\u4e0b\u62c9\u9009\u62e9\uff0c\u81ea\u52a8\u6620\u5c04\u4e3a regionCode\u3002';
  }
  if (currentNode.value.level === 'PROVINCE') {
    const provinceName = resolveRegion(currentRegionCode.value)?.name ?? currentRegionCode.value;
    return `\u5e02\u7ea7\u8282\u70b9\u53ea\u80fd\u9009\u62e9\u4e0e\u7236\u7701\u540c\u524d\u7f00\u7684\u5730\u533a\uff0c\u76ee\u524d\u8303\u56f4\uff1a${provinceName}\u3002`;
  }
  if (currentNode.value.level === 'CITY') {
    return `\u53bf\u7ea7\u8282\u70b9\u5730\u533a\u5df2\u9501\u5b9a\u4e3a\u7236\u5e02 ${selectedRegionName.value}\uff08${currentRegionCode.value}\uff09\u3002`;
  }
  return '\u53bf\u7ea7\u8282\u70b9\u4e0d\u652f\u6301\u4e0b\u7ea7\u6ce8\u518c\u3002';
});
const registerButtonLabel = computed(() => isCountyNode.value
  ? texts.countyCannotRegister
  : submitting.value
    ? texts.submitting
    : texts.registerPrefix + childLevelLabel.value);

async function loadTopology(): Promise<void> {
  loading.value = true;
  try {
    const [descResp, statusResp, routeResp] = await Promise.all([
      requestEnvelope<any[]>(`${currentBackendProxyBase.value}/api/v2/node/direct-descendants`),
      requestEnvelope<any[]>(`${currentBackendProxyBase.value}/api/v2/node/adjacent/status`),
      requestEnvelope<any[]>(`${currentBackendProxyBase.value}/api/v2/node/routes`)
    ]);
    descendants.value = descResp.data ?? [];
    statuses.value = statusResp.data ?? [];
    routes.value = routeResp.data ?? [];
    if (trackingChildCode.value) {
      trackedStatus.value = statuses.value.find((item) => item.nodeCode === trackingChildCode.value && item.direction === 'CHILD') ?? null;
    }
  } finally {
    loading.value = false;
  }
}

async function registerChild(): Promise<void> {
  submitting.value = true;
  statusMessage.value = '';
  try {
    const payload = {
      ...registerForm,
      regionCode: registerForm.regionCode,
      orgCode: registerForm.orgCode.toUpperCase()
    };
    const response = await requestEnvelope<any>(`${currentBackendProxyBase.value}/api/v2/node/register`, {
      method: 'POST',
      body: asJsonBody(payload)
    });
    registerResult.value = response.data;
    trackingChildCode.value = `${payload.regionCode}${payload.orgCode}`;
    statusMessage.value = texts.registerSuccess;
    await loadTopology();
    startTracking();
  } catch (error) {
    statusMessage.value = error instanceof Error ? error.message : String(error);
  } finally {
    submitting.value = false;
  }
}

async function notifyShutdown(): Promise<void> {
  statusMessage.value = '';
  try {
    const response = await requestEnvelope<any>(`${currentBackendProxyBase.value}/api/v2/node/shutdown`, { method: 'POST' });
    statusMessage.value = response.message || texts.shutdownSent;
  } catch (error) {
    statusMessage.value = error instanceof Error ? error.message : String(error);
  }
}

function startTracking(): void {
  if (trackingTimer != null) {
    window.clearInterval(trackingTimer);
  }
  trackingTimer = window.setInterval(() => {
    void loadTopology();
  }, 3000);
}

function resolveRegion(code: string): RegionCatalogItem | undefined {
  if (!code) {
    return undefined;
  }
  return findRegionByCode(code);
}

function resetRegisterForm(): void {
  registerForm.regionCode = selectableRegions.value[0]?.code ?? currentRegionCode.value;
  registerForm.orgCode = '';
  registerForm.childNodeName = '';
  registerForm.childDrirpUrl = '';
  registerResult.value = null;
  trackingChildCode.value = '';
  trackedStatus.value = null;
}

function downloadTextFile(filename: string, content: string, mime: string): void {
  const blob = new Blob([content], { type: mime });
  const url = window.URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  window.URL.revokeObjectURL(url);
}

function slugifyNodeId(value: string): string {
  return (value || 'node')
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '') || 'node';
}

function extractPort(drirpUrl: string): string {
  const matched = (drirpUrl || '').match(/:(\d+)$/);
  return matched?.[1] ?? '19200';
}

function buildBootstrapJson(): string {
  return JSON.stringify({
    ...registerResult.value,
    childDrirpUrl: registerForm.childDrirpUrl,
    generatedAt: new Date().toISOString()
  }, null, 2);
}

function buildBootstrapYaml(): string {
  const config = bootstrapConfig.value;
  if (!config) {
    return '';
  }
  const nodeId = slugifyNodeId(config.nodeName || effectiveChildCode.value);
  const regionCode = config.regionCode ?? registerForm.regionCode;
  const orgCode = config.orgCode ?? registerForm.orgCode;
  const childDrirpUrl = registerForm.childDrirpUrl || drirpPlaceholder.value;
  const childDrirpPort = extractPort(childDrirpUrl);
  return [
    'dris:',
    '  v2:',
    `    node-id: ${nodeId}`,
    `    node-level: ${config.nodeLevel}`,
    `    region-code: ${regionCode}`,
    `    organization-code: ${orgCode}`,
    `    drirp-enabled: ${String(config.drirpEnabled)}`,
    `    drirp-port: ${childDrirpPort}`,
    `    drirp-public-url: ${childDrirpUrl}`,
    `    parent-node-id: ${config.parentNodeName}`,
    `    parent-drirp-url: ${config.parentDrirpUrl}`,
    '    startup-hello-enabled: true',
    `    # child-code: ${effectiveChildCode.value}`,
    ''
  ].join('\n');
}

function downloadBootstrapJson(): void {
  if (!registerResult.value) {
    return;
  }
  const childCode = effectiveChildCode.value || 'child-node';
  downloadTextFile(`${childCode}-bootstrap.json`, buildBootstrapJson(), 'application/json;charset=utf-8');
}

function downloadBootstrapYaml(): void {
  if (!bootstrapConfig.value) {
    return;
  }
  const childCode = effectiveChildCode.value || 'child-node';
  downloadTextFile(`${childCode}-bootstrap.yml`, buildBootstrapYaml(), 'text/yaml;charset=utf-8');
}

async function copyBootstrapYaml(): Promise<void> {
  if (!bootstrapConfig.value) {
    return;
  }
  try {
    await navigator.clipboard.writeText(buildBootstrapYaml());
    statusMessage.value = texts.yamlCopied;
  } catch {
    statusMessage.value = texts.yamlCopyFailed;
  }
}

watch(selectableRegions, (items) => {
  if (items.length === 0) {
    return;
  }
  if (!items.some((item) => item.code === registerForm.regionCode)) {
    registerForm.regionCode = items[0]!.code;
  }
}, { immediate: true });

watch(() => registerForm.orgCode, (value) => {
  const normalized = (value || '').toUpperCase().replace(/[^A-Z]/g, '').slice(0, 4);
  if (normalized !== value) {
    registerForm.orgCode = normalized;
  }
});

watch(() => currentNode.value.code, () => {
  resetRegisterForm();
  void loadTopology();
});

onMounted(() => {
  resetRegisterForm();
  void loadTopology();
});

onBeforeUnmount(() => {
  if (trackingTimer != null) {
    window.clearInterval(trackingTimer);
  }
});
</script>

