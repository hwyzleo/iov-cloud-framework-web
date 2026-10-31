package net.hwyz.iov.cloud.framework.web.timezone;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.hwyz.iov.cloud.framework.common.time.RequestTimeZoneContext;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 请求时区生命周期清理 Filter（FW-WEB-DSN-CR-002，RD-TZ-004）。
 * <p>
 * 作为最外层清理边界：
 * <ul>
 *   <li>每次适用 dispatch（初始/ERROR；ASYNC 由 TaskDecorator 负责，本 Filter 默认跳过）入口
 *       先清除线程残留，避免上一请求泄漏的绑定被新请求误读；</li>
 *   <li>try/finally 清理上下文并恢复 MDC，覆盖正常、抛异常、认证短路和错误分派，
 *       不只依赖 interceptor 的 afterCompletion；</li>
 *   <li>同线程嵌套分派用请求属性维护深度计数：内层入口不会 clear() 掉尚未结束的外层
 *       上下文；深度归零时才最终清理并恢复 MDC；双重 clear 幂等。</li>
 * </ul>
 *
 * @author hwyz_leo
 * @since 2026-10-31
 */
public class RequestTimeZoneCleanupFilter extends OncePerRequestFilter {

    private static final String DEPTH_ATTR = RequestTimeZoneCleanupFilter.class.getName() + ".Depth";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Integer depth = (Integer) request.getAttribute(DEPTH_ATTR);
        if (depth == null) {
            // 最外层适用 dispatch 入口：先清除线程残留
            RequestTimeZoneContext.clear();
            depth = 0;
        }
        request.setAttribute(DEPTH_ATTR, depth + 1);

        // 保存进入前的 MDC 值，finally 恢复（幂等）
        String prevZone = MDC.get(RequestTimeZoneHeaders.MDC_ZONE);
        String prevSource = MDC.get(RequestTimeZoneHeaders.MDC_SOURCE);
        String prevReason = MDC.get(RequestTimeZoneHeaders.MDC_REASON);

        try {
            filterChain.doFilter(request, response);
        } finally {
            int remaining = (Integer) request.getAttribute(DEPTH_ATTR) - 1;
            if (remaining <= 0) {
                request.removeAttribute(DEPTH_ATTR);
                RequestTimeZoneContext.clear();
                restoreMdc(prevZone, prevSource, prevReason);
            } else {
                request.setAttribute(DEPTH_ATTR, remaining);
            }
        }
    }

    private void restoreMdc(String zone, String source, String reason) {
        restore(RequestTimeZoneHeaders.MDC_ZONE, zone);
        restore(RequestTimeZoneHeaders.MDC_SOURCE, source);
        restore(RequestTimeZoneHeaders.MDC_REASON, reason);
    }

    private void restore(String key, String previous) {
        if (previous == null) {
            MDC.remove(key);
        } else {
            MDC.put(key, previous);
        }
    }
}
