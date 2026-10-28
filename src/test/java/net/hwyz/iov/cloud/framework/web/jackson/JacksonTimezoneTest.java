package net.hwyz.iov.cloud.framework.web.jackson;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 时区/精度测试（FW-WEB-DSN-CR-001，对应 CR §8 "UTC 与 Asia/Shanghai JVM 重跑同样结果"）。
 * <p>
 * 框架代码不使用 systemDefault，JVM 时区变化不影响显式 zone 的转换结果；
 * 本测试在 UTC 与 Asia/Shanghai 两种 JVM 时区下重跑相同断言。
 */
class JacksonTimezoneTest {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final Instant SAMPLE_INSTANT = Instant.parse("2026-10-28T12:43:32Z");
    private static final LocalDateTime SAMPLE_LOCAL = LocalDateTime.parse("2026-10-14T14:26:13");

    private TimeZone original;

    @AfterEach
    void restoreJvmTimeZone() {
        if (original != null) {
            TimeZone.setDefault(original);
        }
    }

    private ObjectMapper mapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.registerModule(new IovJavaTimeReadModule(SHANGHAI));
        mapper.registerModule(new IovEpochMillisWriteModule(SHANGHAI));
        return mapper;
    }

    @Test
    void UTC与AsiaShanghai的JVM重跑结果一致() throws Exception {
        original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            runAssertions();
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
            runAssertions();
        } finally {
            TimeZone.setDefault(original);
        }
    }

    private void runAssertions() throws Exception {
        ObjectMapper mapper = mapper();
        // UTC 语义字段输出与 JVM 时区无关
        assertThat(mapper.writeValueAsString(SAMPLE_INSTANT)).isEqualTo(String.valueOf(SAMPLE_INSTANT.toEpochMilli()));
        // Asia/Shanghai 墙上时间往返不变（不漂移八小时）
        long millis = SAMPLE_LOCAL.atZone(SHANGHAI).toInstant().toEpochMilli();
        assertThat(mapper.readValue(String.valueOf(millis), LocalDateTime.class)).isEqualTo(SAMPLE_LOCAL);
        // 显式 zone 转换固定
        assertThat(LocalDateTime.ofInstant(SAMPLE_INSTANT, SHANGHAI))
                .isEqualTo(LocalDateTime.parse("2026-10-28T20:43:32"));
        // OffsetDateTime 数字读取时刻固定
        OffsetDateTime read = mapper.readValue(String.valueOf(SAMPLE_INSTANT.toEpochMilli()), OffsetDateTime.class);
        assertThat(read.toInstant()).isEqualTo(SAMPLE_INSTANT);
    }

    @Test
    void ISO小数秒按原精度保留最多纳秒() throws Exception {
        ObjectMapper mapper = mapper();
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.registerModule(new JavaTimeModule());
        mapper.registerModule(new IovJavaTimeReadModule(SHANGHAI));
        Instant nano = Instant.parse("2026-10-28T12:43:32.123456789Z");
        Instant millis = Instant.parse("2026-10-28T12:43:32.123Z");
        assertThat(mapper.readValue("\"2026-10-28T12:43:32.123456789Z\"", Instant.class)).isEqualTo(nano);
        assertThat(mapper.readValue("\"2026-10-28T12:43:32.123Z\"", Instant.class)).isEqualTo(millis);
    }
}
