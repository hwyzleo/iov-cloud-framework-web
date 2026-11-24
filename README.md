# iov-cloud-framework-web
从零开始练手车联网云端框架Web部分

## 统一异常出口与错误响应面（FW-WEB-DSN-CR-004）

平台**只有一个**异常 → 响应转换点，业务服务不得自建 `@RestControllerAdvice` 重复映射错误码。

### 错误响应面（由端点契约决定，不需要每个服务声明）

| 端点声明返回 | 面 | 业务错误响应 |
|---|---|---|
| `ApiResponse<T>` / `ResponseEntity<ApiResponse<T>>` | 管理面（MPT、后台、OAPI） | HTTP 200 + `ApiResponse{code, message, traceId}`，前端按 code 提示 |
| 裸 DTO / `List` / `void`（Feign 官方服务契约） | 机器面 | `ErrorCode` 声明的非 2xx（默认 422）+ 同一 `ApiResponse` 错误体 |

判定依据是当前请求命中的 `HandlerMethod` 声明返回类型（`ErrorResponseSurface`，读取
`HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE`）。机器面**禁止**用 HTTP 200 返回错误体，
否则 Feign 调用方会把 `ApiResponse` 反序列化成空 DTO 并误判成功。

### 处理器与状态矩阵

| 异常 | 形态 | 状态码 | 响应 code |
|---|---|---|---|
| `BusinessException` | 管理面 | 200 | 业务码 |
| `BusinessException` | 机器面 | `HttpStatusAware`（默认 422） | 业务码 |
| 参数校验失败（Bean Validation） | 管理面 / 机器面 | 200 / 400 | 100001 |
| 缺参、类型不匹配、报文不可读 | 管理面 / 机器面 | 200 / 400 | 100001 |
| 方法不支持 / 处理器不存在 | 任意 | 405 / 404 | 100003 / 100004 |
| 其他异常 | 任意 | 500 | 100002 |

### 服务侧约束

1. 业务失败一律 `throw new XxxBaseException(ErrorCode[, detail])`，异常基类必须继承
   `BusinessException`；禁止 `extends RuntimeException` 的业务异常穿过 Web 边界。
2. 机器面需要非 422 状态时，由错误码实现 `HttpStatusAware#getHttpStatus()` 声明，不在异常类上加状态字段。
3. `detail` 会作为 `message` 外显，必须脱敏；内部细节只进日志（`traceId` 关联）。
4. 业务异常以 `WARN` 记录（含 uri/code/status），系统异常以 `ERROR` 记录堆栈。
5. 模块必须有真实 MockMvc 契约测试，断言 `HTTP 状态 + code + message`；只做注解级校验不算覆盖。
