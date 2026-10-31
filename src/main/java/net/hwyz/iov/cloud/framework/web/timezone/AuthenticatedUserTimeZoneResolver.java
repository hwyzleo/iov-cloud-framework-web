package net.hwyz.iov.cloud.framework.web.timezone;

import jakarta.servlet.http.HttpServletRequest;

import java.time.ZoneId;
import java.util.Optional;

/**
 * 认证用户时区 SPI（FW-WEB-DSN-CR-002，RD-TZ-003）。
 * <p>
 * 由服务实现：只读取本请求已认证 principal 的服务端存储配置；不能信任 query/header
 * 中的 userId 或声称的用户 zone；无配置返回 empty；非法配置返回 empty 并记录低基数
 * reason（由实现方负责），不改变认证结果。不额外新增远程用户查询。
 * <p>
 * 认证后才可执行本 SPI：时区拦截器通过 {@code iov.web.timezone.interceptor-order}
 * 明确排在认证拦截器之后；实现方必须自行校验当前请求确已认证（如读取其认证上下文），
 * 不能仅依赖拦截器注册顺序推定认证完成。
 *
 * @author hwyz_leo
 * @since 2026-10-31
 */
public interface AuthenticatedUserTimeZoneResolver {

    /**
     * 解析当前请求已认证用户的服务端时区配置。
     *
     * @param request 当前请求
     * @return 有效 {@link ZoneId}；未认证/无配置/非法配置返回 empty
     */
    Optional<ZoneId> resolveAuthenticatedUserTimeZone(HttpServletRequest request);
}
