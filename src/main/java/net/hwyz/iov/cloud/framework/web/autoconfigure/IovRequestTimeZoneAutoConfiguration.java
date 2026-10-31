package net.hwyz.iov.cloud.framework.web.autoconfigure;

import net.hwyz.iov.cloud.framework.web.properties.IovRequestTimeZoneProperties;
import net.hwyz.iov.cloud.framework.web.timezone.AuthenticatedUserTimeZoneResolver;
import net.hwyz.iov.cloud.framework.web.timezone.DisplayTimeFacade;
import net.hwyz.iov.cloud.framework.web.timezone.IovRequestTimeZoneExecutorDecorator;
import net.hwyz.iov.cloud.framework.web.timezone.IovRequestTimeZoneTaskDecorator;
import net.hwyz.iov.cloud.framework.web.timezone.RequestTimeZoneCleanupFilter;
import net.hwyz.iov.cloud.framework.web.timezone.RequestTimeZoneFallbackResolver;
import net.hwyz.iov.cloud.framework.web.timezone.RequestTimeZoneInterceptor;
import net.hwyz.iov.cloud.framework.web.timezone.RequestTimeZoneSnapshotResolver;
import net.hwyz.iov.cloud.framework.web.timezone.feign.IovRequestTimeZoneFeignRequestInterceptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * 请求时区自动配置（FW-WEB-DSN-CR-002，T-TZ-02/T-TZ-03）。
 * <p>
 * <ul>
 *   <li>{@code iov.web.timezone.enabled=false} 时不注册解析/绑定/清理/透传链，
 *       仅保留无条件 {@link DisplayTimeFacade}（显式服务 fallback / default-zone-id 兜底），
 *       防止已迁移服务缺 bean 或裸调用 current() 失效；</li>
 *   <li>{@code feign-propagation-enabled} 可独立关闭；Feign 拦截器仅
 *       {@code feign.RequestInterceptor} classpath 存在时装配；</li>
 *   <li>MVC 异步适配经 {@link IovRequestTimeZoneExecutorDecorator} 按 bean 名装饰
 *       {@code applicationTaskExecutor}，不替换执行器、不覆盖服务已有装饰器。</li>
 * </ul>
 *
 * @author hwyz_leo
 * @since 2026-10-31
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(IovRequestTimeZoneProperties.class)
public class IovRequestTimeZoneAutoConfiguration {

    /**
     * 展示 facade：无条件注册（enabled=false 时仍提供兜底能力）。
     */
    @Bean
    @ConditionalOnMissingBean
    public DisplayTimeFacade displayTimeFacade(IovRequestTimeZoneProperties properties,
                                               ObjectProvider<List<RequestTimeZoneFallbackResolver>> fallbackResolverProvider) {
        return new DisplayTimeFacade(properties, fallbackResolverProvider);
    }

    /**
     * 时区解析链。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "iov.web.timezone", name = "enabled", havingValue = "true", matchIfMissing = true)
    public RequestTimeZoneSnapshotResolver requestTimeZoneSnapshotResolver(
            IovRequestTimeZoneProperties properties,
            ObjectProvider<AuthenticatedUserTimeZoneResolver> userResolverProvider,
            ObjectProvider<List<RequestTimeZoneFallbackResolver>> fallbackResolverProvider) {
        return new RequestTimeZoneSnapshotResolver(properties, userResolverProvider, fallbackResolverProvider);
    }

    /**
     * 时区拦截器（MVC）。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "iov.web.timezone", name = "enabled", havingValue = "true", matchIfMissing = true)
    public RequestTimeZoneInterceptor requestTimeZoneInterceptor(RequestTimeZoneSnapshotResolver resolver) {
        return new RequestTimeZoneInterceptor(resolver);
    }

    /**
     * 最外层生命周期清理 Filter。
     */
    @Bean
    @ConditionalOnProperty(prefix = "iov.web.timezone", name = "enabled", havingValue = "true", matchIfMissing = true)
    public FilterRegistrationBean<RequestTimeZoneCleanupFilter> requestTimeZoneCleanupFilter(
            IovRequestTimeZoneProperties properties) {
        FilterRegistrationBean<RequestTimeZoneCleanupFilter> bean =
                new FilterRegistrationBean<>(new RequestTimeZoneCleanupFilter());
        bean.setOrder(properties.getFilterOrder());
        bean.addUrlPatterns("/*");
        return bean;
    }

    /**
     * applicationTaskExecutor 装饰（MVC Callable 真实执行链；不替换执行器/装饰器）。
     */
    @Bean
    @ConditionalOnProperty(prefix = "iov.web.timezone", name = {"enabled", "async-propagation-enabled"},
            havingValue = "true", matchIfMissing = true)
    public static IovRequestTimeZoneExecutorDecorator iovRequestTimeZoneExecutorDecorator() {
        return new IovRequestTimeZoneExecutorDecorator(new IovRequestTimeZoneTaskDecorator());
    }

    /**
     * Feign 透传拦截器（仅 feign classpath 存在时装配）。
     */
    @Bean
    @ConditionalOnClass(name = "feign.RequestInterceptor")
    @ConditionalOnProperty(prefix = "iov.web.timezone", name = {"enabled", "feign-propagation-enabled"},
            havingValue = "true", matchIfMissing = true)
    public IovRequestTimeZoneFeignRequestInterceptor iovRequestTimeZoneFeignRequestInterceptor(
            IovRequestTimeZoneProperties properties) {
        return new IovRequestTimeZoneFeignRequestInterceptor(properties);
    }

    /**
     * MVC 拦截器注册（enabled=false 时不注册解析链）。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "iov.web.timezone", name = "enabled", havingValue = "true", matchIfMissing = true)
    public static class RequestTimeZoneWebMvcConfigurer implements WebMvcConfigurer {

        private final RequestTimeZoneInterceptor interceptor;
        private final IovRequestTimeZoneProperties properties;

        public RequestTimeZoneWebMvcConfigurer(RequestTimeZoneInterceptor interceptor,
                                               IovRequestTimeZoneProperties properties) {
            this.interceptor = interceptor;
            this.properties = properties;
        }

        @Override
        public void addInterceptors(InterceptorRegistry registry) {
            registry.addInterceptor(interceptor)
                    .addPathPatterns("/**")
                    .order(properties.getInterceptorOrder());
        }
    }
}
