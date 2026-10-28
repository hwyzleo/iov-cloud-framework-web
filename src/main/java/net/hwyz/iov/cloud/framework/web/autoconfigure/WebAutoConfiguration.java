package net.hwyz.iov.cloud.framework.web.autoconfigure;

import net.hwyz.iov.cloud.framework.web.filter.IdempotencyFilter;
import net.hwyz.iov.cloud.framework.web.filter.RateLimitFilter;
import net.hwyz.iov.cloud.framework.web.filter.TraceIdFilter;
import net.hwyz.iov.cloud.framework.web.filter.XssFilter;
import net.hwyz.iov.cloud.framework.web.handler.GlobalExceptionHandler;
import net.hwyz.iov.cloud.framework.web.handler.ValidationExceptionHandler;
import net.hwyz.iov.cloud.framework.web.interceptor.RequestLogInterceptor;
import net.hwyz.iov.cloud.framework.web.properties.WebFrameworkProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 自动配置类
 *
 * @author hwyz_leo
 */
@Configuration
@EnableConfigurationProperties(WebFrameworkProperties.class)
public class WebAutoConfiguration implements WebMvcConfigurer {

    @Bean
    @ConditionalOnMissingBean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }

    @Bean
    @ConditionalOnMissingBean
    public ValidationExceptionHandler validationExceptionHandler() {
        return new ValidationExceptionHandler();
    }

    @Bean
    @ConditionalOnMissingBean
    public RequestLogInterceptor requestLogInterceptor() {
        return new RequestLogInterceptor();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(requestLogInterceptor()).addPathPatterns("/**");
    }

    @Bean
    @ConditionalOnProperty(prefix = "framework.web.filter.trace-id", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public FilterRegistrationBean<TraceIdFilter> traceIdFilter(WebFrameworkProperties properties) {
        FilterRegistrationBean<TraceIdFilter> bean = new FilterRegistrationBean<>(new TraceIdFilter());
        bean.setOrder(properties.getFilter().getTraceId().getOrder());
        return bean;
    }

    @Bean
    @ConditionalOnProperty(prefix = "framework.web.filter.rate-limit", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilter(WebFrameworkProperties properties) {
        WebFrameworkProperties.RateLimitFilterProperties rateLimitProperties = properties.getFilter().getRateLimit();
        FilterRegistrationBean<RateLimitFilter> bean = new FilterRegistrationBean<>(
                new RateLimitFilter(rateLimitProperties.getAuthPatterns()));
        bean.setOrder(rateLimitProperties.getOrder());
        return bean;
    }

    @Bean
    @ConditionalOnProperty(prefix = "framework.web.filter.idempotency", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public FilterRegistrationBean<IdempotencyFilter> idempotencyFilter(WebFrameworkProperties properties) {
        WebFrameworkProperties.IdempotencyFilterProperties idempotencyProperties = properties.getFilter().getIdempotency();
        FilterRegistrationBean<IdempotencyFilter> bean = new FilterRegistrationBean<>(
                new IdempotencyFilter(idempotencyProperties.getTtlMillis()));
        bean.setOrder(idempotencyProperties.getOrder());
        return bean;
    }

    @Bean
    @ConditionalOnProperty(prefix = "framework.web.filter.xss", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public FilterRegistrationBean<XssFilter> xssFilter(WebFrameworkProperties properties) {
        FilterRegistrationBean<XssFilter> bean = new FilterRegistrationBean<>(new XssFilter());
        bean.setOrder(properties.getFilter().getXss().getOrder());
        bean.addUrlPatterns("/*");
        return bean;
    }

}
