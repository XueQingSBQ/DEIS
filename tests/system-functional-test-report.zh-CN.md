# DRIS 系统功能测试报告

## 1. 测试对象

本次测试面向 `dris-system-deploy-20260310` 打包目录中的整套本地联调系统，覆盖以下模块：

- `dris-enterprise-backend-v2` 本地 6 节点后端
- `dris-auth-center` 认证中心
- `dris-mcp-server` MCP 服务
- `dris-deepresearch-orchestrator` 轻量编排智能体
- `dris-deepresearch-frontend` 前端界面

## 2. 测试环境

- 测试日期：2026-03-11
- 后端入口节点：`http://127.0.0.1:8200`
- 认证中心：`http://127.0.0.1:8290`
- MCP：`http://127.0.0.1:38082`
- Orchestrator：`http://127.0.0.1:38083`
- Frontend：`http://127.0.0.1:5176`

6 节点后端地址如下：

- `node1-country`: `http://127.0.0.1:8200`
- `node2-province`: `http://127.0.0.1:8201`
- `node3-city-a`: `http://127.0.0.1:8202`
- `node4-county-a`: `http://127.0.0.1:8203`
- `node5-city-b`: `http://127.0.0.1:8204`
- `node6-county-b`: `http://127.0.0.1:8205`

说明：当前运行中的 MCP 已连接 6 节点入口 `8200`。如果后续手动重启 MCP，不要直接照抄 `dris-mcp-server/.env` 中旧的 `8190`，应显式指定 `DRIS_BASE_URL=http://127.0.0.1:8200`。

## 3. 自动化烟测脚本

本次测试使用以下脚本执行：

- `tests\run-system-functional-smoke.ps1`

执行命令：

```powershell
powershell -ExecutionPolicy Bypass -File .\tests\run-system-functional-smoke.ps1 -OutputDir .\tests\output
```

本次最新结果文件：

- `tests\output\system-functional-smoke-20260311134744.json`

## 4. 总体结果

- 总用例数：36
- 通过：35
- 失败：1
- 通过率：97.22%

本次自动化测试生成的样例标识：

- county-a: `1100CNTA011120260311MJSCGPQ298`
- county-b: `1100CNTB011120260311XRZSQ9755J`

## 5. 分模块结果

| 模块 | 用例数 | 通过 | 失败 |
| --- | ---: | ---: | ---: |
| system | 5 | 5 | 0 |
| auth | 3 | 3 | 0 |
| backend | 2 | 2 | 0 |
| identifier | 7 | 7 | 0 |
| resource | 4 | 4 | 0 |
| distributed | 3 | 3 | 0 |
| search | 4 | 4 | 0 |
| audit | 1 | 1 | 0 |
| download | 1 | 1 | 0 |
| mcp | 1 | 1 | 0 |
| agent | 3 | 3 | 0 |
| upload | 2 | 1 | 1 |

## 6. 已通过的关键链路

### 6.1 系统联通

以下入口均返回成功：

- 后端路由表
- 认证中心公钥
- MCP 健康检查
- Orchestrator 健康检查
- Frontend 首页

### 6.2 后端核心业务

以下后端功能已通过：

- 节点拓扑查询
- 指标快照查询
- county-a / county-b 双节点标识注册
- 标识校验、存在性查询、元数据查询
- 元数据全量更新与 Patch 更新
- 资源组件绑定
- 资源版本分页查询
- 分布式批量元数据解析
- 分布式批量数字对象解析
- 分布式批量资源组件解析
- 本地搜索
- 全局递归搜索
- 全局迭代搜索
- 审计历史分页查询
- 下载预签名
- 标识删除与删除后存在性校验

### 6.3 认证链路

以下认证功能已通过：

- 用户注册
- 用户登录
- `Bearer Token` 解析当前用户信息

### 6.4 MCP 链路

MCP 烟测已通过，验证了以下内容：

- 工具列表可获取
- `get_node_routes`
- `search_global`
- `query_audit_logs`
- `resolve_metadata`
- `resolve_object`

### 6.5 DeepResearch 编排链路

Orchestrator 已成功完成一次轻量智能体研究流程：

- `plannerMode`: `heuristic`
- `templateId`: `identifier_compare`
- 识别并聚焦两个显式标识
- 经由 MCP 调用后端完成元数据、对象、组件、审计查询
- 成功返回结构化报告和 Markdown 摘要
- 流式接口返回了 `event: done`

## 7. 发现的 1 个问题

### 7.1 上传完成阶段缺少“实体存在校验”

失败用例：`UPLOAD-002 complete upload without actual object`

预期：

- 当上传会话创建后，如果对象存储中并没有真实文件，调用 `completeUpload` 应返回失败

实际：

- 接口返回 `200 OK`
- 资源被直接绑定到了标识上
- 返回资源中 `extra._verifyStatus = SKIPPED`

这说明当前实现的 `completeUpload` 仍然缺少你之前提到的关键校验能力：

- 对象是否真实存在
- 实际大小是否匹配
- 若提供了 `sha256`，散列是否匹配

这个问题不会影响当前大部分演示链路，但会影响“共享资源真实可信”的设计完整性，建议后续单独补齐。

## 8. 本次测试中修正的样例值

在整理测试样例时，确认了以下值必须按当前代码规则填写：

### 8.1 标识注册枚举

- `dataCategory` 合法值示例：`public`
- `registrationType` 合法值示例：`resource`

不能再使用旧样例里的：

- `dataset`
- `new`

### 8.2 资源组件字段

对于 `componentType=resource`，当前校验规则要求：

- `storageType = external_uri`
- `accessMethod = download`

不能再使用旧样例里的：

- `storageType = local`
- `accessMethod = http`

### 8.3 上传 profile

创建上传会话时，`profile` 不能写 `resource`，应使用实际枚举，例如：

- `data_json`
- `data_csv`
- `doc_pdf`

## 9. 测试结论

从“系统联通性、后端核心功能、跨节点解析、搜索、审计、认证、MCP、轻量智能体编排、前端可访问性”这些主链路来看，当前本地整套系统已经可以作为答辩演示和手工复测基础环境。

当前最主要的遗留点不是路由、搜索、解析或 agent 链路，而是上传完成阶段缺少实体校验。这一项建议作为下一阶段功能完善内容单独修复。
