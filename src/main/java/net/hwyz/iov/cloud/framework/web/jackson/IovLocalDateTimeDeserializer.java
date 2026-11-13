package net.hwyz.iov.cloud.framework.web.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

/**
 * LocalDateTime 双读反序列化器（FW-WEB-DSN-CR-001；FW-WEB-DSN-CR-003 扩展带 offset 字符串）。
 * <p>
 * 输入语义（FW-WEB-DSN-CR-003 FR-003-6：出站统一带时区后，入站必须对称接受）：
 * <ul>
 *   <li>字符串带 offset（{@code 2026-10-28T20:43:32+08:00} / {@code ...Z}）：按显式 ZoneId
 *       归一为墙上时间；未配置 zone-id 时 fail-closed；</li>
 *   <li>字符串无 offset（{@code 2026-10-14T14:26:13}）：原样作为墙上时间（兼容存量客户端）；</li>
 *   <li>整数：10 位 epoch 秒 / 13 位 epoch 毫秒 → Instant → 显式 ZoneId 下的墙上时间；
 *       未配置 zone-id 时 fail-closed，禁止 systemDefault 猜测；</li>
 *   <li>小数、空字符串、错误类型：拒绝。</li>
 * </ul>
 *
 * @author hwyz_leo
 * @since 2026-10-28
 */
public class IovLocalDateTimeDeserializer extends JsonDeserializer<LocalDateTime> {

    private final ZoneId zoneId;

    public IovLocalDateTimeDeserializer(ZoneId zoneId) {
        this.zoneId = zoneId;
    }

    @Override
    public LocalDateTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonToken token = p.currentToken();
        if (token == JsonToken.VALUE_STRING) {
            String text = p.getText().trim();
            if (text.isEmpty()) {
                throw JsonMappingException.from(p, "LocalDateTime 不接受空字符串");
            }
            try {
                OffsetDateTime offsetDateTime = OffsetDateTime.parse(text);
                if (zoneId == null) {
                    throw JsonMappingException.from(p,
                            "带 offset 的 LocalDateTime 入站需要显式 iov.web.jackson.zone-id（禁止使用默认时区猜测业务语义）");
                }
                return offsetDateTime.atZoneSameInstant(zoneId).toLocalDateTime();
            } catch (DateTimeParseException ignored) {
                // 无 offset 的墙上时间：保持原语义
            }
            return LocalDateTime.parse(text);
        }
        if (token == JsonToken.VALUE_NUMBER_INT) {
            if (zoneId == null) {
                throw JsonMappingException.from(p,
                        "数字 → LocalDateTime 需要显式 iov.web.jackson.zone-id（禁止使用默认时区猜测业务语义）");
            }
            Instant instant;
            try {
                instant = IovTimeNumbers.toInstant(p.getLongValue());
            } catch (IllegalArgumentException e) {
                throw JsonMappingException.from(p, e.getMessage());
            }
            return LocalDateTime.ofInstant(instant, zoneId);
        }
        if (token == JsonToken.VALUE_NUMBER_FLOAT) {
            throw JsonMappingException.from(p, "LocalDateTime 拒绝小数时间戳（秒小数/亚毫秒）");
        }
        throw JsonMappingException.from(p,
                "LocalDateTime 仅支持无 offset 的 ISO-8601 墙上时间、10 位 epoch 秒或 13 位 epoch 毫秒整数");
    }
}
