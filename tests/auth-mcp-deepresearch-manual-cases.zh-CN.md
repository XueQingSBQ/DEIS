# 认证、MCP、DeepResearch 手动测试与样例

## 1. 文档用途

本文档单独覆盖以下模块的测试样例和手动测试步骤：

- `dris-auth-center`
- `dris-mcp-server`
- `dris-deepresearch-orchestrator`
- `dris-deepresearch-frontend`

适用目录：`E:\irp\doip-sdk\dris-system-deploy-20260310`

## 2. 相关脚本

测试辅助脚本：

- [manual-system-api-samples.ps1](/E:/irp/doip-sdk/dris-system-deploy-20260310/tests/manual-system-api-samples.ps1)
- [run-auth-mcp-deepresearch-smoke.ps1](/E:/irp/doip-sdk/dris-system-deploy-20260310/tests/run-auth-mcp-deepresearch-smoke.ps1)

一键执行认证 + MCP + DeepResearch 烟测：

```powershell
cd E:\irp\doip-sdk\dris-system-deploy-20260310
powershell -ExecutionPolicy Bypass -File .\tests\run-auth-mcp-deepresearch-smoke.ps1 -OutputDir .\tests\output
```

输出结果会保存到：

- `E:\irp\doip-sdk\dris-system-deploy-20260310\tests\output\auth-mcp-deepresearch-smoke-*.json`

## 3. 前置条件

需要保证以下服务已经启动：

- 后端 6 节点：`8200` 到 `8205`
- 认证中心：`8290`
- MCP：`38082`
- Orchestrator：`38083`
- Frontend：`5176`

推荐先执行：

```powershell
cd E:\irp\doip-sdk\dris-system-deploy-20260310\local-standalone\scripts
.\status-service.ps1
```

说明：

- 当前本地 MCP 默认应连接 `http://127.0.0.1:8200`
- 当前 6 节点后端配置里 `api-auth-required=false`
- 因此本手册里的“认证测试”主要验证认证中心本身是否可用，不代表业务后端当前已经开启强制鉴权

## 4. 加载测试 helper

```powershell
cd E:\irp\doip-sdk\dris-system-deploy-20260310
. .\tests\manual-system-api-samples.ps1
```

加载后可直接使用以下函数：

- `New-AuthSampleUsername`
- `Register-AuthUser`
- `Login-AuthUser`
- `Invoke-AuthPasswordToken`
- `Get-AuthUserInfo`
- `Get-AuthPublicKey`
- `Get-McpHealth`
- `Invoke-McpSmoke`
- `Get-OrchestratorHealth`
- `Get-AgentModels`
- `Invoke-AgentRun`
- `Invoke-AgentStream`
- `New-AgentSamplePair`
- `Get-FrontendHome`

## 5. 认证中心手动测试

### AUTH-M01 公钥接口

执行：

```powershell
Get-AuthPublicKey
```

预期：

- 返回 `ok=true`
- `data.algorithm=RS256`
- `data.publicKeyPem` 非空

### AUTH-M02 注册用户

执行：

```powershell
$u = New-AuthSampleUsername -Prefix 'manualauth'
$register = Register-AuthUser -Username $u -Password 'Passw0rd!' -DisplayName 'Manual Auth Tester' -OrgCode 'CNTA'
$register
```

预期：

- 返回 `ok=true`
- `data.accessToken` 非空
- 用户名为刚生成的 `$u`

### AUTH-M03 登录用户

执行：

```powershell
$login = Login-AuthUser -Username $u -Password 'Passw0rd!'
$login
```

预期：

- 返回 `ok=true`
- `data.accessToken` 非空
- 当前实现里 `refreshToken` 可能为空字符串，这不算失败

### AUTH-M04 OAuth2 风格 token

执行：

```powershell
$oauth = Invoke-AuthPasswordToken -Username $u -Password 'Passw0rd!'
$oauth
```

预期：

- 返回 `ok=true`
- `data.accessToken` 非空

### AUTH-M05 userinfo

执行：

```powershell
Get-AuthUserInfo -AccessToken $login.data.accessToken
```

预期：

- 返回 `ok=true`
- `data.username` 与 `$u` 一致
- `data.orgCode` 为 `CNTA`

## 6. MCP 手动测试

### MCP-M01 健康检查

执行：

```powershell
Get-McpHealth
```

预期：

- 返回 `ok=true`
- `drisBaseUrl=http://127.0.0.1:8200`
- `route=/mcp`

### MCP-M02 直接看 HTTP 健康响应

执行：

```powershell
Invoke-RestMethod http://127.0.0.1:38082/health
```

预期：

- `name=dris-mcp-server`
- `version=0.1.0`
- `activeSessions` 为非负整数

### MCP-M03 准备一个研究样例标识

执行：

```powershell
$pair = New-AgentSamplePair
$pair
```

预期：

- 返回 `IdentifierA` 和 `IdentifierB`
- `IdentifierA` 已额外绑定了一个 `resource` 组件

### MCP-M04 运行 smoke 脚本

执行：

