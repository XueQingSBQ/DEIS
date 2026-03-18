const replacements: Array<[string, string]> = [
  ["Research Overview", "研究总览"],
  ["Identifier Compare", "标识对比"],
  ["Audit Trace", "审计追踪"],
  ["Anchor Search", "锚点搜索"],
  ["Search and Routing", "搜索与路由"],
  ["Metadata Resolution", "元数据解析"],
  ["Object and Resource Evidence", "数字对象与资源证据"],
  ["Audit Observations", "审计观察"],
  ["Evidence Cards", "证据卡片"],
  ["Focused Identifiers", "焦点标识"],
  ["Generate executive summary", "生成执行摘要"],
  ["Load node routes", "加载节点路由"],
  ["Global search", "全局搜索"],
  ["Anchor search", "锚点搜索"],
  ["Batch metadata resolution", "批量元数据解析"],
  ["Batch object resolution", "批量数字对象解析"],
  ["Batch component resolution", "批量资源解析"],
  ["Resolve metadata", "解析元数据"],
  ["Resolve object", "解析数字对象"],
  ["Resolve component", "解析资源"],
  ["Query paginated audit history to show traceability and governance evidence.", "分页查询审计记录，展示可追溯性与治理证据。"],
  ["inherit", "沿用默认"],
  ["heuristic", "启发式规划"],
  ["llm", "LLM 规划"],
  ["KEYWORD", "关键词"],
  ["SEMANTIC", "语义"],
  ["HYBRID", "混合"],
  ["ITERATIVE", "迭代"],
  ["RECURSIVE", "递归"],
  ["yes", "是"],
  ["no", "否"],
  ["true", "是"],
  ["false", "否"],
  ["unknown", "未知"],
  ["none", "无"]
];

export function translateUiText(value?: string | null): string {
  if (!value) {
    return "";
  }

  let next = value;
  for (const [from, to] of replacements) {
    next = next.split(from).join(to);
  }
  return next;
}

export function connectionStatusLabel(status: string): string {
  switch (status) {
    case "connecting":
      return "连接中";
    case "connected":
      return "已连接";
    case "error":
      return "异常";
    default:
      return "待检查";
  }
}

export function stepStatusLabel(status: string): string {
  switch (status) {
    case "pending":
      return "待执行";
    case "running":
      return "执行中";
    case "success":
      return "成功";
    case "error":
      return "失败";
    case "skipped":
      return "已跳过";
    default:
      return translateUiText(status);
  }
}
