import { createRouter, createWebHashHistory } from "vue-router";

import OverviewPage from "../modules/overview/OverviewPage.vue";
import TopologyPage from "../modules/topology/TopologyPage.vue";
import IdentifierOnboardingPage from "../modules/onboarding/IdentifierOnboardingPage.vue";
import IdentifierManagementPage from "../modules/identifiers/IdentifierManagementPage.vue";
import SearchPage from "../modules/search/SearchPage.vue";
import AuditPage from "../modules/audit/AuditPage.vue";
import ResearchWorkspacePage from "../modules/research/ResearchWorkspacePage.vue";
import McpToolPage from "../modules/mcp/McpToolPage.vue";
import AuthPage from "../modules/auth/AuthPage.vue";

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: "/", redirect: "/overview" },
    { path: "/overview", component: OverviewPage },
    { path: "/topology", component: TopologyPage },
    { path: "/onboarding", component: IdentifierOnboardingPage },
    { path: "/identifiers", component: IdentifierManagementPage },
    { path: "/search", component: SearchPage },
    { path: "/audit", component: AuditPage },
    { path: "/research", component: ResearchWorkspacePage },
    { path: "/mcp", component: McpToolPage },
    { path: "/auth", component: AuthPage }
  ]
});

export default router;
