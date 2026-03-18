<template>
  <div class="page-shell">
    <section class="page-hero panel">
      <div>
        <p class="eyebrow">Auth</p>
        <h2>认证中心联调台</h2>
        <p class="panel-caption">围绕认证中心的注册、登录、userinfo 和公钥能力做联调展示，并将令牌纳入前端全局会话。</p>
      </div>
      <div class="page-hero-actions page-toolbar">
        <button class="button ghost" type="button" :disabled="loading" @click="fillDemoUser">填充示例用户</button>
        <button class="button ghost" type="button" :disabled="loading" @click="loadPublicKey">刷新公钥</button>
        <button class="button ghost" type="button" @click="clearSession">清空会话</button>
      </div>
    </section>

    <section class="summary-grid compact-summary-grid">
      <article class="summary-card">
        <span class="summary-label">认证状态</span>
        <strong class="summary-value">{{ isAuthenticated ? '已登录' : '未登录' }}</strong>
        <small>{{ statusMessage || '等待认证操作' }}</small>
      </article>
      <article class="summary-card">
        <span class="summary-label">当前用户</span>
        <strong class="summary-value">{{ displayUsername }}</strong>
        <small>{{ displayName }}</small>
      </article>
      <article class="summary-card">
        <span class="summary-label">Token</span>
        <strong class="summary-value">{{ isAuthenticated ? '已返回 accessToken' : '无 token' }}</strong>
        <small>{{ expiresText }}</small>
      </article>
      <article class="summary-card">
        <span class="summary-label">公钥状态</span>
        <strong class="summary-value">{{ publicKey ? '已加载' : '未加载' }}</strong>
        <small>{{ publicKey?.kid || publicKey?.algorithm || '等待获取 public key' }}</small>
      </article>
    </section>

    <section class="results-grid auth-grid">
      <article class="panel page-card">
        <div class="panel-head">
          <div>
            <p class="eyebrow">Register</p>
            <h3>用户注册</h3>
          </div>
        </div>
        <div class="form-grid">
          <label class="field-block">
            <span class="field-label">username</span>
            <input v-model.trim="registerForm.username" class="text-input" type="text" />
          </label>
          <label class="field-block">
            <span class="field-label">password</span>
            <input v-model.trim="registerForm.password" class="text-input" type="password" />
          </label>
          <label class="field-block">
            <span class="field-label">displayName</span>
            <input v-model.trim="registerForm.displayName" class="text-input" type="text" />
          </label>
          <label class="field-block">
            <span class="field-label">orgCode</span>
            <input v-model.trim="registerForm.orgCode" class="text-input mono" type="text" />
          </label>
          <label class="field-block">
            <span class="field-label">email</span>
            <input v-model.trim="registerForm.email" class="text-input" type="text" />
          </label>
          <label class="field-block">
            <span class="field-label">phone</span>
            <input v-model.trim="registerForm.phone" class="text-input" type="text" />
          </label>
        </div>
        <div class="action-row compact-actions">
          <button class="button warm" type="button" :disabled="loading" @click="registerUser">注册用户</button>
          <button class="button ghost" type="button" :disabled="loading" @click="copyRegisterPayload">复制注册体</button>
        </div>
      </article>

      <article class="panel page-card">
        <div class="panel-head">
          <div>
            <p class="eyebrow">Login</p>
            <h3>登录与会话</h3>
          </div>
        </div>
        <div class="form-grid">
          <label class="field-block">
            <span class="field-label">username</span>
            <input v-model.trim="loginForm.username" class="text-input" type="text" />
          </label>
          <label class="field-block">
            <span class="field-label">password</span>
            <input v-model.trim="loginForm.password" class="text-input" type="password" />
          </label>
        </div>
        <div class="action-row compact-actions">
          <button class="button warm" type="button" :disabled="loading" @click="loginUser">登录</button>
          <button class="button ghost" type="button" :disabled="loading || !isAuthenticated" @click="loadUserInfo">userinfo</button>
          <button class="button ghost" type="button" :disabled="!isAuthenticated" @click="copyAccessToken">复制 token</button>
        </div>
        <div class="entity-list">
          <div class="entity-list-item">
            <div>
              <strong>subject</strong>
              <p class="field-help mono">{{ tokenClaims?.sub || '-' }}</p>
            </div>
          </div>
          <div class="entity-list-item">
            <div>
              <strong>issuedAt / expiresAt</strong>
              <p class="field-help">{{ issuedAtText }} / {{ expiresAtText }}</p>
            </div>
          </div>
          <div class="entity-list-item">
            <div>
              <strong>scope / role</strong>
              <p class="field-help">{{ tokenClaims?.scope || tokenClaims?.role || '-' }}</p>
            </div>
          </div>
        </div>
      </article>
    </section>

    <section class="results-grid auth-grid">
      <article class="panel page-card">
        <div class="panel-head">
          <div>
            <p class="eyebrow">Token</p>
            <h3>令牌结果与 Claims</h3>
          </div>
        </div>
        <pre class="json-view">{{ prettyJson(authDebugPayload) }}</pre>
      </article>

      <article class="panel page-card">
        <div class="panel-head">
          <div>
            <p class="eyebrow">Public Key</p>
            <h3>公钥与 userinfo</h3>
          </div>
        </div>
        <pre class="json-view">{{ prettyJson(accountDebugPayload) }}</pre>
      </article>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';