```powershell
Invoke-McpSmoke -Identifier $pair.IdentifierA -Query 'agent sample'
```

预期：

- 输出中包含 `[smoke] tools:`
- 输出中包含 `resolve_metadata`
- 输出中包含 `get_node_routes`
- 退出时无报错

### MCP-M05 MCP 作用说明

你可以把 `Invoke-McpSmoke` 的输出当作答辩演示证据，说明：

- MCP 已成功暴露工具列表
- MCP 可经由后端访问路由、解析、搜索、审计能力
- DeepResearch 或外部智能体不是直接连数据库，而是通过 MCP 调用系统能力

## 7. DeepResearch Orchestrator API 手动测试

### AGENT-M01 健康检查

执行：

```powershell
Get-OrchestratorHealth
```

预期：

- 返回 `ok=true`
- `plannerMode` 为 `heuristic`、`auto` 或 `llm`
- `mcpServerUrl=http://127.0.0.1:38082/mcp`

### AGENT-M02 模型列表

执行：

```powershell
Get-AgentModels
```

预期：

- 返回 `ok=true`
- 存在 `models` 字段
- 如果你已配置 LLM，则 `models` 应至少返回一个模型

### AGENT-M03 非流式 run 接口

执行：

```powershell
$pair = New-AgentSamplePair
$run = Invoke-AgentRun -IdentifierA $pair.IdentifierA -IdentifierB $pair.IdentifierB
$run
```

预期：

- 返回 `ok=true`
- 存在 `plan`
- 存在 `report`
- `focusedIdentifiers` 至少包含这两个标识中的一部分

### AGENT-M04 流式 stream 接口

执行：

```powershell
$stream = Invoke-AgentStream -IdentifierA $pair.IdentifierA -IdentifierB $pair.IdentifierB
$stream.Content
```

预期：

- 返回 SSE 文本
- 文本中至少能看到：
  - `event: plan`
  - `event: report`
  - `event: done`

### AGENT-M05 研究结果关注点

手动检查 `Invoke-AgentRun` 的返回结果时，重点看：

- `plan.templateId`
- `plan.steps`
- `focusedIdentifiers`
- `report.title`
- `report.summary`
- `report.markdown`

答辩时建议表述为：

- 用户输入自然语言问题
- Orchestrator 先做有界规划
- 再通过 MCP 调后端工具链
- 最终输出结构化报告和 Markdown 摘要

## 8. DeepResearch Frontend 手动测试

### FRONT-M01 页面可访问

浏览器打开：

- `http://127.0.0.1:5176`

预期：

- 页面正常打开
- 标题区域显示 `Digital Object Research Workbench`

### FRONT-M02 Agent 模式健康检查

操作：

1. 页面左侧保持 `Execution Mode = Agent`
2. `Orchestrator URL` 填 `http://127.0.0.1:38083`
3. 点击 `Check Agent`
4. 点击 `Validate Agent`

预期：

- 顶部状态从 `idle` 进入 `ready` 或等效成功状态
- 底部可看到可用工具或连接信息

### FRONT-M03 Agent 模式研究执行

操作：

1. 先在 PowerShell 执行 `$pair = New-AgentSamplePair`
2. 将 `$pair.IdentifierA` 和 `$pair.IdentifierB` 填入页面标识输入区
3. 问题可填：`比较这两个标识的元数据、资源与审计差异`
4. 点击 `Run Agent Research`

预期：

- 中间时间线逐步出现 `plan`、`step`、`report`
- 右侧面板出现研究报告
- 可以点击 `Export Markdown`

### FRONT-M04 Manual 模式研究执行

操作：

1. 切换 `Execution Mode = Manual`
2. `MCP URL` 填 `http://127.0.0.1:38082/mcp`
3. 点击 `Check MCP`
4. 选择一个模板
5. 填写同一组标识并执行

预期：

- 前端直接通过 MCP 完成工具调用
- 时间线正常展开
- 报告区有内容输出

### FRONT-M05 导出结果

操作：

1. 在 Agent 或 Manual 任一模式下完成一次研究
2. 点击 `Export Markdown`

预期：

- 浏览器应下载或导出 Markdown 文本
- 内容包含问题、步骤、标识、研究摘要等信息

## 9. 推荐执行顺序

建议按以下顺序手动测：

1. `AUTH-M01` 到 `AUTH-M05`
2. `MCP-M01` 到 `MCP-M04`
3. `AGENT-M01` 到 `AGENT-M04`
4. `FRONT-M01` 到 `FRONT-M05`

## 10. 注意事项

- 当前 6 节点后端默认未强制开启业务鉴权，所以认证中心测试通过，不代表后端写接口已经强制校验 token
- MCP 当前是只读工具集，适合演示“外部智能体如何安全接入你的系统能力”
- DeepResearch 前端有两种模式：
  - `Agent`：前端连 orchestrator，再由 orchestrator 调 MCP
  - `Manual`：前端直接连 MCP，自行编排工具调用
- 如果你要做答辩演示，优先演示 `Agent` 模式，因为链路更完整，也更符合“智能体访问系统”的叙述