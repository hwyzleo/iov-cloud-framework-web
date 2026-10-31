package net.hwyz.iov.cloud.framework.web.autoconfigure;

import net.hwyz.iov.cloud.framework.common.time.FallbackReason;
import net.hwyz.iov.cloud.framework.common.time.RequestTimeZoneContext;
import net.hwyz.iov.cloud.framework.common.time.Snapshot;
import net.hwyz.iov.cloud.framework.common.time.Source;
import net.hwyz.iov.cloud.framework.web.timezone.RequestTimeZoneInterceptor;
import net.hwyz.iov.cloud.framework.web.timezone.feign.IovRequestTimeZoneFeignRequestInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;

import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * feign-propagation-enabled 独立关闭测试（FW-WEB-DSN-CR-002，RD-TZ-002/006）。
 * <p>
 * enabled=true 但 feign-propagation-enabled=false：时区解析链仍在，Feign 透传链关闭。
 */
@SpringBootTest(classes = RequestTimeZoneFeignDisabledPropagationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "iov.web.timezone.feign-propagation-enabled=false",
                "iov.web.timezone.internal-feign-targets=internal-echo",
                "iov.web.jackson.zone-id=Asia/Shanghai",
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                        + "org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration,"
                        + "com.github.pagehelper.autoconfigure.PageHelperAutoConfiguration"
        })
class RequestTimeZoneFeignDisabledPropagationTest {

    @Autowired
    private org.springframework.context.ApplicationContext context;

    @Test
    void feign透传关闭但时区解析链保留() {
        // 透传拦截器不注册
        assertThat(context.getBeansOfType(IovRequestTimeZoneFeignRequestInterceptor.class)).isEmpty();
        // 时区解析链仍注册（enabled=true）
        assertThat(context.getBeansOfType(RequestTimeZoneInterceptor.class)).hasSize(1);
    }

    @Test
    void feign透传关闭时即使绑定也不注入() {
        // 透传拦截器 bean 缺失即链路关闭（绑定快照的注入行为由单测覆盖）
        assertThat(context.getBeansOfType(IovRequestTimeZoneFeignRequestInterceptor.class)).isEmpty();
    }

    @Configuration
    @EnableAutoConfiguration
    static class TestApplication {
    }
}
