import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";

function createProxy(target: string, prefix: string) {
  return {
    target,
    changeOrigin: true,
    rewrite: (path: string) => path.replace(new RegExp(`^${prefix}`), "")
  };
}

export default defineConfig({
  plugins: [vue()],
  server: {
    host: "0.0.0.0",
    port: 5176,
    proxy: {
      "/proxy/node/root": createProxy("http://127.0.0.1:8200", "/proxy/node/root"),
      "/proxy/node/proa": createProxy("http://127.0.0.1:8201", "/proxy/node/proa"),
      "/proxy/node/cita": createProxy("http://127.0.0.1:8202", "/proxy/node/cita"),
      "/proxy/node/cnta": createProxy("http://127.0.0.1:8203", "/proxy/node/cnta"),
      "/proxy/node/citb": createProxy("http://127.0.0.1:8204", "/proxy/node/citb"),
      "/proxy/node/cntb": createProxy("http://127.0.0.1:8205", "/proxy/node/cntb"),
      "/proxy/auth": createProxy("http://127.0.0.1:8290", "/proxy/auth")
    }
  }
});
