package net.hwyz.iov.cloud.framework.web.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * ISO-8601 写出模块（FW-WEB-DSN-CR-003，RD-003-1/2/4/5）：仅 ISO 模式安装。
 * <p>
 * 与 {@link IovEpochMillisWriteModule} 的分工：两者互斥，由 {@code iov.web.jackson.date-format} 决定，
 * 保证「写单一」。本模块把四类时间**全部纳入框架自身**，不再依赖 JavaTimeModule 默认行为或
 * Spring Boot 的 {@code StdDateFormat}：
 * <ul>
 *   <li>{@link Instant} → 截断毫秒 → {@code ...SSSZ}；</li>
 *   <li>{@link OffsetDateTime} → 归一为瞬时（丢弃原始 offset，统一 `Z`）→ 同上；</li>
 *   <li>{@link LocalDateTime} → 按显式 {@code zone-id} 解释为墙上时间 → 瞬时 → 同上；
 *       未配置 {@code zone-id} 时 fail-closed（禁止用 JVM 默认时区猜测业务语义）；</li>
 *   <li>{@link Date} → 毫秒瞬时 → 同上（此前由 Boot {@code StdDateFormat} 输出，形态不可控）。</li>
 * </ul>
 * 精度固定：统一 {@link ChronoUnit#MILLIS} 截断（不四舍五入），避免 {@code Instant.toString()}
 * 随纳秒值输出 0/3/6/9 位小数。
 *
 * @author hwyz_leo
 * @since 2026-11-13
 */
public class IovIsoWriteModule extends SimpleModule {

    public IovIsoWriteModule(ZoneId zoneId) {
        super("iov-iso-write");
        addSerializer(Instant.class, new JsonSerializer<Instant>() {
            @Override
            public void serialize(Instant value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                gen.writeString(write(value));
            }
        });
        addSerializer(OffsetDateTime.class, new JsonSerializer<OffsetDateTime>() {
            @Override
            public void serialize(OffsetDateTime value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                gen.writeString(write(value.toInstant()));
            }
        });
        addSerializer(LocalDateTime.class, new JsonSerializer<LocalDateTime>() {
            @Override
            public void serialize(LocalDateTime value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                if (zoneId == null) {
                    throw new IllegalStateException(
                            "ISO 模式下 LocalDateTime 输出需要显式 iov.web.jackson.zone-id（禁止使用 JVM 默认时区猜测业务语义）");
                }
                gen.writeString(write(value.atZone(zoneId).toInstant()));
            }
        });
        addSerializer(Date.class, new JsonSerializer<Date>() {
            @Override
            public void serialize(Date value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                gen.writeString(write(Instant.ofEpochMilli(value.getTime())));
            }
        });
    }

    /**
     * 截断到毫秒后按固定 3 位小数 + `Z` 输出。
     */
    static String write(Instant instant) {
        return IovTimeFormats.MILLIS_UTC.format(instant.truncatedTo(ChronoUnit.MILLIS));
    }
}
