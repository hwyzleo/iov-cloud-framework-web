package net.hwyz.iov.cloud.framework.web.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import com.fasterxml.jackson.databind.SerializationFeature;
import net.hwyz.iov.cloud.framework.web.jackson.IovEpochMillisWriteModule;
import net.hwyz.iov.cloud.framework.web.jackson.IovIsoWriteModule;
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
 *   <li>iso（默认）：注册 {@link IovIsoWriteModule}，四类型统一输出 ISO-8601（固定毫秒 + `Z`），
 *       不依赖 Spring Boot 默认日期序列化；</li>
 *   <li>epoch-millis（兼容模式）：追加 epoch 毫秒写出模块（含 {@code java.util.Date}）；</li>
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
                } else {
                    // FW-WEB-DSN-CR-003：ISO 模式下四类型（含 java.util.Date）统一由框架写出，
                    // 不再依赖 JavaTimeModule 默认行为与 Spring Boot StdDateFormat。
                    objectMapper.registerModule(new IovIsoWriteModule(properties.getZoneId()));
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
