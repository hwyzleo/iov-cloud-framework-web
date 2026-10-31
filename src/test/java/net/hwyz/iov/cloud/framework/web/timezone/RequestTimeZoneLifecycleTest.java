package net.hwyz.iov.cloud.framework.web.timezone;

import net.hwyz.iov.cloud.framework.common.time.RequestTimeZoneContext;
import net.hwyz.iov.cloud.framework.common.time.Snapshot;
import net.hwyz.iov.cloud.framework.common.time.Source;
import net.hwyz.iov.cloud.framework.common.time.FallbackReason;
import net.hwyz.iov.cloud.framework.web.properties.IovRequestTimeZoneProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.ZoneId;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 拦截器+Filter 生命周期与异步传播（FW-WEB-DSN-CR-002，TC-TZ-03/09）。
 */
class RequestTimeZoneLifecycleTest {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    @AfterEach
    void cleanup() {
        RequestTimeZoneContext.clear();
        MDC.clear();
    }

    private RequestTimeZoneInterceptor interceptor() {
        IovRequestTimeZoneProperties p = new IovRequestTimeZoneProperties();
        p.setDefaultZoneId(SHANGHAI);
        ObjectProvider<AuthenticatedUserTimeZoneResolver> up =
                new org.springframework.beans.factory.support.DefaultListableBeanFactory()
                        .getBeanProvider(AuthenticatedUserTimeZoneResolver.class);
        ObjectProvider<java.util.List<RequestTimeZoneFallbackResolver>> fp = fixedProvider(java.util.List.of());
        RequestTimeZoneSnapshotResolver resolver = new RequestTimeZoneSnapshotResolver(p, up, fp);
        return new RequestTimeZoneInterceptor(resolver);
    }

    private static <T> ObjectProvider<T> fixedProvider(T value) {
        return new ObjectProvider<T>() {
            @Override
            public T getObject() {
                return value;
            }

            @Override
            public T getObject(Object... args) {
                return value;
            }

            @Override
            public T getIfAvailable() {
                return value;
            }

            @Override
            public T getIfUnique() {
                return value;
            }
        };
    }

    @Test
    void 请求后上下文与MDC清理并恢复() throws Exception {
        RequestTimeZoneCleanupFilter filter = new RequestTimeZoneCleanupFilter();
        RequestTimeZoneInterceptor interceptor = interceptor();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestTimeZoneHeaders.TIME_ZONE, "Europe/Berlin");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MDC.put("existing", "keep");

        filter.doFilter(request, response, (req, res) -> {
            interceptor.preHandle(request, response, new Object());
            assertThat(RequestTimeZoneContext.current()).isEqualTo(BERLIN);
            assertThat(MDC.get(RequestTimeZoneHeaders.MDC_ZONE)).isEqualTo("Europe/Berlin");
            assertThat(MDC.get(RequestTimeZoneHeaders.MDC_SOURCE)).isEqualTo(Source.HEADER.name());
            interceptor.afterCompletion(request, response, new Object(), null);
        });

