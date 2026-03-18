# 后端接入 `dris-java-sdk-next` 改造方案

日期：2026-03-09  
状态：Draft

## 1. 目标

本方案回答两个问题：

1. `dris-enterprise-backend-v2` 如果切换到新的 `dris-java-sdk-next`，后端应该如何改造。
2. 旧版 `dris-java-sdk` 中哪些能力不应继续留在 SDK，而应迁入后端。

核心原则：

- 新 SDK 只保留稳定的标识内核能力。
- 后端负责业务编排、数据库接入、对象存储、搜索、DRIRP、审计、权限。
- 迁移后要尽量保持现有 REST 行为和跨节点行为不变。

## 2. 当前现状

目前 `dris-enterprise-backend-v2` 仍然直接依赖旧 SDK 的运行时内核，而不是只依赖一个薄内核 SDK。

典型依赖点：

- `src/main/java/com/dris/v2/backend/config/BackendV2Configuration.java`
  - 直接装配 `DrisCoreService`、`InMemoryDrisCoreService`、`DrisClient`
- `src/main/java/com/dris/v2/backend/service/IdentifierCoreService.java`
  - 直接调用 `drisClient.generateIdentifier(...)`、`updateIdentifier(...)` 等
- `src/main/java/com/dris/v2/backend/service/IdentifierResourceService.java`
  - 直接调用 `drisClient.getComponent(...)`、`upsertComponent(...)`
- `src/main/java/com/dris/v2/backend/service/resolve/ResolutionSupport.java`
  - 本地解析分支直接走 `drisClient`
- `src/main/java/com/dris/v2/backend/service/operation/OperationSupport.java`
  - 下载签名直接走 `drisClient.createDownloadPresign(...)`
- `src/main/java/com/dris/v2/backend/service/IdentifierUploadService.java`
  - 上传创建、完成仍直接依赖旧 SDK 的上传流程
- `src/main/java/com/dris/v2/backend/service/FederatedSearchService.java`
  - 本地搜索仍走旧 SDK `drisClient.search(...)`
- `src/main/java/com/dris/v2/backend/service/SystemOpsService.java`
  - 向量同步和指标仍走旧 SDK

这说明当前后端对旧 SDK 的依赖，不只是“模型依赖”，而是“运行时和业务能力依赖”。

## 3. 新 SDK 的定位

新的 `dris-java-sdk-next` 当前适合承载这些能力：

- 标识注册、解析、校验、删除
- 元数据查询、更新、补丁更新
- 资源绑定、查询、移除
- 核心模型与固定规则
- 乐观并发控制（revision）

不适合继续承载这些能力：

- JDBC 运行时组装
- HTTP / `DrisClient` 传输包装
- 上传会话与对象存储编排
- 下载签名生成
- 搜索、向量索引同步、指标
- DRIRP 转发路由
- 审计、权限、风控
- 工具执行本地流程

所以迁移的正确方向不是“把旧 SDK 全量平移到新 SDK”，而是：

- `dris-java-sdk-next` 只保留稳定内核
- 旧 SDK 里非内核能力回收到后端

## 4. 旧 SDK 哪些要迁入后端

### 4.1 必须迁入后端的运行时能力

这些能力不应该继续留在新 SDK：

- `DrisCoreService`
- `InMemoryDrisCoreService`
- `DrisClient`
- `DrisClients.inProcess(...)`
- `DrisCoreOptions`

原因：

- 这些类本质是“嵌入式运行时 + 传输壳 + 配置收口”，不是可复用的领域内核。
- 如果继续保留，会让新 SDK 再次演化成“后端小副本”。

迁移后的归属：

- 由后端自己装配 `DrisSdk`
- 由后端自己定义本地门面服务
- 由后端自己决定异常、返回值、权限、审计、事务边界

### 4.2 必须迁入后端的存储实现

这些旧实现也应迁入后端或由后端重写：

