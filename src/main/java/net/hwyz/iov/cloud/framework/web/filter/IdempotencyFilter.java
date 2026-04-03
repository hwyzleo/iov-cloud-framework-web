package net.hwyz.iov.cloud.framework.web.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 幂等性过滤器。
 * <p>
 * 对 POST/PUT 请求检查 X-Idempotency-Key 请求头，在时间窗口内检测重复请求。
 * 重复请求返回缓存的响应。生产环境应替换为 Redis 实现。
 */
@Slf4j
public class IdempotencyFilter extends OncePerRequestFilter {

    static final String IDEMPOTENCY_HEADER = "X-Idempotency-Key";
    private long ttlMillis = 10 * 60 * 1000L; // 10 分钟

    private final ConcurrentHashMap<String, CachedResponse> cache = new ConcurrentHashMap<>();

    public IdempotencyFilter() {
    }

    public IdempotencyFilter(long ttlMillis) {
        if (ttlMillis > 0) {
            this.ttlMillis = ttlMillis;
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String method = request.getMethod();
        if (!"POST".equalsIgnoreCase(method) && !"PUT".equalsIgnoreCase(method)) {
            filterChain.doFilter(request, response);
            return;
        }

        String idempotencyKey = request.getHeader(IDEMPOTENCY_HEADER);
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        evictExpired();

        CachedResponse cached = cache.get(idempotencyKey);
        if (cached != null) {
            log.info("幂等命中: key={}", idempotencyKey);
            response.setStatus(cached.status);
            response.setContentType(cached.contentType);
            response.getWriter().write(cached.body);
            return;
        }

        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);
        filterChain.doFilter(request, wrappedResponse);

        // 缓存成功响应
        int status = wrappedResponse.getStatus();
        if (status >= 200 && status < 300) {
            byte[] body = wrappedResponse.getContentAsByteArray();
            String contentType = wrappedResponse.getContentType();
            cache.put(idempotencyKey, new CachedResponse(
                    status,
                    contentType != null ? contentType : "application/json",
                    new String(body),
                    System.currentTimeMillis()
            ));
        }

        wrappedResponse.copyBodyToResponse();
    }

    void evictExpired() {
        long now = System.currentTimeMillis();
        cache.entrySet().removeIf(e -> now - e.getValue().timestamp > ttlMillis);
    }

    // 仅用于测试
    void clearCache() {
        cache.clear();
    }

    int cacheSize() {
        return cache.size();
    }

    record CachedResponse(int status, String contentType, String body, long timestamp) {
    }
}
