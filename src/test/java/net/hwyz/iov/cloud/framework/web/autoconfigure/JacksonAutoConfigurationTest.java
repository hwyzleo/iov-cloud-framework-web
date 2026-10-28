package net.hwyz.iov.cloud.framework.web.autoconfigure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.JsonSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 自动配置集成测试（FW-WEB-DSN-CR-001，对应 CR §8 "真实 Boot 自动配置/ApplicationContext 集成"）。
 * <p>
 * 加载真实 Boot JacksonAutoConfiguration + 框架自动配置，注入生产 mapper 断言，
 * 覆盖：默认/iso 双模式、非法配置启动失败、探针阻断、无关 customizer 共存、
 * 冲突 serializer 阻断、模块回归、双读、空字符串→空集合。
 */
class JacksonAutoConfigurationTest {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final Instant SAMPLE_INSTANT = Instant.parse("2026-10-28T12:43:32Z");
    private static final OffsetDateTime SAMPLE_OFFSET = OffsetDateTime.parse("2026-10-28T20:43:32+08:00");
    private static final LocalDateTime SAMPLE_LOCAL = LocalDateTime.parse("2026-10-14T14:26:13");

    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration.class,
                        IovJacksonAutoConfiguration.class));
    }

    // ---------- 双模式 golden ----------

    @Test
    void epochMillis模式_配置zone_输出13位毫秒数字() {
        runner()
                .withPropertyValues("iov.web.jackson.zone-id=Asia/Shanghai")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    ObjectMapper mapper = context.getBean(ObjectMapper.class);
                    JsonNode node = mapper.valueToTree(new Sample(SAMPLE_INSTANT, SAMPLE_OFFSET, SAMPLE_LOCAL));
                    assertThat(node.get("instant").isIntegralNumber()).isTrue();
                    assertThat(node.get("instant").longValue()).isEqualTo(SAMPLE_INSTANT.toEpochMilli());
                    assertThat(node.get("offset").longValue()).isEqualTo(SAMPLE_OFFSET.toInstant().toEpochMilli());
                    assertThat(node.get("local").longValue())
                            .isEqualTo(SAMPLE_LOCAL.atZone(SHANGHAI).toInstant().toEpochMilli());
                });
    }

    @Test
    void iso模式_配置zone_输出ISO字符串() {
        runner()
                .withPropertyValues("iov.web.jackson.date-format=iso", "iov.web.jackson.zone-id=Asia/Shanghai")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    ObjectMapper mapper = context.getBean(ObjectMapper.class);
                    JsonNode node = mapper.valueToTree(new Sample(SAMPLE_INSTANT, SAMPLE_OFFSET, SAMPLE_LOCAL));
                    assertThat(node.get("instant").asText()).isEqualTo("2026-10-28T12:43:32Z");
                    assertThat(node.get("offset").asText()).isEqualTo("2026-10-28T20:43:32+08:00");
                    assertThat(node.get("local").asText()).isEqualTo("2026-10-14T14:26:13");
                });
    }

    @Test
    void iso模式_未配置zone_可启动_LocalDateTimeISO写出无需zone() {
        runner()
                .withPropertyValues("iov.web.jackson.date-format=iso")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    ObjectMapper mapper = context.getBean(ObjectMapper.class);
                    assertThat(mapper.writeValueAsString(SAMPLE_LOCAL)).isEqualTo("\"2026-10-14T14:26:13\"");
                });
    }

    // ---------- 配置校验（RD-001-1：非法值启动失败） ----------

    @Test
    void 非法date_format启动失败() {
        runner()
                .withPropertyValues("iov.web.jackson.date-format=banana")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void 非法zone_id启动失败() {
        runner()
                .withPropertyValues("iov.web.jackson.zone-id=Not/AZone")
                .run(context -> assertThat(context).hasFailed());
    }

    // ---------- 探针阻断（epoch-millis 未配置 zone = 存量时区未核验） ----------

    @Test
    void epochMillis模式_未配置zone_探针阻断启动() {
        runner().run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasMessageContaining("启动探针失败");
            assertThat(context.getStartupFailure()).hasMessageContaining("zone-id");
        });
    }

    // ---------- 无关 customizer 共存（不抑制框架策略，服务定制保留） ----------

    @Test
    void 无关customizer不抑制框架策略_服务定制保留() {
        runner()
                .withPropertyValues("iov.web.jackson.date-format=iso", "iov.web.jackson.zone-id=Asia/Shanghai")
                .withUserConfiguration(UnrelatedCustomizerConfig.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    ObjectMapper mapper = context.getBean(ObjectMapper.class);
                    // 框架策略仍生效：Instant 输出 ISO
                    JsonNode node = mapper.valueToTree(new Sample(SAMPLE_INSTANT, SAMPLE_OFFSET, SAMPLE_LOCAL));
                    assertThat(node.get("instant").asText()).isEqualTo("2026-10-28T12:43:32Z");
                    // 服务定制保留：自定义 String 序列化器（后注册覆盖）
                    assertThat(mapper.writeValueAsString("hello")).isEqualTo("\"SVC:hello\"");
                });
    }

    @org.springframework.context.annotation.Configuration
    static class UnrelatedCustomizerConfig {
        @org.springframework.context.annotation.Bean
        public Jackson2ObjectMapperBuilderCustomizer unrelatedCustomizer() {
            return builder -> builder.modulesToInstall(new SimpleModule("svc-string") {
                {
                    addSerializer(String.class, new com.fasterxml.jackson.databind.JsonSerializer<String>() {
                        @Override
                        public void serialize(String value,
                                com.fasterxml.jackson.core.JsonGenerator gen,
                                com.fasterxml.jackson.databind.SerializerProvider serializers) throws java.io.IOException {
                            gen.writeString("SVC:" + value);
                        }
                    });
                }
            });
        }
    }

    // ---------- 冲突时间 customizer：最终行为不符 → 探针阻断 ----------

    @Test
    void 冲突时间serializer_iso模式输出数字_探针阻断() {
        runner()
                .withPropertyValues("iov.web.jackson.date-format=iso", "iov.web.jackson.zone-id=Asia/Shanghai")
                .withUserConfiguration(ConflictingTimeCustomizerConfig.class)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasMessageContaining("启动探针失败");
                });
    }

    @org.springframework.context.annotation.Configuration
    static class ConflictingTimeCustomizerConfig {
        @org.springframework.context.annotation.Bean
        public Jackson2ObjectMapperBuilderCustomizer conflictingTimeCustomizer() {
            return builder -> builder.postConfigurer(objectMapper -> objectMapper
                    .registerModule(new SimpleModule("svc-epoch") {
                        {
                            // 与 iso 模式冲突：强制输出 epoch 毫秒数字
                            addSerializer(Instant.class, new JsonSerializer<Instant>() {
                                @Override
                                public void serialize(Instant value,
                                        com.fasterxml.jackson.core.JsonGenerator gen,
                                        com.fasterxml.jackson.databind.SerializerProvider serializers) throws java.io.IOException {
                                    gen.writeNumber(value.toEpochMilli());
                                }
                            });
                        }
                    }));
        }
    }

    // ---------- 模块回归：JavaTimeModule 默认能力保留 ----------

    @Test
    void modulesToInstall后JavaTimeModule默认能力保留() {
        runner()
                .withPropertyValues("iov.web.jackson.date-format=iso", "iov.web.jackson.zone-id=Asia/Shanghai")
                .run(context -> {
                    ObjectMapper mapper = context.getBean(ObjectMapper.class);
                    assertThat(mapper.writeValueAsString(LocalDate.of(2026, 10, 14))).isEqualTo("\"2026-10-14\"");
                    assertThat(mapper.readValue("\"2026-10-14\"", LocalDate.class)).isEqualTo(LocalDate.of(2026, 10, 14));
                });
    }

    // ---------- 双读（生产 mapper） ----------

    @Test
    void 生产mapper双读_10位秒_13位毫秒_ISO同一时刻() {
        runner()
                .withPropertyValues("iov.web.jackson.zone-id=Asia/Shanghai")
                .run(context -> {
                    ObjectMapper mapper = context.getBean(ObjectMapper.class);
                    long millis = SAMPLE_INSTANT.toEpochMilli();
                    long seconds = SAMPLE_INSTANT.getEpochSecond();
                    assertThat(mapper.readValue(String.valueOf(millis), Instant.class)).isEqualTo(SAMPLE_INSTANT);
                    assertThat(mapper.readValue(String.valueOf(seconds), Instant.class)).isEqualTo(SAMPLE_INSTANT);
                    assertThat(mapper.readValue("\"2026-10-28T12:43:32Z\"", Instant.class)).isEqualTo(SAMPLE_INSTANT);
                    assertThat(mapper.readValue("null", Instant.class)).isNull();
                });
    }

    // ---------- 空字符串 → 空集合（既有行为保留） ----------

    @Test
    void 空字符串反序列化为空集合() {
        runner()
                .withPropertyValues("iov.web.jackson.zone-id=Asia/Shanghai")
                .run(context -> {
                    ObjectMapper mapper = context.getBean(ObjectMapper.class);
                    assertThat(mapper.readValue("\"\"", java.util.List.class)).isEmpty();
                });
    }

    // ---------- 辅助 ----------

    static class Sample {
        public Instant instant;
        public OffsetDateTime offset;
        public LocalDateTime local;

        Sample() {
        }

        Sample(Instant instant, OffsetDateTime offset, LocalDateTime local) {
            this.instant = instant;
            this.offset = offset;
            this.local = local;
        }
    }
}
