import { reactive } from "vue";

export type ToastTone = "success" | "error" | "info";

export interface ToastItem {
  id: number;
  title: string;
  message: string;
  tone: ToastTone;
  durationMs: number;
}

interface ShowToastOptions {
  title?: string;
  message: string;
  tone?: ToastTone;
  durationMs?: number;
}

const state = reactive({
  items: [] as ToastItem[]
});

let nextId = 1;

export function useToastState() {
  return state;
}

export function showToast(options: ShowToastOptions): number {
  const message = (options.message || "").trim();
  if (!message) {
    return 0;
  }
  const item: ToastItem = {
    id: nextId++,
    title: (options.title || "操作反馈").trim() || "操作反馈",
    message,
    tone: options.tone || "info",
    durationMs: Math.max(1800, options.durationMs || 3200)
  };
  state.items.unshift(item);
  if (state.items.length > 5) {
    state.items.splice(5);
  }
  if (typeof window !== "undefined") {
    window.setTimeout(() => {
      dismissToast(item.id);
    }, item.durationMs);
  }
  return item.id;
}

export function dismissToast(id: number): void {
  const index = state.items.findIndex((item) => item.id === id);
  if (index >= 0) {
    state.items.splice(index, 1);
  }
}
