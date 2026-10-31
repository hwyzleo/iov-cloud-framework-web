package net.hwyz.iov.cloud.framework.web.autoconfigure;

import jakarta.servlet.DispatcherType;
import net.hwyz.iov.cloud.framework.common.time.FallbackReason;
import net.hwyz.iov.cloud.framework.common.time.RequestTimeZoneContext;
import net.hwyz.iov.cloud.framework.common.time.Snapshot;
import net.hwyz.iov.cloud.framework.common.time.Source;
import net.hwyz.iov.cloud.framework.web.timezone.AuthenticatedUserTimeZoneResolver;
import net.hwyz.iov.cloud.framework.web.timezone.DisplayTimeFacade;
import net.hwyz.iov.cloud.framework.web.timezone.RequestTimeZoneHeaders;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 请求时区 MVC 集成测试（FW-WEB-DSN-CR-002，TC-TZ-05）。
 * <p>
 * 真实 Boot 装配 + MockMvc：header 展示差异、JSON golden 不变、Callable 异步工作线程
 * 获取快照、异常/错误分派清理、认证用户 SPI 优先、redispatch 快照复用。
 */
@SpringBootTest(classes = RequestTimeZoneMvcIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "iov.web.jackson.zone-id=Asia/Shanghai",
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                        + "org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration,"
                        + "com.github.pagehelper.autoconfigure.PageHelperAutoConfiguration"
        })
@AutoConfigureMockMvc
class RequestTimeZoneMvcIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DisplayTimeFacade displayTimeFacade;

    @AfterEach
    void cleanupThreadState() {
        RequestTimeZoneContext.clear();
        org.slf4j.MDC.clear();
    }

    @Test
    void header影响展示而JSONBody不变() throws Exception {
        String without = mockMvc.perform(get("/display"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String withHeader = mockMvc.perform(get("/display")
                        .header(RequestTimeZoneHeaders.TIME_ZONE, "Europe/Berlin"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        // 展示文本随 header 变化（柏林时间）
        assertThat(without).isEqualTo("2026-10-28 12:43:32");
        assertThat(withHeader).isEqualTo("2026-10-28 05:43:32");

        // 原 JSON body golden 不变（Jackson 双读/输出不受请求时区影响）
        String jsonWithout = mockMvc.perform(get("/time-json"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String jsonWith = mockMvc.perform(get("/time-json")
                        .header(RequestTimeZoneHeaders.TIME_ZONE, "Europe/Berlin"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(jsonWith).isEqualTo(jsonWithout);
        assertThat(jsonWithout).contains("1793162612000");
    }

    @Test
    void 无header默认上海且请求后上下文清理() throws Exception {
        mockMvc.perform(get("/display"))
                .andExpect(status().isOk())
                .andExpect(content().string("2026-10-28 12:43:32"));
        assertThat(RequestTimeZoneContext.currentOptional()).isEmpty();
    }

    @Test
    void Callable异步工作线程获取快照并清理() throws Exception {
        MvcResult result = mockMvc.perform(get("/async")
                        .header(RequestTimeZoneHeaders.TIME_ZONE, "Europe/Berlin"))
                .andExpect(request().asyncStarted())
                .andReturn();
        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string("zone=Europe/Berlin"));
        // 请求线程上下文已清理
        assertThat(RequestTimeZoneContext.currentOptional()).isEmpty();
    }

    @Test
    void 控制器异常后上下文清理() {
        try {
            mockMvc.perform(get("/boom")
                    .header(RequestTimeZoneHeaders.TIME_ZONE, "Europe/Berlin"));
        } catch (Exception expected) {
            // 异常按测试框架包装抛出，预期
        }
        assertThat(RequestTimeZoneContext.currentOptional()).isEmpty();
    }

    @Test
    void ERROR分派复用快照不重复解析() throws Exception {
        Snapshot snapshot = new Snapshot(ZoneId.of("Europe/Berlin"), Source.HEADER, FallbackReason.NONE);
        mockMvc.perform(get("/display")
                        .with(request -> {
                            request.setDispatcherType(DispatcherType.ERROR);
                            return request;
                        })
                        .requestAttr(RequestTimeZoneHeaders.REQUEST_ATTR_SNAPSHOT, snapshot))
                .andExpect(status().isOk())
                .andExpect(content().string("2026-10-28 05:43:32"));
        assertThat(RequestTimeZoneContext.currentOptional()).isEmpty();
    }

    @Test
    void 认证用户SPI优先于header且认证链先执行() throws Exception {
        // auth interceptor(order=-10) 仅在 /user/** 建立 principal；用户 SPI 从 principal 读取东京时区
        mockMvc.perform(get("/user/display")
                        .header(RequestTimeZoneHeaders.TIME_ZONE, "Europe/Berlin"))
                .andExpect(status().isOk())
                .andExpect(content().string("2026-10-28 13:43:32")); // Asia/Tokyo (+09:00)
    }

    @Test
    void facade无绑定时显式兜底() {
        // enabled=true 但无请求上下文（后台线程）：facade 仍可调用，回到默认
        assertThat(displayTimeFacade.format(Instant.parse("2026-10-28T04:43:32Z")))
                .isEqualTo("2026-10-28 12:43:32");
    }

    @Configuration
    @EnableAutoConfiguration
    static class TestApplication {

        @Bean
        public TestController testController(DisplayTimeFacade facade) {
            return new TestController(facade);
        }

        @Bean
        public AuthenticatedUserTimeZoneResolver authenticatedUserTimeZoneResolver() {
            // 仅当认证链已建立 principal 时才提供用户时区（SPI 自检，不依赖注册顺序推定）
            return request -> {
                if (request.getAttribute("auth.userId") == null) {
                    return Optional.empty();
                }
                return Optional.of(ZoneId.of("Asia/Tokyo"));
            };
        }

        @Bean
        public WebMvcConfigurer authInterceptorConfigurer() {
            return new WebMvcConfigurer() {
                @Override
                public void addInterceptors(InterceptorRegistry registry) {
                    registry.addInterceptor(new HandlerInterceptor() {
                        @Override
                        public boolean preHandle(jakarta.servlet.http.HttpServletRequest request,
                                                 jakarta.servlet.http.HttpServletResponse response,
                                                 Object handler) {
                            // 模拟认证：仅在 /user/** 建立 principal
                            request.setAttribute("auth.userId", "u-100");
                            return true;
                        }
                    }).addPathPatterns("/user/**").order(-10);
                }
            };
        }
    }

    @RestController
    static class TestController {

        private static final Instant SAMPLE = Instant.parse("2026-10-28T04:43:32Z");

        private final DisplayTimeFacade facade;

        TestController(DisplayTimeFacade facade) {
            this.facade = facade;
        }

        @GetMapping("/display")
        public String display() {
            return facade.format(SAMPLE);
        }

        @GetMapping("/user/display")
        public String userDisplay() {
            return facade.format(SAMPLE);
        }

        @GetMapping("/time-json")
        public TimeDto timeJson() {
            return new TimeDto(SAMPLE.toEpochMilli());
        }

        @GetMapping("/async")
        public Callable<String> async() {
            return () -> "zone=" + RequestTimeZoneContext.current();
        }

        @GetMapping("/boom")
        public String boom() {
            throw new IllegalStateException("模拟业务异常");
        }

        record TimeDto(long epochMillis) {
        }
    }
}
