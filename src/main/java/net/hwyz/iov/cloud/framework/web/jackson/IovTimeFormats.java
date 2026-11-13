package net.hwyz.iov.cloud.framework.web.jackson;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * 对外 JSON 时间输出格式（FW-WEB-DSN-CR-003，RD-003-2）。
 * <p>
 * 统一形态：`yyyy-MM-dd'T'HH:mm:ss.SSS'Z'`
 * <ul>
 *   <li>固定 3 位毫秒：不向客户端外泄纳秒/微秒，也不出现 0 位或 6 位小数；</li>
 *   <li>`'Z'` 为字面量而非 zone pattern：无论数据原始 offset 是什么，输出恒为 UTC 的 `Z`，
 *       避免同一服务内出现 `Z` 与 `+08:00` 混用（写单一）。</li>
 * </ul>
 *
 * @author hwyz_leo
 * @since 2026-11-13
 */
final class IovTimeFormats {

    /**
     * 毫秒 + UTC（`Z`）写出格式。
     */
    static final DateTimeFormatter MILLIS_UTC =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    /**
     * 探针断言用：ISO-8601 且恰好 3 位小数，时区为 `Z` 或 `±HH:MM`（FW-WEB-DSN-CR-003 §16.4）。
     */
    static final String ISO_MILLIS_REGEX =
            "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}(Z|[+-]\\d{2}:\\d{2})$";

    private IovTimeFormats() {
    }
}
