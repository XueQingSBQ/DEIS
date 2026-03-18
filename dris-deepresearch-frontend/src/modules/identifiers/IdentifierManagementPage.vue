<template>
  <div class="page-shell">
    <section class="page-hero panel">
      <div>
        <p class="eyebrow">Identifier Workspace</p>
        <h2>标识管理</h2>
        <p class="panel-caption">提供标识解析、批量处理、状态维护和关联信息管理，适合作为答辩演示与联调入口。</p>
      </div>
      <div class="page-hero-actions page-toolbar">
        <button class="button ghost" type="button" @click="clearState">清空状态</button>
      </div>
    </section>

    <section class="results-grid two-column-grid">
      <article class="panel page-card management-workbench">
        <div class="panel-head">
          <div>
            <p class="eyebrow">Single Resolve</p>
            <h3>标识解析</h3>
          </div>
          <span class="status-pill" :class="loading ? 'status-warn' : 'status-muted'">{{ loading ? '处理中' : '空闲' }}</span>
        </div>
        <label class="field-block field-block-wide management-identifier-field">
          <span class="field-label">标识</span>
          <input
            v-model.trim="identifierInput"
            class="text-input mono"
            type="text"
            placeholder="输入完整标识，例如 1100PRAA..."
            @keydown.enter.prevent="resolveDigitalObjectSingle"
          />
        </label>
        <div class="management-action-group management-action-group-primary">
          <button class="button warm management-action-button" type="button" :disabled="loading || !identifierInput" @click="resolveMetadataSingle">元数据解析</button>
          <button class="button warm management-action-button" type="button" :disabled="loading || !identifierInput" @click="resolveDigitalObjectSingle">完整解析</button>
        </div>
      </article>

      <article class="panel page-card management-workbench">
        <div class="panel-head">
          <div>
            <p class="eyebrow">Batch Resolve</p>
            <h3>批量解析</h3>
          </div>
          <span class="status-pill" :class="loadingBatch ? 'status-warn' : 'status-muted'">{{ loadingBatch ? '处理中' : '空闲' }}</span>
        </div>
        <label class="field-block field-block-wide">
          <span class="field-label">标识列表</span>
          <textarea v-model.trim="batchIdentifiersText" class="text-area mono" rows="8" placeholder="每行一个标识，或使用逗号/分号分隔"></textarea>
        </label>
        <div class="management-status-strip">
          <span class="status-pill status-muted">共 {{ parsedBatchIdentifiers.length }} 个标识</span>
          <span class="status-pill status-good">结果 {{ resultItems.length }} 条</span>
          <span class="status-pill status-muted">{{ lastResolveLabel }}</span>
        </div>
        <div class="management-action-group management-action-group-primary">
          <button class="button warm management-action-button" type="button" :disabled="loadingBatch || !parsedBatchIdentifiers.length" @click="resolveMetadataBatch">批量元数据解析</button>
          <button class="button warm management-action-button" type="button" :disabled="loadingBatch || !parsedBatchIdentifiers.length" @click="resolveDigitalObjectBatch">批量完整解析</button>
        </div>
      </article>
    </section>

    <section class="panel page-card management-result-panel">
      <div class="panel-head management-result-head">
        <div>
          <p class="eyebrow">Results</p>
          <h3>标识结果列表</h3>
        </div>
        <div class="management-status-strip">
          <span class="status-pill status-muted">{{ statusMessage || '等待操作' }}</span>
        </div>
      </div>

      <div v-if="resultItems.length" class="management-list">
        <article v-for="item in resultItems" :key="item.identifier + '-' + item.kind" class="management-list-item">
          <div class="management-list-head">
            <div>
              <h4 class="management-item-title">{{ item.identifier }}</h4>
              <p class="panel-caption">{{ item.modeLabel }}{{ item.resolvedByNodeCode ? (' / 解析节点 ' + item.resolvedByNodeCode) : '' }}</p>
            </div>
            <div class="management-item-badges">
              <span class="status-pill" :class="item.success === false ? 'status-warn' : 'status-good'">{{ item.success === false ? '失败' : '成功' }}</span>
              <span class="status-pill status-muted">{{ item.componentType || item.kindLabel }}</span>
            </div>
          </div>

          <div v-if="item.kind === 'metadata'" class="management-kv-grid management-kv-grid-wide">
            <div v-for="field in item.fields" :key="item.identifier + '-meta-' + field.label" class="management-kv-item">
              <span>{{ field.label }}</span>
              <strong :class="field.mono ? 'mono-wrap' : ''">{{ field.value }}</strong>
            </div>
          </div>

          <div v-else class="management-section-grid">
            <section class="management-section-card">
              <div class="management-section-head">
                <h5>元数据</h5>
                <span class="status-pill status-muted">{{ item.metadata ? '已包含' : '缺失' }}</span>
              </div>
              <div class="management-kv-grid">
                <div v-for="field in item.metadataFields || []" :key="item.identifier + '-m-' + field.label" class="management-kv-item">
                  <span>{{ field.label }}</span>
                  <strong :class="field.mono ? 'mono-wrap' : ''">{{ field.value }}</strong>
                </div>
              </div>
            </section>

            <section class="management-section-card">
              <div class="management-section-head">
                <h5>资源</h5>
                <span class="status-pill status-muted">{{ item.resource ? (item.componentType || '已绑定') : '未绑定' }}</span>
              </div>
              <div class="management-kv-grid">
                <div v-for="field in item.resourceFields || []" :key="item.identifier + '-r-' + field.label" class="management-kv-item">
                  <span>{{ field.label }}</span>
                  <strong :class="field.mono ? 'mono-wrap' : ''">{{ field.value }}</strong>
                </div>
              </div>
            </section>
          </div>

          <div class="management-inline-actions">
            <button class="button ghost management-action-button" type="button" @click="openMetadataEditor(item)">更新元数据</button>
            <button class="button ghost management-action-button" type="button" @click="openStatusEditor(item)">状态变更</button>
            <button class="button ghost management-action-button" type="button" @click="openResourceEditor(item)">更新关联资源</button>
            <button
              v-for="action in item.actions"
              :key="item.identifier + '-' + action.key"
              class="button ghost management-action-button"
              type="button"
              :disabled="action.disabled"
              @click="handleItemAction(item, action.key)"
            >
              {{ action.label }}
            </button>
            <button class="button ghost management-action-button" type="button" @click="deleteIdentifier(item.identifier)">注销标识</button>
          </div>

          <details class="management-raw-fold">
            <summary>查看原始结果</summary>
            <pre class="json-view management-json-view">{{ prettyJson(item.raw) }}</pre>
          </details>
        </article>
      </div>
      <div v-else class="panel-empty">先执行单条解析或批量解析，结果会显示在这里。</div>
    </section>

    <div v-if="statusEditorVisible" class="management-modal-backdrop" @click.self="closeEditors">
      <section class="management-modal panel">
        <div class="panel-head">
          <div>
            <p class="eyebrow">Status Edit</p>
            <h3>状态变更</h3>
          </div>
          <button class="button ghost management-action-button" type="button" @click="closeEditors">关闭</button>
        </div>
        <p class="panel-caption">当前标识：{{ editorIdentifier || '-' }}</p>
        <label class="field-block">
          <span class="field-label">目标状态</span>
          <select v-model="statusForm.status" class="select-input">
            <option value="active">active</option>
            <option value="inactive">inactive</option>
          </select>
        </label>
        <label class="field-block field-block-wide">
          <span class="field-label">变更说明</span>
          <textarea v-model.trim="statusForm.reason" class="text-area" rows="4" placeholder="可选：填写本次状态变更原因"></textarea>
        </label>
        <div class="management-action-group">
          <button class="button warm management-action-button" type="button" :disabled="loading || !editorIdentifier" @click="submitStatusPatch">保存状态</button>
          <button class="button ghost management-action-button" type="button" @click="closeEditors">取消</button>
        </div>
      </section>
    </div>

    <div v-if="metadataEditorVisible" class="management-modal-backdrop" @click.self="closeEditors">
      <section class="management-modal panel">
        <div class="panel-head">
          <div>
            <p class="eyebrow">Metadata Edit</p>
            <h3>元数据更新</h3>
          </div>
          <button class="button ghost management-action-button" type="button" @click="closeEditors">关闭</button>
        </div>
        <p class="panel-caption">当前标识：{{ editorIdentifier || '-' }}</p>
        <label class="field-block field-block-wide">
          <span class="field-label">元数据补丁 JSON</span>
          <textarea v-model.trim="patchText" class="text-area mono" rows="12"></textarea>
        </label>
        <div class="management-action-group">
          <button class="button warm management-action-button" type="button" :disabled="loading || !editorIdentifier" @click="submitMetadataPatch">保存元数据</button>
          <button class="button ghost management-action-button" type="button" @click="closeEditors">取消</button>
        </div>
      </section>
    </div>

    <div v-if="resourceEditorVisible" class="management-modal-backdrop" @click.self="closeEditors">
      <section class="management-modal panel">
        <div class="panel-head">
          <div>
            <p class="eyebrow">Resource Edit</p>
            <h3>关联资源更新</h3>
          </div>
          <button class="button ghost management-action-button" type="button" @click="closeEditors">关闭</button>
        </div>
        <p class="panel-caption">当前标识：{{ editorIdentifier || '-' }}</p>
        <label class="field-block field-block-wide">
          <span class="field-label">资源 JSON</span>
          <textarea v-model.trim="componentText" class="text-area mono" rows="14"></textarea>
        </label>
        <div class="management-action-group">
          <button class="button warm management-action-button" type="button" :disabled="loading || !editorIdentifier" @click="submitResourcePatch">保存关联资源</button>
          <button class="button ghost management-action-button" type="button" :disabled="loading || !editorIdentifier" @click="removeComponent">删除关联资源</button>
          <button class="button ghost management-action-button" type="button" @click="closeEditors">取消</button>
        </div>
      </section>
    </div>

    <div v-if="toolInvokerVisible" class="management-modal-backdrop" @click.self="toolInvokerVisible = false">
      <section class="management-modal panel">
        <div class="panel-head">
          <div>
            <p class="eyebrow">Tool Execute</p>
            <h3>工具调用</h3>
          </div>
          <button class="button ghost management-action-button" type="button" @click="toolInvokerVisible = false">关闭</button>
        </div>
        <p class="panel-caption">当前标识：{{ editorIdentifier || '-' }}</p>
        <label class="field-block field-block-wide">
          <span class="field-label">args(JSON)</span>
          <textarea v-model.trim="toolArgsText" class="text-area mono" rows="10"></textarea>
        </label>
        <label class="field-block">
          <span class="field-label">timeoutMs</span>
          <input v-model.number="toolTimeoutMs" class="text-input mono" type="number" min="100" />
        </label>
        <div class="management-action-group">
          <button class="button warm management-action-button" type="button" :disabled="loadingTool || !editorIdentifier" @click="executeTool">执行工具</button>
          <button class="button ghost management-action-button" type="button" @click="copyToolRequest()">复制调用模板</button>
        </div>
        <pre class="json-view management-json-view">{{ prettyJson(toolExecuteResult) }}</pre>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";

