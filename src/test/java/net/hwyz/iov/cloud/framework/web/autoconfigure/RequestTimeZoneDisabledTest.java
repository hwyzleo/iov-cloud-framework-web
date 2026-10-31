package net.hwyz.iov.cloud.framework.web.autoconfigure;

import net.hwyz.iov.cloud.framework.common.time.RequestTimeZoneContext;
import net.hwyz.iov.cloud.framework.web.timezone.DisplayTimeFacade;
import net.hwyz.iov.cloud.framework.web.timezone.RequestTimeZoneCleanupFilter;
import net.hwyz.iov.cloud.framework.web.timezone.RequestTimeZoneHeaders;
import net.hwyz.iov.cloud.framework.web.timezone.RequestTimeZoneInterceptor;
import net.hwyz.iov.cloud.framework.web.timezone.feign.IovRequestTimeZoneFeignRequestInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * enabled=false 开关装配测试（FW-WEB-DSN-CR-002，TC-TZ-02/05/09）。
 * <p>
 * enabled=false 关闭解析/绑定/清理/透传链，但保留可调用的 facade 与兜底能力，
 * 防止已迁移服务缺 bean 或裸调用 current() 失效。
 */
@SpringBootTest(classes = RequestTimeZoneDisabledTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "iov.web.timezone.enabled=false",
                "iov.web.jackson.zone-id=Asia/Shanghai",
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                        + "org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration,"
                        + "com.github.pagehelper.autoconfigure.PageHelperAutoConfiguration"
        })
@AutoConfigureMockMvc
class RequestTimeZoneDisabledTest {

    @Autowired
    private org.springframework.context.ApplicationContext context;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DisplayTimeFacade displayTimeFacade;

    @Test
    void enabled为false时解析绑定清理透传链不注册() {
        assertThat(context.getBeansOfType(RequestTimeZoneInterceptor.class)).isEmpty();
        assertThat(context.getBeansOfType(IovRequestTimeZoneFeignRequestInterceptor.class)).isEmpty();
        boolean cleanupRegistered = Arrays.stream(context.getBeanNamesForType(FilterRegistrationBean.class))
                .anyMatch(name -> context.getBean(name, FilterRegistrationBean.class).getFilter()
                        instanceof RequestTimeZoneCleanupFilter);
        assertThat(cleanupRegistered).isFalse();
        assertThat(context.getBeansOfType(net.hwyz.iov.cloud.framework.web.timezone.RequestTimeZoneSnapshotResolver.class))
                .isEmpty();
    }

    @Test
    void enabled为false时facade仍可调用且header不生效() throws Exception {
        // facade 兜底能力保留
        assertThat(displayTimeFacade.format(Instant.parse("2026-10-28T04:43:32Z")))
                .isEqualTo("2026-10-28 12:43:32");
        // 请求带 header 也不绑定上下文（无解析链），展示回到默认
        mockMvc.perform(get("/display")
                        .header(RequestTimeZoneHeaders.TIME_ZONE, "Europe/Berlin"))
                .andExpect(status().isOk())
                .andExpect(content().string("2026-10-28 12:43:32"));
        assertThat(RequestTimeZoneContext.currentOptional()).isEmpty();
    }

    @Configuration
    @EnableAutoConfiguration
    static class TestApplication {

        @Bean
        public DisabledController disabledController(DisplayTimeFacade facade) {
            return new DisabledController(facade);
        }
    }

    @RestController
    static class DisabledController {

        private static final Instant SAMPLE = Instant.parse("2026-10-28T04:43:32Z");

        private final DisplayTimeFacade facade;

        DisabledController(DisplayTimeFacade facade) {
            this.facade = facade;
        }

        @GetMapping("/display")
        public String display() {
            return facade.format(SAMPLE);
        }
    }
}
