# DRIS 系统手工功能测试用例

## 1. 使用说明

本文档用于你手工复测整套系统，覆盖：

- 后端 6 节点
- 认证中心
- MCP
- DeepResearch Orchestrator
- DeepResearch Frontend

建议测试顺序：

1. 先做系统联通
2. 再做认证
3. 再做后端核心业务
4. 再做 MCP 与 Orchestrator
5. 最后做前端页面联调

## 2. 前置条件

以下服务需要已经启动：

- 后端 6 节点：`8200` 到 `8205`
- 认证中心：`8290`
- MCP：`38082`
- Orchestrator：`38083`
- Frontend：`5176`

建议先进入打包根目录：

```powershell
cd e:\irp\doip-sdk\dris-system-deploy-20260310
```

## 3. 加载通用样例脚本

执行一次：

```powershell
. .\tests\manual-system-api-samples.ps1
```

执行后会注入一组便捷函数，例如：

- `Register-SampleIdentifier`
- `Bind-SampleResource`
- `New-UploadSession`
- `Invoke-LocalSearch`
- `Invoke-GlobalSearch`
- `Register-AuthUser`
- `Login-AuthUser`
- `Invoke-AgentRun`

## 4. 公共变量

如果你不想加载 helper，也可以手动定义：

```powershell
$N1 = 'http://127.0.0.1:8200'
$N2 = 'http://127.0.0.1:8201'
$N3 = 'http://127.0.0.1:8202'
$N4 = 'http://127.0.0.1:8203'
$N5 = 'http://127.0.0.1:8204'
$N6 = 'http://127.0.0.1:8205'
$AUTH = 'http://127.0.0.1:8290'
$MCP = 'http://127.0.0.1:38082'
$AGENT = 'http://127.0.0.1:38083'
$FRONT = 'http://127.0.0.1:5176'
```

## 5. 系统联通测试

### SYS-001 后端路由表

```powershell
Invoke-RestMethod "$N1/api/v2/node/routes"
```

预期：返回 `ok=true`，并包含跨节点路由信息。

### SYS-002 认证中心公钥

```powershell
Invoke-RestMethod "$AUTH/auth/public-key"
```

预期：返回 `issuer`、`audience`、`publicKeyPem`。

### SYS-003 MCP 健康检查

```powershell
Invoke-RestMethod "$MCP/health"
```

预期：返回 `ok=true`，并看到当前连接的 `drisBaseUrl`。

### SYS-004 Orchestrator 健康检查

```powershell
Invoke-RestMethod "$AGENT/health"
```

预期：返回 `ok=true`，并看到 `plannerMode`、`mcpServerUrl`。

### SYS-005 Frontend 首页

浏览器打开：

- `http://127.0.0.1:5176`

预期：页面可正常加载。

## 6. 认证测试

### AUTH-001 用户注册

```powershell
$suffix = Get-Date -Format 'yyyyMMddHHmmss'
$username = "tester_$suffix"
$registerResp = Register-AuthUser -Username $username -Password 'Passw0rd!' -DisplayName "Tester $suffix" -OrgCode 'CNTA'
$registerResp
```

预期：返回 `accessToken`。

### AUTH-002 用户登录

```powershell
$loginResp = Login-AuthUser -Username $username -Password 'Passw0rd!'
$loginResp
```

预期：登录成功，返回新的 `accessToken`。

### AUTH-003 当前用户信息

```powershell
$token = $loginResp.data.accessToken
Invoke-RestMethod "$AUTH/auth/userinfo" -Headers @{ Authorization = "Bearer $token" }
```

预期：`username`、`orgCode` 与注册信息一致。

## 7. 后端拓扑与运维测试

### BE-001 子节点状态

```powershell
Invoke-RestMethod "$N2/api/v2/node/direct-descendants/status"
```

预期：能看到 `node3-city-a`、`node5-city-b`，且在线状态正常。

### BE-002 指标快照

```powershell
Invoke-RestMethod "$N1/api/v2/ops/metrics"
```

预期：返回系统指标快照。

## 8. 标识核心功能测试

### ID-001 在 county-a 注册标识

```powershell
$idA = Register-SampleIdentifier -BaseUrl $N4 -OrgCode 'CNTA' -Name ("sample-meter-a-" + (New-SampleSuffix)) -Owner 'manual-lab-a' -Description 'county-a sample object'
$idA
```

预期：返回一个新的标识字符串。

### ID-002 在 county-b 注册标识

