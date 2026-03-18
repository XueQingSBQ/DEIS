<template>
  <div class="console-shell">
    <aside class="console-sidebar">
      <div class="brand-block">
        <p class="brand-kicker">DRIS Console</p>
        <h1>系统演示控制台</h1>
        <p class="brand-copy">保留现有 DeepResearch，补齐节点、标识、审计、认证与工具页。</p>
      </div>

      <nav class="nav-stack">
        <RouterLink
          v-for="item in navigationItems"
          :key="item.to"
          :to="item.to"
          class="nav-item"
          :class="route.path === item.to ? 'nav-item-active' : ''"
        >
          <strong>{{ item.label }}</strong>
          <span>{{ item.caption }}</span>
        </RouterLink>
      </nav>
    </aside>

    <section class="console-main">
      <header class="console-topbar">
        <div class="topbar-title">
          <p class="eyebrow">当前节点</p>
          <h2>{{ currentNode.label }}</h2>
          <p class="topbar-meta">{{ currentNode.code }} / {{ currentNode.level }} / {{ currentNode.backendBaseUrl }}</p>
        </div>

        <div class="topbar-controls">
          <label class="control-field">
            <span>节点切换</span>
            <select v-model="state.selectedNodeCode" class="select-input">
              <option v-for="item in nodeOptions" :key="item.code" :value="item.code">
                {{ item.label }} / {{ item.code }}
              </option>
            </select>
          </label>

          <label class="control-field compact-field">
            <span>MCP</span>
            <input v-model.trim="state.mcpUrl" class="text-input mono" type="text" />
          </label>

          <label class="control-field compact-field">
            <span>编排</span>
            <input v-model.trim="state.orchestratorUrl" class="text-input mono" type="text" />
          </label>

          <label class="control-field compact-field">
            <span>认证</span>
            <input v-model.trim="state.authBaseUrl" class="text-input mono" type="text" />
          </label>

          <div class="control-field compact-field">
            <span>会话</span>
            <div class="auth-session-box">
              <strong>{{ isAuthenticated ? '已登录' : '未登录' }}</strong>
              <small class="mono">{{ authIdentity }}</small>
            </div>
          </div>

          <button v-if="isAuthenticated" class="button ghost" type="button" @click="clearSession">退出会话</button>
        </div>
      </header>

      <div class="console-page">
        <RouterView />
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { RouterLink, RouterView, useRoute } from "vue-router";

import { navigationItems } from "./navigation";
import { clearAuthSession, useAuthSession } from "../shared/auth/session";
import { useConsoleRuntime } from "../shared/composables/useConsoleRuntime";

const route = useRoute();
const { state, nodeOptions, currentNode } = useConsoleRuntime();
const { state: authState, tokenClaims, isAuthenticated } = useAuthSession();

const authIdentity = computed(() => {
  const username = authState.userInfo?.username as string | undefined;
  const displayName = authState.userInfo?.displayName as string | undefined;
  const subject = tokenClaims.value?.sub as string | undefined;
  return displayName || username || subject || "-";
});

function clearSession(): void {
  clearAuthSession();
}
</script>