import { asJsonBody, requestEnvelope } from "../../shared/api/http";
import { useStatusToast } from "../../shared/composables/useStatusToast";
import { useConsoleRuntime } from "../../shared/composables/useConsoleRuntime";
import { formatTime, prettyJson } from "../../shared/utils/format";

type ResultField = {
  label: string;
  value: string;
  mono?: boolean;
};

type ResultActionKey = "generateDownload" | "openDownload" | "openLocator" | "copyLocator" | "copyServiceCurl" | "openTool" | "copyToolRequest";

type ResultAction = {
  key: ResultActionKey;
  label: string;
  disabled?: boolean;
};

type ResultItem = {
  identifier: string;
  modeLabel: string;
  kindLabel: string;
  kind: "metadata" | "digitalObject";
  success: boolean;
  componentType?: string;
  resolvedByNodeCode?: string;
  metadata?: any;
  resource?: any;
  downloadResult?: any | null;
  fields: ResultField[];
  metadataFields?: ResultField[];
  resourceFields?: ResultField[];
  actions: ResultAction[];
  raw: unknown;
};

const { currentBackendProxyBase } = useConsoleRuntime();

const identifierInput = ref("");
const batchIdentifiersText = ref("");
const loading = ref(false);
const loadingBatch = ref(false);
const loadingTool = ref(false);
const statusMessage = ref("");
const lastResolveLabel = ref("尚未解析");
const resultItems = ref<ResultItem[]>([]);
useStatusToast(statusMessage, "标识管理");

