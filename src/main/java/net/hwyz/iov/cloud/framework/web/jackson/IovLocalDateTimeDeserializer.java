package net.hwyz.iov.cloud.framework.web.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * LocalDateTime 双读反序列化器（FW-WEB-DSN-CR-001）。
 * <p>
 * 输入语义：
 * <ul>
 *   <li>字符串：ISO-8601 墙上时间（无 offset，如 2026-10-14T14:26:13），带 offset 的字符串
 *       属于绝对时刻语义（Instant/OffsetDateTime），拒绝并提示，不得静默丢弃 offset；</li>
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
