# DRIRP 跨节点方案（精简版）

## 1. 目标

- 同一后端可多节点部署
- 本地优先，未命中再跨节点转发
- 直连关系与路由跳转分表维护

## 2. 关键规则

- 注册不传 level，父节点自动推导子级
- 县级不允许继续注册下级
- `regionCode` 4 位数字，`orgCode` 4 位大写字母
- 全网唯一性通过上行到国家节点判定

## 3. 数据模型（已按新语义）

### 3.1 直连表 `dris_direct_descendant`

- 主键：`child_code = child_region_code + child_org_code`
- 关键字段：`child_node_name`、`child_drirp_url`、`relation_status`、`health_status`

### 3.2 路由表 `dris_route_forward`

- 主键：`target_code = target_region_code + target_org_code`
- 关键字段：`target_node_name`、`next_hop_code`、`next_hop_drirp_url`、`hop_count`
- 说明：下一跳使用编码 `next_hop_code`，不再使用下一跳节点名

## 4. 链路

1. 注册成功后写直连表为 `PENDING/UNKNOWN`
2. 子节点启动发送 `NODE_HELLO`
3. 父节点首次激活后上报 `ROUTE_INSERT_UP`
4. 上级节点落路由，形成跨节点跳转链

## 5. DRIRP 消息字段口径

- `NODE_HELLO`：推荐 `nodeName + nodeCode`
- `ROUTE_INSERT_UP`：推荐 `targetNodeName + senderCode + senderNodeName`
- 兼容旧字段：`nodeId/targetNodeId/senderNodeId`（仅兼容，建议新接入不用）

## 6. 当前状态

- 注册、唯一性上行、启动握手、路由上报：已落地
- 跨节点解析主流程（统一 resolve）：仍在后续迭代