const patchText = ref('{\n  "description": "更新后的说明"\n}');
const componentText = ref(defaultComponentPayload());
const statusEditorVisible = ref(false);
const metadataEditorVisible = ref(false);
const resourceEditorVisible = ref(false);
const toolInvokerVisible = ref(false);
const editorIdentifier = ref("");
const statusForm = ref({ status: "active", reason: "" });
const toolArgsText = ref('{\n  "input": "demo"\n}');
const toolTimeoutMs = ref(5000);
const toolExecuteResult = ref<unknown>(null);

const parsedBatchIdentifiers = computed(() =>
  batchIdentifiersText.value
    .split(/[\s,;，；]+/)
    .map((item) => item.trim())
    .filter(Boolean)
);

function defaultComponentPayload(): string {
  return prettyJson({
    componentType: "resource",
    storageType: "external_uri",
    accessMethod: "download",
    contentType: "application/json",
    format: "json",
    locator: "",
    extra: {
      filename: "resource.json"
    }
  });
}

function clearState(): void {
  identifierInput.value = "";
  batchIdentifiersText.value = "";
  resultItems.value = [];
  statusMessage.value = "已清空当前页状态。";
  lastResolveLabel.value = "尚未解析";
  closeEditors();
  toolInvokerVisible.value = false;
  toolExecuteResult.value = null;
}