- `JdbcDigitalObjectRepository`
- `JdbcIndustryRepository`
- `JdbcRegionRepository`
- `JdbcVectorIndexGateway`
- 其他基于 JDBC 的 server repository / gateway

原因：

- 新 SDK 不应直接绑死 JDBC。
- 数据库表结构、分页查询、批量查询、审计、上传会话，本来就属于后端基础设施。

迁移后的归属：

- 后端新增适配器，实现新 SDK 的接口：
  - `DigitalObjectRepository`
  - `RegistrationDictionary`
  - `TransactionRunner`
  - `TimeProvider`
  - `IdentifierCredentialGenerator`

### 4.3 必须迁入后端的业务编排能力

这些能力不应再回新 SDK：

- 创建上传会话
- 完成上传
- 实体文件校验
- 下载签名生成
- 本地工具执行
- 搜索与联邦搜索
- 向量同步、指标快照
- 风控判断
- 权限判断
- 审计记录

对应现有后端文件：

- `src/main/java/com/dris/v2/backend/service/IdentifierUploadService.java`
- `src/main/java/com/dris/v2/backend/service/ToolExecutionLocalService.java`
- `src/main/java/com/dris/v2/backend/service/FederatedSearchService.java`
- `src/main/java/com/dris/v2/backend/service/SystemOpsService.java`
- `src/main/java/com/dris/v2/backend/service/IdentifierPermissionService.java`
- `src/main/java/com/dris/v2/backend/service/IdentifierBusinessAuditService.java`

### 4.4 必须迁入后端的节点本地策略

旧 SDK 里带有明显节点语义的策略，也应迁入后端：

- `regionCode` 必须等于本节点配置
- `organizationCode` 必须等于本节点配置
- `strictIndustryValidation`
- `allowComponentTypeReplace`
- `requireToolSpec`
- `requireToolSha256`

这些参数当前集中在：

- `src/main/java/com/dris/v2/backend/config/BackendV2Properties.java`
- `src/main/java/com/dris/v2/backend/config/BackendV2Configuration.java`

迁移后建议：

- 这些配置继续留在后端
- 由后端新增一个“本地策略校验器”负责补齐新 SDK 不覆盖的旧行为

## 5. 哪些保留在新 SDK

下面这些能力适合保留在 `dris-java-sdk-next`：

- `DigitalObject`
- `IdentifierMetadata`
- `Resource`
- `ResourceVersion`
- `IdentifierCode`
- `DataCategory`
- `RegistrationType`
- 标识规则 `IdentifierRules`
- `IdentifierApplicationService`
- `MetadataApplicationService`
- `ResourceApplicationService`
- 默认实现类

也就是说：

- “标识内核”留在 SDK
- “业务系统”回到后端

## 6. 后端如何改造

### 6.1 第一步：装配方式改掉

当前：

- 后端装配 `DrisCoreService`
- 再包装成 `DrisClient`
- 业务服务通过 `drisClient.*` 调用

目标：

- 后端直接装配 `DrisSdk`
- 再提供一个后端本地门面，例如：
  - `BackendIdentifierKernelFacade`
  - 或 `LocalDrisKernelService`

这个门面的职责：

- 调用新 SDK 的三个 service
- 补齐旧版本地策略
- 统一把异常转换为后端结果对象
- 屏蔽控制层对 SDK 细节的直接依赖

建议新增位置：

- `src/main/java/com/dris/v2/backend/sdknext/`
- `src/main/java/com/dris/v2/backend/service/local/`

### 6.2 第二步：实现新 SDK 所需基础适配器

后端需要自己提供以下适配器：

- `DigitalObjectRepository` 的 JDBC 实现
- `RegistrationDictionary` 的 JDBC 实现
- `TransactionRunner` 的 Spring/JDBC 事务实现
- `TimeProvider` 的系统时钟实现
- `IdentifierCredentialGenerator` 的随机凭证实现

建议新增类：

