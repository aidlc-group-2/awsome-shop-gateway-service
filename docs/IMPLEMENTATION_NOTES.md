# 实现说明与注意事项（API 网关 / Unit6）

> 记录 Unit6 API 网关补全过程中的关键决策、临时方案与待联调项。
> 关联设计：`awsome-shop-plan/aidlc-docs/inception/`；差距分析：`team/gap-analysis-unit2-unit6.md`。
> 原则：一律按设计实现；设计不明确处取**简单方案**（MVP）。

---

## 本次补全内容（对照 FR-G）

| 需求 | 实现 |
|------|------|
| FR-G1 统一入口 | 已有（Spring Cloud Gateway，reactive/WebFlux） |
| FR-G2 认证校验 | 对齐认证服务契约：`AuthServiceClient` 调 `POST /api/v1/internal/auth/validate`，解析 `{success,userId,role,message}`；`AuthenticationGatewayFilter` 校验成功后注入 `X-User-Id` / `X-User-Role` 头（并存入 exchange 属性供下游过滤器使用） |
| FR-G3 角色鉴权 | 新增 `RoleAuthorizationFilter`（order=150）：命中 `gateway.security.admin-paths` 的请求要求 `role=ADMIN`，否则 403（`AUTHZ_001`） |
| FR-G4 路由转发 | 已有（`application-local.yml` 路由到 auth/product/point/order） |
| FR-G5 安全防护 | 新增 `SecurityHeadersFilter`（安全响应头）+ `RateLimitFilter`（限流，429 / `RATE_001`）；CORS 暴露头同步为 `X-User-Id`/`X-User-Role` |

## 身份透传契约（与 Unit2 对齐）

- 校验响应契约：`{success, userId(Long), role, message}`（与认证服务 `ValidateTokenResponse` 一致）。
- 注入下游：
  - 请求头 `X-User-Id` / `X-User-Role`（**设计要求的主通道**）。
  - 此外 `OperatorIdInjectionFilter` 仍将 `userId` 注入 POST JSON 请求体字段 `userId`，以兼容认证服务的 `GatewayInjectableRequest`（POST-Only 约定）。字段名由原 `operatorId` 统一为 `userId`。

## 关键决策（2026-06-10）

| # | 主题 | 决策 |
|---|------|------|
| G-D1 | 令牌校验方式 | **沿用远程校验**（调用认证服务 validate），为现有实现，最简单。⚠️ 见下方 NFR 风险。 |
| G-D2 | 管理端路径（FR-G3） | 设计为 `/admin/**`，但实际服务用 `/api/v1/...` 前缀且无 `/admin/` 段。取**可配置 Ant 路径**（`gateway.security.admin-paths`），默认含认证服务已知管理端（`/api/v1/auth/user/detail`、`/api/v1/auth/user/role`）+ 通用约定前缀。 |
| G-D3 | 限流（FR-G5） | 取**进程内令牌桶**（按客户端 IP），无需引入 Redis。默认 ~100 req/s/IP，可配。 |
| G-D4 | 安全头（FR-G5） | 添加 `X-Content-Type-Options`、`X-Frame-Options`、`X-XSS-Protection`、`Referrer-Policy`、`Content-Security-Policy`。SQL 注入防护由各服务参数化查询负责，网关提供纵深防御头。 |

## 过滤器顺序

```
AccessLogFilter            HIGHEST_PRECEDENCE        生成 requestId
RateLimitFilter            HIGHEST_PRECEDENCE + 10   限流（尽早拒绝）
SecurityHeadersFilter      HIGHEST_PRECEDENCE + 20   注册安全响应头
AuthenticationGatewayFilter            +100          JWT 校验 + 注入身份头
RoleAuthorizationFilter                +150          管理端 ADMIN 鉴权
OperatorIdInjectionFilter              +200          注入 userId 到请求体
```

## ⚠️ 待关注 / 待联调

- **NFR-1（网关开销 P95≤50ms）**：当前每请求一次远程 validate 调用，高并发下有延迟与认证服务可用性耦合风险。后续可改为**网关本地 JWT 校验**（与认证服务共享 HMAC 密钥），validate 仅作吊销/兜底。
- **限流为单实例内存方案**：多实例部署需换共享存储（如 Redis `RequestRateLimiter`）。当前 `bootstrap` 未引入 Redis 依赖。
- **配置仅落在 `application-local.yml`**：`gateway.security.*`、路由、`validate-url` 目前只在 local profile；`docker`/`prod` profile 待补（docker profile 仍残留对网关无用的 MySQL/Druid 配置，建议清理）。
- **端到端联调**：product/points/order（Unit3/4/5）尚未实现，目前仅可联调 auth 路由 + 公共/受保护/401/403/429 行为。
- **未本地编译验证**：本环境无 JDK/Maven，未执行 `mvn clean install`，需后续编译复核。
