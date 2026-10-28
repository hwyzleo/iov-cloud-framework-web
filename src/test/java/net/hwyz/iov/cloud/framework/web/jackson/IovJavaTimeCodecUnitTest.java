package net.hwyz.iov.cloud.framework.web.jackson;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * java.time codec 单元测试（FW-WEB-DSN-CR-001）。
 * <p>
 * 模拟真实装配：JavaTimeModule（Boot 自动发现）在前，框架模块 modulesToInstall 追加在后，
 * 最后注册的 serializer/deserializer 胜出。
 */
class IovJavaTimeCodecUnitTest {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final Instant SAMPLE_INSTANT = Instant.parse("2026-10-28T12:43:32Z");
    private static final OffsetDateTime SAMPLE_OFFSET = OffsetDateTime.parse("2026-10-28T20:43:32+08:00");
    private static final LocalDateTime SAMPLE_LOCAL = LocalDateTime.parse("2026-10-14T14:26:13");

    // ---------- mapper 构造（模拟真实装配） ----------

    private ObjectMapper epochMillisMapper(ZoneId zone) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.registerModule(new IovJavaTimeReadModule(zone));
        mapper.registerModule(new IovEpochMillisWriteModule(zone));
        return mapper;
    }

    private ObjectMapper isoMapper(ZoneId zone) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.registerModule(new JavaTimeModule());
        mapper.registerModule(new IovJavaTimeReadModule(zone));
        return mapper;
    }

    // ---------- 双模式写出 ----------

    @Test
    void epochMillis模式三类型输出13位毫秒数字() throws Exception {
        ObjectMapper mapper = epochMillisMapper(SHANGHAI);
        String json = mapper.writeValueAsString(new Sample(SAMPLE_INSTANT, SAMPLE_OFFSET, SAMPLE_LOCAL));
        assertThat(json).contains("\"instant\":" + SAMPLE_INSTANT.toEpochMilli());
        assertThat(json).contains("\"offset\":" + SAMPLE_OFFSET.toInstant().toEpochMilli());
        assertThat(json).contains("\"local\":" + SAMPLE_LOCAL.atZone(SHANGHAI).toInstant().toEpochMilli());
    }

    @Test
    void iso模式三类型输出ISO8601字符串() throws Exception {
        ObjectMapper mapper = isoMapper(SHANGHAI);
        String json = mapper.writeValueAsString(new Sample(SAMPLE_INSTANT, SAMPLE_OFFSET, SAMPLE_LOCAL));
        assertThat(json).contains("\"instant\":\"2026-10-28T12:43:32Z\"");
        assertThat(json).contains("\"offset\":\"2026-10-28T20:43:32+08:00\"");
        assertThat(json).contains("\"local\":\"2026-10-14T14:26:13\"");
    }

    @Test
    void iso模式无数组输出且无秒小数() throws Exception {
        String json = isoMapper(SHANGHAI).writeValueAsString(SAMPLE_LOCAL);
        assertThat(json).isEqualTo("\"2026-10-14T14:26:13\"");
    }

    // ---------- 双读 ----------

    @Test
    void 数字13位毫秒与10位秒归一至同一Instant() throws Exception {
        ObjectMapper mapper = epochMillisMapper(SHANGHAI);
        long millis = SAMPLE_INSTANT.toEpochMilli();
        long seconds = SAMPLE_INSTANT.getEpochSecond();
        assertThat(mapper.readValue(String.valueOf(millis), Instant.class)).isEqualTo(SAMPLE_INSTANT);
        assertThat(mapper.readValue(String.valueOf(seconds), Instant.class)).isEqualTo(SAMPLE_INSTANT);
    }

    @Test
    void ISO字符串读取Instant() throws Exception {
        ObjectMapper mapper = epochMillisMapper(SHANGHAI);
        assertThat(mapper.readValue("\"2026-10-28T12:43:32Z\"", Instant.class)).isEqualTo(SAMPLE_INSTANT);
        // ISO 小数秒保留纳秒
        Instant nano = Instant.parse("2026-10-28T12:43:32.123456789Z");
        assertThat(mapper.readValue("\"2026-10-28T12:43:32.123456789Z\"", Instant.class)).isEqualTo(nano);
    }

    @Test
    void 数字10位边界与13位边界() throws Exception {
        ObjectMapper mapper = epochMillisMapper(SHANGHAI);
        assertThat(mapper.readValue("1000000000", Instant.class)).isEqualTo(Instant.parse("2001-09-09T01:46:40Z"));
        assertThat(mapper.readValue("9999999999", Instant.class)).isEqualTo(Instant.parse("2286-11-20T17:46:39Z"));
        assertThat(mapper.readValue("1000000000000", Instant.class)).isEqualTo(Instant.parse("2001-09-09T01:46:40Z"));
        assertThat(mapper.readValue("9999999999999", Instant.class)).isEqualTo(Instant.parse("2286-11-20T17:46:39.999Z"));
    }

    @Test
    void LocalDateTime数字输入走显式zone() throws Exception {
        ObjectMapper mapper = epochMillisMapper(SHANGHAI);
        LocalDateTime read = mapper.readValue(
                String.valueOf(SAMPLE_LOCAL.atZone(SHANGHAI).toInstant().toEpochMilli()), LocalDateTime.class);
        assertThat(read).isEqualTo(SAMPLE_LOCAL);
    }

    @Test
    void OffsetDateTime数字输入走显式zone且不声明恢复原offset() throws Exception {
        ObjectMapper mapper = epochMillisMapper(SHANGHAI);
        OffsetDateTime read = mapper.readValue(
                String.valueOf(SAMPLE_OFFSET.toInstant().toEpochMilli()), OffsetDateTime.class);
        assertThat(read.toInstant()).isEqualTo(SAMPLE_OFFSET.toInstant());
        assertThat(read.getOffset()).isEqualTo(SHANGHAI.getRules().getOffset(read.toInstant()));
    }

    @Test
    void OffsetDateTimeISO带offset读取() throws Exception {
        ObjectMapper mapper = epochMillisMapper(SHANGHAI);
        assertThat(mapper.readValue("\"2026-10-28T20:43:32+08:00\"", OffsetDateTime.class)).isEqualTo(SAMPLE_OFFSET);
    }

    @Test
    void LocalDateTimeISO无offset读取() throws Exception {
        ObjectMapper mapper = epochMillisMapper(SHANGHAI);
        assertThat(mapper.readValue("\"2026-10-14T14:26:13\"", LocalDateTime.class)).isEqualTo(SAMPLE_LOCAL);
    }

    // ---------- null 与空字符串 ----------

    @Test
    void JSON_null按可空性处理不转为epoch0() throws Exception {
        ObjectMapper mapper = epochMillisMapper(SHANGHAI);
        assertThat(mapper.readValue("null", Instant.class)).isNull();
        assertThat(mapper.readValue("null", LocalDateTime.class)).isNull();
        assertThat(mapper.readValue("null", OffsetDateTime.class)).isNull();
    }

    @Test
    void 空字符串拒绝() {
        ObjectMapper mapper = epochMillisMapper(SHANGHAI);
        assertThatThrownBy(() -> mapper.readValue("\"\"", Instant.class)).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> mapper.readValue("\"\"", LocalDateTime.class)).isInstanceOf(Exception.class);
    }

    // ---------- 非法格式拒绝（RD-001-3 fail-closed） ----------

    @Test
    void 数组拒绝() {
        ObjectMapper mapper = epochMillisMapper(SHANGHAI);
        assertThatThrownBy(() -> mapper.readValue("[2026,10,28]", LocalDateTime.class)).isInstanceOf(Exception.class);
    }

    @Test
    void 小数秒拒绝() {
        ObjectMapper mapper = epochMillisMapper(SHANGHAI);
        assertThatThrownBy(() -> mapper.readValue("1793177012000.5", Instant.class)).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> mapper.readValue("1793177012.5", Instant.class)).isInstanceOf(Exception.class);
    }

    @Test
    void 窗口外数字拒绝_0_负_其他位数() {
        ObjectMapper mapper = epochMillisMapper(SHANGHAI);
        assertThatThrownBy(() -> mapper.readValue("0", Instant.class)).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> mapper.readValue("-1", Instant.class)).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> mapper.readValue("946684800", Instant.class)).isInstanceOf(Exception.class); // 9位秒
        assertThatThrownBy(() -> mapper.readValue("12345678901", Instant.class)).isInstanceOf(Exception.class); // 11位
        assertThatThrownBy(() -> mapper.readValue("123456789012", Instant.class)).isInstanceOf(Exception.class); // 12位
    }

    @Test
    void 溢出拒绝() {
        ObjectMapper mapper = epochMillisMapper(SHANGHAI);
        assertThatThrownBy(() -> mapper.readValue("99999999999999999999", Instant.class)).isInstanceOf(Exception.class);
    }

    @Test
    void 非法ISO与无offsetInstant拒绝() {
        ObjectMapper mapper = epochMillisMapper(SHANGHAI);
        assertThatThrownBy(() -> mapper.readValue("\"2026-10-28T12:43:32\"", Instant.class)).isInstanceOf(Exception.class); // 无 offset
        assertThatThrownBy(() -> mapper.readValue("\"not-a-date\"", Instant.class)).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> mapper.readValue("\"2026-10-14T14:26:13+08:00\"", LocalDateTime.class))
                .isInstanceOf(Exception.class); // LocalDateTime 带 offset 不得静默丢弃
        assertThatThrownBy(() -> mapper.readValue("\"2026-10-28T12:43:32\"", OffsetDateTime.class))
                .isInstanceOf(Exception.class); // OffsetDateTime 无 offset
    }

    @Test
    void 数字字符串拒绝不猜测() {
        ObjectMapper mapper = epochMillisMapper(SHANGHAI);
        assertThatThrownBy(() -> mapper.readValue("\"1793177012000\"", Instant.class)).isInstanceOf(Exception.class);
    }

    @Test
    void 错误类型拒绝() {
        ObjectMapper mapper = epochMillisMapper(SHANGHAI);
        assertThatThrownBy(() -> mapper.readValue("true", Instant.class)).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> mapper.readValue("{}", Instant.class)).isInstanceOf(Exception.class);
    }

    // ---------- zone fail-closed（未配置 zone，禁止 systemDefault） ----------

    @Test
    void 未配置zone时数字转LocalDateTime拒绝() {
        ObjectMapper mapper = epochMillisMapper(null);
        assertThatThrownBy(() -> mapper.readValue("1793177012000", LocalDateTime.class))
                .hasMessageContaining("zone-id");
    }

    @Test
    void 未配置zone时数字转OffsetDateTime拒绝() {
        ObjectMapper mapper = epochMillisMapper(null);
        assertThatThrownBy(() -> mapper.readValue("1793177012000", OffsetDateTime.class))
                .hasMessageContaining("zone-id");
    }

    @Test
    void 未配置zone时epochMillis模式LocalDateTime写出拒绝() {
        ObjectMapper mapper = epochMillisMapper(null);
        assertThatThrownBy(() -> mapper.writeValueAsString(SAMPLE_LOCAL))
                .hasMessageContaining("zone-id");
    }

    // ---------- 模块回归（JavaTimeModule 默认能力保留） ----------

    @Test
    void LocalDate_LocalTime_Optional不受影响() throws Exception {
        // epoch-millis 模式：JavaTimeModule 默认行为保留（WRITE_DATES_AS_TIMESTAMPS 开启 → 数组，非本 CR 引入的 timestamp 化）
        ObjectMapper epoch = epochMillisMapper(SHANGHAI);
        assertThat(epoch.readValue(epoch.writeValueAsString(LocalDate.of(2026, 10, 14)), LocalDate.class))
                .isEqualTo(LocalDate.of(2026, 10, 14));
        assertThat(epoch.readValue(epoch.writeValueAsString(LocalTime.of(14, 26, 13)), LocalTime.class))
                .isEqualTo(LocalTime.of(14, 26, 13));
        // iso 模式：LocalDate/LocalTime 输出 ISO 字符串，不被本 CR 变为 timestamp
        ObjectMapper iso = isoMapper(SHANGHAI);
        assertThat(iso.writeValueAsString(LocalDate.of(2026, 10, 14))).isEqualTo("\"2026-10-14\"");
        assertThat(iso.writeValueAsString(LocalTime.of(14, 26, 13))).isEqualTo("\"14:26:13\"");
        assertThat(iso.readValue("\"2026-10-14\"", LocalDate.class)).isEqualTo(LocalDate.of(2026, 10, 14));
    }

    // ---------- 空字符串 → 空集合（既有行为保留） ----------

    @Test
    void 空字符串反序列化为空集合() throws Exception {
        ObjectMapper mapper = epochMillisMapper(SHANGHAI);
        mapper.coercionConfigFor(LogicalType.Collection)
                .setCoercion(CoercionInputShape.EmptyString, CoercionAction.AsEmpty);
        assertThat(mapper.readValue("\"\"", List.class)).isEmpty();
    }

    // ---------- 辅助 ----------

    static class Sample {
        public Instant instant;
        public OffsetDateTime offset;
        public LocalDateTime local;

        Sample() {
        }

        Sample(Instant instant, OffsetDateTime offset, LocalDateTime local) {
            this.instant = instant;
            this.offset = offset;
            this.local = local;
        }
    }
}