function displayValue(value: unknown): string {
  if (value == null) {
    return "-";
  }
  if (typeof value === "string") {
    return value.trim() || "-";
  }
  if (typeof value === "number" || typeof value === "boolean") {
    return String(value);
  }
  return JSON.stringify(value);
}

function formatSize(value: unknown): string {
  if (typeof value !== "number" || Number.isNaN(value) || value < 0) {
    return "-";
  }
  if (value < 1024) {
    return `${value} B`;
  }
  if (value < 1024 * 1024) {
    return `${(value / 1024).toFixed(1)} KB`;
  }
  return `${(value / 1024 / 1024).toFixed(2)} MB`;
}

function metadataFields(metadata: any): ResultField[] {
  return [
    { label: "名称", value: displayValue(metadata?.name) },
    { label: "状态", value: displayValue(metadata?.status) },
    { label: "行业", value: displayValue(metadata?.industry) },
    { label: "分类", value: displayValue(metadata?.dataCategory) },
    { label: "所有者", value: displayValue(metadata?.owner) },
    { label: "地区码", value: displayValue(metadata?.regionCode) },
    { label: "机构码", value: displayValue(metadata?.organizationCode) },
    { label: "应用场景", value: displayValue(metadata?.applicationScenario) },
    { label: "描述", value: displayValue(metadata?.description) }
  ];
}

function resourceFields(resource: any): ResultField[] {
  return [
    { label: "资源类型", value: displayValue(resource?.componentType) },
    { label: "存储方式", value: displayValue(resource?.storageType) },
    { label: "访问方式", value: displayValue(resource?.accessMethod) },
    { label: "内容类型", value: displayValue(resource?.contentType) },
    { label: "格式", value: displayValue(resource?.format) },
    { label: "文件名", value: displayValue(resource?.extra?.filename) },
    { label: "大小", value: formatSize(resource?.size) },
    { label: "资源定位", value: displayValue(resource?.locator), mono: true }
  ];
}

