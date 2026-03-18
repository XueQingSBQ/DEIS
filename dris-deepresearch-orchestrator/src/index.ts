import { env } from "./config/env.js";
import { createHttpApp } from "./server/createHttpApp.js";

const app = createHttpApp(env);
app.listen(env.PORT, env.HOST, () => {
  console.log(`[orchestrator] listening on http://${env.HOST}:${env.PORT}`);
  console.log(`[orchestrator] planner=${env.PLANNER_MODE} mcp=${env.MCP_SERVER_URL}`);
});
