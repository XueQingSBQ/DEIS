<template>
  <section class="panel insights-panel">
    <div class="panel-head">
      <div>
        <p class="eyebrow">研究结果</p>
        <h2>报告与证据</h2>
      </div>
      <span class="pill" :class="report ? 'pill-live' : 'pill-still'">{{ report ? '已生成' : '等待结果' }}</span>
    </div>

    <p class="panel-caption">这里展示结构化摘要、证据卡片和完整 Markdown 报告。</p>

    <div v-if="!report" class="panel-empty">
      暂无研究报告。执行任务后，这里会显示摘要、证据卡片和可导出的 Markdown 文本。
    </div>

    <template v-else>
      <article class="report-hero">
        <p class="report-template">{{ translateUiText(report.templateTitle) }}</p>
        <h3>{{ translateUiText(report.title) }}</h3>
        <p class="report-question">{{ translateUiText(report.question || '本次运行未提供自然语言问题。') }}</p>
        <div class="hero-meta">
          <span>生成于 {{ formatTime(report.generatedAt) }}</span>
          <span>焦点标识 {{ report.focusedIdentifiers.length }} 个</span>
        </div>
      </article>

      <article class="report-markdown">
        <pre>{{ report.markdown }}</pre>
      </article>

      <details class="structured-panel">
        <summary>展开结构化摘要与证据</summary>

        <article v-for="section in report.summarySections" :key="section.title" class="report-section">
          <h3>{{ translateUiText(section.title) }}</h3>
          <ul>
            <li v-for="line in section.lines" :key="line">{{ translateUiText(line) }}</li>
          </ul>
        </article>

        <div class="evidence-grid">
          <article
            v-for="card in report.evidenceCards"
            :key="`${card.title}-${card.subtitle || ''}`"
            class="evidence-card"
            :class="`tone-${card.tone || 'neutral'}`"
          >
            <p class="evidence-subtitle">{{ translateUiText(card.subtitle || '') }}</p>
            <h4>{{ translateUiText(card.title) }}</h4>
            <dl v-if="card.metrics?.length" class="evidence-metrics">
              <div v-for="metric in card.metrics" :key="`${card.title}-${metric.label}`">
                <dt>{{ translateUiText(metric.label) }}</dt>
                <dd>{{ translateUiText(metric.value) }}</dd>
              </div>
            </dl>
            <ul v-if="card.bullets?.length" class="evidence-bullets">
              <li v-for="bullet in card.bullets" :key="bullet">{{ translateUiText(bullet) }}</li>
            </ul>
          </article>
        </div>
      </details>

      <details class="markdown-panel">
        <summary>展开 Markdown 原文</summary>
        <pre>{{ report.markdown }}</pre>
      </details>
    </template>
  </section>
</template>

<script setup lang="ts">
import type { ResearchReport } from "../lib/types";
import { translateUiText } from "../lib/uiText";

defineProps<{
  report: ResearchReport | null;
}>();

function formatTime(value: string): string {
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? value : parsed.toLocaleString("zh-CN", { hour12: false });
}
</script>

<style scoped>
.report-markdown {
  border: 1px solid rgba(31, 41, 55, 0.08);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.94);
  padding: 18px 20px;
}

.report-markdown pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.8;
  font-size: 14px;
  color: #1f2937;
  font-family: "Microsoft YaHei UI", "PingFang SC", "Noto Sans SC", sans-serif;
}

.structured-panel {
  margin-top: 16px;
}
</style>
