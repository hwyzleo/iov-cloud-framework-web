package net.hwyz.iov.cloud.framework.web.timezone;

import net.hwyz.iov.cloud.framework.common.time.FallbackReason;
import net.hwyz.iov.cloud.framework.common.time.Snapshot;
import net.hwyz.iov.cloud.framework.common.time.Source;
import net.hwyz.iov.cloud.framework.web.properties.IovRequestTimeZoneProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 请求时区解析优先级与信任策略（FW-WEB-DSN-CR-002，TC-TZ-02）。
 */
class RequestTimeZoneSnapshotResolverTest {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final ZoneId TOKYO = ZoneId.of("Asia/Tokyo");

    private IovRequestTimeZoneProperties defaultProperties() {
        IovRequestTimeZoneProperties p = new IovRequestTimeZoneProperties();
        p.setDefaultZoneId(SHANGHAI);
        return p;
    }

    private RequestTimeZoneSnapshotResolver resolver(IovRequestTimeZoneProperties p,
                                                     AuthenticatedUserTimeZoneResolver user,
                                                     List<RequestTimeZoneFallbackResolver> fallbacks) {
        DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
        if (user != null) {
            bf.registerSingleton("userResolver", user);
        }
        ObjectProvider<AuthenticatedUserTimeZoneResolver> up =
                bf.getBeanProvider(AuthenticatedUserTimeZoneResolver.class);
        ObjectProvider<List<RequestTimeZoneFallbackResolver>> fp = fixedProvider(fallbacks);
        return new RequestTimeZoneSnapshotResolver(p, up, fp);
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
    void header有效时采用HEADER来源() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestTimeZoneHeaders.TIME_ZONE, "Europe/Berlin");
        Snapshot s = resolver(defaultProperties(), null, null).resolve(request);
        assertThat(s.zoneId()).isEqualTo(BERLIN);
        assertThat(s.source()).isEqualTo(Source.HEADER);
        assertThat(s.fallbackReason()).isEqualTo(FallbackReason.NONE);
    }

    @Test
    void header存在但非法不回退到参数直接服务fallback或默认() {
        IovRequestTimeZoneProperties p = defaultProperties();
        p.setParamNames(List.of("timezone"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestTimeZoneHeaders.TIME_ZONE, "CST");
        request.setParameter("timezone", "Asia/Tokyo");
        Snapshot s = resolver(p, null, null).resolve(request);
        // 非法 header 不回退到参数
        assertThat(s.source()).isEqualTo(Source.DEFAULT);
        assertThat(s.zoneId()).isEqualTo(SHANGHAI);
        assertThat(s.fallbackReason()).isEqualTo(FallbackReason.INVALID_HEADER);
    }

    @Test
    void header多值回退() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestTimeZoneHeaders.TIME_ZONE, "Asia/Shanghai");
        request.addHeader(RequestTimeZoneHeaders.TIME_ZONE, "Europe/Berlin");
        Snapshot s = resolver(defaultProperties(), null, null).resolve(request);
        assertThat(s.source()).isEqualTo(Source.DEFAULT);
        assertThat(s.fallbackReason()).isEqualTo(FallbackReason.MULTIPLE_HEADER_VALUES);
    }

    @Test
    void header缺失时按paramNames顺序取参数() {
        IovRequestTimeZoneProperties p = defaultProperties();
        p.setParamNames(List.of("timezone"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("timezone", "Asia/Tokyo");
        Snapshot s = resolver(p, null, null).resolve(request);
        assertThat(s.zoneId()).isEqualTo(TOKYO);
        assertThat(s.source()).isEqualTo(Source.PARAM);
    }

    @Test
    void 参数歧义回退() {
        IovRequestTimeZoneProperties p = defaultProperties();
        p.setParamNames(List.of("tz1", "tz2"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("tz1", "Asia/Tokyo");
        request.setParameter("tz2", "Europe/Berlin");
        Snapshot s = resolver(p, null, null).resolve(request);
        assertThat(s.source()).isEqualTo(Source.DEFAULT);
        assertThat(s.fallbackReason()).isEqualTo(FallbackReason.AMBIGUOUS_PARAMS);

        // 单参数多值同样歧义
        IovRequestTimeZoneProperties p2 = defaultProperties();
        p2.setParamNames(List.of("timezone"));
        MockHttpServletRequest request2 = new MockHttpServletRequest();
        request2.addParameter("timezone", "Asia/Tokyo", "Europe/Berlin");
        Snapshot s2 = resolver(p2, null, null).resolve(request2);
        assertThat(s2.fallbackReason()).isEqualTo(FallbackReason.AMBIGUOUS_PARAMS);
    }

    @Test
    void 参数非法回退默认() {
        IovRequestTimeZoneProperties p = defaultProperties();
        p.setParamNames(List.of("timezone"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("timezone", "invalid-zone");
        Snapshot s = resolver(p, null, null).resolve(request);
        assertThat(s.source()).isEqualTo(Source.DEFAULT);
        assertThat(s.fallbackReason()).isEqualTo(FallbackReason.INVALID_PARAM);
    }

    @Test
    void trustHeader为false同时忽略header与参数() {
        IovRequestTimeZoneProperties p = defaultProperties();
        p.setTrustHeader(false);
        p.setParamNames(List.of("timezone"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestTimeZoneHeaders.TIME_ZONE, "Europe/Berlin");
        request.setParameter("timezone", "Asia/Tokyo");
        Snapshot s = resolver(p, null, null).resolve(request);
        assertThat(s.source()).isEqualTo(Source.DEFAULT);
        assertThat(s.zoneId()).isEqualTo(SHANGHAI);
        assertThat(s.fallbackReason()).isEqualTo(FallbackReason.MISSING_HEADER);
    }

    @Test
    void 用户SPI优先且认证与否由实现自检() {
        IovRequestTimeZoneProperties p = defaultProperties();
        p.setTrustHeader(false);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestTimeZoneHeaders.TIME_ZONE, "Europe/Berlin");
        // 模拟认证已完成：SPI 从认证上下文读取用户时区
        AuthenticatedUserTimeZoneResolver user = req -> Optional.of(TOKYO);
        Snapshot s = resolver(p, user, null).resolve(request);
        assertThat(s.zoneId()).isEqualTo(TOKYO);
        assertThat(s.source()).isEqualTo(Source.USER_PROFILE);

        // 未认证/无配置：SPI 返回 empty → 即使 trust=false 也继续候选 → 默认
        AuthenticatedUserTimeZoneResolver noUser = req -> Optional.empty();
        Snapshot s2 = resolver(p, noUser, null).resolve(request);
        assertThat(s2.source()).isEqualTo(Source.DEFAULT);
        assertThat(s2.zoneId()).isEqualTo(SHANGHAI);
    }

    @Test
    void 用户SPI异常继续候选不改变结果() {
        IovRequestTimeZoneProperties p = defaultProperties();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestTimeZoneHeaders.TIME_ZONE, "Europe/Berlin");
        AuthenticatedUserTimeZoneResolver broken = req -> {
            throw new IllegalStateException("模拟 SPI 异常");
        };
        Snapshot s = resolver(p, broken, null).resolve(request);
        assertThat(s.zoneId()).isEqualTo(BERLIN);
        assertThat(s.source()).isEqualTo(Source.HEADER);
    }

    @Test
    void 服务端点fallback位于默认之前且不是请求输入() {
        IovRequestTimeZoneProperties p = defaultProperties();
        p.setTrustHeader(false);
        RequestTimeZoneFallbackResolver excelFallback = req -> Optional.of(TOKYO);
        MockHttpServletRequest request = new MockHttpServletRequest();
        Snapshot s = resolver(p, null, List.of(excelFallback)).resolve(request);
        assertThat(s.zoneId()).isEqualTo(TOKYO);
        assertThat(s.source()).isEqualTo(Source.SERVICE_FALLBACK);
        assertThat(s.fallbackReason()).isEqualTo(FallbackReason.MISSING_HEADER);
    }
}