function buildActions(identifier: string, resource: any, downloadResult?: any | null): ResultAction[] {
  const componentType = String(resource?.componentType || "").toLowerCase();
  const locator = String(resource?.locator || "");
  const locatorHttp = /^https?:\/\//i.test(locator);
  if (componentType === "service") {
    return [
      { key: "openLocator", label: "打开服务", disabled: !locatorHttp },
      { key: "copyServiceCurl", label: "复制 curl", disabled: !locator },
      { key: "copyLocator", label: "复制 locator", disabled: !locator }
    ];
  }
  if (componentType === "tool") {
    return [
      { key: "openTool", label: "调用工具", disabled: !identifier },
      { key: "copyToolRequest", label: "复制调用模板", disabled: !identifier },
      { key: "copyLocator", label: "复制 locator", disabled: !locator }
    ];
  }
  return [
    { key: "generateDownload", label: downloadResult?.url ? "刷新下载地址" : "生成下载地址", disabled: !identifier },
    { key: "openDownload", label: "打开资源", disabled: !downloadResult?.url },
    { key: "openLocator", label: "打开 locator", disabled: !locatorHttp },
    { key: "copyLocator", label: "复制 locator", disabled: !locator }
  ];
}

function buildMetadataItem(identifier: string, metadata: any, resolvedByNodeCode = "", success = true, modeLabel = "单条元数据解析"): ResultItem {
  return {
    identifier,
    modeLabel,
    kindLabel: "metadata",
    kind: "metadata",
    success,
    resolvedByNodeCode,
    metadata,
    fields: metadataFields(metadata),
    actions: [],
    raw: metadata
  };
}

function buildDigitalObjectItem(identifier: string, object: any, resource: any, resolvedByNodeCode = "", success = true, modeLabel = "单条完整解析"): ResultItem {
  const metadata = object?.metadata || null;
  const finalResource = resource || object?.resource || null;
  return {
    identifier,
    modeLabel,
    kindLabel: "digitalObject",
    kind: "digitalObject",
    success,
    resolvedByNodeCode,
    componentType: displayValue(finalResource?.componentType),
    metadata,
    resource: finalResource,
    fields: [
      { label: "对象名称", value: displayValue(metadata?.name) },
      { label: "标识状态", value: displayValue(metadata?.status || object?.status) },
      { label: "资源类型", value: displayValue(finalResource?.componentType) },
      { label: "资源定位", value: displayValue(finalResource?.locator), mono: true }
    ],
    metadataFields: metadataFields(metadata),
    resourceFields: resourceFields(finalResource),
    actions: buildActions(identifier, finalResource),
    raw: { object, resource: finalResource }
  };
}

async function copyText(value: string, successMessage: string): Promise<void> {
  if (!value) {
    return;
  }
  try {
    await navigator.clipboard.writeText(value);
    statusMessage.value = successMessage;
  } catch {
    statusMessage.value = "复制失败，请手动复制。";
  }
}

function parseJsonObject(raw: string): Record<string, unknown> {
  if (!raw.trim()) {
    return {};
  }
  const parsed = JSON.parse(raw);
  if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
    throw new Error("必须输入 JSON 对象。");
  }
  return parsed as Record<string, unknown>;
}

async function resolveMetadataSingle(): Promise<void> {
  if (!identifierInput.value) {
    return;
  }
  loading.value = true;
  try {
    const response = await requestEnvelope<any>(`${currentBackendProxyBase.value}/api/v2/identifiers/${identifierInput.value}/metadata`);
    resultItems.value = [buildMetadataItem(identifierInput.value, response.data, "", true, "单条元数据解析")];
    lastResolveLabel.value = "单条元数据解析";
    statusMessage.value = "单条元数据解析完成。";
  } catch (error) {
    statusMessage.value = error instanceof Error ? error.message : String(error);
  } finally {
    loading.value = false;
  }
}

