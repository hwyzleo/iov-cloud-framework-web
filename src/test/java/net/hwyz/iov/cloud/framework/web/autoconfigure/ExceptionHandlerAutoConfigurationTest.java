package net.hwyz.iov.cloud.framework.web.autoconfigure;

import net.hwyz.iov.cloud.framework.web.handler.GlobalExceptionHandler;
import net.hwyz.iov.cloud.framework.web.handler.ValidationExceptionHandler;
import net.hwyz.iov.cloud.framework.web.handler.WebRequestExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 异常处理器自动装配测试（FW-WEB-DSN-CR-004）。
 * <p>
 * 三个 advice 均为自动配置 bean（非组件扫描），漏注册会静默退化为框架兜底 500。
 * </p>
 *
 * @author hwyz_leo
 */
@DisplayName("FW-WEB-DSN-CR-004 异常处理器自动装配")
class ExceptionHandlerAutoConfigurationTest {

    @Test
    @DisplayName("业务异常、参数校验、请求层异常三个处理器均自动注册")
    void registersAllExceptionHandlers() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(WebAutoConfiguration.class);
            context.refresh();

            assertNotNull(context.getBean(GlobalExceptionHandler.class));
            assertNotNull(context.getBean(ValidationExceptionHandler.class));
            assertNotNull(context.getBean(WebRequestExceptionHandler.class));
        }
    }
}
