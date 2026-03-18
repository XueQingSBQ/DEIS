# DRIS 开放数据样本集（2026-03-11）

这套样本集用于把官方开放数据目录中的“元数据 + 资源链接”改造成 DRIS 可注册的数字对象数据。

## 数据来源

- 来源门户：`https://catalog.data.gov/`
- 来源接口：`https://catalog.data.gov/api/3/action/package_search`
- 采集方式：按 6 个主题关键词抓取官方开放数据目录元数据，不下载原始大文件，只保留元数据与资源 URL。

## 样本规模

- 总记录数：`360`
- 节点分布：当前可导入节点 `CNTA / CNTB` 各 `180` 条
- 主题分布：`transportation / energy / climate / agriculture / health / education` 各 `60` 条

## 生成文件

- `generate_open_catalog_dataset.py`
  - 重新抓取官方开放数据目录，并生成 DRIS 导入数据
- `open_catalog_dataset_360.json`
  - 主数据文件，每条记录包含：来源信息、目标节点、注册请求、资源绑定
- `open_catalog_dataset_360.summary.json`
  - 数据分布摘要
- `source_manifest.json`
  - 数据来源清单
- `import_open_catalog_dataset.ps1`
  - 导入当前本地 6 节点环境中的可注册节点（`CNTA / CNTB`）

## 字段映射

原始开放数据目录字段会映射到 DRIS 的两部分：

1. 标识注册元数据
- `name` <- 数据集标题
- `description` <- 数据集说明
- `industry` <- 查询主题，如 `道路运输业` / `电力、热力生产和供应业`
- `dataCategory` <- 固定为 `public`
- `registrationType` <- 固定为 `resource`
- `owner` <- 原始数据集所属机构
- `applicationScenario` <- 面向 DeepResearch 的研究场景描述
- `entityType` <- `dataset` 或 `api`
- `additionalAttributes` <- 来源门户、来源数据集 ID、标签、时间、稳定 `_idempotencyKey`

2. 资源绑定
- `storageType` <- 固定为 `external_uri`
- `locator` <- 原始开放数据资源 URL
- `format` <- 原始资源格式
- `accessMethod` <- `download` 或 `api`
- `contentType` <- 推断后的 MIME 类型

## 使用方式

### 1. 重新生成样本集

```powershell
cd E:\irp\doip-sdk\dris-system-deploy-20260310\dris-enterprise-backend-v2\deploy\local-6nodes\sample-data\open-catalog-20260311
python .\generate_open_catalog_dataset.py
```

### 2. 先做干跑验证

```powershell
cd E:\irp\doip-sdk\dris-system-deploy-20260310\dris-enterprise-backend-v2\deploy\local-6nodes\sample-data\open-catalog-20260311
.\import_open_catalog_dataset.ps1 -Limit 12 -DryRun
```

### 3. 小批量导入验证

```powershell
cd E:\irp\doip-sdk\dris-system-deploy-20260310\dris-enterprise-backend-v2\deploy\local-6nodes\sample-data\open-catalog-20260311
.\import_open_catalog_dataset.ps1 -Limit 12
```

### 4. 全量导入 360 条

```powershell
cd E:\irp\doip-sdk\dris-system-deploy-20260310\dris-enterprise-backend-v2\deploy\local-6nodes\sample-data\open-catalog-20260311
.\import_open_catalog_dataset.ps1
```

## 说明

- 每条记录都带 `_idempotencyKey = open-catalog:<sourceDatasetId>`，重复导入不会无限生成新标识。
- 资源绑定使用外部链接，不会把源数据复制到 COS。
- 为了兼容当前新 SDK 校验，已补充行业字典：`transport / climate / agriculture / education`。
- 这套数据最适合验证：
  - 标识注册
  - 元数据检索
  - 跨节点搜索
  - DeepResearch 结果汇总
  - 外部资源绑定展示
- 这套数据不适合作为“本地实体文件上传校验”样本，因为这里绑定的是公开 URL，不是你本地上传到 COS 的对象。

## 当前导入结果

- 当前全量导入结果：`360/360` 注册成功，`360/360` 资源绑定成功
- 导入结果清单：`import-results/import-20260311-195046.json`

## 建议测试问题

- 查询跨节点交通类数据对象有哪些，分别绑定了什么资源。
- 比较两个能源类标识的元数据差异和资源格式差异。
- 从某个锚点节点发起气候类搜索，观察分布式返回路径。
- 检索教育类对象，汇总 owner、格式、资源链接类型。

- 当前导入版本的幂等前缀：open-catalog-v2:，用于避免与旧样本缓存冲突。

