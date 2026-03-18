# 本地 6 节点联调说明

本目录用于在一台机器上模拟 6 个 DRIS 业务节点，便于你做本地联调、功能测试和实验验证。

## 节点拓扑

1. `node1-country`：HTTP `8200`，DRIRP `19200`
2. `node2-province`：HTTP `8201`，DRIRP `19201`
3. `node3-city-a`：HTTP `8202`，DRIRP `19202`
4. `node4-county-a`：HTTP `8203`，DRIRP `19203`
5. `node5-city-b`：HTTP `8204`，DRIRP `19204`
6. `node6-county-b`：HTTP `8205`，DRIRP `19205`

## 目录说明

- `nodes/`：6 个节点的配置文件
- `scripts/`：启停、状态、日志窗口、数据库初始化脚本
- `tests/`：后端手工测试样例
- `runtime/`：运行日志和 pid 文件

## 当前存储模式

这套 `local-6nodes` 配置已经接成私有 COS 可用的形式，关键环境变量如下：

- `DRIS_STORAGE_PROVIDER`
- `DRIS_STORAGE_ENDPOINT`
- `DRIS_STORAGE_REGION`
- `DRIS_DEFAULT_BUCKET`
- `DRIS_STORAGE_PUBLIC_BASE_URL`
- `DRIS_STORAGE_SECRET_ID`
- `DRIS_STORAGE_SECRET_KEY`
- `DRIS_STORAGE_SESSION_TOKEN`

如果不额外传值，默认仍会使用：

- `provider = cos`
- `endpoint = https://cos.ap-beijing.myqcloud.com`
- `region = ap-beijing`
- `bucket = dris-repo-prod-1330585503`

注意：`DRIS_STORAGE_SECRET_ID` 和 `DRIS_STORAGE_SECRET_KEY` 在私有 COS 场景下是必填的；没有这两个值时，后端不会再返回不可用的裸上传地址，而是直接在创建上传会话时失败。

## 已实现的 COS 能力

当前后端已经支持：

- 创建带签名的 COS `PUT` 上传地址
- 创建带签名的 COS `GET` 下载地址
- `completeUpload` 时通过 COS SDK 校验对象是否存在
- 校验对象大小是否与上传会话一致
- 如果上传会话提供 `sha256`，校验对象元数据中的 `x-cos-meta-sha256`

这意味着私有桶下的上传链路已经不再依赖匿名 `HEAD` 请求。

## 启动方式

### 1. 普通启动

```powershell
cd e:\irp\doip-sdk\dris-system-deploy-20260310\dris-enterprise-backend-v2\deploy\local-6nodes
.\scripts\start-6nodes.ps1 -BuildJar:$false
```

这时节点会直接读取 `nodes/*.yml` 中的默认值或当前终端环境变量。

### 2. 用 COS 包装脚本启动

```powershell
cd e:\irp\doip-sdk\dris-system-deploy-20260310\dris-enterprise-backend-v2\deploy\local-6nodes
.\scripts\start-6nodes-cos.ps1 -BuildJar:$false `
  -StorageSecretId '你的SecretId' `
  -StorageSecretKey '你的SecretKey'
```

如果你使用临时凭证，还可以继续传：

```powershell
.\scripts\start-6nodes-cos.ps1 -BuildJar:$false `
  -StorageEndpoint 'https://cos.ap-beijing.myqcloud.com' `
  -StorageRegion 'ap-beijing' `
  -DefaultBucket 'your-bucket-1250000000' `
  -StorageSecretId '你的SecretId' `
  -StorageSecretKey '你的SecretKey' `
  -StorageSessionToken '你的SessionToken'
```

## 启动前建议先做的检查

如果是服务器环境，先确认能访问 COS：

```bash
nslookup your-bucket-1250000000.cos.ap-beijing.myqcloud.com
nc -vz your-bucket-1250000000.cos.ap-beijing.myqcloud.com 443
curl -I https://your-bucket-1250000000.cos.ap-beijing.myqcloud.com
```

只要能解析域名、能连 443、`curl` 返回 `403` 或 `404`，通常都说明网络是通的。

## 查看状态

```powershell
.\scripts\status-6nodes.ps1
```

## 打开日志窗口

```powershell
.\scripts\open-log-windows.ps1
```

## 停止节点

```powershell
.\scripts\stop-6nodes.ps1
```

## 手工测试文档

后端手工样例：

- `tests/manual-functional-cases.zh-CN.md`
- `tests/manual-api-samples.ps1`

整套系统手工样例请优先看打包根目录：

- `e:\irp\doip-sdk\dris-system-deploy-20260310\tests\system-manual-functional-cases.zh-CN.md`
