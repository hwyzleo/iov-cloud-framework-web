package net.hwyz.iov.cloud.framework.web.jackson;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * FW-WEB Jackson 启动探针（FW-WEB-DSN-CR-001 §5.5；FW-WEB-DSN-CR-003 §16.4 升级）。
 * <p>
 * 在 web server 启动前（readiness 放行前）校验实际生效的 Spring ObjectMapper 行为与配置一致；
 * 失败抛异常 → 启动失败，不允许接入业务流量。校验对象是注入的生产 mapper（已应用全部 customizer），
 * 不是临时构造的理想化 mapper——可发现后注册 customizer、模块或显式 serializer 覆盖框架策略的问题。
 * <p>
 * 探针内容：
 * <ol>
 *   <li>输出形态：iso 模式下 {@code Instant/OffsetDateTime/LocalDateTime/java.util.Date} 四类必须输出
 *       「字符串 + 含 Z/offset + 恰好 3 位小数」；epoch-millis 模式下四类必须输出整数毫秒。仅判断
 *       「是字符串」不足（CR-001 曾因此放过 9 位小数与裸 LocalDateTime）；</li>
 *   <li>兼容读：13 位毫秒、10 位秒、ISO 字符串反序列化得到同一时刻；</li>
 *   <li>时区一致性：JVM/容器默认时区与 {@code zone-id} 不一致时告警（strict 时阻断）。</li>
 * </ol>
 *
 * @author hwyz_leo
 * @since 2026-10-28
 */
public class JacksonStartupProbe implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(JacksonStartupProbe.class);

    private static final Instant SAMPLE_INSTANT = Instant.parse("2026-10-28T12:43:32Z");
    private static final OffsetDateTime SAMPLE_OFFSET = OffsetDateTime.parse("2026-10-28T20:43:32+08:00");
    private static final LocalDateTime SAMPLE_LOCAL = LocalDateTime.parse("2026-10-14T14:26:13");
    private static final Date SAMPLE_DATE = new Date(SAMPLE_INSTANT.toEpochMilli());

    private static final Pattern ISO_MILLIS = Pattern.compile(IovTimeFormats.ISO_MILLIS_REGEX);

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
            verifyJvmZoneConsistency();
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
        sample.put("date", SAMPLE_DATE);
        JsonNode node = objectMapper.valueToTree(sample);
        if (properties.getDateFormat() == JacksonProperties.DateFormat.EPOCH_MILLIS) {
            requireNumber(node, "instant", "epoch-millis 模式下 Instant 应输出 epoch 毫秒数字");
            requireNumber(node, "offset", "epoch-millis 模式下 OffsetDateTime 应输出 epoch 毫秒数字");
            requireNumber(node, "local", "epoch-millis 模式下 LocalDateTime 应输出 epoch 毫秒数字（需要显式 zone-id）");
            requireNumber(node, "date", "epoch-millis 模式下 java.util.Date 应输出 epoch 毫秒数字");
            // 数字往返一致（13 位毫秒正确性）
            Instant round = objectMapper.convertValue(node.get("instant"), Instant.class);
            requireEqual(SAMPLE_INSTANT, round, "epoch-millis 数字往返不一致");
        } else {
            if (properties.getZoneId() == null) {
                throw new IllegalStateException(
                        "iso 模式下 LocalDateTime 输出需要显式 iov.web.jackson.zone-id（禁止使用 JVM 默认时区猜测业务语义）");
            }
            requireIsoMillis(node, "instant", "iso 模式下 Instant 应输出「ISO-8601 + 3 位毫秒 + Z/offset」");
            requireIsoMillis(node, "offset", "iso 模式下 OffsetDateTime 应输出「ISO-8601 + 3 位毫秒 + Z/offset」");
            requireIsoMillis(node, "local", "iso 模式下 LocalDateTime 应输出「ISO-8601 + 3 位毫秒 + Z/offset」（不得裸输出）");
            requireIsoMillis(node, "date", "iso 模式下 java.util.Date 应输出「ISO-8601 + 3 位毫秒 + Z/offset」");
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
        } else {
            LocalDateTime read = objectMapper.readValue(serialized, LocalDateTime.class);
            requireEqual(SAMPLE_LOCAL, read, "LocalDateTime ISO（按 zone-id 换算）往返不一致");
        }
        // 数字 → OffsetDateTime：只要求时刻一致（数字不携带原始 offset）
        long millis = SAMPLE_OFFSET.toInstant().toEpochMilli();
        OffsetDateTime read = objectMapper.readValue(String.valueOf(millis), OffsetDateTime.class);
        requireEqual(SAMPLE_OFFSET.toInstant(), read.toInstant(),
                "数字 → OffsetDateTime 时刻不一致");
    }

    /**
     * JVM/容器默认时区与 {@code zone-id} 一致性（FW-WEB-DSN-CR-003 RD-003-9）。
     * <p>
     * 不一致时写入侧的 {@code LocalDateTime.now()} 与 JSON 解释会静默偏移（典型 8 小时），
     * 但不会报错——必须显式暴露。默认告警，{@code iov.web.jackson.strict-jvm-zone=true} 时阻断。
     */
    private void verifyJvmZoneConsistency() {
        ZoneId configured = properties.getZoneId();
        if (configured == null) {
            return;
        }
        ZoneId jvm = ZoneId.systemDefault();
        if (configured.getRules().equals(jvm.getRules())) {
            return;
        }
        String message = "JVM/容器默认时区(" + jvm + ") 与 iov.web.jackson.zone-id(" + configured
                + ") 不一致，LocalDateTime 写入与 JSON 解释会静默偏移；请固化部署 TZ 或配置 strict-jvm-zone";
        if (properties.isStrictJvmZone()) {
            throw new IllegalStateException(message);
        }
        log.warn(message);
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

    /**
     * iso 模式形态断言：字符串 + 含 `Z`/offset + 恰好 3 位小数（FW-WEB-DSN-CR-003 §16.4）。
     */
    private static void requireIsoMillis(JsonNode node, String field, String message) {
        JsonNode child = node.get(field);
        if (child == null || !child.isTextual()) {
            throw new IllegalStateException(field + ": " + message + "（实际: " + child + "）");
        }
        String text = child.asText();
        if (!ISO_MILLIS.matcher(text).matches()) {
            throw new IllegalStateException(field + ": " + message + "（实际: " + text + "）");
        }
    }

    private static void requireEqual(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new IllegalStateException(message + "（期望: " + expected + "，实际: " + actual + "）");
        }
    }
}
