# DRIS Enterprise Backend V2

`dris-enterprise-backend-v2` 已融合 `dris-sdk` 核心能力，并采用分层结构。

## 目录分层

- `control`: REST 控制器
- `service`: 业务服务（封装 `DrisClient`）
- `dto`: API 响应模型
- `model`: 业务模型与异常
- `api`: 统一响应与异常处理
- `config`: Spring/SDK 配置

## 启动

```bash
mvn spring-boot:run
```

默认端口：`8190`

## 已融合功能

- 标识核心：注册、批量注册、校验、存在性、解析、批量解析、对象查询、全量更新、局部更新、删除
- 组件资源：查询、绑定/更新、删除
- 搜索：`POST /api/v2/search`
- 上传下载：创建上传会话、完成上传、下载预签名
- 运维：向量索引同步、指标快照

## 文档

- 接口文档：`docs/后端接口文档.md`
- 软件测试文档：`docs/软件测试文档.md`
- 测试矩阵：`docs/测试用例矩阵.md`
- 测试样例明细：`docs/测试样例明细.md`
