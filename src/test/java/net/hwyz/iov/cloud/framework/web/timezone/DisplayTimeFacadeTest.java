package net.hwyz.iov.cloud.framework.web.timezone;

import net.hwyz.iov.cloud.framework.common.time.FallbackReason;
import net.hwyz.iov.cloud.framework.common.time.RequestTimeZoneContext;
import net.hwyz.iov.cloud.framework.common.time.Snapshot;
import net.hwyz.iov.cloud.framework.common.time.Source;
import net.hwyz.iov.cloud.framework.web.properties.IovRequestTimeZoneProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 展示 facade 兜底与 null 契约（FW-WEB-DSN-CR-002，RD-TZ-005/007）。
 */
class DisplayTimeFacadeTest {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final Instant SAMPLE = Instant.parse("2026-10-28T04:43:32Z");

    @AfterEach
    void cleanup() {
        RequestTimeZoneContext.clear();
    }

    private DisplayTimeFacade facade(IovRequestTimeZoneProperties p,
                                     List<RequestTimeZoneFallbackResolver> fallbacks) {
        return new DisplayTimeFacade(p, fixedProvider(fallbacks));
    }

    private static <T> org.springframework.beans.factory.ObjectProvider<T> fixedProvider(T value) {
        return new org.springframework.beans.factory.ObjectProvider<T>() {
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
    void 无绑定时兜底defaultZoneId不读JVM默认() {
        IovRequestTimeZoneProperties p = new IovRequestTimeZoneProperties();
        p.setDefaultZoneId(BERLIN);
        DisplayTimeFacade facade = facade(p, null);
        // enabled=false 等无绑定场景：facade 仍可调用，不抛错
        assertThat(facade.resolveEffectiveZone()).isEqualTo(BERLIN);
        assertThat(facade.format(SAMPLE)).isEqualTo("2026-10-28 05:43:32");
    }

    @Test
    void 绑定时使用快照时区() {
        RequestTimeZoneContext.set(new Snapshot(BERLIN, Source.HEADER, FallbackReason.NONE));
        IovRequestTimeZoneProperties p = new IovRequestTimeZoneProperties();
        p.setDefaultZoneId(SHANGHAI);
        DisplayTimeFacade facade = facade(p, null);
        assertThat(facade.resolveEffectiveZone()).isEqualTo(BERLIN);
        assertThat(facade.toLocalDateTime(SAMPLE))
                .isEqualTo(java.time.LocalDateTime.of(2026, 10, 28, 5, 43, 32));
    }

    @Test
    void 请求场景fallback优先于默认() {
        IovRequestTimeZoneProperties p = new IovRequestTimeZoneProperties();
        p.setDefaultZoneId(SHANGHAI);
        RequestTimeZoneFallbackResolver fallback = req -> Optional.of(BERLIN);
        DisplayTimeFacade facade = facade(p, List.of(fallback));
        org.springframework.mock.web.MockHttpServletRequest request =
                new org.springframework.mock.web.MockHttpServletRequest();
        assertThat(facade.resolveEffectiveZone(request)).isEqualTo(BERLIN);
    }

    @Test
    void 数据null透传且zone校验() {
        DisplayTimeFacade facade = facade(new IovRequestTimeZoneProperties(), null);
        assertThat(facade.toLocalDateTime(null)).isNull();
        assertThat(facade.format(null)).isNull();
        assertThat(facade.utcWallClockToLocal(null)).isNull();
        assertThatThrownBy(() -> facade.toLocalDateTime(SAMPLE, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void UTC墙钟转换与offset() {
        DisplayTimeFacade facade = facade(new IovRequestTimeZoneProperties(), null);
        java.time.LocalDateTime utcWall = java.time.LocalDateTime.of(2026, 10, 28, 4, 43, 32);
        assertThat(facade.utcWallClockToLocal(utcWall, ZoneOffset.ofHours(8)))
                .isEqualTo(java.time.LocalDateTime.of(2026, 10, 28, 12, 43, 32));
    }
}