        assertThat(RequestTimeZoneContext.currentOptional()).isEmpty();
        assertThat(MDC.get(RequestTimeZoneHeaders.MDC_ZONE)).isNull();
        assertThat(MDC.get("existing")).isEqualTo("keep");
    }

    @Test
    void 异常路径finally清理不残留() throws Exception {
        RequestTimeZoneCleanupFilter filter = new RequestTimeZoneCleanupFilter();
        RequestTimeZoneInterceptor interceptor = interceptor();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestTimeZoneHeaders.TIME_ZONE, "Europe/Berlin");
        MockHttpServletResponse response = new MockHttpServletResponse();

        try {
            filter.doFilter(request, response, (req, res) -> {
                interceptor.preHandle(request, response, new Object());
                throw new IllegalStateException("模拟业务异常");
            });
        } catch (IllegalStateException expected) {
            // 预期异常
        }
        assertThat(RequestTimeZoneContext.currentOptional()).isEmpty();
        assertThat(MDC.get(RequestTimeZoneHeaders.MDC_ZONE)).isNull();
    }

    @Test
    void 入口清除线程残留避免串请求() throws Exception {
        // 模拟线程残留（上一请求泄漏）
        RequestTimeZoneContext.set(new Snapshot(BERLIN, Source.HEADER, FallbackReason.NONE));
        RequestTimeZoneCleanupFilter filter = new RequestTimeZoneCleanupFilter();
        RequestTimeZoneInterceptor interceptor = interceptor();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        // 无 header → 应解析为默认（上海），而不是读到残留柏林
        filter.doFilter(request, response, (req, res) -> {
            interceptor.preHandle(request, response, new Object());
            assertThat(RequestTimeZoneContext.current()).isEqualTo(SHANGHAI);
            interceptor.afterCompletion(request, response, new Object(), null);
        });
        assertThat(RequestTimeZoneContext.currentOptional()).isEmpty();
    }

    @Test
    void 重派发复用快照不重复解析() throws Exception {
        RequestTimeZoneInterceptor interceptor = interceptor();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestTimeZoneHeaders.TIME_ZONE, "Europe/Berlin");

        interceptor.preHandle(request, new MockHttpServletResponse(), new Object());
        assertThat(RequestTimeZoneContext.current()).isEqualTo(BERLIN);

        // 模拟 redispatch：header 已变化（用户状态已变），但快照必须复用首次解析结果
        request.removeHeader(RequestTimeZoneHeaders.TIME_ZONE);
        request.addHeader(RequestTimeZoneHeaders.TIME_ZONE, "Asia/Tokyo");
        interceptor.preHandle(request, new MockHttpServletResponse(), new Object());
        assertThat(RequestTimeZoneContext.current()).isEqualTo(BERLIN);

        interceptor.afterCompletion(request, new MockHttpServletResponse(), new Object(), null);
        assertThat(RequestTimeZoneContext.currentOptional()).isEmpty();
    }

    @Test
    void 无快照任务不继承工作线程残留() throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            // 先在工作线程留下残留绑定
            pool.submit(() -> RequestTimeZoneContext.set(
                    new Snapshot(BERLIN, Source.HEADER, FallbackReason.NONE))).get(5, TimeUnit.SECONDS);

            AtomicReference<Optional<Snapshot>> during = new AtomicReference<>();
            AtomicReference<Optional<Snapshot>> after = new AtomicReference<>();
            pool.submit(IovRequestTimeZonePropagator.wrap(() -> {
                during.set(RequestTimeZoneContext.capture());
                // 模拟任务体
            })).get(5, TimeUnit.SECONDS);
            pool.submit(() -> after.set(RequestTimeZoneContext.capture())).get(5, TimeUnit.SECONDS);

            // 无快照 wrapper：任务期内不得看到工作线程残留；任务后恢复（仍残留原值）
            assertThat(during.get()).isEmpty();
            assertThat(after.get().map(Snapshot::zoneId)).contains(BERLIN);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void 有快照任务工作线程绑定并finally恢复() throws Exception {
        RequestTimeZoneContext.set(new Snapshot(SHANGHAI, Source.HEADER, FallbackReason.NONE));
        AtomicReference<ZoneId> workerZone = new AtomicReference<>();
        AtomicReference<Optional<Snapshot>> afterWorker = new AtomicReference<>();
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            pool.submit(IovRequestTimeZonePropagator.wrap(() -> {
                workerZone.set(RequestTimeZoneContext.current());
            })).get(5, TimeUnit.SECONDS);
            pool.submit(() -> afterWorker.set(RequestTimeZoneContext.capture())).get(5, TimeUnit.SECONDS);
            assertThat(workerZone.get()).isEqualTo(SHANGHAI);
            assertThat(afterWorker.get()).isEmpty();
        } finally {
            pool.shutdownNow();
        }
        assertThat(RequestTimeZoneContext.current()).isEqualTo(SHANGHAI);
    }

    @Test
    void 任务异常时wrapper仍恢复上下文() throws Exception {
        RequestTimeZoneContext.set(new Snapshot(SHANGHAI, Source.HEADER, FallbackReason.NONE));
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            AtomicReference<Throwable> error = new AtomicReference<>();
            pool.submit(IovRequestTimeZonePropagator.wrap(() -> {
                throw new IllegalStateException("任务异常");
            })).get(5, TimeUnit.SECONDS);
        } catch (java.util.concurrent.ExecutionException expected) {
            // 预期
        } finally {
            pool.shutdownNow();
        }
        assertThat(RequestTimeZoneContext.current()).isEqualTo(SHANGHAI);
    }

    @Test
    void callable包装异常时恢复() throws Exception {
        RequestTimeZoneContext.set(new Snapshot(SHANGHAI, Source.HEADER, FallbackReason.NONE));
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            pool.submit(IovRequestTimeZonePropagator.wrap(() -> {
                throw new IllegalStateException("callable 异常");
            })).get(5, TimeUnit.SECONDS);
        } catch (java.util.concurrent.ExecutionException expected) {
            // 预期
        } finally {
            pool.shutdownNow();
        }
        assertThat(RequestTimeZoneContext.current()).isEqualTo(SHANGHAI);
    }
}
