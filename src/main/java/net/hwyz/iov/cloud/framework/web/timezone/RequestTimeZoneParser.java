package net.hwyz.iov.cloud.framework.web.timezone;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 请求时区解析与校验（FW-WEB-DSN-CR-002，RD-TZ-003）。
 * <p>
 * 校验规则（按序，全部不满足即非法，客户端错误 fail-safe 回退）：
 * <ul>
 *   <li>原始值长度 ≤ 64 ASCII 字符；任何控制字符、逗号、非 ASCII 字符先拒绝
 *       （不能通过 trim 隐藏非法输入）；</li>
 *   <li>仅去除首尾 ASCII 普通空白（空格）后判断；空值无效；剩余内部空白拒绝；</li>
 *   <li>IANA：使用运行 JDK tzdb 的可用地区 ID（含 UTC，大小写精确）；拒绝 CST/PST
 *       等短别名（不在可用 ID 集合）、数字猜测、UTC+8 等非本约定形式；</li>
 *   <li>offset：仅严格 ±HH:mm，按 {@link ZoneOffset} 校验 -18:00～+18:00（±18 时
 *       分钟须 00，超界由 ZoneOffset 拒绝）；拒绝秒级 offset；-00:00 因未知 offset
 *       语义不接受，+00:00 接受；</li>
 *   <li>规范化输出：地区 ID 原样（UTC 输出 UTC），offset 输出 ±HH:mm；
 *       不把 Asia/Shanghai 压成永久 +08:00。</li>
 * </ul>
 * JDK tzdb 版本差异可能使未知新 ID 回退（返回 empty），由调用方记录 reason，
 * 不下载规则库或猜地区。
 *
 * @author hwyz_leo
 * @since 2026-10-31
 */
public final class RequestTimeZoneParser {

    private static final int MAX_LENGTH = 64;
    private static final Pattern OFFSET_PATTERN = Pattern.compile("[+-]\\d{2}:\\d{2}");
    private static final int MAX_OFFSET_SECONDS = 18 * 3600;

    private RequestTimeZoneParser() {
    }

    /**
     * 解析并校验时区字符串。
     *
     * @param value 原始输入（可能为 null）
     * @return 规范化后的 {@link ZoneId}；非法返回 empty
     */
    public static Optional<ZoneId> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        // 1) 控制字符/逗号/非 ASCII 先拒绝（不 trim 隐藏）
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < 0x20 || c == 0x7F || c == ',') {
                return Optional.empty();
            }
            if (c > 0x7F) {
                return Optional.empty();
            }
        }
        // 2) 长度 ≤ 64 ASCII 字符
        if (value.length() > MAX_LENGTH) {
            return Optional.empty();
        }
        // 3) 去除首尾 ASCII 普通空白（空格）后判断
        String trimmed = trimAsciiSpace(value);
        if (trimmed.isEmpty()) {
            return Optional.empty();
        }
        // 4) 剩余内部空白拒绝
        if (trimmed.indexOf(' ') >= 0) {
            return Optional.empty();
        }
        // 5) IANA 可用地区 ID（含 UTC，大小写精确；CST/PST/UTC+8 等不在此集合）
        if (ZoneId.getAvailableZoneIds().contains(trimmed)) {
            return Optional.of(ZoneId.of(trimmed));
        }
        // 6) 严格 ±HH:mm offset
        return parseOffset(trimmed);
    }

    /**
     * 规范化输出（用于 Feign 透传头等）：地区 ID 原样，offset 输出 ±HH:mm。
     */
    public static String normalize(ZoneId zoneId) {
        return zoneId.toString();
    }

    private static Optional<ZoneId> parseOffset(String value) {
        if (!OFFSET_PATTERN.matcher(value).matches()) {
            return Optional.empty();
        }
        ZoneOffset offset;
        try {
            offset = ZoneOffset.of(value);
        } catch (DateTimeException e) {
            return Optional.empty();
        }
        // 范围 -18:00～+18:00（±18 时分钟须 00 由 ZoneOffset 超界拒绝）
        if (offset.getTotalSeconds() < -MAX_OFFSET_SECONDS || offset.getTotalSeconds() > MAX_OFFSET_SECONDS) {
            return Optional.empty();
        }
        // -00:00 未知 offset 语义不接受；+00:00 接受
        if ("-00:00".equals(value)) {
            return Optional.empty();
        }
        return Optional.of(offset);
    }

    private static String trimAsciiSpace(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && value.charAt(start) == ' ') {
            start++;
        }
        while (end > start && value.charAt(end - 1) == ' ') {
            end--;
        }
        return value.substring(start, end);
    }
}
