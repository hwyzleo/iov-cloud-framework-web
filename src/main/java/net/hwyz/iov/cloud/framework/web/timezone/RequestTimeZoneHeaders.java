package net.hwyz.iov.cloud.framework.web.timezone;

/**
 * 请求时区 HTTP 头与请求属性/MDC 键（FW-WEB-DSN-CR-002）。
 *
 * @author hwyz_leo
 * @since 2026-10-31
 */
public final class RequestTimeZoneHeaders {

    /** 请求展示时区头（HTTP 字段名大小写不敏感）。 */
    public static final String TIME_ZONE = "X-Timezone";

    /** 请求属性键：已解析快照（异步 redispatch 复用）。 */
    public static final String REQUEST_ATTR_SNAPSHOT = RequestTimeZoneHeaders.class.getName() + ".Snapshot";

    /** MDC：规范化有效时区（低基数）。 */
    public static final String MDC_ZONE = "requestTimezone";

    /** MDC：来源（低基数枚举名）。 */
    public static final String MDC_SOURCE = "requestTimezoneSource";

    /** MDC：回退原因（低基数枚举名）。 */
    public static final String MDC_REASON = "requestTimezoneReason";

    private RequestTimeZoneHeaders() {
    }
}
