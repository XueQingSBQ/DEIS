import type { ResearchTemplate } from "./types";

export const researchTemplates: ResearchTemplate[] = [
  {
    id: "discovery_overview",
    title: "研究总览",
    subtitle: "搜索 + 跨节点批量解析",
    description: "适合演示完整链路。先根据自然语言问题执行全局搜索，再批量解析元数据、数字对象、资源和审计信息。",
    expectedOutput: "输出一份结构化研究报告，突出候选标识、跨节点覆盖、资源证据与审计观察。",
    requiresQuestion: true,
    requiresIdentifiers: false
  },
  {
    id: "identifier_compare",
    title: "标识对比",
    subtitle: "多标识并行证据分析",
    description: "适合已有明确标识集合的场景，直接对比元数据、数字对象状态、资源绑定情况和近期审计记录。",
    expectedOutput: "输出并列对比结果，突出跨节点解析差异和资源完整性。",
    requiresQuestion: false,
    requiresIdentifiers: true
  },
  {
    id: "audit_trace",
    title: "审计追踪",
    subtitle: "单标识操作链路回放",
    description: "围绕单个标识展开，解析其元数据与对象状态，并查询分页审计日志，说明谁在什么时候执行了什么操作。",
    expectedOutput: "输出一份面向治理与溯源展示的审计追踪报告。",
    requiresQuestion: false,
    requiresIdentifiers: true
  },
  {
    id: "anchor_search",
    title: "锚点搜索",
    subtitle: "指定节点发起的搜索研究",
    description: "用于展示搜索从指定锚点节点发起时的执行路径，并返回跨节点合并后的候选结果和证据链。",
    expectedOutput: "输出以锚点节点为中心的搜索研究报告，附带候选标识与解析证据。",
    requiresQuestion: true,
    requiresIdentifiers: false
  }
];

export function getTemplateById(templateId: string): ResearchTemplate {
  const matched = researchTemplates.find((item) => item.id === templateId);
  return matched ?? researchTemplates[0]!;
}