- `src/main/java/com/dris/v2/backend/sdknext/JdbcNextDigitalObjectRepository.java`
- `src/main/java/com/dris/v2/backend/sdknext/JdbcNextRegistrationDictionary.java`
- `src/main/java/com/dris/v2/backend/sdknext/SpringTransactionRunner.java`
- `src/main/java/com/dris/v2/backend/sdknext/SystemTimeProvider.java`
- `src/main/java/com/dris/v2/backend/sdknext/RandomIdentifierCredentialGenerator.java`

### 6.3 第三步：后端加本地兼容策略层

新 SDK 当前只保证“通用内核规则”，不保证所有旧版本地策略。

因此后端需要补一层：

- `LocalIdentifierPolicyValidator`
- `LocalResourcePolicyValidator`

建议职责：

- 校验本节点 `regionCode` / `organizationCode`
- 严格行业校验
- `componentType` 替换策略
- `tool` 组件的 `sha256` 和 `spec` 校验

这样做的原因：

- 新 SDK 保持通用
- 后端仍可做到与旧版行为一致

## 7. 旧接口到新实现的映射

### 7.1 标识核心接口映射

旧调用：`drisClient.generateIdentifier(request)`  
新实现：`sdk.identifiers().register(mappedRequest)`

旧调用：`drisClient.generateIdentifierBatch(requests)`  
新实现：`sdk.identifiers().registerBatch(mappedRequests)`

旧调用：`drisClient.validateIdentifier(identifier)`  
新实现：

- 先走 `sdk.identifiers().validate(identifier)`
- 再走后端本地策略校验（region/org/industry）

旧调用：`drisClient.existsIdentifier(identifier)`  
新实现：`sdk.identifiers().exists(identifier)`

旧调用：`drisClient.resolveIdentifier(identifier)`  
新实现：`sdk.metadata().get(identifier)`

旧调用：`drisClient.resolveIdentifierBatch(identifiers)`  
新实现：`sdk.metadata().getBatch(identifiers)`

旧调用：`drisClient.getDigitalObject(identifier)`  
新实现：`sdk.identifiers().getDigitalObject(identifier)`

旧调用：`drisClient.updateIdentifier(metadata)`  
新实现：`sdk.metadata().update(request)`

旧调用：`drisClient.partialUpdateIdentifier(identifier, fields)`  
新实现：`sdk.metadata().patch(request)`

旧调用：`drisClient.deleteIdentifier(identifier)`  
新实现：`sdk.identifiers().delete(request)`

### 7.2 资源接口映射

旧调用：`drisClient.getComponent(identifier)`  
新实现：`sdk.resources().get(identifier)`

旧调用：`drisClient.upsertComponent(identifier, resource, forceReplaceType)`  
新实现：

- 先走后端 `LocalResourcePolicyValidator`
- 再调用 `sdk.resources().bind(request)`

说明：

- `forceReplaceType` 不是新 SDK 当前内核接口的一部分
- 是否允许替换组件类型，应在后端策略层控制

旧调用：`drisClient.removeComponent(identifier)`  
新实现：`sdk.resources().remove(request)`

### 7.3 仍留在后端的接口

这些没有必要迁入新 SDK：

- `createUploadSession`
- `completeUpload`
- `createDownloadPresign`
- `search`
- `syncVectorIndex`
- `metricsSnapshot`

原因：

- 它们都依赖对象存储、分页、索引、缓存、限流、风控、审计或节点运行态。

## 8. 代码改造落点

### 8.1 配置层

当前文件：

- `src/main/java/com/dris/v2/backend/config/BackendV2Configuration.java`

改造目标：

- 删除对 `DrisCoreService` / `InMemoryDrisCoreService` / `DrisClient` 的直接装配
- 改为装配 `DrisSdk`
- 同时装配后端本地门面服务

### 8.2 核心服务层

当前文件：

