package net.hwyz.iov.cloud.framework.web.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 基于内存滑动窗口的限流过滤器。
 * <p>
 * 按 IP + 端点模式进行限流。认证端点默认 10 次/分钟，其他端点默认 60 次/分钟。
 * 生产环境应替换为 Redis 实现。
 */
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    public static final int AUTH_LIMIT = 10;
    public static final int DEFAULT_LIMIT = 60;
    static final long WINDOW_MILLIS = 60_000L;

    private List<String> authPatterns = List.of(
            "/api/v1/auth/**",
            "/api/v1/oauth/token"
    );

    private final ConcurrentHashMap<String, Deque<Long>> counters = new ConcurrentHashMap<>();
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public RateLimitFilter() {
    }

    public RateLimitFilter(List<String> authPatterns) {
        if (authPatterns != null && !authPatterns.isEmpty()) {
            this.authPatterns = authPatterns;
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String clientIp = getClientIp(request);
        String path = request.getRequestURI();
        int limit = resolveLimit(path);
        String key = clientIp + ":" + (isAuthEndpoint(path) ? "auth" : "general");

        if (!tryAcquire(key, limit)) {
            log.warn("限流触发: ip={}, path={}, limit={}", clientIp, path, limit);
            sendTooManyRequests(response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    public boolean tryAcquire(String key, int limit) {
        long now = System.currentTimeMillis();
        Deque<Long> timestamps = counters.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());

        // 清理窗口外的时间戳
        while (!timestamps.isEmpty() && now - timestamps.peekFirst() > WINDOW_MILLIS) {
            timestamps.pollFirst();
        }

        if (timestamps.size() >= limit) {
            return false;
        }

        timestamps.addLast(now);
        return true;
    }

    public int resolveLimit(String path) {
        return isAuthEndpoint(path) ? AUTH_LIMIT : DEFAULT_LIMIT;
    }

    public boolean isAuthEndpoint(String path) {
        return authPatterns.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    // 仅用于测试
    public void clearCounters() {
        counters.clear();
    }

    static String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    private void sendTooManyRequests(HttpServletResponse response) throws IOException {
        response.setStatus(429);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                "{\"code\":\"100003\",\"message\":\"请求过于频繁，请稍后重试\"}"
        );
    }
}
