export interface NavigationItem {
  label: string;
  to: string;
  caption: string;
}

export const navigationItems: NavigationItem[] = [
  { label: "总览", to: "/overview", caption: "系统状态与快捷入口" },
  { label: "节点拓扑", to: "/topology", caption: "拓扑、路由、节点注册" },
  { label: "标识接入", to: "/onboarding", caption: "标识注册、关联资源与上传" },
  { label: "标识维护", to: "/identifiers", caption: "标识解析、更新、注销与关联信息维护" },
  { label: "标识发现", to: "/search", caption: "检索、筛选与批量解析" },
  { label: "审计中心", to: "/audit", caption: "治理与可追踪证据" },
  { label: "研究工作台", to: "/research", caption: "保留现有 DeepResearch" },
  { label: "MCP 工具台", to: "/mcp", caption: "工具层联调与原始响应" },
  { label: "认证中心", to: "/auth", caption: "注册、登录、userinfo、公钥" }
];

