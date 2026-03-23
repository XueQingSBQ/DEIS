# DEIS — 数据基础设施标识管理系统

> **D**ata Infr**E**structure **I**dentifier Management **S**ystem

本课题围绕"数据基础设施中的统一标识、分布式解析、资源关联与开放接入"展开，目标是设计并实现一套可部署、可联调、可演示的数据基础设施标识管理系统。系统既服务于传统的数据资源登记、解析和共享，也为大模型、智能体、MCP 工具接入提供标准化底层支撑。

## 核心能力

- **统一标识** — 30 位编码规则（地区 + 组织 + 行业 + 数据分类 + 登记类型 + 日期 + 凭证），支持注册、校验、解析、删除、更新
- **数字对象模型** — 建立标识、元数据、资源实体之间的关联关系，统一管理数据资源、工具资源和服务资源
- **分布式解析** — 多节点环境下的路由传播、跨节点迭代/递归解析与全局检索
- **认证与权限** — JWT 令牌签发、公钥校验、Bearer Token 鉴权
- **MCP 开放接入** — 将系统能力封装为 MCP 工具/资源/提示词，供智能体标准化调用
- **DeepResearch 编排** — 基于有界研究模板的领域智能体，支持启发式与 LLM 双模规划

## 系统架构

```
┌─────────────────────────────────────────────────────────────────┐
│                  dris-deepresearch-frontend                     │
│                    (Vue 3 + Vite 前端)                          │
└──────────────────────────┬──────────────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────────────┐
│              dris-deepresearch-orchestrator                      │
│           (领域编排智能体 · Node.js/TypeScript)                  │
└──────────────────────────┬──────────────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────────────┐
│                    dris-mcp-server                               │
│          (MCP Streamable HTTP 服务 · Node.js/TypeScript)         │
└──────────────────────────┬──────────────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────────────┐
│              dris-enterprise-backend-v2                          │
│        (Spring Boot 企业后端 · 标识/元数据/资源/路由/审计)        │
├─────────────────┬───────────────────────────────────────────────┤
│ dris-java-sdk-next       │            drirp-java-sdk            │
│  (标识核心 SDK)           │       (跨节点通信协议 SDK)            │
└─────────────────┴───────────────────────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────────────┐
│                    dris-auth-center                              │
│              (统一认证中心 · Spring Boot)                        │
└─────────────────────────────────────────────────────────────────┘
```

## 项目模块

| 模块 | 技术栈 | 说明 |
| --- | --- | --- |
| [dris-java-sdk-next](dris-java-sdk-next/) | Java 8 · Maven | 标识核心 SDK：编码规则、格式校验、标识注册/解析/删除、元数据与资源模型、领域抽象 |
| [drirp-java-sdk](drirp-java-sdk/) | Java 8 · gRPC · Protobuf | 跨节点通信协议 SDK：统一消息模型、HTTP/gRPC 双通道、HMAC 鉴权、防重放、幂等重试 |
| [dris-enterprise-backend-v2](dris-enterprise-backend-v2/) | Spring Boot 2.7 · MySQL | 企业级业务后端：标识注册/解析、资源绑定、上传下载、搜索、审计、节点路由、向量索引 |
| [dris-auth-center](dris-auth-center/) | Spring Boot 2.7 · JWT | 统一认证中心：用户注册/登录、OAuth2 风格令牌签发、公钥发布、用户信息查询 |
| [dris-mcp-server](dris-mcp-server/) | Node.js · TypeScript · Express | MCP 服务层：将后端能力封装为 MCP 工具、资源和提示词，Streamable HTTP 传输 |
| [dris-deepresearch-orchestrator](dris-deepresearch-orchestrator/) | Node.js · TypeScript · Express | 领域编排智能体：归一化请求 → 选择研究模板 → 执行 MCP 工具 → 生成结构化报告 |
| [dris-deepresearch-frontend](dris-deepresearch-frontend/) | Vue 3 · TypeScript · Vite | 研究前端：数字对象研究台，可视化展示编排结果 |
| [local-standalone](local-standalone/) | PowerShell 脚本 | 本地单服务/多节点启停脚本与运维工具 |

## 环境要求

- Java 17+、Maven 3.8+
- Node.js 18+、npm
- MySQL 8.0+（数据库 `dris_db`、`dris_auth`）

## 快速开始

### 1. 构建 SDK

```bash
# 标识核心 SDK
mvn -f dris-java-sdk-next/pom.xml install

# 协议 SDK
mvn -f drirp-java-sdk/pom.xml install
```

### 2. 启动认证中心（可选）

```bash
cd dris-auth-center
mvn spring-boot:run
# 默认端口：8290
```

### 3. 启动企业后端

```bash
cd dris-enterprise-backend-v2
mvn spring-boot:run
# 默认端口：8190
```

### 4. 启动 MCP 服务

```bash
cd dris-mcp-server
npm install
npm run dev
# 默认端口：38082
```

