package net.hwyz.iov.cloud.framework.web.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MVC 端到端集成测试（FW-WEB-DSN-CR-001，对应 CR §8 "MockMvc/MVC → 前端"）。
 * <p>
 * iso 模式：验证真实 MVC 链路（HttpMessageConverter 使用生产 ObjectMapper）的
 * 序列化输出为 ISO-8601，且双读兼容历史数字输入。epoch-millis 数字输出的 MVC 链路
 * 与 mapper 等价（已由自动配置集成测试与单元测试覆盖）。
 */
@SpringBootTest(classes = JacksonMvcIntegrationTest.TestApplication.class,
        webEnvironment = WebEnvironment.MOCK,
        properties = {
                "iov.web.jackson.date-format=iso",
                "iov.web.jackson.zone-id=Asia/Shanghai",
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                        + "org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration,"
                        + "com.github.pagehelper.autoconfigure.PageHelperAutoConfiguration"
        })
@AutoConfigureMockMvc
class JacksonMvcIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void GET输出ISO字符串() throws Exception {
        mockMvc.perform(get("/time"))
                .andExpect(status().isOk())
                .andExpect(content().json(
                        "{\"instant\":\"2026-10-28T12:43:32Z\","
                                + "\"offset\":\"2026-10-28T20:43:32+08:00\","
                                + "\"local\":\"2026-10-14T14:26:13\"}"));
    }

    @Test
    void POST接收ISO输入() throws Exception {
        mockMvc.perform(post("/time")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"instant\":\"2026-10-28T12:43:32Z\","
                                + "\"offset\":\"2026-10-28T20:43:32+08:00\","
                                + "\"local\":\"2026-10-14T14:26:13\"}"))
                .andExpect(status().isOk())
                .andExpect(content().json(
                        "{\"instant\":\"2026-10-28T12:43:32Z\","
                                + "\"offset\":\"2026-10-28T20:43:32+08:00\","
                                + "\"local\":\"2026-10-14T14:26:13\"}"));
    }

    @Test
    void POST兼容历史13位毫秒输入() throws Exception {
        Instant instant = Instant.parse("2026-10-28T12:43:32Z");
        mockMvc.perform(post("/time")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"instant\":" + instant.toEpochMilli() + ","
                                + "\"offset\":" + instant.toEpochMilli() + ","
                                + "\"local\":" + instant.toEpochMilli() + "}"))
                .andExpect(status().isOk())
                .andExpect(content().json(
                        "{\"instant\":\"2026-10-28T12:43:32Z\","
                                + "\"offset\":\"2026-10-28T20:43:32+08:00\","
                                + "\"local\":\"2026-10-28T20:43:32\"}"));
    }

    @Test
    void POST非法时间输入被拒绝() throws Exception {
        // 拒绝语义由框架 codec 保证（不静默纠正）；HTTP 状态遵循框架既有 GlobalExceptionHandler 策略（所有异常 → 500）
        mockMvc.perform(post("/time")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"instant\":\"not-a-date\","
                                + "\"offset\":\"2026-10-28T20:43:32+08:00\","
                                + "\"local\":\"2026-10-14T14:26:13\"}"))
                .andExpect(status().is5xxServerError());
    }

    @Configuration
    @EnableAutoConfiguration
    @org.springframework.context.annotation.Import(JacksonMvcIntegrationTest.TimeController.class)
    static class TestApplication {
    }

    @RestController
    static class TimeController {

        @GetMapping("/time")
        public TimeDto get() {
            return new TimeDto(Instant.parse("2026-10-28T12:43:32Z"),
                    OffsetDateTime.parse("2026-10-28T20:43:32+08:00"),
                    LocalDateTime.parse("2026-10-14T14:26:13"));
        }

        @PostMapping("/time")
        public TimeDto echo(@RequestBody TimeDto dto) {
            return dto;
        }
    }

    static class TimeDto {
        public Instant instant;
        public OffsetDateTime offset;
        public LocalDateTime local;

        TimeDto() {
        }

        TimeDto(Instant instant, OffsetDateTime offset, LocalDateTime local) {
            this.instant = instant;
            this.offset = offset;
            this.local = local;
        }
    }
}