async function resolveDigitalObjectSingle(): Promise<void> {
  if (!identifierInput.value) {
    return;
  }
  loading.value = true;
  try {
    const [objectResp, resourceResp] = await Promise.all([
      requestEnvelope<any>(`${currentBackendProxyBase.value}/api/v2/identifiers/${identifierInput.value}/object`),
      requestEnvelope<any>(`${currentBackendProxyBase.value}/api/v2/identifiers/${identifierInput.value}/component`).catch(() => ({ data: null } as any))
    ]);
    resultItems.value = [buildDigitalObjectItem(identifierInput.value, objectResp.data, resourceResp.data, "", true, "单条完整解析")];
    lastResolveLabel.value = "单条完整解析";
    statusMessage.value = "单条完整解析完成。";
  } catch (error) {
    statusMessage.value = error instanceof Error ? error.message : String(error);
  } finally {
    loading.value = false;
  }
}

async function resolveMetadataBatch(): Promise<void> {
  if (!parsedBatchIdentifiers.value.length) {
    return;
  }
  loadingBatch.value = true;
  try {
    const response = await requestEnvelope<any>(`${currentBackendProxyBase.value}/api/v2/identifiers/resolve/batch/distributed`, {
      method: "POST",
      body: asJsonBody(parsedBatchIdentifiers.value)
    });
    const items = Array.isArray(response.data?.items) ? response.data.items : [];
    resultItems.value = items.map((item: any) =>
      buildMetadataItem(item.identifier, item.metadata, item.resolvedByNodeCode || "", item.success !== false, "批量元数据解析")
    );
    lastResolveLabel.value = `批量元数据解析 ${items.length} 条`;
    statusMessage.value = "批量元数据解析完成。";
  } catch (error) {
    statusMessage.value = error instanceof Error ? error.message : String(error);
  } finally {
    loadingBatch.value = false;
  }
}

async function resolveDigitalObjectBatch(): Promise<void> {
  if (!parsedBatchIdentifiers.value.length) {
    return;
  }
  loadingBatch.value = true;
  try {
    const [objectResp, resourceResp] = await Promise.all([
      requestEnvelope<any>(`${currentBackendProxyBase.value}/api/v2/identifiers/resolve/batch/object/distributed`, {
        method: "POST",
        body: asJsonBody(parsedBatchIdentifiers.value)
      }),
      requestEnvelope<any>(`${currentBackendProxyBase.value}/api/v2/identifiers/resolve/batch/component/distributed`, {
        method: "POST",
        body: asJsonBody(parsedBatchIdentifiers.value)
      })
    ]);
    const objectItems = Array.isArray(objectResp.data?.items) ? objectResp.data.items : [];
    const resourceItems = Array.isArray(resourceResp.data?.items) ? resourceResp.data.items : [];
    const resourceMap = new Map<string, any>(resourceItems.map((item: any) => [item.identifier, item] as [string, any]));
    resultItems.value = objectItems.map((item: any) => {
      const resourceItem = resourceMap.get(item.identifier);
      return buildDigitalObjectItem(
        item.identifier,
        item.digitalObject,
        resourceItem?.resource,
        item.resolvedByNodeCode || resourceItem?.resolvedByNodeCode || "",
        item.success !== false,
        "批量完整解析"
      );
    });
    lastResolveLabel.value = `批量完整解析 ${resultItems.value.length} 条`;
    statusMessage.value = "批量完整解析完成。";
  } catch (error) {
    statusMessage.value = error instanceof Error ? error.message : String(error);
  } finally {
    loadingBatch.value = false;
  }
}

function closeEditors(): void {
  statusEditorVisible.value = false;
  metadataEditorVisible.value = false;
  resourceEditorVisible.value = false;
  editorIdentifier.value = "";
}

function openStatusEditor(item: ResultItem): void {
  editorIdentifier.value = item.identifier;
  statusForm.value = {
    status: String(item.metadata?.status || "active").toLowerCase() === "inactive" ? "inactive" : "active",
    reason: ""
  };
  statusEditorVisible.value = true;
  metadataEditorVisible.value = false;
  resourceEditorVisible.value = false;
}

