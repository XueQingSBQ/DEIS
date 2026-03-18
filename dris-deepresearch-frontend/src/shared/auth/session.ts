import { computed, reactive } from "vue";

export interface AuthSessionState {
  accessToken: string;
  tokenPayload: unknown | null;
  userInfo: Record<string, unknown> | null;
}

const storageKey = "dris-auth-session-v1";

const state = reactive<AuthSessionState>({
  accessToken: "",
  tokenPayload: null,
  userInfo: null
});

function canUseStorage(): boolean {
  return typeof window !== "undefined" && typeof window.localStorage !== "undefined";
}

function decodeJwtClaims(token: string): Record<string, unknown> | null {
  if (!token) {
    return null;
  }
  const segments = token.split(".");
  if (segments.length < 2) {
    return null;
  }
  try {
    const base64 = segments[1]!.replace(/-/g, "+").replace(/_/g, "/");
    const normalized = base64.padEnd(Math.ceil(base64.length / 4) * 4, "=");
    const decoded = window.atob(normalized);
    return JSON.parse(decoded) as Record<string, unknown>;
  } catch {
    return null;
  }
}

function persist(): void {
  if (!canUseStorage()) {
    return;
  }
  window.localStorage.setItem(storageKey, JSON.stringify(state));
}

function load(): void {
  if (!canUseStorage()) {
    return;
  }
  const raw = window.localStorage.getItem(storageKey);
  if (!raw) {
    return;
  }
  try {
    const parsed = JSON.parse(raw) as Partial<AuthSessionState>;
    state.accessToken = parsed.accessToken?.trim() || "";
    state.tokenPayload = parsed.tokenPayload ?? null;
    state.userInfo = (parsed.userInfo as Record<string, unknown> | null) ?? null;
  } catch {
    state.accessToken = "";
    state.tokenPayload = null;
    state.userInfo = null;
  }
}

load();

export function setAuthSession(payload: { accessToken?: string; tokenPayload?: unknown; userInfo?: Record<string, unknown> | null }): void {
  state.accessToken = payload.accessToken?.trim() || "";
  state.tokenPayload = payload.tokenPayload ?? null;
  state.userInfo = payload.userInfo ?? null;
  persist();
}

export function updateAuthUserInfo(userInfo: Record<string, unknown> | null): void {
  state.userInfo = userInfo;
  persist();
}

export function clearAuthSession(): void {
  state.accessToken = "";
  state.tokenPayload = null;
  state.userInfo = null;
  if (!canUseStorage()) {
    return;
  }
  window.localStorage.removeItem(storageKey);
}

export function getAccessToken(): string {
  return state.accessToken?.trim() || "";
}

export function useAuthSession() {
  const tokenClaims = computed(() => decodeJwtClaims(state.accessToken));
  const isAuthenticated = computed(() => !!state.accessToken);
  return {
    state,
    tokenClaims,
    isAuthenticated
  };
}
