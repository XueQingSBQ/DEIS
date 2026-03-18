<template>
  <div class="node-graph-wrap">
    <div
      class="node-graph-stage node-graph-stage-grid"
      :style="{ width: `${stageWidth}px`, minWidth: `${stageWidth}px`, height: `${stageHeight}px` }"
    >
      <svg
        class="node-graph-svg"
        :viewBox="`0 0 ${stageWidth} ${stageHeight}`"
        preserveAspectRatio="none"
      >
        <defs>
          <linearGradient id="node-edge-gradient" x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" stop-color="rgba(23,106,98,0.16)" />
            <stop offset="100%" stop-color="rgba(191,107,63,0.18)" />
          </linearGradient>
        </defs>
        <path
          v-for="edge in graphEdges"
          :key="edge.key"
          :d="edge.path"
          class="node-edge"
          :class="`node-edge-${edge.state}`"
        />
      </svg>

      <div class="node-lane-guides">
        <div
          v-for="lane in graphLanes"
          :key="lane.level"
          class="node-lane-guide"
          :style="{ top: `${lane.y - 16}px` }"
        >
          <span class="node-lane-tag">{{ lane.label }}</span>
          <div class="node-lane-rule" />
        </div>
      </div>

      <article
        v-for="node in graphNodes"
        :key="node.code"
        class="node-card"
        :class="`node-card-${node.status}`"
        :style="{ left: `${node.x}px`, top: `${node.y}px` }"
      >
        <div class="node-card-head">
          <span class="node-card-level">{{ levelLabel(node.level) }}</span>
          <span class="node-card-badge">{{ statusLabel(node.status) }}</span>
        </div>
        <strong>{{ node.label }}</strong>
        <p class="node-card-code">{{ node.code }}</p>
        <small class="node-card-endpoint mono" :title="node.drirpPublicUrl">{{ compactEndpoint(node.drirpPublicUrl) }}</small>
      </article>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";

import type { ConsoleNodeOption } from "../composables/useConsoleRuntime";

interface StatusItem {
  nodeCode?: string;
  relationStatus?: string;
  healthStatus?: string;
  online?: boolean;
}

interface RouteItem {
  targetCode?: string;
}

type GraphNodeStatus = "current" | "online" | "pending" | "offline" | "idle";
type NodeLevel = ConsoleNodeOption["level"];

const props = defineProps<{
  nodes: ConsoleNodeOption[];
  currentNodeCode: string;
  statusItems?: StatusItem[];
  routeItems?: RouteItem[];
}>();

const stageWidth = 1120;
const stageHeight = 760;
const cardHeight = 116;
const laneYMap: Record<NodeLevel, number> = {
  COUNTRY: 48,
  PROVINCE: 238,
  CITY: 448,
  COUNTY: 638
};
const levelOrder: NodeLevel[] = ["COUNTRY", "PROVINCE", "CITY", "COUNTY"];

function levelLabel(level: NodeLevel): string {
  return level === "COUNTRY"
    ? "国家"
    : level === "PROVINCE"
      ? "省"
      : level === "CITY"
        ? "市"
        : "县";
}

function statusLabel(status: GraphNodeStatus): string {
  return status === "current"
    ? "当前节点"
    : status === "online"
      ? "在线"
      : status === "pending"
        ? "待激活"
        : status === "offline"
          ? "离线"
          : "未观测";
}

function compactEndpoint(endpoint: string): string {
  if (!endpoint) {
    return "未配置 DRIRP 地址";
  }
  return endpoint.replace(/^grpc:\/\//, "");
}

const statusMap = computed(() => {
  const map = new Map<string, StatusItem>();
  for (const item of props.statusItems ?? []) {
    if (item.nodeCode) {
      map.set(item.nodeCode, item);
    }
  }
  return map;
});

function resolveStatus(node: ConsoleNodeOption): GraphNodeStatus {
  if (node.code === props.currentNodeCode) {
    return "current";
  }
  const status = statusMap.value.get(node.code);
  if (status?.online) {
    return "online";
  }
  if (status && (status.healthStatus === "OFFLINE" || status.relationStatus === "OFFLINE")) {
    return "offline";
  }
  if (status && (status.relationStatus === "PENDING" || status.healthStatus === "UNKNOWN" || status.healthStatus === "ACTIVE")) {
    return "pending";
  }
  return "idle";
}

const graphLanes = computed(() => levelOrder.map((level) => ({
  level,
  label: `${levelLabel(level)}级轨道`,
  y: laneYMap[level]
})));

const graphNodes = computed(() => {
  const groups = new Map<NodeLevel, ConsoleNodeOption[]>();
  for (const level of levelOrder) {
    groups.set(level, []);
  }
  for (const node of props.nodes) {
    const nodes = groups.get(node.level) ?? [];
    nodes.push(node);
    groups.set(node.level, nodes);
  }

  return levelOrder.flatMap((level) => {
    const nodes = (groups.get(level) ?? []).slice().sort((left, right) => left.code.localeCompare(right.code));
    const count = nodes.length;
    return nodes.map((node, index) => {
      const centerX = stageWidth * (index + 1) / (count + 1);
      return {
        ...node,
        x: centerX,
        y: laneYMap[level],
        centerX,
        status: resolveStatus(node)
      };
    });
  });
});

const graphEdges = computed(() => {
  const positionMap = new Map(graphNodes.value.map((node) => [node.code, node]));
  return graphNodes.value
    .filter((node) => node.parentCode)
    .map((node) => {
      const from = positionMap.get(node.parentCode!);
      const to = node;
      if (!from) {
        return null;
      }
      const parentStatus = from.status;
      const childStatus = to.status;
      const state = childStatus === "online"
        ? "online"
        : childStatus === "pending" || parentStatus === "current"
          ? "active"
          : "idle";
      const startX = from.centerX;
      const startY = from.y + cardHeight;
      const endX = to.centerX;
      const endY = to.y;
      const midY = Math.round((startY + endY) / 2);
      return {
        key: `${node.parentCode}-${node.code}`,
        path: `M ${startX} ${startY} L ${startX} ${midY} L ${endX} ${midY} L ${endX} ${endY}`,
        state
      };
    })
    .filter(Boolean) as Array<{ key: string; path: string; state: string }>;
});
</script>
