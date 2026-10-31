package net.hwyz.iov.cloud.framework.web.timezone;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.hwyz.iov.cloud.framework.common.time.RequestTimeZoneContext;
import net.hwyz.iov.cloud.framework.common.time.Snapshot;
import org.slf4j.MDC;
import org.springframework.web.servlet.AsyncHandlerInterceptor;

/**
 * 请求时区拦截器（FW-WEB-DSN-CR-002，RD-TZ-003/004）。
 * <p>
 * <ul>
 *   <li>preHandle：解析有效时区并生成不可变 {@link Snapshot}，保存到请求 attributes
 *       （异步 redispatch 复用，不重复读取已变化的配置/用户状态），绑定
 *       {@link RequestTimeZoneContext} 并写入低基数 MDC；</li>
 *   <li>afterConcurrentHandlingStarted：Servlet 线程向异步工作线程移交时立即清理；</li>
 *   <li>afterCompletion：清理上下文与 MDC（Filter 的 finally 独立兜底，双重 clear 幂等）。</li>
 * </ul>
 * 排序由 {@code iov.web.timezone.interceptor-order} 控制（默认 0），须排在认证
 * 拦截器之后；认证完成才允许执行用户时区 SPI。
 *
 * @author hwyz_leo
 * @since 2026-10-31
 */
public class RequestTimeZoneInterceptor implements AsyncHandlerInterceptor {

    private final RequestTimeZoneSnapshotResolver resolver;

    public RequestTimeZoneInterceptor(RequestTimeZoneSnapshotResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Object existing = request.getAttribute(RequestTimeZoneHeaders.REQUEST_ATTR_SNAPSHOT);
        if (existing instanceof Snapshot snapshot) {
            // 异步/错误重派发复用已解析快照
            RequestTimeZoneContext.set(snapshot);
            bindMdc(snapshot);
            return true;
        }
        Snapshot snapshot = resolver.resolve(request);
        request.setAttribute(RequestTimeZoneHeaders.REQUEST_ATTR_SNAPSHOT, snapshot);
        RequestTimeZoneContext.set(snapshot);
        bindMdc(snapshot);
        return true;
    }

    @Override
    public void afterConcurrentHandlingStarted(HttpServletRequest request,
                                               HttpServletResponse response, Object handler) {
        // Servlet 线程移交异步工作线程时立即清理（工作线程由 TaskDecorator/wrap 管理）
        clear();
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        clear();
    }

    private void clear() {
        RequestTimeZoneContext.clear();
        MDC.remove(RequestTimeZoneHeaders.MDC_ZONE);
        MDC.remove(RequestTimeZoneHeaders.MDC_SOURCE);
        MDC.remove(RequestTimeZoneHeaders.MDC_REASON);
    }

    private void bindMdc(Snapshot snapshot) {
        // 仅规范化有效值与低基数枚举名，原始非法输入不落日志
        MDC.put(RequestTimeZoneHeaders.MDC_ZONE, RequestTimeZoneParser.normalize(snapshot.zoneId()));
        MDC.put(RequestTimeZoneHeaders.MDC_SOURCE, snapshot.source().name());
        MDC.put(RequestTimeZoneHeaders.MDC_REASON, snapshot.fallbackReason().name());
    }
}
