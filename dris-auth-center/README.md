# dris-auth-center 运行说明（2026-03-07）

## 1. 工程位置

`e:\irp\doip-sdk\dris-auth-center`

## 2. 默认端口

- `8290`

## 3. 启动命令

```powershell
cd e:\irp\doip-sdk\dris-auth-center
mvn spring-boot:run
```

## 4. 关键接口

### 4.1 注册

```powershell
curl -X POST http://127.0.0.1:8290/auth/register `
  -H "Content-Type: application/json" `
  -d "{\"username\":\"alice\",\"password\":\"secret123\",\"displayName\":\"Alice\",\"orgCode\":\"ABCD\"}"
```

### 4.2 登录

```powershell
curl -X POST http://127.0.0.1:8290/auth/login `
  -H "Content-Type: application/json" `
  -d "{\"username\":\"alice\",\"password\":\"secret123\"}"
```

### 4.3 OAuth 风格 token

```powershell
curl -X POST http://127.0.0.1:8290/oauth2/token `
  -H "Content-Type: application/x-www-form-urlencoded" `
  -d "grant_type=password&username=alice&password=secret123"
```

### 4.4 获取公钥

```powershell
curl http://127.0.0.1:8290/auth/public-key
```

### 4.5 userinfo

```powershell
curl http://127.0.0.1:8290/auth/userinfo `
  -H "Authorization: Bearer <accessToken>"
```

## 5. 业务节点对接

在 `dris-enterprise-backend-v2/src/main/resources/application.yml` 中配置：

```yaml
dris:
  v2:
    auth:
      api-auth-required: true
      write-permission-enabled: true
      issuer: auth-center
      audience: dris-business-node
      auth-center-base-url: http://127.0.0.1:8290
```

## 6. 联调顺序

1. 先启动 `dris-auth-center`
2. 再启动 `dris-enterprise-backend-v2`
3. 先调用 `/auth/login` 拿 token
4. 再带 `Authorization: Bearer <token>` 调业务节点 `/api/v2/**`
