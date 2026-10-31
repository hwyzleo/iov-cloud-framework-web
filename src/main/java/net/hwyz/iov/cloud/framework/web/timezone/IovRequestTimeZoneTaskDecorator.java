package net.hwyz.iov.cloud.framework.web.timezone;

import org.springframework.core.task.TaskDecorator;

/**
 * MVC Callable 执行器 TaskDecorator（FW-WEB-DSN-CR-002，RD-TZ-004）。
 * <p>
 * 由 {@link IovRequestTimeZoneExecutorDecorator} 应用到 Boot 默认
 * {@code applicationTaskExecutor}（该执行器同时是 MVC 异步 Callable 执行器与默认
 * @Async 执行器）：提交时捕获快照，工作线程 open，finally 恢复；无快照任务清除
 * 任务期残留并恢复。不替换执行器，不覆盖服务已有装饰器。
 *
 * @author hwyz_leo
 * @since 2026-10-31
 */
public class IovRequestTimeZoneTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        return IovRequestTimeZonePropagator.wrap(runnable);
    }
}
