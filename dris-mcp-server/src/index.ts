import { createServer } from "node:http";

import { env } from "./config/env.js";
import { createHttpApp } from "./server/httpApp.js";

const { app, shutdown } = createHttpApp(env);
const server = createServer(app);

server.listen(env.MCP_PORT, () => {
  console.log(`[mcp] ${env.MCP_SERVER_NAME}@${env.MCP_SERVER_VERSION} listening on http://127.0.0.1:${env.MCP_PORT}${env.MCP_ROUTE_PATH}`);
  console.log(`[mcp] forwarding DRIS requests to ${env.DRIS_BASE_URL}`);
});

async function closeGracefully(signal: string): Promise<void> {
  console.log(`[mcp] received ${signal}, shutting down`);

  await new Promise<void>((resolve, reject) => {
    server.close((error) => {
      if (error) {
        reject(error);
        return;
      }
      resolve();
    });
  });

  await shutdown();
  process.exit(0);
}

for (const signal of ["SIGINT", "SIGTERM"] as const) {
  process.on(signal, () => {
    void closeGracefully(signal).catch((error) => {
      console.error("[mcp] graceful shutdown failed:", error);
      process.exit(1);
    });
  });
}
