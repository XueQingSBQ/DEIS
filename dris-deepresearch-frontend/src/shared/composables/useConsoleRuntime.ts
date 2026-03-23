import { computed, reactive, ref, watch } from "vue";

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

interface ConsoleRuntimeConfig {
  profileId: string;
  authProxyBase?: string;
  services?: {
    mcpUrl?: string;
    orchestratorUrl?: string;
    authBaseUrl?: string;
  };
  defaultSelectedNodeCode?: string;
  nodes?: ConsoleNodeOption[];
}

interface PersistedRuntimeState extends RuntimeState {
  profileId: string;
}

const storageKey = "dris-console-runtime-v1";
const runtimeConfigPath = "/console-runtime.config.json";

const fallbackConfig: ConsoleRuntimeConfig = {
  profileId: "default-local-6nodes",
  authProxyBase: "/proxy/auth",
  services: {
    mcpUrl: "http://127.0.0.1:38082/mcp",
    orchestratorUrl: "http://127.0.0.1:38083",
    authBaseUrl: "http://127.0.0.1:8290"
  },
  defaultSelectedNodeCode: "1100RTAA",
  nodes: [
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
  ]
};

export const nodeOptions = ref<ConsoleNodeOption[]>(fallbackConfig.nodes ?? []);
const activeProfileId = ref(fallbackConfig.profileId);
const authProxyBase = ref(fallbackConfig.authProxyBase ?? "/proxy/auth");

const state = reactive<RuntimeState>({
  selectedNodeCode: fallbackConfig.defaultSelectedNodeCode ?? fallbackConfig.nodes?.[0]?.code ?? "",
  mcpUrl: fallbackConfig.services?.mcpUrl ?? "",
  orchestratorUrl: fallbackConfig.services?.orchestratorUrl ?? "",
  authBaseUrl: fallbackConfig.services?.authBaseUrl ?? ""
});

function firstNodeCode(): string {
  return nodeOptions.value[0]?.code ?? "";
}

function normalizeSelectedNodeCode(nodeCode: string | undefined, defaultNodeCode: string | undefined): string {
  if (nodeCode && nodeOptions.value.some((item) => item.code === nodeCode)) {
    return nodeCode;
  }
  if (defaultNodeCode && nodeOptions.value.some((item) => item.code === defaultNodeCode)) {
    return defaultNodeCode;
  }
  return firstNodeCode();
}

function readPersistedState(): PersistedRuntimeState | null {
  if (typeof window === "undefined") {
    return null;
  }
  const raw = window.localStorage.getItem(storageKey);
  if (!raw) {
    return null;
  }
  try {
    return JSON.parse(raw) as PersistedRuntimeState;
  } catch {
    return null;
  }
}

function applyConfig(config: ConsoleRuntimeConfig, allowPersistedState: boolean): void {
  const safeConfig: ConsoleRuntimeConfig = {
    ...fallbackConfig,
    ...config,
    services: {
      ...fallbackConfig.services,
      ...(config.services ?? {})
    },
    nodes: Array.isArray(config.nodes) && config.nodes.length > 0
      ? config.nodes
      : fallbackConfig.nodes
  };

  nodeOptions.value = safeConfig.nodes ?? [];
  activeProfileId.value = safeConfig.profileId || fallbackConfig.profileId;
  authProxyBase.value = safeConfig.authProxyBase || "/proxy/auth";

  const persisted = allowPersistedState ? readPersistedState() : null;
  const persistedMatchesProfile = persisted?.profileId === activeProfileId.value;

  state.mcpUrl = persistedMatchesProfile && persisted?.mcpUrl
    ? persisted.mcpUrl
    : safeConfig.services?.mcpUrl ?? fallbackConfig.services?.mcpUrl ?? "";
  state.orchestratorUrl = persistedMatchesProfile && persisted?.orchestratorUrl
    ? persisted.orchestratorUrl
    : safeConfig.services?.orchestratorUrl ?? fallbackConfig.services?.orchestratorUrl ?? "";
  state.authBaseUrl = persistedMatchesProfile && persisted?.authBaseUrl
    ? persisted.authBaseUrl
    : safeConfig.services?.authBaseUrl ?? fallbackConfig.services?.authBaseUrl ?? "";
  state.selectedNodeCode = normalizeSelectedNodeCode(
    persistedMatchesProfile ? persisted?.selectedNodeCode : undefined,
    safeConfig.defaultSelectedNodeCode
  );
}

async function loadRuntimeConfig(): Promise<void> {
  if (typeof window === "undefined") {
    return;
  }
  try {
    const response = await fetch(`${runtimeConfigPath}?t=${Date.now()}`, {
      cache: "no-store"
    });
    if (!response.ok) {
      return;
    }
    const config = await response.json() as ConsoleRuntimeConfig;
    applyConfig(config, true);
  } catch {
    // keep fallback config
  }
}

applyConfig(fallbackConfig, true);
void loadRuntimeConfig();

if (typeof window !== "undefined") {
  watch(
    () => ({
      ...state,
      profileId: activeProfileId.value
    }),
    (value) => {
      window.localStorage.setItem(storageKey, JSON.stringify(value));
    },
    { deep: true }
  );
}

const currentNode = computed(() => nodeOptions.value.find((item) => item.code === state.selectedNodeCode) ?? nodeOptions.value[0]!);
const currentBackendProxyBase = computed(() => currentNode.value.proxyBase);

export function useConsoleRuntime() {
  return {
    state,
    nodeOptions,
    currentNode,
    currentBackendProxyBase,
    authProxyBase
  };
}
