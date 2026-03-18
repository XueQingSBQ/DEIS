<template>
  <div class="page-shell">
    <section class="page-hero panel">
      <div>
        <p class="eyebrow">{{ t.heroEyebrow }}</p>
        <h2>{{ t.heroTitle }}</h2>
        <p class="panel-caption">{{ t.heroCaption }}</p>
      </div>
      <div class="page-hero-actions page-toolbar">
        <button class="button ghost" type="button" @click="loadPreset('resource')">{{ t.presetResource }}</button>
        <button class="button ghost" type="button" @click="loadPreset('service')">{{ t.presetService }}</button>
        <button class="button ghost" type="button" @click="loadPreset('tool')">{{ t.presetTool }}</button>
        <button class="button ghost" type="button" @click="resetWorkflow">{{ t.resetWorkflow }}</button>
      </div>
    </section>

    <section class="summary-grid compact-summary-grid">
      <article class="summary-card">
        <span class="summary-label">{{ t.summaryCurrentNode }}</span>
        <strong class="summary-value">{{ currentNode.label }}</strong>
        <small>{{ currentNode.code }}</small>
      </article>
      <article class="summary-card">
        <span class="summary-label">{{ t.summaryCurrentIdentifier }}</span>
        <strong class="summary-value mono-wrap">{{ createdIdentifier || '-' }}</strong>
        <small>{{ createdIdentifier ? t.summaryIdentifierReady : t.summaryIdentifierEmpty }}</small>
      </article>
      <article class="summary-card">
        <span class="summary-label">{{ t.summaryCurrentBinding }}</span>
        <strong class="summary-value">{{ bindingTypeLabel }}</strong>
        <small>{{ componentSnapshot ? componentTypeLabel(componentSnapshot.componentType) : t.summaryNoComponent }}</small>
      </article>
      <article class="summary-card">
        <span class="summary-label">{{ t.summaryVersionDownload }}</span>
        <strong class="summary-value">{{ versionItems.length }} {{ t.summaryVersionUnit }}</strong>
        <small>{{ downloadResult?.url ? t.summaryDownloadReady : t.summaryDownloadEmpty }}</small>
      </article>
    </section>

    <section class="panel page-card">
      <div class="panel-head">
        <div>
          <p class="eyebrow">{{ t.workflowEyebrow }}</p>
          <h3>{{ t.workflowTitle }}</h3>
        </div>
      </div>
      <div class="workflow-strip">
        <div class="workflow-step" :class="createdIdentifier ? 'workflow-step-completed' : 'workflow-step-active'">
          <span class="workflow-index">1</span>
          <div>
            <strong>{{ t.workflowRegisterTitle }}</strong>
            <p class="field-help">{{ t.workflowRegisterDesc }}</p>
          </div>
        </div>
        <div class="workflow-step" :class="uploadSession ? 'workflow-step-completed' : createdIdentifier && supportsUploadSession && bindingMode === 'upload' ? 'workflow-step-active' : 'workflow-step-pending'">
          <span class="workflow-index">2</span>
          <div>
            <strong>{{ t.workflowUploadTitle }}</strong>
            <p class="field-help">{{ t.workflowUploadDesc }}</p>
          </div>
        </div>
        <div class="workflow-step" :class="componentSnapshot ? 'workflow-step-completed' : createdIdentifier ? 'workflow-step-active' : 'workflow-step-pending'">
          <span class="workflow-index">3</span>
          <div>
            <strong>{{ t.workflowBindTitle }}</strong>
            <p class="field-help">{{ t.workflowBindDesc }}</p>
          </div>
        </div>
        <div class="workflow-step" :class="downloadResult?.url ? 'workflow-step-completed' : componentSnapshot ? 'workflow-step-active' : 'workflow-step-pending'">
          <span class="workflow-index">4</span>
          <div>
            <strong>{{ t.workflowDownloadTitle }}</strong>
            <p class="field-help">{{ t.workflowDownloadDesc }}</p>
          </div>
        </div>
      </div>
      <p v-if="statusMessage" class="field-help status-inline">{{ statusMessage }}</p>
    </section>

    <section class="results-grid onboarding-grid">
      <article class="panel page-card">
        <div class="panel-head">
          <div>
            <p class="eyebrow">{{ t.registerEyebrow }}</p>
            <h3>{{ t.registerTitle }}</h3>
          </div>
        </div>
        <div class="form-grid">
          <label class="field-block">
            <span class="field-label">{{ t.regionCodeLabel }}</span>
            <input v-model.trim="registerForm.regionCode" class="text-input mono" type="text" disabled />
            <small class="field-help">{{ t.currentRegionPrefix }} {{ registerForm.regionCode }}</small>
          </label>
          <label class="field-block">
            <span class="field-label">{{ t.organizationCodeLabel }}</span>
            <input v-model.trim="registerForm.organizationCode" class="text-input mono" type="text" disabled />
            <small class="field-help">{{ t.organizationHelp }}</small>
          </label>
          <label class="field-block">
            <span class="field-label">{{ t.industryLabel }}</span>
            <select v-model="registerForm.industry" class="select-input">
              <option v-for="item in industryOptions" :key="item.code" :value="item.name">{{ item.label }}</option>
            </select>
          </label>
          <label class="field-block">
            <span class="field-label">{{ t.dataCategoryLabel }}</span>
            <select v-model="registerForm.dataCategory" class="select-input">
              <option v-for="item in dataCategoryOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
            </select>
          </label>
          <label class="field-block">
            <span class="field-label">{{ t.registrationTypeLabel }}</span>
            <select v-model="registerForm.registrationType" class="select-input">
              <option v-for="item in registrationTypeOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
            </select>
          </label>
          <label class="field-block">
            <span class="field-label">{{ t.entityTypeLabel }}</span>
            <select v-model="registerForm.entityType" class="select-input">
              <option v-for="item in entityTypeOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
            </select>
          </label>
          <label class="field-block">
            <span class="field-label">{{ t.ownerLabel }}</span>
            <input v-model.trim="registerForm.owner" class="text-input" type="text" :placeholder="t.ownerPlaceholder" />
          </label>
          <label class="field-block">
            <span class="field-label">{{ t.nameLabel }}</span>
            <input v-model.trim="registerForm.name" class="text-input" type="text" :placeholder="t.namePlaceholder" />
          </label>
          <label class="field-block field-block-wide">
            <span class="field-label">{{ t.descriptionLabel }}</span>
            <textarea v-model.trim="registerForm.description" class="text-area" rows="3" :placeholder="t.descriptionPlaceholder"></textarea>
          </label>
          <label class="field-block field-block-wide">
            <span class="field-label">{{ t.scenarioLabel }}</span>
            <input v-model.trim="registerForm.applicationScenario" class="text-input" type="text" :placeholder="t.scenarioPlaceholder" />
          </label>
          <label class="field-block field-block-wide">
            <span class="field-label">{{ t.additionalAttributesLabel }}</span>
            <textarea v-model.trim="additionalAttributesText" class="text-area mono" rows="4"></textarea>
          </label>
        </div>
        <div class="action-row compact-actions">
          <button class="button warm" type="button" :disabled="registering" @click="registerIdentifier">{{ registering ? t.registering : t.registerNow }}</button>
          <button class="button ghost" type="button" :disabled="!createdIdentifier" @click="copyIdentifier">{{ copyIdentifierLabel }}</button>
        </div>
      </article>

      <article class="panel page-card">
        <div class="panel-head">
          <div>
            <p class="eyebrow">{{ t.bindingEyebrow }}</p>
            <h3>{{ t.bindingTitle }}</h3>
          </div>
        </div>

        <div class="segmented-control">
          <button class="segment" :class="bindingType === 'resource' ? 'segment-active' : ''" type="button" @click="setBindingType('resource')">{{ t.bindingTypeResource }}</button>
          <button class="segment" :class="bindingType === 'tool' ? 'segment-active' : ''" type="button" @click="setBindingType('tool')">{{ t.bindingTypeTool }}</button>
          <button class="segment" :class="bindingType === 'service' ? 'segment-active' : ''" type="button" @click="setBindingType('service')">{{ t.bindingTypeService }}</button>
        </div>

        <div v-if="supportsUploadSession" class="segmented-control binding-mode-control">
          <button class="segment" :class="bindingMode === 'upload' ? 'segment-active' : ''" type="button" @click="setBindingMode('upload')">{{ t.modeUpload }}</button>
          <button class="segment" :class="bindingMode === 'direct' ? 'segment-active' : ''" type="button" @click="setBindingMode('direct')">{{ t.modeDirect }}</button>
        </div>

        <div v-if="bindingMode === 'upload'" class="resource-bind-stack">
          <p class="field-help">{{ uploadBindHelpText }}</p>
          <div class="form-grid">
            <label class="field-block">
              <span class="field-label">{{ t.uploadProfileLabel }}</span>
              <select v-model="uploadForm.profile" class="select-input">
                <option v-for="item in visibleUploadProfileOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
              </select>
              <small class="field-help">{{ currentUploadProfileSummary }}</small>
            </label>
            <label class="field-block">
              <span class="field-label">{{ t.fileNameLabel }}</span>
              <input v-model.trim="uploadForm.filename" class="text-input" type="text" />
            </label>
            <label class="field-block">
              <span class="field-label">{{ t.localFileLabel }}</span>
              <input :key="uploadFileInputKey" class="text-input" type="file" :accept="currentUploadAccept" @change="onUploadFileSelected" />
              <small class="field-help">{{ t.localFileHelp }}</small>
            </label>
            <label class="field-block">
              <span class="field-label">{{ t.sizeBytesLabel }}</span>
              <input :value="uploadForm.size" class="text-input mono" type="number" min="0" readonly />
              <small class="field-help">{{ t.sizeBytesHelp }}</small>
            </label>
            <label class="field-block">
              <span class="field-label">SHA-256</span>
              <input v-model.trim="uploadForm.sha256" class="text-input mono" type="text" />
              <small class="field-help">{{ uploadShaHelpText }}</small>
            </label>
          </div>
          <div class="action-row compact-actions">
            <button class="button ghost" type="button" :disabled="!createdIdentifier || bindingBusy" @click="createUploadSession">{{ bindingBusy ? t.creating : t.createUploadSession }}</button>
            <button class="button ghost" type="button" :disabled="!uploadPutUrl || !selectedUploadFile || bindingBusy" @click="uploadSelectedFile">{{ bindingBusy ? t.uploading : t.uploadSelectedFile }}</button>
            <button class="button warm" type="button" :disabled="!uploadSession || bindingBusy" @click="completeUpload">{{ bindingBusy ? t.finishing : completeUploadLabel }}</button>
            <button class="button ghost" type="button" :disabled="!uploadPutUrl" @click="copyUploadUrl">{{ t.copyPutUrl }}</button>
            <button class="button ghost" type="button" :disabled="!selectedUploadFile && !uploadForm.filename" @click="clearSelectedUploadFile(true)">{{ t.clearFile }}</button>
          </div>
          <div v-if="uploadSession" class="hint-card">
            <div class="hint-card-head">
              <strong>{{ t.uploadSessionReady }}</strong>
              <span class="status-pill status-good">{{ t.uploadSessionId }}: {{ uploadSession.uploadId }}</span>
            </div>
            <div class="kv-grid">
              <div class="kv-item">
                <span>{{ t.bucketLabel }}</span>
                <strong class="mono-wrap">{{ uploadSession.bucket || '-' }}</strong>
              </div>
              <div class="kv-item">
                <span>{{ t.objectKeyLabel }}</span>
                <strong class="mono-wrap">{{ uploadSession.objectKey || '-' }}</strong>
              </div>
              <div class="kv-item">
                <span>{{ t.putUrlLabel }}</span>
                <strong class="mono-wrap">{{ uploadPutUrl || '-' }}</strong>
              </div>
              <div class="kv-item">
                <span>{{ t.putExpiresLabel }}</span>
                <strong>{{ uploadSession.put?.expiresInSeconds ?? '-' }}</strong>
              </div>
            </div>
            <pre class="json-view compact-json">{{ prettyJson(uploadSession.put?.headers || {}) }}</pre>
          </div>
        </div>

        <div v-else class="resource-bind-stack">
          <p class="field-help">{{ directBindHelpText }}</p>
          <div class="form-grid">
            <label class="field-block">
              <span class="field-label">{{ t.storageTypeLabel }}</span>
              <select v-model="directForm.storageType" class="select-input">
                <option v-for="item in directStorageOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
              </select>
            </label>
            <label class="field-block">
              <span class="field-label">{{ t.accessMethodLabel }}</span>
              <select v-model="directForm.accessMethod" class="select-input">
                <option v-for="item in directAccessOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
              </select>
            </label>
            <label class="field-block field-block-wide">
              <span class="field-label">{{ t.locatorLabel }}</span>
              <input v-model.trim="directForm.locator" class="text-input mono" type="text" :placeholder="t.locatorPlaceholder" />
            </label>
            <label class="field-block">
              <span class="field-label">{{ t.contentTypeLabel }}</span>
              <input v-model.trim="directForm.contentType" class="text-input" type="text" :placeholder="t.contentTypePlaceholder" />
            </label>
            <label class="field-block">
              <span class="field-label">{{ t.formatLabel }}</span>
              <input v-model.trim="directForm.format" class="text-input" type="text" :placeholder="t.formatPlaceholder" />
            </label>
            <label class="field-block">
              <span class="field-label">{{ t.sizeBytesLabel }}</span>
              <input v-model.number="directForm.size" class="text-input mono" type="number" min="0" />
            </label>
            <label class="field-block">
              <span class="field-label">SHA-256</span>
              <input v-model.trim="directForm.hashValue" class="text-input mono" type="text" />
            </label>
            <label class="field-block field-block-wide">
              <span class="field-label">{{ t.extraJsonLabel }}</span>
              <textarea v-model.trim="directExtraText" class="text-area mono" rows="5"></textarea>
            </label>
          </div>
          <div class="action-row compact-actions">
            <button class="button warm" type="button" :disabled="!createdIdentifier || bindingBusy" @click="bindDirectComponent">{{ bindingBusy ? t.bindingNow : bindDirectLabel }}</button>
            <button class="button ghost" type="button" :disabled="!componentSnapshot || bindingBusy" @click="removeComponent">{{ t.removeBinding }}</button>
          </div>
        </div>
      </article>

      <article class="panel page-card">
        <div class="panel-head">
          <div>
            <p class="eyebrow">{{ t.verifyEyebrow }}</p>
            <h3>{{ t.verifyTitle }}</h3>
          </div>
        </div>
        <div class="action-row compact-actions compact-head-actions">
          <button class="button ghost" type="button" :disabled="!createdIdentifier || loadingState" @click="refreshSnapshots">{{ loadingState ? t.refreshing : t.refreshState }}</button>
          <button class="button ghost" type="button" :disabled="!createdIdentifier || loadingState" @click="createDownloadPresign">{{ loadingState ? t.generating : t.generateDownload }}</button>
          <button class="button ghost" type="button" :disabled="!downloadResult?.url" @click="copyDownloadUrl">{{ t.copyDownloadUrl }}</button>
        </div>

        <div class="hint-card">
          <div class="hint-card-head">
            <strong>{{ t.componentSnapshotTitle }}</strong>
            <span class="status-pill" :class="componentSnapshot ? 'status-good' : 'status-muted'">{{ componentSnapshot ? t.bound : t.notBound }}</span>
          </div>
          <div class="kv-grid">
            <div class="kv-item">
              <span>{{ t.componentTypeLabel }}</span>
              <strong>{{ componentSnapshot ? componentTypeLabel(componentSnapshot.componentType) : '-' }}</strong>
            </div>
            <div class="kv-item">
              <span>{{ t.storageTypeLabel }}</span>
              <strong>{{ componentSnapshot?.storageType || '-' }}</strong>
            </div>
            <div class="kv-item">
              <span>{{ t.accessMethodLabel }}</span>
              <strong>{{ componentSnapshot?.accessMethod || '-' }}</strong>
            </div>
            <div class="kv-item">
              <span>{{ t.locatorLabel }}</span>
              <strong class="mono-wrap">{{ componentSnapshot?.locator || '-' }}</strong>
            </div>
          </div>
          <pre class="json-view compact-json">{{ prettyJson(componentSnapshot || {}) }}</pre>
        </div>

        <div class="hint-card">
          <div class="hint-card-head">
            <strong>{{ t.versionTitle }}</strong>
            <span class="status-pill status-good">{{ versionItems.length }} {{ t.summaryVersionUnit }}</span>
          </div>
          <div v-if="versionItems.length" class="entity-list">
            <div v-for="(item, index) in versionItems" :key="item.versionNo ?? index" class="entity-list-item">
              <div>
                <strong>{{ t.versionPrefix }} {{ item.versionNo ?? index + 1 }}</strong>
                <p class="field-help mono">{{ item.locator || item.objectKey || '-' }}</p>
                <p class="field-help">{{ item.contentType || item.format || '-' }} / {{ item.createdAt || '-' }}</p>
              </div>
            </div>
          </div>
          <div v-else class="panel-empty compact-empty">{{ t.noVersionData }}</div>
        </div>

        <div class="hint-card">
          <div class="hint-card-head">
            <strong>{{ t.downloadCardTitle }}</strong>
            <span class="status-pill" :class="downloadResult?.url ? 'status-good' : 'status-muted'">{{ downloadResult?.url ? t.generated : t.notGenerated }}</span>
          </div>
          <div class="kv-grid">
            <div class="kv-item">
              <span>{{ t.downloadUrlLabel }}</span>
              <strong class="mono-wrap">{{ downloadResult?.url || '-' }}</strong>
            </div>
            <div class="kv-item">
              <span>{{ t.fileNameLabel }}</span>
              <strong>{{ downloadResult?.filename || '-' }}</strong>
            </div>
            <div class="kv-item">
              <span>{{ t.contentTypeLabel }}</span>
              <strong>{{ downloadResult?.mime || '-' }}</strong>
            </div>
            <div class="kv-item">
              <span>{{ t.expireSecondsLabel }}</span>
              <strong>{{ downloadResult?.expiresInSeconds ?? '-' }}</strong>
            </div>
          </div>
        </div>
      </article>
      <article class="panel page-card">
        <div class="panel-head">
          <div>
            <p class="eyebrow">{{ t.registerEyebrow }}</p>
            <h3>{{ t.registerResultTitle }}</h3>
          </div>
          <span class="status-pill" :class="createdIdentifier ? 'status-good' : 'status-muted'">{{ createdIdentifier ? t.registerResultReady : t.summaryIdentifierEmpty }}</span>
        </div>
        <div class="hint-card register-result-card">
          <div class="register-identifier-hero">
            <span class="register-identifier-label">{{ t.registerIdentifierCard }}</span>
            <strong class="register-identifier-value mono-wrap">{{ createdIdentifier || '-' }}</strong>
            <p class="field-help register-identifier-caption">{{ metadataDisplay.name || metadataDisplay.description || t.summaryIdentifierEmpty }}</p>
          </div>
          <div class="register-summary-grid">
            <div class="hint-card register-summary-card">
              <div class="hint-card-head">
                <strong>{{ t.registerStateCard }}</strong>
              </div>
              <div class="kv-grid register-kv-grid">
                <div class="kv-item">
                  <span>{{ t.registerStateCard }}</span>
                  <strong>{{ registrationStatusLabel }}</strong>
                </div>
                <div class="kv-item">
                  <span>{{ t.registerResultTitle }}</span>
                  <strong>{{ registrationDetail?.createTime || metadataDisplay.createTime || '-' }}</strong>
                </div>
              </div>
              <div class="register-description-block">
                <span>{{ t.registerStateCard }}</span>
                <strong>{{ createdIdentifier ? t.registerStateReady : t.registerStateEmpty }}</strong>
              </div>
            </div>
            <div class="hint-card register-summary-card">
              <div class="hint-card-head">
                <strong>{{ t.registerMetadataCard }}</strong>
              </div>
              <div class="kv-grid register-kv-grid">
                <div class="kv-item">
                  <span>{{ t.nameLabel }}</span>
                  <strong>{{ metadataDisplay.name || '-' }}</strong>
                </div>
                <div class="kv-item">
                  <span>{{ t.ownerLabel }}</span>
                  <strong>{{ metadataDisplay.owner || '-' }}</strong>
                </div>
                <div class="kv-item">
                  <span>{{ t.industryLabel }}</span>
                  <strong>{{ metadataDisplay.industry || '-' }}</strong>
                </div>
                <div class="kv-item">
                  <span>{{ t.entityTypeLabel }}</span>
                  <strong>{{ metadataDisplay.entityType || '-' }}</strong>
                </div>
              </div>
              <div class="register-description-block">
                <span>{{ t.descriptionLabel }}</span>
                <strong>{{ createdIdentifier ? (metadataDisplay.description || t.registerMetadataReady) : t.registerMetadataEmpty }}</strong>
              </div>
            </div>
            <div class="hint-card register-summary-card register-resource-card">
              <div class="hint-card-head">
                <strong>{{ t.registerResourceCard }}</strong>
              </div>
              <div class="kv-grid register-kv-grid">
                <div class="kv-item">
                  <span>{{ t.componentTypeLabel }}</span>
                  <strong>{{ resourceDisplay.componentType ? componentTypeLabel(resourceDisplay.componentType) : '-' }}</strong>
                </div>
                <div class="kv-item">
                  <span>{{ t.storageTypeLabel }}</span>
                  <strong>{{ resourceDisplay.storageType || '-' }}</strong>
                </div>
                <div class="kv-item">
                  <span>{{ t.accessMethodLabel }}</span>
                  <strong>{{ resourceDisplay.accessMethod || '-' }}</strong>
                </div>
                <div class="kv-item">
                  <span>{{ t.formatLabel }}</span>
                  <strong>{{ resourceDisplay.format || resourceDisplay.contentType || '-' }}</strong>
                </div>
              </div>
              <div class="register-locator-block">
                <div class="hint-card-head register-locator-head">
                  <span>{{ t.locatorLabel }}</span>
                  <div class="action-row register-locator-actions">
                    <button class="button ghost" type="button" :disabled="!resourceLocator" @click="openResourceLocator">{{ t.openLocator }}</button>
                    <button class="button ghost" type="button" :disabled="!resourceLocator" @click="copyResourceLocator">{{ t.copyLocator }}</button>
                  </div>
                </div>
                <strong class="mono-wrap">{{ resourceLocator || '-' }}</strong>
              </div>
              <div class="register-description-block">
                <span>{{ t.registerResourceCard }}</span>
                <strong>{{ resourceLocator ? t.registerResourceReady : t.registerResourceEmpty }}</strong>
              </div>
            </div>
          </div>
        </div>
      </article>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from "vue";