function openMetadataEditor(item: ResultItem): void {
  editorIdentifier.value = item.identifier;
  patchText.value = prettyJson({ description: item.metadata?.description || "" });
  metadataEditorVisible.value = true;
  statusEditorVisible.value = false;
  resourceEditorVisible.value = false;
}

function openResourceEditor(item: ResultItem): void {
  editorIdentifier.value = item.identifier;
  componentText.value = prettyJson(item.resource || parseJsonObject(defaultComponentPayload()));
  resourceEditorVisible.value = true;
  statusEditorVisible.value = false;
  metadataEditorVisible.value = false;
}

async function submitStatusPatch(): Promise<void> {
  if (!editorIdentifier.value) {
    return;
  }
  loading.value = true;
  try {
    await requestEnvelope<boolean>(`${currentBackendProxyBase.value}/api/v2/identifiers/${editorIdentifier.value}/status`, {
      method: "PATCH",
      body: asJsonBody(statusForm.value)
    });
    resultItems.value = resultItems.value.map((item) => {
      if (item.identifier !== editorIdentifier.value) {
        return item;
      }
      if (item.metadata) {
        item.metadata.status = statusForm.value.status;
        item.metadataFields = metadataFields(item.metadata);
      }
      if (item.kind === "digitalObject") {
        item.fields = [
          { label: "对象名称", value: displayValue(item.metadata?.name) },
          { label: "标识状态", value: displayValue(item.metadata?.status) },
          { label: "资源类型", value: displayValue(item.resource?.componentType) },
          { label: "资源定位", value: displayValue(item.resource?.locator), mono: true }
        ];
      } else {
        item.fields = metadataFields(item.metadata);
      }
      return item;
    });
    resultItems.value = [...resultItems.value];
    statusMessage.value = `状态已更新：${editorIdentifier.value} -> ${statusForm.value.status}`;
    statusEditorVisible.value = false;
  } catch (error) {
    statusMessage.value = error instanceof Error ? error.message : String(error);
  } finally {
    loading.value = false;
  }
}

async function submitMetadataPatch(): Promise<void> {
  if (!editorIdentifier.value) {
    return;
  }
  loading.value = true;
  try {
    await requestEnvelope<boolean>(`${currentBackendProxyBase.value}/api/v2/identifiers/${editorIdentifier.value}/metadata`, {
      method: "PATCH",
      body: asJsonBody(parseJsonObject(patchText.value))
    });
    statusMessage.value = `元数据已更新：${editorIdentifier.value}`;
    metadataEditorVisible.value = false;
  } catch (error) {
    statusMessage.value = error instanceof Error ? error.message : String(error);
  } finally {
    loading.value = false;
  }
}

async function submitResourcePatch(): Promise<void> {
  if (!editorIdentifier.value) {
    return;
  }
  loading.value = true;
  try {
    await requestEnvelope<boolean>(`${currentBackendProxyBase.value}/api/v2/identifiers/${editorIdentifier.value}/component`, {
      method: "PUT",
      body: asJsonBody(parseJsonObject(componentText.value))
    });
    statusMessage.value = `关联资源已更新：${editorIdentifier.value}`;
    resourceEditorVisible.value = false;
  } catch (error) {
    statusMessage.value = error instanceof Error ? error.message : String(error);
  } finally {
    loading.value = false;
  }
}

async function removeComponent(): Promise<void> {
  if (!editorIdentifier.value) {
    return;
  }
  loading.value = true;
  try {
    await requestEnvelope<boolean>(`${currentBackendProxyBase.value}/api/v2/identifiers/${editorIdentifier.value}/component`, {
      method: "DELETE"
    });
    statusMessage.value = `关联资源已删除：${editorIdentifier.value}`;
    resourceEditorVisible.value = false;
  } catch (error) {
    statusMessage.value = error instanceof Error ? error.message : String(error);
  } finally {
    loading.value = false;
  }
}

