# COS 上传与多组件测试样例

## 1. 后端配置（application.yml）

```yaml
dris:
  v2:
    mode: jdbc
    region-code: 1100
    organization-code: ABCD
    storage-provider: cos
    storage-region: ap-beijing
    storage-endpoint: https://cos.ap-beijing.myqcloud.com
    default-bucket: dris-repo-prod-1330585503
```

## 2. 通用流程

1. 注册标识：`POST /api/v2/identifiers/register`
2. 创建上传会话：`POST /api/v2/identifiers/{identifier}/upload-sessions`
3. 客户端按返回 `data.put.url` 上传对象（直传）
4. 完成绑定：`POST /api/v2/upload-sessions/{uploadId}/complete`
5. 查询组件：`GET /api/v2/identifiers/{identifier}/component`

## 3. 资源（resource）示例

创建上传会话：

```json
{
  "profile": "data_json",
  "filename": "resource.json",
  "size": 128,
  "sha256": "abc123"
}
```

预期：

- `put.url` 包含 `dris-repo-prod-1330585503.cos.ap-beijing.myqcloud.com`
- 完成后组件字段：
  - `componentType=resource`
  - `storageType=external_uri`
  - `accessMethod=download`
  - `locator=cos://dris-repo-prod-1330585503/identifiers/{id}/resource`

## 4. 工具（tool）示例

创建上传会话：

```json
{
  "profile": "tool_zip_cli",
  "filename": "tool.zip",
  "size": 256,
  "sha256": "toolhash123"
}
```

预期：

- 完成后组件字段：
  - `componentType=tool`
  - `contentType=tool`
  - `hashValue` 以 `sha256:` 开头
  - `locator=cos://dris-repo-prod-1330585503/identifiers/{id}/tool`

## 5. 服务（service）示例

创建上传会话：

```json
{
  "profile": "service_openapi_json",
  "filename": "openapi.json",
  "size": 0,
  "serviceEndpoint": "https://api.example.com/v1/invoke"
}
```

说明：

- `serviceEndpoint` 必填，且必须以 `http://` 或 `https://` 开头。

预期：

- 完成后组件字段：
  - `componentType=service`
  - `storageType=api_service`
  - `accessMethod=api_call`
  - `contentType=service`
  - `locator=https://api.example.com/v1/invoke`

无效样例（应返回 `400`）：

```json
{
  "profile": "service_openapi_json",
  "filename": "openapi.json",
  "size": 0
}
```

## 6. 自动化测试用例

集成测试方法：

- `BackendDbBusinessIntegrationTest#uploadProfiles_resource_tool_service_shouldWorkOnCos`
- `BackendDbBusinessIntegrationTest#directComponentBinding_toolAndService_shouldWorkWithoutObjectStorageUpload`

执行命令：

```bash
cd dris-enterprise-backend-v2
mvn -q -Dtest=BackendDbBusinessIntegrationTest#uploadProfiles_resource_tool_service_shouldWorkOnCos test
mvn -q -Dtest=BackendDbBusinessIntegrationTest#directComponentBinding_toolAndService_shouldWorkWithoutObjectStorageUpload test
```

## 7. 不上传对象仓库，直接写组件（推荐给工具/服务）

接口：

- `PUT /api/v2/identifiers/{identifier}/component`

### 7.1 本地工具（tool）直接绑定示例

```json
{
  "componentType": "tool",
  "role": "primary",
  "storageType": "local_exec",
  "accessMethod": "invoke",
  "contentType": "tool",
  "format": "json",
  "locator": "local://tools/sum-cli",
  "size": 0,
  "hashValue": "",
  "extra": {
    "operationName": "sum",
    "operationDesc": "本地求和工具",
    "argTemplate": "{\"a\":${a},\"b\":${b}}",
    "input_schema": {
      "type": "object",
      "required": ["a", "b"]
    },
    "output_schema": {
      "type": "number"
    }
  }
}
```

### 7.2 URL 服务（service）直接绑定示例

```json
{
  "componentType": "service",
  "role": "primary",
  "storageType": "api_service",
  "accessMethod": "api_call",
  "contentType": "service",
  "format": "openapi_json",
  "locator": "https://api.example.com/v1/sum",
  "size": 0,
  "extra": {
    "operationName": "sum",
    "operationDesc": "远程求和服务",
    "method": "POST",
    "path": "/v1/sum",
    "timeoutMs": 3000
  }
}
```

校验规则：

- `service` 必须：
  - `storageType=api_service`
  - `accessMethod=api_call`
  - `contentType=service`
  - `locator` 必须 `http://` 或 `https://` 开头

## 8. 解析后执行验证（新增）

如果你要验证“解析结果能直接驱动调用方动作”，执行：

```bash
cd dris-enterprise-backend-v2
mvn -q -Dtest=BackendDbBusinessIntegrationTest#resolveDigitalObject_thenFetchResourceInvokeServiceExecuteTool_shouldReturnResults test
```

该用例会验证：

1. `/identifiers/{id}/object` 解析返回 metadata + resource
2. 按 `resource.locator` 拉取资源内容
3. 按服务 `locator` 访问服务并拿到业务结果
4. 按工具 `extra.command*` 执行本地命令并返回执行结果