import { asJsonBody, requestEnvelope } from "../../shared/api/http";
import { useStatusToast } from "../../shared/composables/useStatusToast";
import { useConsoleRuntime } from "../../shared/composables/useConsoleRuntime";
import { industryCatalog } from "../../shared/constants/industryCatalog";
import { prettyJson } from "../../shared/utils/format";

type BindingType = "resource" | "tool" | "service";
type BindingMode = "upload" | "direct";
type PresetMode = BindingType;

interface RegistrationDetail {
  identifier: string;
  status?: string;
  metadataCreated?: boolean;
  resourceBound?: boolean;
  createTime?: string;
  updateTime?: string;
}

interface UploadProfileOption {
  value: string;
  label: string;
  componentType: Exclude<BindingType, "service">;
  entityTypes: string[];
  accept: string;
  format: string;
  mime: string;
  requireSha256: boolean;
  summary: string;
}

interface SelectOption {
  value: string;
  label: string;
}

const t = {
  heroEyebrow: "Identifier Onboarding",
  heroTitle: "\u6807\u8bc6\u63a5\u5165\u4e0e\u5173\u8054\u8d44\u6e90",
  heroCaption: "\u5148\u5b8c\u6210\u6807\u8bc6\u767b\u8bb0\uff0c\u518d\u8865\u5145\u5143\u6570\u636e\u5e76\u5173\u8054\u8d44\u6e90\u3001\u670d\u52a1\u6216\u5de5\u5177\uff0c\u5728\u540c\u4e00\u9875\u5b8c\u6210\u5168\u94fe\u8def\u64cd\u4f5c\u3002",
  presetResource: "\u9884\u7f6e\uff1a\u6570\u636e\u8d44\u6e90",
  presetService: "\u9884\u7f6e\uff1a\u6570\u636e\u670d\u52a1",
  presetTool: "\u9884\u7f6e\uff1a\u5de5\u5177\u80fd\u529b",
  resetWorkflow: "\u91cd\u7f6e\u6d41\u7a0b",
  summaryCurrentNode: "\u5f53\u524d\u8282\u70b9",
  summaryCurrentIdentifier: "\u5f53\u524d\u6807\u8bc6",
  summaryCurrentBinding: "\u5f53\u524d\u7ed1\u5b9a",
  summaryVersionDownload: "\u7248\u672c / \u4e0b\u8f7d",
  summaryIdentifierReady: "\u6807\u8bc6\u5df2\u521b\u5efa",
  summaryIdentifierEmpty: "\u7b49\u5f85\u5148\u5b8c\u6210\u6ce8\u518c",
  summaryNoComponent: "\u6682\u65e0\u7ec4\u4ef6\u7ed1\u5b9a",
  summaryVersionUnit: "\u4e2a\u7248\u672c",
  summaryDownloadReady: "\u4e0b\u8f7d\u5730\u5740\u5df2\u751f\u6210",
  summaryDownloadEmpty: "\u5c1a\u672a\u751f\u6210\u4e0b\u8f7d\u5730\u5740",
  workflowEyebrow: "Workflow",
  workflowTitle: "\u4e00\u6761\u94fe\u8def\u8dd1\u901a",
  workflowRegisterTitle: "\u6807\u8bc6\u767b\u8bb0",
  workflowRegisterDesc: "\u6309\u5f53\u524d\u8282\u70b9\u56fa\u5b9a\u5730\u533a\u7801\u548c\u673a\u6784\u7801\u5b8c\u6210\u6807\u8bc6\u767b\u8bb0\u3002",
  workflowUploadTitle: "\u4e0a\u4f20\u51c6\u5907",
  workflowUploadDesc: "\u8d44\u6e90 / \u5de5\u5177\u53ef\u9009 COS \u4e0a\u4f20\u4f1a\u8bdd\uff0c\u670d\u52a1\u7c7b\u76f4\u63a5\u5173\u8054\u3002",
  workflowBindTitle: "\u5173\u8054\u8d44\u6e90",
  workflowBindDesc: "\u8fd4\u56de\u5173\u8054\u8d44\u6e90\u5feb\u7167\u4e0e\u7248\u672c\u8bb0\u5f55\u3002",
  workflowDownloadTitle: "\u9a8c\u8bc1\u4e0b\u8f7d",
  workflowDownloadDesc: "\u6309 identifier \u751f\u6210\u53ef\u7528\u7684\u4e0b\u8f7d\u5730\u5740\u3002",
  registerEyebrow: "Register",
  registerTitle: "\u6807\u8bc6\u767b\u8bb0",
  regionCodeLabel: "\u5730\u533a\u7801",
  organizationCodeLabel: "\u673a\u6784\u7801",
  currentRegionPrefix: "\u5f53\u524d\u8282\u70b9\u81ea\u52a8\u5e26\u51fa\u5730\u533a\u7801\uff1a",
  organizationHelp: "\u673a\u6784\u7801\u6765\u81ea\u5f53\u524d\u63a7\u5236\u53f0\u9009\u4e2d\u7684\u8282\u70b9\u914d\u7f6e\u3002",
  industryLabel: "\u884c\u4e1a",
  dataCategoryLabel: "\u6570\u636e\u5206\u7c7b",
  registrationTypeLabel: "\u767b\u8bb0\u7c7b\u578b",
  entityTypeLabel: "\u5b9e\u4f53\u7c7b\u578b",
  ownerLabel: "\u6240\u5c5e\u65b9",
  ownerPlaceholder: "manual-lab",
  nameLabel: "\u540d\u79f0",
  namePlaceholder: "sample-meter-a",
  descriptionLabel: "\u63cf\u8ff0",
  descriptionPlaceholder: "\u8f93\u5165\u5bf9\u5e94\u6570\u5b57\u5bf9\u8c61\u7684\u57fa\u672c\u8bf4\u660e",
  scenarioLabel: "\u5e94\u7528\u573a\u666f",
  scenarioPlaceholder: "lab / energy / demo",
  additionalAttributesLabel: "additionalAttributes (JSON)",
  registerNow: "\u6ce8\u518c\u6807\u8bc6",
  registering: "\u6ce8\u518c\u4e2d...",
  copyIdentifier: "\u590d\u5236 identifier",
  bindingEyebrow: "Resource Linking",
  bindingTitle: "\u6807\u8bc6\u5173\u8054\u8d44\u6e90",
  bindingTypeResource: "\u8d44\u6e90",
  bindingTypeTool: "\u5de5\u5177",
  bindingTypeService: "\u670d\u52a1",
  modeUpload: "COS \u4e0a\u4f20\u4f1a\u8bdd",
  modeDirect: "\u76f4\u63a5\u7ed1\u5b9a",
  uploadProfileLabel: "\u4e0a\u4f20\u7c7b\u578b",
  fileNameLabel: "\u6587\u4ef6\u540d",
  localFileLabel: "\u672c\u5730\u6587\u4ef6",
  localFileHelp: "\u9009\u4e2d\u540e\u81ea\u52a8\u56de\u586b\u6587\u4ef6\u540d\u4e0e\u5927\u5c0f\u3002\u5982 bucket CORS \u5141\u8bb8\uff0c\u53ef\u76f4\u63a5\u4e0a\u4f20\u5230 COS\u3002",
  sizeBytesLabel: "\u6587\u4ef6\u5927\u5c0f (bytes)",
  sizeBytesHelp: "\u8be5\u5b57\u6bb5\u6765\u81ea\u672c\u5730\u9009\u4e2d\u6587\u4ef6\u7684\u5b9e\u9645\u5927\u5c0f\u3002",
  creating: "\u521b\u5efa\u4e2d...",
  createUploadSession: "\u521b\u5efa\u4e0a\u4f20\u4f1a\u8bdd",
  uploading: "\u4e0a\u4f20\u4e2d...",
  uploadSelectedFile: "\u4e0a\u4f20\u9009\u4e2d\u6587\u4ef6",
  finishing: "\u5b8c\u6210\u4e2d...",
  copyPutUrl: "\u590d\u5236 PUT \u5730\u5740",
  clearFile: "\u6e05\u7a7a\u6587\u4ef6",
  uploadSessionReady: "\u4e0a\u4f20\u4f1a\u8bdd\u5df2\u5c31\u7eea",
  uploadSessionId: "UploadId",
  bucketLabel: "Bucket",
  objectKeyLabel: "Object Key",
  putUrlLabel: "PUT URL",
  putExpiresLabel: "\u8fc7\u671f\u65f6\u95f4 (s)",
  storageTypeLabel: "storageType",
  accessMethodLabel: "accessMethod",
  locatorLabel: "locator",
  locatorPlaceholder: "https://example.com/data.csv",
  contentTypeLabel: "contentType",
  contentTypePlaceholder: "application/json",
  formatLabel: "format",
  formatPlaceholder: "json / csv / openapi_json",
  extraJsonLabel: "extra (JSON)",
  bindingNow: "\u5173\u8054\u4e2d...",
  removeBinding: "\u89e3\u9664\u8d44\u6e90\u5173\u8054",
  verifyEyebrow: "Verify",
  verifyTitle: "\u72b6\u6001\u4e0e\u4e0b\u8f7d\u9a8c\u8bc1",
  refreshing: "\u5237\u65b0\u4e2d...",
  refreshState: "\u5237\u65b0\u5feb\u7167",
  generating: "\u751f\u6210\u4e2d...",
  generateDownload: "\u751f\u6210\u4e0b\u8f7d\u5730\u5740",
  copyDownloadUrl: "\u590d\u5236\u4e0b\u8f7d\u5730\u5740",
  componentSnapshotTitle: "\u5f53\u524d\u7ec4\u4ef6\u5feb\u7167",
  bound: "\u5df2\u7ed1\u5b9a",
  notBound: "\u672a\u7ed1\u5b9a",
  componentTypeLabel: "componentType",
  versionTitle: "\u7248\u672c\u8bb0\u5f55",
  versionPrefix: "Version",
  noVersionData: "\u5f53\u524d\u6807\u8bc6\u6682\u65e0\u7248\u672c\u8bb0\u5f55\u3002",
  downloadCardTitle: "\u4e0b\u8f7d\u8bbf\u95ee",
  generated: "\u5df2\u751f\u6210",
  notGenerated: "\u672a\u751f\u6210",
  downloadUrlLabel: "Download URL",
  expireSecondsLabel: "expiresInSeconds",
  registerResultTitle: "\u6807\u8bc6\u767b\u8bb0\u7ed3\u679c",
  registerResultReady: "\u5df2\u767b\u8bb0",
  registerIdentifierCard: "\u6807\u8bc6",
  registerStateCard: "\u72b6\u6001",
  registerMetadataCard: "\u5143\u6570\u636e",
  registerResourceCard: "\u5173\u8054\u8d44\u6e90",
  registerStateReady: "\u5df2\u521b\u5efa\u57fa\u7840\u72b6\u6001",
  registerStateEmpty: "\u7b49\u5f85\u5b8c\u6210\u767b\u8bb0",
  registerMetadataReady: "\u5df2\u521b\u5efa\u57fa\u7840\u5143\u6570\u636e",
  registerMetadataEmpty: "\u5c1a\u672a\u521b\u5efa",
  registerResourceReady: "\u5df2\u5b58\u5728\u5173\u8054\u8d44\u6e90",
  registerResourceEmpty: "\u5c1a\u672a\u5173\u8054\u8d44\u6e90",
  openLocator: "\u6253\u5f00\u8d44\u6e90",
  copyLocator: "\u590d\u5236\u5730\u5740"
} as const;