async function deleteIdentifier(identifier: string): Promise<void> {
  if (!identifier || !window.confirm(`确认注销标识 ${identifier} 吗？该操作不可撤销。`)) {
    return;
  }
  loading.value = true;
  try {
    await requestEnvelope<boolean>(`${currentBackendProxyBase.value}/api/v2/identifiers/${identifier}`, {
      method: "DELETE"
    });
    resultItems.value = resultItems.value.filter((item) => item.identifier !== identifier);
    statusMessage.value = `标识已注销：${identifier}`;
  } catch (error) {
    statusMessage.value = error instanceof Error ? error.message : String(error);
  } finally {
    loading.value = false;
  }
}

async function createDownloadLink(item: ResultItem): Promise<void> {
  loading.value = true;
  try {
    const response = await requestEnvelope<any>(`${currentBackendProxyBase.value}/api/v2/downloads/presign`, {
      method: "POST",
      body: asJsonBody({ identifier: item.identifier, expiresInSeconds: 600 })
    });
    item.downloadResult = response.data;
    item.actions = buildActions(item.identifier, item.resource, item.downloadResult);
    resultItems.value = [...resultItems.value];
    statusMessage.value = `下载地址已生成：${item.identifier}`;
  } catch (error) {
    statusMessage.value = error instanceof Error ? error.message : String(error);
  } finally {
    loading.value = false;
  }
}

async function copyServiceCurl(item: ResultItem): Promise<void> {
  const locator = String(item.resource?.locator || "");
  const method = displayValue(item.resource?.accessMethod).toUpperCase() === "-" ? "GET" : displayValue(item.resource?.accessMethod).toUpperCase();
  await copyText(`curl -X ${method} "${locator}"`, `curl 已复制：${item.identifier}`);
}

async function copyToolRequest(item?: ResultItem): Promise<void> {
  const identifier = item?.identifier || editorIdentifier.value;
  await copyText(prettyJson({ identifier, args: parseJsonObject(toolArgsText.value), timeoutMs: toolTimeoutMs.value }), "工具调用模板已复制到剪贴板。");
}

async function executeTool(): Promise<void> {
  if (!editorIdentifier.value) {
    return;
  }
  loadingTool.value = true;
  try {
    const response = await requestEnvelope<any>(`${currentBackendProxyBase.value}/api/v2/identifiers/${editorIdentifier.value}/tool/execute`, {
      method: "POST",
      body: asJsonBody({ args: parseJsonObject(toolArgsText.value), timeoutMs: toolTimeoutMs.value })
    });
    toolExecuteResult.value = response.data;
    statusMessage.value = `工具调用完成：${editorIdentifier.value}`;
  } catch (error) {
    toolExecuteResult.value = { error: error instanceof Error ? error.message : String(error) };
    statusMessage.value = error instanceof Error ? error.message : String(error);
  } finally {
    loadingTool.value = false;
  }
}

async function handleItemAction(item: ResultItem, key: ResultActionKey): Promise<void> {
  if (key === "generateDownload") {
    await createDownloadLink(item);
    return;
  }
  if (key === "openDownload") {
    if (item.downloadResult?.url) {
      window.open(item.downloadResult.url, "_blank", "noopener,noreferrer");
    }
    return;
  }
  if (key === "openLocator") {
    const locator = String(item.resource?.locator || "");
    if (/^https?:\/\//i.test(locator)) {
      window.open(locator, "_blank", "noopener,noreferrer");
    }
    return;
  }
  if (key === "copyLocator") {
    await copyText(String(item.resource?.locator || ""), `locator 已复制：${item.identifier}`);
    return;
  }
  if (key === "copyServiceCurl") {
    await copyServiceCurl(item);
    return;
  }
  if (key === "openTool") {
    editorIdentifier.value = item.identifier;
    toolArgsText.value = '{\n  "input": "demo"\n}';
    toolExecuteResult.value = null;
    toolInvokerVisible.value = true;
    return;
  }
  if (key === "copyToolRequest") {
    await copyToolRequest(item);
  }
}
</script>
