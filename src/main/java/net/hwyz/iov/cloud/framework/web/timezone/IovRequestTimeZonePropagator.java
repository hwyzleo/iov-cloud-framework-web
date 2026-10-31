package net.hwyz.iov.cloud.framework.web.timezone;

import net.hwyz.iov.cloud.framework.common.time.RequestTimeZoneContext;
import net.hwyz.iov.cloud.framework.common.time.Snapshot;

import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * 请求时区异步任务传播工具（FW-WEB-DSN-CR-002，RD-TZ-004）。
 * <p>
 * DeferredResult/CompletableFuture/@Async/自建线程池不自动继承请求时区上下文；
 * 提交者必须使用 {@link #wrap(Runnable)}/{@link #wrap(Callable)} 或显式
 * {@link RequestTimeZoneContext.Scope}：
 * <ul>
 *   <li>提交时捕获不可变快照，工作线程 {@code open(snapshot)}，finally 恢复之前值；</li>
 *   <li>无快照任务也须明确清除任务期绑定并 finally 恢复，不能沿用工作线程残留；</li>
 *   <li>异步导出应在生成任务时捕获 zone（本工具即该模式），不能执行时读任意工作线程。</li>
 * </ul>
 *
 * @author hwyz_leo
 * @since 2026-10-31
 */
public final class IovRequestTimeZonePropagator {

    private IovRequestTimeZonePropagator() {
    }

    /**
     * 包装任务：提交时捕获当前线程快照，工作线程绑定并在 finally 恢复。
     */
    public static Runnable wrap(Runnable task) {
        Optional<Snapshot> snapshot = RequestTimeZoneContext.capture();
        return () -> {
            if (snapshot.isPresent()) {
                try (RequestTimeZoneContext.Scope scope = RequestTimeZoneContext.open(snapshot.get())) {
                    task.run();
                }
            } else {
                runWithoutSnapshot(task);
            }
        };
    }

    /**
     * 包装带返回值的任务：提交时捕获当前线程快照，工作线程绑定并在 finally 恢复。
     */
    public static <T> Callable<T> wrap(Callable<T> task) {
        Optional<Snapshot> snapshot = RequestTimeZoneContext.capture();
        return () -> {
            if (snapshot.isPresent()) {
                try (RequestTimeZoneContext.Scope scope = RequestTimeZoneContext.open(snapshot.get())) {
                    return task.call();
                }
            } else {
                return callWithoutSnapshot(task);
            }
        };
    }

    private static void runWithoutSnapshot(Runnable task) {
        Optional<Snapshot> previous = RequestTimeZoneContext.capture();
        RequestTimeZoneContext.clear();
        try {
            task.run();
        } finally {
            previous.ifPresentOrElse(RequestTimeZoneContext::set, RequestTimeZoneContext::clear);
        }
    }

    private static <T> T callWithoutSnapshot(Callable<T> task) throws Exception {
        Optional<Snapshot> previous = RequestTimeZoneContext.capture();
        RequestTimeZoneContext.clear();
        try {
            return task.call();
        } finally {
            previous.ifPresentOrElse(RequestTimeZoneContext::set, RequestTimeZoneContext::clear);
        }
    }
}
