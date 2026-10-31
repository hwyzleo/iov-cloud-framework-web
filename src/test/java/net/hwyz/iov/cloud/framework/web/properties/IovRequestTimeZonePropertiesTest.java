package net.hwyz.iov.cloud.framework.web.properties;

import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 配置校验（FW-WEB-DSN-CR-002，RD-TZ-002：非法/空默认、重复/空参数名启动失败）。
 */
class IovRequestTimeZonePropertiesTest {

    @Test
    void 非法默认zone启动失败() {
        IovRequestTimeZoneProperties p = new IovRequestTimeZoneProperties();
        p.setDefaultZoneId(ZoneId.of("GMT+8")); // ZoneId.of 成功但违反校验约定
        assertThatThrownBy(p::afterPropertiesSet).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 重复参数名启动失败() {
        IovRequestTimeZoneProperties p = new IovRequestTimeZoneProperties();
        p.setParamNames(List.of("timezone", "timezone"));
        assertThatThrownBy(p::afterPropertiesSet).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 空参数名启动失败() {
        IovRequestTimeZoneProperties p = new IovRequestTimeZoneProperties();
        p.setParamNames(List.of("", "timezone"));
        assertThatThrownBy(p::afterPropertiesSet).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 合法配置通过() {
        IovRequestTimeZoneProperties p = new IovRequestTimeZoneProperties();
        p.setDefaultZoneId(ZoneId.of("Asia/Shanghai"));
        p.setParamNames(List.of("timezone"));
        p.afterPropertiesSet();
    }
}
