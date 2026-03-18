import { computed, reactive, watch } from "vue";

export interface ConsoleNodeOption {
  code: string;
  label: string;
  level: "COUNTRY" | "PROVINCE" | "CITY" | "COUNTY";
  backendBaseUrl: string;
  proxyBase: string;
  drirpPublicUrl: string;
  parentCode?: string;
}

interface RuntimeState {
  selectedNodeCode: string;
  mcpUrl: string;
  orchestratorUrl: string;
  authBaseUrl: string;
}

const storageKey = "dris-console-runtime-v1";

export const nodeOptions: ConsoleNodeOption[] = [
  {
    code: "1100RTAA",
    label: "国家根节点",
    level: "COUNTRY",
    backendBaseUrl: "http://127.0.0.1:8200",
    proxyBase: "/proxy/node/root",
    drirpPublicUrl: "grpc://127.0.0.1:19200"
  },
  {
    code: "1100PRAA",
    label: "省级节点 A",
    level: "PROVINCE",
    backendBaseUrl: "http://127.0.0.1:8201",
    proxyBase: "/proxy/node/proa",
    drirpPublicUrl: "grpc://127.0.0.1:19201",
    parentCode: "1100RTAA"
  },
  {
    code: "1100CTAA",
    label: "市级节点 A",
    level: "CITY",
    backendBaseUrl: "http://127.0.0.1:8202",
    proxyBase: "/proxy/node/cita",
    drirpPublicUrl: "grpc://127.0.0.1:19202",
    parentCode: "1100PRAA"
  },
  {
    code: "1100CNTA",
    label: "县级节点 A",
    level: "COUNTY",
    backendBaseUrl: "http://127.0.0.1:8203",
    proxyBase: "/proxy/node/cnta",
    drirpPublicUrl: "grpc://127.0.0.1:19203",
    parentCode: "1100CTAA"
  },
  {
    code: "1100CTAB",
    label: "市级节点 B",
    level: "CITY",
    backendBaseUrl: "http://127.0.0.1:8204",
    proxyBase: "/proxy/node/citb",
    drirpPublicUrl: "grpc://127.0.0.1:19204",
    parentCode: "1100PRAA"
  },
  {
    code: "1100CNTB",
    label: "县级节点 B",
    level: "COUNTY",
    backendBaseUrl: "http://127.0.0.1:8205",
    proxyBase: "/proxy/node/cntb",
    drirpPublicUrl: "grpc://127.0.0.1:19205",
    parentCode: "1100CTAB"
  }
];

const state = reactive<RuntimeState>({
  selectedNodeCode: "1100RTAA",
  mcpUrl: "http://127.0.0.1:38082/mcp",
  orchestratorUrl: "http://127.0.0.1:38083",
  authBaseUrl: "http://127.0.0.1:8290"
});

function loadPersistedState(): void {
  if (typeof window === "undefined") {
    return;
  }
  const raw = window.localStorage.getItem(storageKey);
  if (!raw) {
    return;
  }
  try {
    const parsed = JSON.parse(raw) as Partial<RuntimeState>;
    if (parsed.selectedNodeCode && nodeOptions.some((item) => item.code === parsed.selectedNodeCode)) {
      state.selectedNodeCode = parsed.selectedNodeCode;
    }
    if (parsed.mcpUrl) {
      state.mcpUrl = parsed.mcpUrl;
    }
    if (parsed.orchestratorUrl) {
      state.orchestratorUrl = parsed.orchestratorUrl;
    }
    if (parsed.authBaseUrl) {
      state.authBaseUrl = parsed.authBaseUrl;
    }
  } catch {
    // ignore invalid local cache
  }
}

loadPersistedState();

if (typeof window !== "undefined") {
  watch(
    () => ({ ...state }),
    (value) => {
      window.localStorage.setItem(storageKey, JSON.stringify(value));
    },
    { deep: true }
  );
}

const currentNode = computed(() => nodeOptions.find((item) => item.code === state.selectedNodeCode) ?? nodeOptions[0]!);
const currentBackendProxyBase = computed(() => currentNode.value.proxyBase);

export function useConsoleRuntime() {
  return {
    state,
    nodeOptions,
    currentNode,
    currentBackendProxyBase,
    authProxyBase: "/proxy/auth"
  };
}