```powershell
$idB = Register-SampleIdentifier -BaseUrl $N6 -OrgCode 'CNTB' -Name ("sample-meter-b-" + (New-SampleSuffix)) -Owner 'manual-lab-b' -Description 'county-b sample object'
$idB
```

预期：返回一个新的标识字符串。

### ID-003 校验、存在性、元数据查询

```powershell
Invoke-RestMethod "$N4/api/v2/identifiers/$idA/validate"
Invoke-RestMethod "$N4/api/v2/identifiers/$idA/exists"
Invoke-RestMethod "$N4/api/v2/identifiers/$idA/metadata"
Invoke-RestMethod "$N1/api/v2/identifiers/$idA/metadata"
```

预期：本地和跨节点元数据均可查到。

### ID-004 元数据全量更新

```powershell
$meta = (Invoke-RestMethod "$N4/api/v2/identifiers/$idA/metadata").data
$meta.description = 'county-a full update'
$meta.applicationScenario = 'lab-updated'
Invoke-RestMethod -Method Put -Uri "$N4/api/v2/identifiers/$idA/metadata" -ContentType 'application/json' -Body ($meta | ConvertTo-Json -Depth 20)
```

预期：返回 `data=true`。

### ID-005 元数据 Patch 更新

```powershell
$patch = @{ description = 'county-a patch update' } | ConvertTo-Json
Invoke-RestMethod -Method Patch -Uri "$N4/api/v2/identifiers/$idA/metadata" -ContentType 'application/json' -Body $patch
```

预期：返回 `data=true`。

### ID-006 删除标识

```powershell
Invoke-RestMethod -Method Delete -Uri "$N6/api/v2/identifiers/$idB"
Invoke-RestMethod "$N6/api/v2/identifiers/$idB/exists"
```

预期：删除成功，随后 `exists=false`。

## 9. 资源组件测试

注意：当前代码规则要求 `componentType=resource` 时必须使用：

- `storageType = external_uri`
- `accessMethod = download`

### RES-001 绑定资源组件

```powershell
Bind-SampleResource -BaseUrl $N4 -Identifier $idA -Locator "local://manual/$idA.json" -Size 128 -Format 'json' -ContentType 'application/json'
Invoke-RestMethod "$N4/api/v2/identifiers/$idA/component"
```

预期：组件绑定成功，可查询到 `storageType=external_uri`。

### RES-002 再次绑定，测试版本记录

```powershell
$body = @{
  componentType = 'resource'
  role = 'primary'
  storageType = 'external_uri'
  accessMethod = 'download'
  contentType = 'application/json'
  format = 'json'
  locator = "local://manual/$idA-v2.json"
  size = 256
  hashValue = ''
  etag = 'etag-v2'
  versionId = 'v2'
  createdAt = (Get-Date).ToString('s')
  extra = @{ quality = 'manual'; revision = 2 }
} | ConvertTo-Json -Depth 20
Invoke-RestMethod -Method Put -Uri "$N4/api/v2/identifiers/$idA/component" -ContentType 'application/json' -Body $body
Invoke-RestMethod "$N4/api/v2/identifiers/$idA/component/versions?page=1&size=10"
```

预期：版本列表中至少出现 2 条记录。

### RES-003 分布式资源组件解析

```powershell
Invoke-RestMethod -Method Post -Uri "$N1/api/v2/identifiers/resolve/batch/component/distributed" -ContentType 'application/json' -Body (@($idA) | ConvertTo-Json)
```

预期：跨节点组件解析成功。

## 10. 搜索与审计测试

### SEARCH-001 向量同步

```powershell
Invoke-RestMethod -Method Post -Uri "$N1/api/v2/ops/vector/sync"
Invoke-RestMethod -Method Post -Uri "$N4/api/v2/ops/vector/sync"
```

预期：返回成功。

### SEARCH-002 本地搜索

```powershell
Invoke-LocalSearch -BaseUrl $N4 -Query 'sample-meter'
```

预期：能查到 `idA`。

### SEARCH-003 全局递归搜索

```powershell
Invoke-GlobalSearch -BaseUrl $N1 -Query 'sample-meter' -Strategy RECURSIVE
```

预期：能看到来自多个节点的结果。

### SEARCH-004 全局迭代搜索

```powershell
Invoke-GlobalSearch -BaseUrl $N1 -Query 'sample-meter' -Strategy ITERATIVE
```

预期：同样能看到来自多个节点的结果。

### AUDIT-001 审计历史分页查询

```powershell
Invoke-RestMethod "$N4/api/v2/audit/identifier-history?identifier=$idA&page=1&size=20"
```

