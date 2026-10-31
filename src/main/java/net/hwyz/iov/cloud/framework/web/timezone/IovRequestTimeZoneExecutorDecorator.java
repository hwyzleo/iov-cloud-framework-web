package net.hwyz.iov.cloud.framework.web.timezone;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.lang.reflect.Field;

/**
 * applicationTaskExecutor 装饰器（FW-WEB-DSN-CR-002，RD-TZ-004）。
 * <p>
 * Boot 的 WebMvcAutoConfiguration 将 {@code applicationTaskExecutor} 注入为 MVC 异步
 * Callable 执行器（已验证 3.0.2 字节码）。本后处理器按 bean 名只装饰该执行器：
 * <ul>
 *   <li>不替换执行器（不调用 setTaskExecutor、不覆盖服务已有的 MVC 执行器）；</li>
 *   <li>在 {@code postProcessBeforeInitialization}（属性设置后、afterPropertiesSet 前）
 *       应用装饰器：ThreadPoolTaskExecutor 在 initializeExecutor 时固化 taskDecorator，
 *       之后设置无效；通过反射读取既有值，仅为空时应用，不覆盖服务已有装饰器；</li>
 *   <li>注意：默认共享执行器同时被 @Async 使用，开启后 Callable 与 @Async 均随
 *       提交线程快照传播；如不希望 @Async 继承，请使用独立执行器或显式 wrap。</li>
 * </ul>
 *
 * @author hwyz_leo
 * @since 2026-10-31
 */
@Slf4j
public class IovRequestTimeZoneExecutorDecorator implements BeanPostProcessor {

    public static final String APPLICATION_TASK_EXECUTOR_BEAN_NAME = "applicationTaskExecutor";

    private final TaskDecorator decorator;

    public IovRequestTimeZoneExecutorDecorator(TaskDecorator decorator) {
        this.decorator = decorator;
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        if (APPLICATION_TASK_EXECUTOR_BEAN_NAME.equals(beanName) && bean instanceof ThreadPoolTaskExecutor executor) {
            if (readTaskDecorator(executor) == null) {
                executor.setTaskDecorator(decorator);
            }
        }
        return bean;
    }

    private TaskDecorator readTaskDecorator(ThreadPoolTaskExecutor executor) {
        try {
            Field field = ThreadPoolTaskExecutor.class.getDeclaredField("taskDecorator");
            field.setAccessible(true);
            return (TaskDecorator) field.get(executor);
        } catch (ReflectiveOperationException e) {
            log.warn("无法读取 ThreadPoolTaskExecutor.taskDecorator 字段，跳过请求时区装饰器应用（不覆盖既有装饰器）");
            return null;
        }
    }
}
