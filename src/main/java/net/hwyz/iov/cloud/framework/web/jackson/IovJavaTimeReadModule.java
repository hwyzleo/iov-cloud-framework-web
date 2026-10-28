package net.hwyz.iov.cloud.framework.web.jackson;

import com.fasterxml.jackson.databind.module.SimpleModule;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * 兼容读模块（FW-WEB-DSN-CR-001，RD-001-2）：始终安装。
 * <p>
 * 仅注册 Instant/OffsetDateTime/LocalDateTime 反序列化器（ISO + 旧数字双读），
 * 不注册序列化器——写出由配置模式决定（epoch-millis 走 {@link IovEpochMillisWriteModule}，
 * iso 走 JavaTimeModule 默认），保持写单一、读兼容。
 * <p>
 * 通过 modulesToInstall 追加注册，位于 JavaTimeModule（自动发现）之后，
 * 同类型反序列化器最后注册者胜出。
 *
 * @author hwyz_leo
 * @since 2026-10-28
 */
public class IovJavaTimeReadModule extends SimpleModule {

    public IovJavaTimeReadModule(ZoneId zoneId) {
        super("iov-java-time-read");
        addDeserializer(Instant.class, new IovInstantDeserializer());
        addDeserializer(OffsetDateTime.class, new IovOffsetDateTimeDeserializer(zoneId));
        addDeserializer(LocalDateTime.class, new IovLocalDateTimeDeserializer(zoneId));
    }
}
