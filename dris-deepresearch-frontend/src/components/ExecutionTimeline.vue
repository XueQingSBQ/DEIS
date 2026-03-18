<template>
  <section class="panel timeline-panel">
    <div class="panel-head">
      <div>
        <p class="eyebrow">执行过程</p>
        <h2>工具时间线</h2>
      </div>
      <span class="pill" :class="running ? 'pill-live' : 'pill-still'">
        {{ running ? '执行中' : steps.length ? '已完成' : '空闲' }}
      </span>
    </div>

    <p class="panel-caption">按时间顺序展示工具调用、参数、耗时和摘要，用于说明链路与排查问题。</p>

    <div v-if="!steps.length" class="panel-empty">
      任务开始后，这里会逐步出现每一步工具调用和执行状态。
    </div>

    <ol v-else class="timeline-list">
      <li v-for="step in steps" :key="step.id" class="timeline-item" :class="`status-${step.status}`">
        <div class="timeline-marker"></div>
        <article class="timeline-card">
          <header class="timeline-header">
            <div>
              <p class="timeline-tool">{{ step.toolName }}</p>
              <h3>{{ translateUiText(step.title) }}</h3>
            </div>
            <span class="status-chip">{{ stepStatusLabel(step.status) }}</span>
          </header>

          <p class="timeline-description">{{ translateUiText(step.description) }}</p>
          <p v-if="step.summary" class="timeline-summary">{{ translateUiText(step.summary) }}</p>
          <p v-if="step.error" class="timeline-error">{{ translateUiText(step.error) }}</p>

          <dl class="timeline-meta">
            <div>
              <dt>开始时间</dt>
              <dd>{{ formatTime(step.startedAt) }}</dd>
            </div>
            <div>
              <dt>耗时</dt>
              <dd>{{ formatDuration(step.durationMs) }}</dd>
            </div>
          </dl>

          <details v-if="step.args" class="timeline-details">
            <summary>查看参数</summary>
            <pre>{{ stringify(step.args) }}</pre>
          </details>
        </article>
      </li>
    </ol>
  </section>
</template>

<script setup lang="ts">
import type { ToolExecutionStep } from "../lib/types";
import { stepStatusLabel, translateUiText } from "../lib/uiText";

defineProps<{
  steps: ToolExecutionStep[];
  running: boolean;
}>();

function formatTime(value?: string): string {
  if (!value) {
    return "-";
  }
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? value : parsed.toLocaleTimeString("zh-CN", { hour12: false });
}

function formatDuration(value?: number): string {
  if (value == null) {
    return "-";
  }
  if (value >= 1000) {
    return `${(value / 1000).toFixed(value >= 10000 ? 0 : 1)} s`;
  }
  return `${value} ms`;
}

function stringify(value: unknown): string {
  return JSON.stringify(value, null, 2);
}
</script>
