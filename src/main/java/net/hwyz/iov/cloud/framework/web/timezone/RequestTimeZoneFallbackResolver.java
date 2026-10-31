package net.hwyz.iov.cloud.framework.web.timezone;

import jakarta.servlet.http.HttpServletRequest;

import java.time.ZoneId;
import java.util.Optional;

/**
 * 服务/端点显式兜底时区提供者（FW-WEB-DSN-CR-002，RD-TZ-003/007）。
 * <p>
 * 仅用于迁移既有展示默认（如 TSP Excel 的 tsp.display.zone-id），优先级位于客户端
 * 输入之后、框架 {@code default-zone-id} 之前；该兜底不是请求输入，不进入鉴权流程。
 * 框架不写入 TSP 等服务的配置键；服务将其配置映射为 {@link ZoneId} 后返回。
 *
 * @author hwyz_leo
 * @since 2026-10-31
 */
public interface RequestTimeZoneFallbackResolver {

    /**
     * 为当前请求解析服务/端点显式兜底时区。
     *
     * @param request 当前请求
     * @return 兜底 {@link ZoneId}；无兜底返回 empty
     */
    Optional<ZoneId> resolveFallbackZone(HttpServletRequest request);
}
