package net.hwyz.iov.cloud.framework.web.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Web 框架配置属性
 *
 * @author hwyz_leo
 */
@Data
@ConfigurationProperties(prefix = "framework.web")
public class WebFrameworkProperties {

    /**
     * 过滤器配置
     */
    private FilterProperties filter = new FilterProperties();

    @Data
    public static class FilterProperties {
        /**
         * TraceId 过滤器
         */
        private TraceIdFilterProperties traceId = new TraceIdFilterProperties();

        /**
         * 限流过滤器
         */
        private RateLimitFilterProperties rateLimit = new RateLimitFilterProperties();

        /**
         * 幂等过滤器
         */
        private IdempotencyFilterProperties idempotency = new IdempotencyFilterProperties();

        /**
         * XSS 过滤器
         */
        private XssFilterProperties xss = new XssFilterProperties();
    }

    @Data
    public static class TraceIdFilterProperties {
        private boolean enabled = true;
        private int order = Integer.MIN_VALUE;
    }

    @Data
    public static class RateLimitFilterProperties {
        private boolean enabled = true;
        private int order = 5;
        private List<String> authPatterns = List.of("/api/v1/auth/**", "/api/v1/oauth/token");
    }

    @Data
    public static class IdempotencyFilterProperties {
        private boolean enabled = true;
        private int order = 6;
        private long ttlMillis = 10 * 60 * 1000L;
    }

    @Data
    public static class XssFilterProperties {
        private boolean enabled = true;
        private int order = Integer.MAX_VALUE;
    }

}
