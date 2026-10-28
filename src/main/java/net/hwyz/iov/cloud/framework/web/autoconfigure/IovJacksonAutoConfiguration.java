package net.hwyz.iov.cloud.framework.web.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import com.fasterxml.jackson.databind.SerializationFeature;
import net.hwyz.iov.cloud.framework.web.jackson.IovEpochMillisWriteModule;
import net.hwyz.iov.cloud.framework.web.jackson.IovJavaTimeReadModule;
import net.hwyz.iov.cloud.framework.web.jackson.JacksonProperties;
import net.hwyz.iov.cloud.framework.web.jackson.JacksonStartupProbe;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/**
 * iov.web.jackson 自动配置（FW-WEB-DSN-CR-001）。
 * <p>
 * 职责（对应 CR §5）：
 * <ul>
 *   <li>消除宽泛 {@code @ConditionalOnMissingBean(Jackson2ObjectMapperBuilderCustomizer.class)}
 *       对框架策略的误抑制：改为按框架自有 bean 名 {@code iovWebJacksonCustomizer} 让位，
 *       无关 customizer 不再抑制框架策略；</li>
 *   <li>模块经 postConfigurer 在 ObjectMapper 构建后注册，保留 JavaTimeModule 自动发现
 *       与默认能力（不受 modulesToInstall 替换语义影响，后注册覆盖同类型 serializer）；</li>
 *   <li>epoch-millis（默认）：追加 epoch 毫秒写出模块 + 双读模块，复现存量输出；</li>
 *   <li>iso：不注册显式 epoch 序列化器、关闭 WRITE_DATES_AS_TIMESTAMPS，JavaTimeModule 输出 ISO；</li>
 *   <li>保留空字符串反序列化为空集合的既有行为；</li>
 *   <li>启动探针校验最终生效 mapper，失败阻断发布。</li>
 * </ul>
 *
 * @author hwyz_leo
 * @since 2026-10-28
 */
@AutoConfiguration
@EnableConfigurationProperties(JacksonProperties.class)
public class IovJacksonAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "iovWebJacksonCustomizer")
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public Jackson2ObjectMapperBuilderCustomizer iovWebJacksonCustomizer(JacksonProperties properties) {
        return builder -> {
            if (properties.getDateFormat() == JacksonProperties.DateFormat.ISO) {
                builder.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            }
            // 模块经 postConfigurer 在 build 后注册：不受 Jackson2ObjectMapperBuilder.modulesToInstall
            // 替换语义影响（Boot Standard customizer 会以空 modulesToInstall 覆盖追加结果），
            // 且不抑制 well-known 自动发现（JavaTimeModule 等仍注册）；
            // 后注册的 serializer/deserializer 覆盖 JavaTimeModule 同类型（写单一、读兼容）。
            builder.postConfigurer(objectMapper -> {
                objectMapper.registerModule(new IovJavaTimeReadModule(properties.getZoneId()));
                if (properties.getDateFormat() == JacksonProperties.DateFormat.EPOCH_MILLIS) {
                    objectMapper.registerModule(new IovEpochMillisWriteModule(properties.getZoneId()));
                }
                // 保留：空字符串反序列化为空集合（既有行为）
                objectMapper.coercionConfigFor(LogicalType.Collection)
                        .setCoercion(CoercionInputShape.EmptyString, CoercionAction.AsEmpty);
            });
        };
    }

    @Bean
    @ConditionalOnMissingBean(name = "iovWebJacksonStartupProbe")
    public JacksonStartupProbe iovWebJacksonStartupProbe(ObjectMapper objectMapper, JacksonProperties properties) {
        return new JacksonStartupProbe(objectMapper, properties);
    }
}