预期：可以看到注册、更新、组件绑定等审计记录。

## 11. 上传与下载测试

### UPLOAD-001 创建上传会话

注意：当前合法的 `profile` 需要用具体枚举，例如 `data_json`，不能写 `resource`。

```powershell
$uploadResp = New-UploadSession -BaseUrl $N4 -Identifier $idA -Profile 'data_json' -Filename 'sample.json' -Size 128
$uploadResp
```

预期：返回 `uploadId`、`bucket`、`objectKey`、`put.url`。

### UPLOAD-002 完成上传

```powershell
$uploadId = $uploadResp.data.uploadId
Invoke-RestMethod -Method Post -Uri "$N4/api/v2/upload-sessions/$uploadId/complete" -ContentType 'application/json' -Body '{}'
```

当前现象：

- 该接口在“没有真实对象内容”的情况下仍可能返回成功

说明：

- 这是当前实现的已知缺口，不是你的测试步骤错了
- 真实设计上，这一步应该补“实体存在校验”

### DOWNLOAD-001 下载预签名

```powershell
$dl = @{ identifier = $idA; expiresInSeconds = 600 } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "$N1/api/v2/downloads/presign" -ContentType 'application/json' -Body $dl
```

预期：返回 `url`、`filename`、`mime`、`size`。

## 12. MCP 测试

### MCP-001 MCP 健康检查

```powershell
Invoke-RestMethod "$MCP/health"
```

### MCP-002 MCP 烟测脚本

```powershell
$env:MCP_SERVER_URL = "$MCP/mcp"
$env:SMOKE_QUERY = 'sample-meter'
$env:SMOKE_IDENTIFIER = $idA
cd e:\irp\doip-sdk\dris-system-deploy-20260310\dris-mcp-server
npm run smoke
```

预期：

- 列出工具名
- 成功调用 `get_node_routes`
- 成功调用 `search_global`
- 成功调用 `query_audit_logs`
- 成功调用 `resolve_metadata`
- 成功调用 `resolve_object`

## 13. Orchestrator 测试

### AGENT-001 健康检查

```powershell
Invoke-RestMethod "$AGENT/health"
```

### AGENT-002 获取模型列表

```powershell
Invoke-RestMethod "$AGENT/api/llm/models"
```

### AGENT-003 执行一次研究任务

```powershell
Invoke-AgentRun -IdentifierA $idA -IdentifierB $idB
```

预期：

- 返回 `plannerMode`
- 返回 `plan.templateId`
- 返回 `focusedIdentifiers`
- 返回 `report`

### AGENT-004 流式研究

```powershell
$body = @{
  question = "compare $idA and $idB metadata, resource and audit history"
  identifiersText = "$idA`n$idB"
  mcpUrl = "$MCP/mcp"
  strategy = 'RECURSIVE'
  searchMode = 'KEYWORD'
  limit = 5
  auditPageSize = 3
} | ConvertTo-Json -Depth 20
Invoke-WebRequest -Method Post -Uri "$AGENT/api/research/stream" -Headers @{ Accept = 'text/event-stream' } -ContentType 'application/json' -Body $body
```

预期：返回的内容中包含 `event: done`。

## 14. Frontend 测试

浏览器打开：

- `http://127.0.0.1:5176`

建议手工检查：

- 页面能正常加载
- 可以切换 MCP / Agent 模式
- 健康检查按钮可用
- 可以填入问题并发起研究
- 在 Agent 模式下能看到研究计划、步骤和最终报告

## 15. 当前必须记住的真实取值

### 15.1 注册请求

正确示例：

```json
{
  "dataCategory": "public",
  "registrationType": "resource"
}
```

### 15.2 资源组件请求

正确示例：

```json
{
  "componentType": "resource",
  "storageType": "external_uri",
  "accessMethod": "download"
}
```

### 15.3 上传会话请求

正确示例：

```json
{
  "profile": "data_json"
}
```

## 16. 推荐执行顺序

如果你想最快复现一遍完整演示链路，建议按下面顺序：

1. `SYS-001` 到 `SYS-005`
2. `AUTH-001` 到 `AUTH-003`
3. `ID-001` 到 `ID-005`
4. `RES-001` 到 `RES-003`
5. `SEARCH-001` 到 `SEARCH-004`
6. `AUDIT-001`
7. `UPLOAD-001` 与 `DOWNLOAD-001`
8. `MCP-002`
9. `AGENT-003` 与 `AGENT-004`
10. 最后做前端页面联调
