package net.hwyz.iov.cloud.framework.web.jackson;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.SmartInitializingSingleton;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * FW-WEB Jackson 启动探针（FW-WEB-DSN-CR-001，§5.5 / §9）。
 * <p>
 * 在 web server 启动前（SmartInitializingSingleton，即 readiness 放行前）校验
 * 实际生效的 Spring ObjectMapper 行为与配置一致；失败抛异常 → 启动失败，
 * 不允许接入业务流量。校验对象是注入的生产 mapper（已应用全部 customizer），
 * 不是临时构造的理想化 mapper——可发现后注册 customizer、模块或显式 serializer
 * 覆盖框架策略的问题。
 * <p>
 * 探针内容：
 * <ol>
 *   <li>输出一致性：Instant/OffsetDateTime/LocalDateTime 三类型输出与 date-format
 *       模式一致（epoch-millis 数字 / iso 字符串）；epoch-millis 模式 LocalDateTime
 *       输出需要显式 zone-id，未配置即阻断启动（强制升级前核验存量时区语义）；</li>
 *   <li>兼容读：13 位毫秒、10 位秒、ISO 字符串反序列化得到同一时刻；</li>
 *   <li>时区转换（配置 zone-id 时）：数字 → LocalDateTime/OffsetDateTime 往返一致。</li>
 * </ol>
 *
 * @author hwyz_leo
 * @since 2026-10-28
 */
public class JacksonStartupProbe implements SmartInitializingSingleton {

    private static final Instant SAMPLE_INSTANT = Instant.parse("2026-10-28T12:43:32Z");
    private static final OffsetDateTime SAMPLE_OFFSET = OffsetDateTime.parse("2026-10-28T20:43:32+08:00");
    private static final LocalDateTime SAMPLE_LOCAL = LocalDateTime.parse("2026-10-14T14:26:13");

    private final ObjectMapper objectMapper;
    private final JacksonProperties properties;

    public JacksonStartupProbe(ObjectMapper objectMapper, JacksonProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public void afterSingletonsInstantiated() {
        try {
            verifyWriteMode();
            verifyCompatRead();
            if (properties.getZoneId() != null) {
                verifyZoneConversion();
            }
        } catch (Exception e) {
            throw new IllegalStateException(
                    "FW-WEB Jackson 启动探针失败，阻断发布（date-format=" + properties.getDateFormat()
                            + ", zone-id=" + properties.getZoneId() + "）: " + e.getMessage(), e);
        }
    }

    private void verifyWriteMode() throws Exception {
        Map<String, Object> sample = new LinkedHashMap<>();
        sample.put("instant", SAMPLE_INSTANT);
        sample.put("offset", SAMPLE_OFFSET);
        sample.put("local", SAMPLE_LOCAL);
        JsonNode node = objectMapper.valueToTree(sample);
        if (properties.getDateFormat() == JacksonProperties.DateFormat.EPOCH_MILLIS) {
            requireNumber(node, "instant", "epoch-millis 模式下 Instant 应输出 epoch 毫秒数字");
            requireNumber(node, "offset", "epoch-millis 模式下 OffsetDateTime 应输出 epoch 毫秒数字");
            requireNumber(node, "local", "epoch-millis 模式下 LocalDateTime 应输出 epoch 毫秒数字（需要显式 zone-id）");
            // 数字往返一致（13 位毫秒正确性）
            Instant round = objectMapper.convertValue(node.get("instant"), Instant.class);
            requireEqual(SAMPLE_INSTANT, round, "epoch-millis 数字往返不一致");
        } else {
            requireText(node, "instant", "iso 模式下 Instant 应输出含 offset 的 ISO-8601 字符串");
            requireText(node, "offset", "iso 模式下 OffsetDateTime 应输出含 offset 的 ISO-8601 字符串");
            requireText(node, "local", "iso 模式下 LocalDateTime 应输出无 offset 的 ISO-8601 字符串");
            Instant round = objectMapper.convertValue(node.get("instant"), Instant.class);
            requireEqual(SAMPLE_INSTANT, round, "iso 往返不一致");
        }
    }

    private void verifyCompatRead() throws Exception {
        long millis = SAMPLE_INSTANT.toEpochMilli();
        long seconds = SAMPLE_INSTANT.getEpochSecond();
        Instant fromMillis = readInstant(String.valueOf(millis));
        requireEqual(SAMPLE_INSTANT, fromMillis, "13 位 epoch 毫秒读取未归一至同一 Instant");
        Instant fromSeconds = readInstant(String.valueOf(seconds));
        requireEqual(SAMPLE_INSTANT, fromSeconds, "10 位 epoch 秒读取未归一至同一 Instant");
        Instant fromIso = readInstant("\"" + SAMPLE_INSTANT + "\"");
        requireEqual(SAMPLE_INSTANT, fromIso, "ISO-8601 字符串读取未归一至同一 Instant");
    }

    private void verifyZoneConversion() throws Exception {
        // LocalDateTime 数字往返（显式 zone 语义）
        String serialized = objectMapper.writeValueAsString(SAMPLE_LOCAL);
        if (properties.getDateFormat() == JacksonProperties.DateFormat.EPOCH_MILLIS) {
            LocalDateTime read = objectMapper.readValue(serialized, LocalDateTime.class);
            requireEqual(SAMPLE_LOCAL, read, "LocalDateTime epoch 数字往返不一致");
        }
        // 数字 → OffsetDateTime：只要求时刻一致（数字不携带原始 offset）
        long millis = SAMPLE_OFFSET.toInstant().toEpochMilli();
        OffsetDateTime read = objectMapper.readValue(String.valueOf(millis), OffsetDateTime.class);
        requireEqual(SAMPLE_OFFSET.toInstant(), read.toInstant(),
                "数字 → OffsetDateTime 时刻不一致");
    }

    private Instant readInstant(String json) throws Exception {
        return objectMapper.readValue(json, Instant.class);
    }

    private static void requireNumber(JsonNode node, String field, String message) {
        JsonNode child = node.get(field);
        if (child == null || !child.isIntegralNumber()) {
            throw new IllegalStateException(field + ": " + message + "（实际: " + child + "）");
        }
    }

    private static void requireText(JsonNode node, String field, String message) {
        JsonNode child = node.get(field);
        if (child == null || !child.isTextual()) {
            throw new IllegalStateException(field + ": " + message + "（实际: " + child + "）");
        }
    }

    private static void requireEqual(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new IllegalStateException(message + "（期望: " + expected + "，实际: " + actual + "）");
        }
    }
}