- `src/main/java/com/dris/v2/backend/service/IdentifierCoreService.java`
- `src/main/java/com/dris/v2/backend/service/IdentifierResourceService.java`

改造目标：

- 去掉 `DrisClient` 依赖
- 改成依赖后端本地门面
- 审计、权限逻辑保留不动

### 8.3 本地解析分支

当前文件：

- `src/main/java/com/dris/v2/backend/service/resolve/ResolutionSupport.java`

改造目标：

- `resolveLocalRaw(...)` 不再调用 `drisClient`
- 改为调用后端本地门面

这样 DRIRP 分布式解析的本地分支就能直接使用新 SDK。

### 8.4 工具执行本地流程

当前文件：

- `src/main/java/com/dris/v2/backend/service/ToolExecutionLocalService.java`

改造目标：

- 不再通过 `drisClient.getDigitalObject(identifier)` 获取对象
- 改为调用后端本地门面

### 8.5 控制层

当前文件：

- `src/main/java/com/dris/v2/backend/control/IdentifierCoreController.java`
- `src/main/java/com/dris/v2/backend/control/IdentifierResourceController.java`
- `src/main/java/com/dris/v2/backend/control/IdentifierUploadController.java`

改造目标：

- 中短期可以继续维持现有接口路径和 JSON 结构
- 但建议逐步去掉 controller 直接暴露旧 SDK model/request/response 的方式
- 改为后端自有 DTO + mapper

## 9. 迁移时必须补齐的兼容点

如果后端切到新 SDK，但不补下面这些点，就会和旧版行为不一致：

- 本地节点 `regionCode` / `organizationCode` 绑定策略
- `strictIndustryValidation`
- `allowComponentTypeReplace`
- `requireToolSha256`
- `requireToolSpec`
- 批量接口的失败项处理语义
- 幂等键持久化语义

其中最关键的是：

- 新 SDK 当前的幂等是内存态
- 后端正式接入时，必须把幂等做成数据库态或其他可持久化机制

否则服务重启后行为会和旧版不一致。

## 10. 建议的迁移顺序

### 阶段 1：并行接入

- `pom.xml` 先并行保留旧 SDK 和新 SDK
- 后端新增 `sdknext` 适配层和本地门面
- 不立刻替换控制层

### 阶段 2：本地核心切换

先切以下本地能力：

- `IdentifierCoreService`
- `IdentifierResourceService`
- `ResolutionSupport` 的本地分支
- `ToolExecutionLocalService`

这样跨节点 DRIRP 逻辑不用先大改。

### 阶段 3：上传/搜索/系统能力后端化

- 上传逻辑彻底留在后端
- 搜索逻辑彻底留在后端
- 向量同步和指标彻底留在后端

### 阶段 4：移除旧 SDK 运行时依赖

- 删除 `DrisCoreService`
- 删除 `DrisClient`
- 删除 `DrisClients.inProcess(...)`
- 删除旧 SDK JDBC server runtime 依赖

## 11. 最终推荐的职责边界

### 新 SDK 负责

- 标识规则
- 核心模型
- 标识 / 元数据 / 资源 聚合操作
- 乐观并发控制

### 后端负责

- 数据库适配
- 事务
- 本地策略
- 审计
- 权限
- 风控
- 上传和对象存储
- 下载签名
- 搜索与向量
- DRIRP 协议编排
- REST DTO 与响应封装

## 12. 结论

后端接入新的 `dris-java-sdk-next`，不是简单替换依赖，而是一次职责重划分：

- 旧 SDK 的“内核规则”继续保留在新 SDK
- 旧 SDK 的“运行时壳、数据库实现、业务编排、系统能力”迁入后端

最值得优先做的不是改 controller，而是先做两层：

- `sdknext` 基础适配器
- 后端本地门面 + 本地策略校验器

只要这两层建立起来，后续 `IdentifierCoreService`、`IdentifierResourceService`、`ResolutionSupport` 的迁移就会很顺。
