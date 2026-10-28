package net.hwyz.iov.cloud.framework.web.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;

import java.io.IOException;
import java.time.Instant;

/**
 * Instant 双读反序列化器（FW-WEB-DSN-CR-001，RD-001-2/RD-001-3）。
 * <p>
 * 输入语义：
 * <ul>
 *   <li>字符串：ISO-8601，必须含 offset（如 2026-10-28T12:43:32Z），非法或缺失 offset 拒绝；</li>
 *   <li>整数：10 位 epoch 秒 / 13 位 epoch 毫秒，窗口外拒绝；</li>
 *   <li>小数（秒小数/亚毫秒）、空字符串、错误类型：一律拒绝（fail-closed），不悄悄纠正；</li>
 *   <li>JSON null：按字段可空性处理，不转换为 epoch 0。</li>
 * </ul>
 *
 * @author hwyz_leo
 * @since 2026-10-28
 */
public class IovInstantDeserializer extends JsonDeserializer<Instant> {

    @Override
    public Instant deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonToken token = p.currentToken();
        if (token == JsonToken.VALUE_STRING) {
            String text = p.getText().trim();
            if (text.isEmpty()) {
                throw JsonMappingException.from(p, "Instant 不接受空字符串");
            }
            return Instant.parse(text);
        }
        if (token == JsonToken.VALUE_NUMBER_INT) {
            try {
                return IovTimeNumbers.toInstant(p.getLongValue());
            } catch (IllegalArgumentException e) {
                throw JsonMappingException.from(p, e.getMessage());
            }
        }
        if (token == JsonToken.VALUE_NUMBER_FLOAT) {
            throw JsonMappingException.from(p, "Instant 拒绝小数时间戳（秒小数/亚毫秒），默认不做无依据截断");
        }
        throw JsonMappingException.from(p,
                "Instant 仅支持含 offset 的 ISO-8601 字符串、10 位 epoch 秒或 13 位 epoch 毫秒整数");
    }
}