const { currentNode, currentBackendProxyBase } = useConsoleRuntime();

const dataCategoryOptions: SelectOption[] = [
  { value: "public", label: "\u516c\u5171\u6570\u636e (public)" },
  { value: "enterprise", label: "\u4f01\u4e1a\u6570\u636e (enterprise)" },
  { value: "individual", label: "\u4e2a\u4eba\u6570\u636e (individual)" },
  { value: "others", label: "\u5176\u4ed6 (others)" }
];

const registrationTypeOptions: SelectOption[] = [
  { value: "resource", label: "\u6570\u636e\u8d44\u6e90 (resource)" },
  { value: "asset", label: "\u6570\u636e\u8d44\u4ea7 (asset)" },
  { value: "product_service", label: "\u6570\u636e\u4ea7\u54c1 / \u670d\u52a1 (product_service)" }
];

const entityTypeOptions: SelectOption[] = [
  { value: "dataset", label: "\u6570\u636e\u96c6 (dataset)" },
  { value: "table", label: "\u8868\u683c (table)" },
  { value: "image", label: "\u56fe\u50cf (image)" },
  { value: "document", label: "\u6587\u6863 (document)" },
  { value: "device", label: "\u8bbe\u5907 (device)" },
  { value: "model", label: "\u6a21\u578b (model)" },
  { value: "tool", label: "\u5de5\u5177 (tool)" },
  { value: "service", label: "\u670d\u52a1 (service)" },
  { value: "file", label: "\u5176\u4ed6\u6587\u4ef6 (file)" }
];

