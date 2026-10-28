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

/**
 * epoch 毫秒写出模块（FW-WEB-DSN-CR-001，RD-001-1）：仅 epoch-millis 模式安装。
 * <p>
 * 复现存量输出：Instant/OffsetDateTime → epoch 毫秒；LocalDateTime → 显式业务 ZoneId
 * 的 epoch 毫秒（旧实现为 systemDefault，本 CR 禁止，须显式配置核验后的 zone）。
 * 未配置 zone-id 时 LocalDateTime 写出 fail-closed。
 * <p>
 * 仅注册序列化器，不注册反序列化器（读兼容统一由 {@link IovJavaTimeReadModule} 提供）。
 *
 * @author hwyz_leo
 * @since 2026-10-28
 */
public class IovEpochMillisWriteModule extends SimpleModule {

    public IovEpochMillisWriteModule(ZoneId zoneId) {
        super("iov-epoch-millis-write");
        addSerializer(Instant.class, new JsonSerializer<Instant>() {
            @Override
            public void serialize(Instant value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                gen.writeNumber(value.toEpochMilli());
            }
        });
        addSerializer(OffsetDateTime.class, new JsonSerializer<OffsetDateTime>() {
            @Override
            public void serialize(OffsetDateTime value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                gen.writeNumber(value.toInstant().toEpochMilli());
            }
        });
        addSerializer(LocalDateTime.class, new JsonSerializer<LocalDateTime>() {
            @Override
            public void serialize(LocalDateTime value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                if (zoneId == null) {
                    throw new IllegalStateException(
                            "LocalDateTime epoch 数字输出需要显式 iov.web.jackson.zone-id（禁止 systemDefault 猜测业务语义）");
                }
                gen.writeNumber(value.atZone(zoneId).toInstant().toEpochMilli());
            }
        });
    }
}
