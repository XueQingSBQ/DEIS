# 本地 6 节点后端手工测试说明

这份文档只保留后端 6 节点相关测试入口。

如果你要测试整套系统，请优先使用打包根目录下的文档：

- `e:\irp\doip-sdk\dris-system-deploy-20260310\tests\system-manual-functional-cases.zh-CN.md`
- `e:\irp\doip-sdk\dris-system-deploy-20260310\tests\system-functional-test-report.zh-CN.md`

## 1. 节点地址

- `node1-country`: `http://127.0.0.1:8200`
- `node2-province`: `http://127.0.0.1:8201`
- `node3-city-a`: `http://127.0.0.1:8202`
- `node4-county-a`: `http://127.0.0.1:8203`
- `node5-city-b`: `http://127.0.0.1:8204`
- `node6-county-b`: `http://127.0.0.1:8205`

## 2. 当前必须使用的真实取值

### 2.1 标识注册

正确：

- `dataCategory = public`
- `registrationType = resource`

错误旧值：

- `dataCategory = dataset`
- `registrationType = new`

### 2.2 资源组件

当 `componentType = resource` 时，正确：

- `storageType = external_uri`
- `accessMethod = download`

错误旧值：

- `storageType = local`
- `accessMethod = http`

### 2.3 上传会话

`profile` 不能写 `resource`，应写具体 profile，例如：

- `data_json`
- `data_csv`
- `doc_pdf`

## 3. helper 脚本

当前目录下的 helper 脚本已经更新：

- `manual-api-samples.ps1`

加载方式：

```powershell
cd e:\irp\doip-sdk\dris-system-deploy-20260310\dris-enterprise-backend-v2\deploy\local-6nodes\tests
. .\manual-api-samples.ps1
```

## 4. 推荐入口

如果你只做后端手工测试，建议直接从下面几项开始：

```powershell
$N1 = 'http://127.0.0.1:8200'
$N4 = 'http://127.0.0.1:8203'
$N6 = 'http://127.0.0.1:8205'

$idA = Register-SampleIdentifier -BaseUrl $N4 -OrgCode 'CNTA' -Name 'sample-meter-a'
$idB = Register-SampleIdentifier -BaseUrl $N6 -OrgCode 'CNTB' -Name 'sample-meter-b'

Bind-SampleResource -BaseUrl $N4 -Identifier $idA
Invoke-RestMethod "$N4/api/v2/identifiers/$idA/component"
Invoke-RestMethod -Method Post -Uri "$N1/api/v2/identifiers/resolve/batch/distributed" -ContentType 'application/json' -Body (@($idA, $idB) | ConvertTo-Json)
```

更完整的用例、认证、MCP、Orchestrator、Frontend 联调步骤请看系统级文档。
