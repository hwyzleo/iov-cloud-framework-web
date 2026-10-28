package net.hwyz.iov.cloud.framework.web.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;

import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * OffsetDateTime 双读反序列化器（FW-WEB-DSN-CR-001）。
 * <p>
 * 输入语义：
 * <ul>
 *   <li>字符串：ISO-8601，必须含 offset（如 2026-10-28T20:43:32+08:00），缺失 offset 拒绝；</li>
 *   <li>整数：10 位 epoch 秒 / 13 位 epoch 毫秒 → Instant → 显式 ZoneId 下的 OffsetDateTime
 *       （数字不携带原始 offset，不做"恢复原 offset"声明）；未配置 zone-id 时 fail-closed；</li>
 *   <li>小数、空字符串、错误类型：拒绝。</li>
 * </ul>
 *
 * @author hwyz_leo
 * @since 2026-10-28
 */
public class IovOffsetDateTimeDeserializer extends JsonDeserializer<OffsetDateTime> {

    private final ZoneId zoneId;

    public IovOffsetDateTimeDeserializer(ZoneId zoneId) {
        this.zoneId = zoneId;
    }

    @Override
    public OffsetDateTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonToken token = p.currentToken();
        if (token == JsonToken.VALUE_STRING) {
            String text = p.getText().trim();
            if (text.isEmpty()) {
                throw JsonMappingException.from(p, "OffsetDateTime 不接受空字符串");
            }
            return OffsetDateTime.parse(text);
        }
        if (token == JsonToken.VALUE_NUMBER_INT) {
            if (zoneId == null) {
                throw JsonMappingException.from(p,
                        "数字 → OffsetDateTime 需要显式 iov.web.jackson.zone-id（禁止使用默认时区猜测业务语义）");
            }
            Instant instant;
            try {
                instant = IovTimeNumbers.toInstant(p.getLongValue());
            } catch (IllegalArgumentException e) {
                throw JsonMappingException.from(p, e.getMessage());
            }
            return instant.atOffset(zoneId.getRules().getOffset(instant));
        }
        if (token == JsonToken.VALUE_NUMBER_FLOAT) {
            throw JsonMappingException.from(p, "OffsetDateTime 拒绝小数时间戳（秒小数/亚毫秒）");
        }
        throw JsonMappingException.from(p,
                "OffsetDateTime 仅支持含 offset 的 ISO-8601 字符串、10 位 epoch 秒或 13 位 epoch 毫秒整数");
    }
}
