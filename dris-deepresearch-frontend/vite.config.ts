import fs from "node:fs";
import path from "node:path";
import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";

interface RuntimeNodeProxyConfig {
  proxyBase: string;
  proxyTarget?: string;
}

interface RuntimeConsoleConfig {
  authProxyBase?: string;
  authProxyTarget?: string;
  nodes?: RuntimeNodeProxyConfig[];
}

function createProxy(target: string, prefix: string) {
  return {
    target,
    changeOrigin: true,
    rewrite: (requestPath: string) => requestPath.replace(new RegExp(`^${prefix}`), "")
  };
}

function loadRuntimeConfig(): RuntimeConsoleConfig {
  const configPath = path.resolve(__dirname, "public/console-runtime.config.json");
  try {
    const raw = fs.readFileSync(configPath, "utf-8");
    return JSON.parse(raw) as RuntimeConsoleConfig;
  } catch {
    return {};
  }
}

function buildProxyTable() {
  const runtimeConfig = loadRuntimeConfig();
  const proxy: Record<string, ReturnType<typeof createProxy>> = {};

  for (const node of runtimeConfig.nodes ?? []) {
    if (node?.proxyBase && node?.proxyTarget) {
      proxy[node.proxyBase] = createProxy(node.proxyTarget, node.proxyBase);
    }
  }

  const authProxyBase = runtimeConfig.authProxyBase || "/proxy/auth";
  const authProxyTarget = runtimeConfig.authProxyTarget || "http://127.0.0.1:8290";
  proxy[authProxyBase] = createProxy(authProxyTarget, authProxyBase);

  return proxy;
}

export default defineConfig({
  plugins: [vue()],
  server: {
    host: "0.0.0.0",
    port: 5176,
    proxy: buildProxyTable()
  }
});
