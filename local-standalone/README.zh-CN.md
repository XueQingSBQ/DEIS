# 本地单服务启动说明

目录：`e:\irp\doip-sdk\dris-system-deploy-20260310\local-standalone`

这套脚本按“单服务独立启动”设计，不提供统一总控。你可以像部署单个后端一样，分别启动：

- `auth` -> `dris-auth-center`
- `backend` -> `dris-enterprise-backend-v2`
- `mcp` -> `dris-mcp-server`
- `orchestrator` -> `dris-deepresearch-orchestrator`
- `frontend` -> `dris-deepresearch-frontend`

## 运行前提

- MySQL 已启动，`127.0.0.1:3306` 可访问
- 本地已安装 `Java 17+`、`Maven`、`Node.js 18+`、`npm`
- `dris_db` 和 `dris_auth` 数据库可用

## 默认端口

- `auth`: `8290`
- `backend`: `8190`
- `mcp`: `38082`
- `orchestrator`: `38083`
- `frontend`: `5176`

说明：
- 这里把 `mcp` 固定为 `38082`，与 DeepResearch 前端和 orchestrator 默认配置保持一致。
- `mcp` 默认转发到 `http://127.0.0.1:8190`，如果你接六节点后端入口，请启动时显式传 `-DrisBaseUrl http://127.0.0.1:8200`
- `orchestrator` 默认连接 `http://127.0.0.1:38082/mcp`
- `orchestrator` 默认使用 `heuristic` 模式，避免本地没配 LLM 时启动失败

## 常用命令

查看全部服务状态：

```powershell
cd e:\irp\doip-sdk\dris-system-deploy-20260310\local-standalone\scripts
.\status-service.ps1
```

启动单个服务：

```powershell
.\start-service.ps1 -Service backend
.\start-service.ps1 -Service auth
.\start-service.ps1 -Service mcp
.\start-service.ps1 -Service mcp -DrisBaseUrl http://127.0.0.1:8200
.\start-service.ps1 -Service orchestrator
.\start-service.ps1 -Service orchestrator -McpServerUrl http://127.0.0.1:38082/mcp -PlannerMode heuristic
.\start-service.ps1 -Service frontend
```

如果你想看实时控制台输出，可以加 `-ShowWindow`：

```powershell
.\start-service.ps1 -Service backend -ShowWindow
```

停止单个服务：

```powershell
.\stop-service.ps1 -Service backend
.\stop-service.ps1 -Service auth
.\stop-service.ps1 -Service mcp
.\stop-service.ps1 -Service orchestrator
.\stop-service.ps1 -Service frontend
```

## 推荐启动顺序

如果你只跑后端：

```powershell
.\start-service.ps1 -Service backend -ShowWindow
```

如果你要跑 DeepResearch 整条链路（接六节点入口 `8200`）：

```powershell
.\start-service.ps1 -Service backend
.\start-service.ps1 -Service mcp
.\start-service.ps1 -Service mcp -DrisBaseUrl http://127.0.0.1:8200
.\start-service.ps1 -Service orchestrator
.\start-service.ps1 -Service orchestrator -McpServerUrl http://127.0.0.1:38082/mcp -PlannerMode heuristic
.\start-service.ps1 -Service frontend
```

如果你要带认证：

```powershell
.\start-service.ps1 -Service auth
.\start-service.ps1 -Service backend
```

## 日志与 pid

- 日志目录：`local-standalone\runtime\logs`
- pid 目录：`local-standalone\runtime\pids`
- 运行脚本目录：`local-standalone\runtime\runners`

## 访问地址

- 后端：`http://127.0.0.1:8190`
- 认证中心：`http://127.0.0.1:8290`
- MCP 健康检查：`http://127.0.0.1:38082/health`
- Orchestrator 健康检查：`http://127.0.0.1:38083/health`
- DeepResearch 前端：`http://127.0.0.1:5176`