### 5. 启动编排服务

```bash
cd dris-deepresearch-orchestrator
npm install
npm run dev
# 默认端口：38083
```

### 6. 启动前端

```bash
cd dris-deepresearch-frontend
npm install
npm run dev
# 默认端口：5176
```

### 使用本地脚本一键管理

```powershell
cd local-standalone/scripts

# 启动各服务
.\start-service.ps1 -Service auth
.\start-service.ps1 -Service backend
.\start-service.ps1 -Service mcp
.\start-service.ps1 -Service orchestrator
.\start-service.ps1 -Service frontend

# 查看状态
.\status-service.ps1

# 停止各服务
.\stop-service.ps1 -Service backend
```

详见 [local-standalone/README.zh-CN.md](local-standalone/README.zh-CN.md)。

## 多节点部署

系统支持本地 6 节点拓扑部署（Country → Province → City/County），模拟分布式标识解析环境：

| 节点 | 级别 | HTTP 端口 | DRIRP 端口 |
| --- | --- | ---: | ---: |
| node1-country | COUNTRY | 8200 | 19200 |
| node2-province | PROVINCE | 8201 | 19201 |
| node3-city-a | CITY | 8202 | 19202 |
| node4-county-a | COUNTY | 8203 | 19203 |
| node5-city-b | CITY | 8204 | 19204 |
| node6-county-b | COUNTY | 8205 | 19205 |

详见 [dris-enterprise-backend-v2/deploy/local-6nodes/README.md](dris-enterprise-backend-v2/deploy/local-6nodes/README.md)。

## 默认端口一览

| 服务 | 端口 |
| --- | ---: |
| 企业后端（单节点） | 8190 |
| 认证中心 | 8290 |
| MCP 服务 | 38082 |
| 编排服务 | 38083 |
| 前端 | 5176 |

## 测试

### 自动化烟测

```powershell
# 系统功能烟测（36 项用例）
powershell -ExecutionPolicy Bypass -File .\tests\run-system-functional-smoke.ps1 -OutputDir .\tests\output

# 认证 + MCP + DeepResearch 烟测
powershell -ExecutionPolicy Bypass -File .\tests\run-auth-mcp-deepresearch-smoke.ps1 -OutputDir .\tests\output
```

### 手动测试

- [系统手工功能测试用例](tests/system-manual-functional-cases.zh-CN.md)
- [认证/MCP/DeepResearch 手动测试](tests/auth-mcp-deepresearch-manual-cases.zh-CN.md)
- [系统功能测试报告](tests/system-functional-test-report.zh-CN.md)（36 项用例，通过率 97.22%）

### SDK 单元测试

```bash
mvn -f dris-java-sdk-next/pom.xml test
mvn -f drirp-java-sdk/pom.xml test
```

## MCP 工具列表

| 工具 | 说明 |
| --- | --- |
| `resolve_metadata` | 解析标识元数据 |
| `resolve_object` | 解析数字对象 |
| `resolve_component` | 解析资源组件 |
| `batch_resolve_metadata_distributed` | 分布式批量元数据解析 |
| `batch_resolve_object_distributed` | 分布式批量数字对象解析 |
| `batch_resolve_component_distributed` | 分布式批量资源组件解析 |
| `search_global` | 全局搜索 |
| `query_audit_logs` | 审计日志查询 |
| `get_node_routes` | 获取节点路由 |

## 项目文档

### 交付材料

- [中期报告](deliverables/中期报告.md)
- [需求分析文档](deliverables/中期检查材料/需求分析文档.md)
- [概要设计文档](deliverables/中期检查材料/概要设计文档.md)
- [数据库设计文档](deliverables/中期检查材料/数据库设计文档.md)
- [测试文档](deliverables/中期检查材料/测试文档.md)

### UML 图

- [系统组件图](deliverables/uml/01-system-component.puml)
- [SDK 类图](deliverables/uml/02-sdk-class-diagram.puml)
- [后端类图](deliverables/uml/03-backend-class-diagram.puml)
- [MCP 类图](deliverables/uml/04-mcp-class-diagram.puml)
- [研究编排时序图](deliverables/uml/05-research-sequence.puml)
- [部署图](deliverables/uml/06-deployment-diagram.puml)
- [数字对象核心类图](deliverables/uml/07-digital-object-core-class.puml)
- [数字对象服务类图](deliverables/uml/08-digital-object-service-class.puml)

### 后端文档

- [后端接口文档](dris-enterprise-backend-v2/docs/后端接口文档.md)
- [软件测试文档](dris-enterprise-backend-v2/docs/软件测试文档.md)

### SDK 文档

- [SDK 内核设计](dris-java-sdk-next/docs/sdk-kernel-design.md)
- [DRIRP 协议契约](drirp-java-sdk/DRIRP_DRIS_CONTRACT.md)

## 许可证

本项目为毕业设计项目，仅供学术用途。