import { asJsonBody, requestEnvelope } from '../../shared/api/http';
import {
  clearAuthSession as clearGlobalAuthSession,
  setAuthSession,
  updateAuthUserInfo,
  useAuthSession
} from '../../shared/auth/session';
import { useStatusToast } from '../../shared/composables/useStatusToast';
import { useConsoleRuntime } from '../../shared/composables/useConsoleRuntime';
import { formatTime, prettyJson } from '../../shared/utils/format';

const { authProxyBase } = useConsoleRuntime();
const { state: authState, tokenClaims, isAuthenticated } = useAuthSession();

const loading = ref(false);
const statusMessage = ref('');
const publicKey = ref<any | null>(null);

useStatusToast(statusMessage, '认证中心');

const registerForm = reactive({ username: '', password: 'Passw0rd!', displayName: '', orgCode: 'CNTA', email: '', phone: '' });
const loginForm = reactive({ username: '', password: 'Passw0rd!' });

const accessToken = computed(() => authState.accessToken);
const tokenPayload = computed(() => authState.tokenPayload);
const userInfo = computed(() => authState.userInfo);
const displayUsername = computed(() => String((userInfo.value?.username as string | undefined) || (tokenClaims.value?.sub as string | undefined) || registerForm.username || '-'));
const displayName = computed(() => String((userInfo.value?.displayName as string | undefined) || registerForm.displayName || '-'));
const issuedAtText = computed(() => formatEpoch(numberClaim(tokenClaims.value, 'iat')));
const expiresAtText = computed(() => formatEpoch(numberClaim(tokenClaims.value, 'exp')));
const expiresText = computed(() => {
  const exp = numberClaim(tokenClaims.value, 'exp');
  return exp ? `exp ${formatEpoch(exp)}` : '未解析到过期时间';
});
const authDebugPayload = computed(() => ({
  tokenPayload: tokenPayload.value,
  tokenClaims: tokenClaims.value,
  accessToken: accessToken.value
}));
const accountDebugPayload = computed(() => ({
  publicKey: publicKey.value,
  userInfo: userInfo.value
}));

function numberClaim(claims: Record<string, unknown> | null, key: string): number | undefined {
  const value = claims?.[key];
  return typeof value === 'number' ? value : undefined;
}

function formatEpoch(value?: number): string {
  if (typeof value !== 'number') {
    return '-';
  }
  return formatTime(new Date(value * 1000).toISOString());
}

function fillDemoUser(): void {
  const suffix = Date.now().toString().slice(-6);
  registerForm.username = `demo_${suffix}`;
  registerForm.displayName = `演示用户${suffix}`;
  registerForm.email = `demo_${suffix}@example.com`;
  registerForm.phone = `1380000${suffix}`.slice(0, 11);
  loginForm.username = registerForm.username;
  loginForm.password = registerForm.password;
  statusMessage.value = '已填充示例用户。';
}

function clearSession(): void {
  clearGlobalAuthSession();
  statusMessage.value = '会话已清空。';
}

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

async function copyAccessToken(): Promise<void> {
  await copyText(accessToken.value, 'accessToken 已复制到剪贴板。');
}

async function copyRegisterPayload(): Promise<void> {
  await copyText(JSON.stringify(registerForm, null, 2), '注册请求体已复制到剪贴板。');
}

async function loadPublicKey(): Promise<void> {
  loading.value = true;
  try {
    const response = await requestEnvelope<any>(`${authProxyBase}/auth/public-key`);
    publicKey.value = response.data;
  } catch (error) {
    statusMessage.value = error instanceof Error ? error.message : String(error);
  } finally {
    loading.value = false;
  }
}

async function registerUser(): Promise<void> {
  loading.value = true;
  statusMessage.value = '';
  try {
    const response = await requestEnvelope<any>(`${authProxyBase}/auth/register`, {
      method: 'POST',
      body: asJsonBody(registerForm)
    });
    setAuthSession({
      accessToken: response.data?.accessToken || '',
      tokenPayload: response.data,
      userInfo: null
    });
    loginForm.username = registerForm.username;
    loginForm.password = registerForm.password;
    statusMessage.value = '注册成功，已写入全局会话。';
  } catch (error) {
    statusMessage.value = error instanceof Error ? error.message : String(error);
  } finally {
    loading.value = false;
  }
}

async function loginUser(): Promise<void> {
  loading.value = true;
  statusMessage.value = '';
  try {
    const response = await requestEnvelope<any>(`${authProxyBase}/auth/login`, {
      method: 'POST',
      body: asJsonBody(loginForm)
    });
    setAuthSession({
      accessToken: response.data?.accessToken || '',
      tokenPayload: response.data,
      userInfo: userInfo.value as Record<string, unknown> | null
    });
    statusMessage.value = '登录成功，其他页面请求将自动携带 token。';
  } catch (error) {
    statusMessage.value = error instanceof Error ? error.message : String(error);
  } finally {
    loading.value = false;
  }
}

async function loadUserInfo(): Promise<void> {
  if (!accessToken.value) {
    return;
  }
  loading.value = true;
  try {
    const response = await requestEnvelope<any>(`${authProxyBase}/auth/userinfo`);
    updateAuthUserInfo(response.data as Record<string, unknown> | null);
    statusMessage.value = 'userinfo 已加载。';
  } catch (error) {
    statusMessage.value = error instanceof Error ? error.message : String(error);
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  void loadPublicKey();
});
</script>