const uploadProfileCatalog: UploadProfileOption[] = [
  { value: "data_json", label: "\u6570\u636e\u96c6 JSON (data_json)", componentType: "resource", entityTypes: ["dataset", "device", "model", "file"], accept: ".json,application/json", format: "json", mime: "application/json", requireSha256: false, summary: "\u9002\u5408\u7ed3\u6784\u5316 JSON \u6570\u636e\u96c6\u3002" },
  { value: "data_jsonl", label: "\u6570\u636e\u96c6 JSONL (data_jsonl)", componentType: "resource", entityTypes: ["dataset", "model"], accept: ".jsonl,.ndjson,application/x-ndjson", format: "jsonl", mime: "application/x-ndjson", requireSha256: false, summary: "\u9002\u5408\u5927\u6279\u91cf\u884c\u5f0f\u6837\u672c\u8bb0\u5f55\u3002" },
  { value: "data_csv", label: "CSV (data_csv)", componentType: "resource", entityTypes: ["dataset", "table", "device", "file"], accept: ".csv,text/csv", format: "csv", mime: "text/csv", requireSha256: false, summary: "\u9002\u5408\u8868\u683c\u3001\u4f20\u611f\u65f6\u5e8f\u3001\u6570\u636e\u5206\u6790\u8f93\u51fa\u3002" },
  { value: "data_parquet", label: "Parquet (data_parquet)", componentType: "resource", entityTypes: ["dataset", "table", "model"], accept: ".parquet,application/octet-stream", format: "parquet", mime: "application/octet-stream", requireSha256: false, summary: "\u9002\u5408\u9762\u5411\u5206\u6790\u573a\u666f\u7684\u5927\u6279\u91cf\u6570\u636e\u6587\u4ef6\u3002" },
  { value: "table_excel", label: "Excel (table_excel)", componentType: "resource", entityTypes: ["table", "dataset", "file"], accept: ".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", format: "xlsx", mime: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", requireSha256: false, summary: "\u9002\u5408\u5de5\u7a0b\u8868\u683c\u548c\u7edf\u8ba1\u7ed3\u679c\u4ea4\u4ed8\u3002" },
  { value: "image_png", label: "PNG (image_png)", componentType: "resource", entityTypes: ["image", "device", "file"], accept: ".png,image/png", format: "png", mime: "image/png", requireSha256: false, summary: "\u9002\u5408\u56fe\u50cf\u3001\u622a\u56fe\u3001\u7ed3\u679c\u56fe\u7247\u3002" },
  { value: "image_jpeg", label: "JPEG (image_jpeg)", componentType: "resource", entityTypes: ["image", "device", "file"], accept: ".jpg,.jpeg,image/jpeg", format: "jpeg", mime: "image/jpeg", requireSha256: false, summary: "\u9002\u5408\u7167\u7247\u3001\u5916\u573a\u91c7\u96c6\u56fe\u50cf\u3002" },
  { value: "image_webp", label: "WebP (image_webp)", componentType: "resource", entityTypes: ["image", "file"], accept: ".webp,image/webp", format: "webp", mime: "image/webp", requireSha256: false, summary: "\u9002\u5408\u8f7b\u91cf\u5316\u56fe\u50cf\u5206\u53d1\u3002" },
  { value: "doc_pdf", label: "PDF (doc_pdf)", componentType: "resource", entityTypes: ["document", "file", "dataset"], accept: ".pdf,application/pdf", format: "pdf", mime: "application/pdf", requireSha256: false, summary: "\u9002\u5408\u62a5\u544a\u3001\u8bf4\u660e\u6587\u6863\u3001\u8bba\u6587\u7b49\u9759\u6001\u6587\u4ef6\u3002" },
  { value: "doc_word", label: "Word (doc_word)", componentType: "resource", entityTypes: ["document", "file"], accept: ".docx,application/vnd.openxmlformats-officedocument.wordprocessingml.document", format: "docx", mime: "application/vnd.openxmlformats-officedocument.wordprocessingml.document", requireSha256: false, summary: "\u9002\u5408\u53ef\u7f16\u8f91\u6587\u6863\u548c\u5236\u5ea6\u6750\u6599\u3002" },
  { value: "generic_file", label: "\u901a\u7528\u6587\u4ef6 (generic_file)", componentType: "resource", entityTypes: ["file", "device", "dataset", "document", "image", "model"], accept: "*/*", format: "bin", mime: "application/octet-stream", requireSha256: false, summary: "\u5f53\u524d\u7c7b\u578b\u65e0\u4e13\u7528\u4e0a\u4f20\u6a21\u5f0f\u65f6\u4f7f\u7528\u8be5\u7c7b\u578b\u3002" },
  { value: "tool_zip_cli", label: "CLI \u5de5\u5177\u5305 (tool_zip_cli)", componentType: "tool", entityTypes: ["tool"], accept: ".zip,application/zip", format: "zip", mime: "application/zip", requireSha256: true, summary: "\u9002\u5408\u53ef\u6267\u884c\u5de5\u5177\u7684 zip \u53d1\u5e03\u5305\uff0c\u5efa\u8bae\u4e0a\u4f20\u524d\u586b\u5199 sha256\u3002" }
];

const registerForm = reactive({ regionCode: "", organizationCode: "", industry: industryCatalog[43]?.name ?? industryCatalog[0]?.name ?? "", dataCategory: "public", registrationType: "resource", owner: "manual-lab", description: "", applicationScenario: "lab", name: "sample-meter-a", entityType: "dataset" });
const uploadForm = reactive({ profile: "data_json", filename: "", size: 0, sha256: "" });
const directForm = reactive({ storageType: "external_uri", accessMethod: "GET", locator: "", contentType: "application/json", format: "json", size: 0, hashValue: "" });
const additionalAttributesText = ref(`{
  "source": "sensor-a",
  "batch": "manual-001"
}`);
const directExtraText = ref(`{
  "bindMode": "direct"
}`);
const createdIdentifier = ref("");
const registrationDetail = ref<RegistrationDetail | null>(null);
const registering = ref(false);
const bindingBusy = ref(false);
const loadingState = ref(false);
const statusMessage = ref("");
const bindingType = ref<BindingType>("resource");
const bindingMode = ref<BindingMode>("upload");
const uploadSession = ref<any | null>(null);
const uploadUploaded = ref(false);
const selectedUploadFile = ref<File | null>(null);
const uploadFileInputKey = ref(0);
const componentSnapshot = ref<any | null>(null);
const versionItems = ref<any[]>([]);
const downloadResult = ref<any | null>(null);
const objectSnapshot = ref<any | null>(null);
const metadataSnapshot = ref<any | null>(null);

useStatusToast(statusMessage, "标识接入");

const industryOptions = industryCatalog.map((item) => ({ code: item.code, name: item.name, label: `${item.name} (${item.code})` }));
const currentRegionCode = computed(() => currentNode.value.code.slice(0, 4));
const currentOrganizationCode = computed(() => currentNode.value.code.slice(4, 8));
const currentUploadProfile = computed(() => uploadProfileCatalog.find((item) => item.value === uploadForm.profile) ?? null);
const supportsUploadSession = computed(() => bindingType.value !== "service");
const uploadPutUrl = computed(() => uploadSession.value?.put?.url ?? "");
const bindingTypeLabel = computed(() => componentTypeLabel(bindingType.value));
const currentUploadProfileSummary = computed(() => currentUploadProfile.value?.summary ?? "");
const currentUploadAccept = computed(() => currentUploadProfile.value?.accept ?? "*/*");
const uploadShaHelpText = computed(() => currentUploadProfile.value?.requireSha256 ? "\u5f53\u524d\u4e0a\u4f20\u7c7b\u578b\u5efa\u8bae\u4f20\u5165 SHA-256\uff0c\u672c\u5730\u5c0f\u6587\u4ef6\u4f1a\u81ea\u52a8\u8ba1\u7b97\u3002" : "\u53ef\u9009\u586b\u5199\uff0c\u7528\u4e8e\u5b8c\u6210\u65f6\u505a\u5bf9\u8c61\u9a8c\u8bc1\u3002");
const uploadBindHelpText = computed(() => bindingType.value === "tool" ? "\u5de5\u5177\u7c7b\u578b\u5efa\u8bae\u901a\u8fc7 COS \u4e0a\u4f20\u4f1a\u8bdd\u7ed1\u5b9a zip \u53d1\u5e03\u5305\u3002" : "\u8d44\u6e90\u7c7b\u578b\u53ef\u901a\u8fc7 COS \u4e0a\u4f20\u4f1a\u8bdd\u5b8c\u6210\u5b9e\u4f53\u4e0a\u4f20\u4e0e\u7ed1\u5b9a\u3002");
const directBindHelpText = computed(() => {
  if (bindingType.value === "service") {
    return "\u670d\u52a1\u7c7b\u578b\u4e0d\u9700\u8981\u4e0a\u4f20\u4f1a\u8bdd\uff0c\u76f4\u63a5\u7ed1\u5b9a API / OpenAPI \u8bbf\u95ee\u5730\u5740\u5373\u53ef\u3002";
  }
  if (bindingType.value === "tool") {
    return "\u5982\u679c\u5de5\u5177\u5df2\u5b58\u5728\u4e8e\u5916\u90e8\u5b58\u50a8\u6216\u53ef\u76f4\u63a5\u8bbf\u95ee\uff0c\u53ef\u8d70\u76f4\u63a5\u7ed1\u5b9a\u6a21\u5f0f\u3002";
  }
  return "\u5f53\u5b9e\u4f53\u8d44\u6e90\u5df2\u6709\u56fa\u5b9a URL / locator \u65f6\uff0c\u53ef\u76f4\u63a5\u7ed1\u5b9a\u800c\u4e0d\u7ecf COS \u4e0a\u4f20\u4f1a\u8bdd\u3002";
});
const completeUploadLabel = computed(() => uploadUploaded.value ? "\u5b8c\u6210\u4e0a\u4f20\u7ed1\u5b9a" : "\u5b8c\u6210\u7ed1\u5b9a");
const bindDirectLabel = computed(() => `\u76f4\u63a5\u7ed1\u5b9a${componentTypeLabel(bindingType.value)}`);
const directStorageOptions = computed<SelectOption[]>(() => {
  if (bindingType.value === "service") {
    return [{ value: "external_uri", label: "external_uri" }];
  }
  if (bindingType.value === "tool") {
    return [{ value: "external_uri", label: "external_uri" }, { value: "local_exec", label: "local_exec" }];
  }
  return [{ value: "external_uri", label: "external_uri" }, { value: "cos", label: "cos" }, { value: "s3", label: "s3" }];
});
const directAccessOptions = computed<SelectOption[]>(() => {
  if (bindingType.value === "service") {
    return [{ value: "GET", label: "GET" }, { value: "POST", label: "POST" }];
  }
  if (bindingType.value === "tool") {
    return [{ value: "download", label: "download" }, { value: "exec", label: "exec" }];
  }
  return [{ value: "download", label: "download" }, { value: "GET", label: "GET" }];
});
const copyIdentifierLabel = computed(() => {
  return createdIdentifier.value ? `${t.copyIdentifier}: ${createdIdentifier.value}` : t.copyIdentifier;
});
const metadataDisplay = computed(() => metadataSnapshot.value || {});
const resourceDisplay = computed(() => componentSnapshot.value || {});
const resourceLocator = computed(() => String(resourceDisplay.value.locator || ""));
const registrationStatusLabel = computed(() => metadataSnapshot.value?.status || registrationDetail.value?.status || "-");

watch(() => currentNode.value.code, () => {
  registerForm.regionCode = currentRegionCode.value;
  registerForm.organizationCode = currentOrganizationCode.value;
}, { immediate: true });

const visibleUploadProfileOptions = computed(() => {
  const entityType = registerForm.entityType || "file";
  return uploadProfileCatalog.filter((item) => item.componentType === bindingType.value && item.entityTypes.includes(entityType));
});

watch([bindingType, () => registerForm.entityType], () => {
  const options = visibleUploadProfileOptions.value;
  if (!options.length) {
    uploadForm.profile = "";
    return;
  }
  if (!options.some((item) => item.value === uploadForm.profile)) {
    uploadForm.profile = options[0]!.value;
  }
}, { immediate: true });

watch(bindingType, (value) => {
  if (value === "service") {
    bindingMode.value = "direct";
    registerForm.entityType = "service";
  } else if (value === "tool") {
    if (registerForm.entityType !== "tool") {
      registerForm.entityType = "tool";
    }
    if (bindingMode.value !== "upload") {
      bindingMode.value = "upload";
    }
  } else if (registerForm.entityType === "service" || registerForm.entityType === "tool") {
    registerForm.entityType = "dataset";
  }
  applyBindingDefaults();
}, { immediate: true });

watch(() => uploadForm.profile, () => {
  const option = currentUploadProfile.value;
  if (option && (!uploadForm.filename || uploadForm.filename.startsWith("sample."))) {
    uploadForm.filename = `sample.${option.format}`;
  }
}, { immediate: true });

function identifierBase(identifier?: string): string {
  const value = identifier ?? createdIdentifier.value;
  return `${currentBackendProxyBase.value}/api/v2/identifiers/${value}`;
}

function componentTypeLabel(value: string | null | undefined): string {
  if (value === "resource") return "\u8d44\u6e90";
  if (value === "tool") return "\u5de5\u5177";
  if (value === "service") return "\u670d\u52a1";
  return value || "-";
}

function safeError(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}

function ensureIdentifier(): string {
  if (!createdIdentifier.value) {
    throw new Error("\u8bf7\u5148\u6ce8\u518c\u6807\u8bc6\u3002");
  }
  return createdIdentifier.value;
}

function parseJsonMap(text: string, fieldName: string): Record<string, any> {
  if (!text.trim()) return {};
  let parsed: unknown;
  try {
    parsed = JSON.parse(text);
  } catch {
    throw new Error(`${fieldName} JSON \u89e3\u6790\u5931\u8d25`);
  }
  if (!parsed || Array.isArray(parsed) || typeof parsed !== "object") {
    throw new Error(`${fieldName} \u5fc5\u987b\u662f JSON object`);
  }
  return parsed as Record<string, any>;
}

async function copyText(value: string, successMessage: string): Promise<void> {
  if (!value) return;
  try {
    await navigator.clipboard.writeText(value);
    statusMessage.value = successMessage;
  } catch {
    statusMessage.value = "\u590d\u5236\u5931\u8d25\uff0c\u8bf7\u624b\u52a8\u590d\u5236\u3002";
  }
}
async function copyIdentifier(): Promise<void> { await copyText(createdIdentifier.value, "identifier \u5df2\u590d\u5236\u5230\u526a\u8d34\u677f\u3002"); }
async function copyUploadUrl(): Promise<void> { await copyText(uploadPutUrl.value, "PUT \u5730\u5740\u5df2\u590d\u5236\u5230\u526a\u8d34\u677f\u3002"); }
async function copyDownloadUrl(): Promise<void> { await copyText(downloadResult.value?.url ?? "", "\u4e0b\u8f7d\u5730\u5740\u5df2\u590d\u5236\u5230\u526a\u8d34\u677f\u3002"); }
async function copyResourceLocator(): Promise<void> { await copyText(resourceLocator.value, "\u8d44\u6e90\u5730\u5740\u5df2\u590d\u5236\u5230\u526a\u8d34\u677f\u3002"); }
async function openResourceLocator(): Promise<void> {
  const locator = resourceLocator.value.trim();
  if (!locator) return;
  const storageType = String(resourceDisplay.value.storageType || "").toLowerCase();
  const needsPresign = storageType === "cos" || storageType === "s3" || locator.startsWith("cos://") || locator.startsWith("s3://");
  if (needsPresign && createdIdentifier.value) {
    const pendingWindow = window.open("about:blank", "_blank");
    loadingState.value = true;
    try {
      const response = await requestEnvelope<any>(`${currentBackendProxyBase.value}/api/v2/downloads/presign`, {
        method: "POST",
        body: asJsonBody({ identifier: createdIdentifier.value, expiresInSeconds: 600 })
      });
      const freshUrl = String(response.data?.url || "");
      if (!freshUrl) {
        if (pendingWindow) pendingWindow.close();
        statusMessage.value = "\u672a\u751f\u6210\u53ef\u7528\u4e0b\u8f7d\u5730\u5740\u3002";
        return;
      }
      downloadResult.value = response.data;
      statusMessage.value = "\u5df2\u751f\u6210\u6700\u65b0\u4e0b\u8f7d\u5730\u5740\u5e76\u6253\u5f00\u8d44\u6e90\u3002";
      if (pendingWindow) pendingWindow.location.href = freshUrl;
      else window.open(freshUrl, "_blank", "noopener,noreferrer");
      return;
    } catch (error) {
      if (pendingWindow) pendingWindow.close();
      statusMessage.value = safeError(error);
      return;
    } finally {
      loadingState.value = false;
    }
  }
  window.open(locator, "_blank", "noopener,noreferrer");
}

function applyBindingDefaults(): void {
  if (bindingType.value === "service") {
    directForm.accessMethod = "GET";
    directForm.contentType = "application/json";
    directForm.format = "openapi_json";
    directForm.size = 0;
    directForm.hashValue = "";
    if (!directForm.locator) directForm.locator = "https://example.com/openapi.json";
    directExtraText.value = `{
  "bindMode": "direct",
  "category": "service"
}`;
    return;
  }
  if (bindingType.value === "tool") {
    directForm.storageType = "external_uri";
    directForm.accessMethod = "exec";
    directForm.contentType = "application/zip";
    directForm.format = "zip";
    directForm.size = 0;
    directForm.hashValue = "";
    if (!directForm.locator) directForm.locator = "https://example.com/tool.zip";
    directExtraText.value = `{
  "bindMode": "direct",
  "entrypoint": "bin/run.sh"
}`;
    return;
  }
  directForm.storageType = "external_uri";
  directForm.accessMethod = "download";
  directForm.contentType = currentUploadProfile.value?.mime ?? "application/json";
  directForm.format = currentUploadProfile.value?.format ?? "json";
  directForm.size = 0;
  directForm.hashValue = "";
  if (!directForm.locator) directForm.locator = "https://example.com/data.json";
  directExtraText.value = `{
  "bindMode": "direct"
}`;
}
function setBindingType(value: BindingType): void { bindingType.value = value; }
function setBindingMode(value: BindingMode): void { if (value === "upload" && !supportsUploadSession.value) return; bindingMode.value = value; }

function clearSelectedUploadFile(resetName: boolean): void {
  selectedUploadFile.value = null;
  uploadUploaded.value = false;
  uploadFileInputKey.value += 1;
  uploadForm.size = 0;
  uploadForm.sha256 = "";
  if (resetName) uploadForm.filename = currentUploadProfile.value ? `sample.${currentUploadProfile.value.format}` : "";
}

async function maybeComputeSha256(file: File): Promise<string> {
  if (!window.crypto?.subtle) return "";
  if (file.size > 32 * 1024 * 1024) return "";
  const buffer = await file.arrayBuffer();
  const digest = await window.crypto.subtle.digest("SHA-256", buffer);
  const bytes = Array.from(new Uint8Array(digest));
  return bytes.map((item) => item.toString(16).padStart(2, "0")).join("");
}

async function onUploadFileSelected(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement | null;
  const file = input?.files?.[0] ?? null;
  selectedUploadFile.value = file;
  uploadUploaded.value = false;
  if (!file) {
    uploadForm.size = 0;
    return;
  }
  uploadForm.filename = file.name;
  uploadForm.size = file.size;
  const currentHash = await maybeComputeSha256(file).catch(() => "");
  if (currentHash) {
    uploadForm.sha256 = currentHash;
    statusMessage.value = "\u5df2\u4e3a\u5c0f\u6587\u4ef6\u81ea\u52a8\u8ba1\u7b97 SHA-256\u3002";
  }
}

function syncRegistrationWithPreset(mode: PresetMode): void {
  if (mode === "service") {
    registerForm.registrationType = "product_service";
    registerForm.entityType = "service";
    registerForm.name = "sample-service-api";
    registerForm.description = "\u793a\u4f8b\u670d\u52a1\u578b\u6807\u8bc6\uff0c\u7528\u4e8e\u6302\u8f7d OpenAPI \u6216 API \u8bbf\u95ee\u5730\u5740\u3002";
    registerForm.applicationScenario = "api-gateway";
    return;
  }
  if (mode === "tool") {
    registerForm.registrationType = "product_service";
    registerForm.entityType = "tool";
    registerForm.name = "sample-tool-cli";
    registerForm.description = "\u793a\u4f8b\u5de5\u5177\u578b\u6807\u8bc6\uff0c\u7528\u4e8e\u6302\u8f7d\u53ef\u6267\u884c\u5de5\u5177\u5305\u3002";
    registerForm.applicationScenario = "processing";
    return;
  }
  registerForm.registrationType = "resource";
  registerForm.entityType = "dataset";
  registerForm.name = "sample-meter-a";
  registerForm.description = "\u793a\u4f8b\u6570\u636e\u8d44\u6e90\uff0c\u7528\u4e8e\u6f14\u793a\u6807\u8bc6\u6ce8\u518c\u540e\u7ee7\u7ed1\u5b9a\u5b9e\u4f53\u8d44\u6e90\u3002";
  registerForm.applicationScenario = "lab";
}

function loadPreset(mode: PresetMode): void {
  bindingType.value = mode;
  bindingMode.value = mode === "service" ? "direct" : "upload";
  syncRegistrationWithPreset(mode);
  applyBindingDefaults();
  if (mode === "resource") uploadForm.profile = "data_json";
  else if (mode === "tool") uploadForm.profile = "tool_zip_cli";
  clearSelectedUploadFile(true);
  statusMessage.value = "\u5df2\u52a0\u8f7d\u6f14\u793a\u9884\u7f6e\u3002";
}

function resetWorkflow(): void {
  createdIdentifier.value = "";
  registrationDetail.value = null;
  uploadSession.value = null;
  uploadUploaded.value = false;
  componentSnapshot.value = null;
  versionItems.value = [];
  downloadResult.value = null;
  objectSnapshot.value = null;
  metadataSnapshot.value = null;
  statusMessage.value = "";
  loadPreset("resource");
}

async function registerIdentifier(): Promise<void> {
  registering.value = true;
  statusMessage.value = "";
  try {
    const additionalAttributes = parseJsonMap(additionalAttributesText.value, "additionalAttributes");
    const payload = {
      regionCode: registerForm.regionCode,
      organizationCode: registerForm.organizationCode,
      industry: registerForm.industry,
      dataCategory: registerForm.dataCategory,
      registrationType: registerForm.registrationType,
      owner: registerForm.owner,
      description: registerForm.description,
      applicationScenario: registerForm.applicationScenario,
      name: registerForm.name,
      entityType: registerForm.entityType,
      additionalAttributes
    };
    const response = await requestEnvelope<RegistrationDetail>(`${currentBackendProxyBase.value}/api/v2/identifiers/register/detail`, { method: "POST", body: asJsonBody(payload) });
    registrationDetail.value = response.data;
    createdIdentifier.value = response.data?.identifier || "";
    uploadSession.value = null;
    uploadUploaded.value = false;
    downloadResult.value = null;
    statusMessage.value = "\u6807\u8bc6\u767b\u8bb0\u6210\u529f\uff0c\u53ef\u7ee7\u7eed\u5173\u8054\u8d44\u6e90\u6216\u670d\u52a1\u3002";
    await refreshSnapshots();
  } catch (error) {
    statusMessage.value = safeError(error);
  } finally {
    registering.value = false;
  }
}

async function createUploadSession(): Promise<void> {
  bindingBusy.value = true;
  statusMessage.value = "";
  try {
    const identifier = ensureIdentifier();
    if (!uploadForm.profile) throw new Error("\u5f53\u524d\u5b9e\u4f53\u7c7b\u578b\u6ca1\u6709\u53ef\u7528\u7684\u4e0a\u4f20\u7c7b\u578b\u3002");
    const payload = { profile: uploadForm.profile, filename: uploadForm.filename, size: Number(uploadForm.size || 0), sha256: uploadForm.sha256 || "" };
    const response = await requestEnvelope<any>(`${identifierBase(identifier)}/upload-sessions`, { method: "POST", body: asJsonBody(payload) });
    uploadSession.value = response.data;
    uploadUploaded.value = false;
    statusMessage.value = "\u4e0a\u4f20\u4f1a\u8bdd\u5df2\u521b\u5efa\u3002";
  } catch (error) {
    statusMessage.value = safeError(error);
  } finally {
    bindingBusy.value = false;
  }
}

async function uploadSelectedFile(): Promise<void> {
  if (!uploadPutUrl.value || !selectedUploadFile.value) {
    statusMessage.value = "\u8bf7\u5148\u521b\u5efa\u4e0a\u4f20\u4f1a\u8bdd\u5e76\u9009\u62e9\u672c\u5730\u6587\u4ef6\u3002";
    return;
  }
  bindingBusy.value = true;
  statusMessage.value = "";
  try {
    const headers = new Headers(uploadSession.value?.put?.headers ?? {});
    if (!headers.has("Content-Type") && selectedUploadFile.value.type) headers.set("Content-Type", selectedUploadFile.value.type);
    const response = await fetch(uploadPutUrl.value, { method: "PUT", headers, body: selectedUploadFile.value });
    if (!response.ok) throw new Error(`PUT HTTP ${response.status}`);
    uploadUploaded.value = true;
    statusMessage.value = "\u672c\u5730\u6587\u4ef6\u5df2\u4e0a\u4f20\u5230 COS\u3002";
  } catch (error) {
    statusMessage.value = `${safeError(error)}; \u82e5\u4e3a COS CORS \u9650\u5236\uff0c\u53ef\u590d\u5236 PUT URL \u6539\u4e3a\u5916\u90e8\u5ba2\u6237\u7aef\u4e0a\u4f20\u3002`;
  } finally {
    bindingBusy.value = false;
  }
}
async function completeUpload(): Promise<void> {
  bindingBusy.value = true;
  statusMessage.value = "";
  try {
    const uploadId = uploadSession.value?.uploadId;
    if (!uploadId) throw new Error("\u8bf7\u5148\u521b\u5efa\u4e0a\u4f20\u4f1a\u8bdd\u3002");
    const response = await requestEnvelope<any>(`${currentBackendProxyBase.value}/api/v2/upload-sessions/${uploadId}/complete`, { method: "POST" });
    componentSnapshot.value = response.data;
    statusMessage.value = "\u4e0a\u4f20\u5b8c\u6210\u5e76\u5df2\u7ed1\u5b9a\u5230\u6807\u8bc6\u3002";
    await refreshSnapshots();
  } catch (error) {
    statusMessage.value = safeError(error);
  } finally {
    bindingBusy.value = false;
  }
}

async function bindDirectComponent(): Promise<void> {
  bindingBusy.value = true;
  statusMessage.value = "";
  try {
    const identifier = ensureIdentifier();
    const extra = parseJsonMap(directExtraText.value, "extra");
    const payload = {
      componentType: bindingType.value,
      storageType: directForm.storageType,
      accessMethod: directForm.accessMethod,
      contentType: directForm.contentType,
      format: directForm.format,
      locator: directForm.locator,
      size: Number(directForm.size || 0),
      hashValue: directForm.hashValue,
      extra
    };
    await requestEnvelope<boolean>(`${identifierBase(identifier)}/component`, { method: "PUT", body: asJsonBody(payload) });
    statusMessage.value = "\u76f4\u63a5\u7ed1\u5b9a\u6210\u529f\u3002";
    await refreshSnapshots();
  } catch (error) {
    statusMessage.value = safeError(error);
  } finally {
    bindingBusy.value = false;
  }
}

async function removeComponent(): Promise<void> {
  bindingBusy.value = true;
  statusMessage.value = "";
  try {
    const identifier = ensureIdentifier();
    await requestEnvelope<boolean>(`${identifierBase(identifier)}/component`, { method: "DELETE" });
    componentSnapshot.value = null;
    versionItems.value = [];
    downloadResult.value = null;
    statusMessage.value = "\u5f53\u524d\u7ec4\u4ef6\u7ed1\u5b9a\u5df2\u79fb\u9664\u3002";
  } catch (error) {
    statusMessage.value = safeError(error);
  } finally {
    bindingBusy.value = false;
  }
}

async function refreshSnapshots(): Promise<void> {
  if (!createdIdentifier.value) return;
  loadingState.value = true;
  try {
    const identifier = createdIdentifier.value;
    const [metadataResp, objectResp, componentResp, versionResp] = await Promise.all([
      requestEnvelope<any>(`${identifierBase(identifier)}/metadata`).catch(() => ({ data: null } as any)),
      requestEnvelope<any>(`${identifierBase(identifier)}/object`).catch(() => ({ data: null } as any)),
      requestEnvelope<any>(`${identifierBase(identifier)}/component`).catch(() => ({ data: null } as any)),
      requestEnvelope<any>(`${identifierBase(identifier)}/component/versions`).catch(() => ({ data: { records: [], items: [] } } as any))
    ]);
    metadataSnapshot.value = metadataResp.data ?? null;
    objectSnapshot.value = objectResp.data ?? null;
    componentSnapshot.value = componentResp.data ?? null;
    versionItems.value = versionResp.data?.records ?? versionResp.data?.items ?? [];
    if (componentSnapshot.value) {
      directForm.storageType = componentSnapshot.value.storageType || directForm.storageType;
      directForm.accessMethod = componentSnapshot.value.accessMethod || directForm.accessMethod;
      directForm.locator = componentSnapshot.value.locator || directForm.locator;
      directForm.contentType = componentSnapshot.value.contentType || directForm.contentType;
      directForm.format = componentSnapshot.value.format || directForm.format;
      directForm.size = Number(componentSnapshot.value.size || 0);
      directForm.hashValue = componentSnapshot.value.hashValue || "";
      directExtraText.value = prettyJson(componentSnapshot.value.extra || {});
      if (componentSnapshot.value.componentType === "service") {
        bindingType.value = "service";
        bindingMode.value = "direct";
      } else if (componentSnapshot.value.componentType === "tool") {
        bindingType.value = "tool";
      } else if (componentSnapshot.value.componentType === "resource") {
        bindingType.value = "resource";
      }
    }
    if (metadataSnapshot.value) {
      registerForm.industry = metadataSnapshot.value.industry || registerForm.industry;
      registerForm.dataCategory = metadataSnapshot.value.dataCategory || registerForm.dataCategory;
      registerForm.registrationType = metadataSnapshot.value.registrationType || registerForm.registrationType;
      registerForm.owner = metadataSnapshot.value.owner || registerForm.owner;
      registerForm.description = metadataSnapshot.value.description || registerForm.description;
      registerForm.applicationScenario = metadataSnapshot.value.applicationScenario || registerForm.applicationScenario;
      registerForm.name = metadataSnapshot.value.name || registerForm.name;
      registerForm.entityType = metadataSnapshot.value.entityType || registerForm.entityType;
      additionalAttributesText.value = prettyJson(metadataSnapshot.value.additionalAttributes || {});
    }
  } catch (error) {
    statusMessage.value = safeError(error);
  } finally {
    loadingState.value = false;
  }
}

async function createDownloadPresign(): Promise<void> {
  loadingState.value = true;
  statusMessage.value = "";
  try {
    const identifier = ensureIdentifier();
    const response = await requestEnvelope<any>(`${currentBackendProxyBase.value}/api/v2/downloads/presign`, { method: "POST", body: asJsonBody({ identifier, expiresInSeconds: 600 }) });
    downloadResult.value = response.data;
    statusMessage.value = "\u4e0b\u8f7d\u5730\u5740\u751f\u6210\u6210\u529f\u3002";
  } catch (error) {
    statusMessage.value = safeError(error);
  } finally {
    loadingState.value = false;
  }
}

loadPreset("resource");
</script>




