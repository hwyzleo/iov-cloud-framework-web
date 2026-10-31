package net.hwyz.iov.cloud.framework.web.timezone;

import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 请求时区解析/校验矩阵（FW-WEB-DSN-CR-002，TC-TZ-01）。
 */
class RequestTimeZoneParserTest {

    @Test
    void IANA地区ID与UTC() {
        assertThat(RequestTimeZoneParser.parse("Asia/Shanghai")).contains(ZoneId.of("Asia/Shanghai"));
        assertThat(RequestTimeZoneParser.parse("Europe/Berlin")).contains(ZoneId.of("Europe/Berlin"));
        assertThat(RequestTimeZoneParser.parse("UTC")).contains(ZoneId.of("UTC"));
        // 大小写精确：小写拒绝
        assertThat(RequestTimeZoneParser.parse("asia/shanghai")).isEmpty();
        assertThat(RequestTimeZoneParser.parse("UTC")).isPresent();
    }

    @Test
    void 正负offset() {
        assertThat(RequestTimeZoneParser.parse("+08:00")).contains(ZoneOffset.ofHours(8));
        assertThat(RequestTimeZoneParser.parse("-05:30")).contains(ZoneOffset.ofHoursMinutes(-5, -30));
        assertThat(RequestTimeZoneParser.parse("+00:00")).contains(ZoneOffset.ofHours(0));
    }

    @Test
    void offset18小时边界() {
        assertThat(RequestTimeZoneParser.parse("+18:00")).contains(ZoneOffset.ofHours(18));
        assertThat(RequestTimeZoneParser.parse("-18:00")).contains(ZoneOffset.ofHours(-18));
        // ±18 时分钟须 00；超界拒绝
        assertThat(RequestTimeZoneParser.parse("+18:01")).isEmpty();
        assertThat(RequestTimeZoneParser.parse("-18:30")).isEmpty();
        assertThat(RequestTimeZoneParser.parse("+19:00")).isEmpty();
    }

    @Test
    void 拒绝短别名数字猜测与非常规形式() {
        assertThat(RequestTimeZoneParser.parse("CST")).isEmpty();
        assertThat(RequestTimeZoneParser.parse("PST")).isEmpty();
        assertThat(RequestTimeZoneParser.parse("EST")).isEmpty();
        assertThat(RequestTimeZoneParser.parse("8")).isEmpty();
        assertThat(RequestTimeZoneParser.parse("08:00")).isEmpty();
        assertThat(RequestTimeZoneParser.parse("UTC+8")).isEmpty();
        assertThat(RequestTimeZoneParser.parse("GMT+8")).isEmpty();
    }

    @Test
    void 拒绝负零与秒级offset() {
        assertThat(RequestTimeZoneParser.parse("-00:00")).isEmpty();
        assertThat(RequestTimeZoneParser.parse("+08:00:30")).isEmpty();
        assertThat(RequestTimeZoneParser.parse("+0830")).isEmpty();
    }

    @Test
    void 拒绝长度字符类问题() {
        // 超长
        assertThat(RequestTimeZoneParser.parse("Asia/" + "A".repeat(60))).isEmpty();
        // 控制字符/逗号/内部空白（先拒绝，不能 trim 隐藏）
        assertThat(RequestTimeZoneParser.parse("Asia/Shanghai\u0001")).isEmpty();
        assertThat(RequestTimeZoneParser.parse("Asia/Shanghai,Europe/Berlin")).isEmpty();
        assertThat(RequestTimeZoneParser.parse("Asia/ Shanghai")).isEmpty();
        assertThat(RequestTimeZoneParser.parse("\tAsia/Shanghai")).isEmpty();
        assertThat(RequestTimeZoneParser.parse("\nAsia/Shanghai")).isEmpty();
        // 非 ASCII
        assertThat(RequestTimeZoneParser.parse("Asia/Shanghai\u00A0")).isEmpty();
        assertThat(RequestTimeZoneParser.parse("Asia/上海")).isEmpty();
    }

    @Test
    void 首尾普通空格去除后判断空值无效() {
        // 允许首尾 ASCII 空格
        assertThat(RequestTimeZoneParser.parse("  Asia/Shanghai  ")).contains(ZoneId.of("Asia/Shanghai"));
        // 全空格 → 空值无效
        assertThat(RequestTimeZoneParser.parse("   ")).isEmpty();
        assertThat(RequestTimeZoneParser.parse("")).isEmpty();
        assertThat(RequestTimeZoneParser.parse(null)).isEmpty();
    }

    @Test
    void 规范化输出() {
        assertThat(RequestTimeZoneParser.normalize(ZoneId.of("Asia/Shanghai"))).isEqualTo("Asia/Shanghai");
        assertThat(RequestTimeZoneParser.normalize(ZoneId.of("UTC"))).isEqualTo("UTC");
        assertThat(RequestTimeZoneParser.normalize(ZoneOffset.ofHours(8))).isEqualTo("+08:00");
    }
}
