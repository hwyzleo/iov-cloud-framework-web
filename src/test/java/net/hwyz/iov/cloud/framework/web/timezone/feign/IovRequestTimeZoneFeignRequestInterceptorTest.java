package net.hwyz.iov.cloud.framework.web.timezone.feign;

import feign.Request;
import feign.RequestTemplate;
import feign.Target;
import net.hwyz.iov.cloud.framework.common.time.FallbackReason;
import net.hwyz.iov.cloud.framework.common.time.RequestTimeZoneContext;
import net.hwyz.iov.cloud.framework.common.time.Snapshot;
import net.hwyz.iov.cloud.framework.common.time.Source;
import net.hwyz.iov.cloud.framework.web.properties.IovRequestTimeZoneProperties;
import net.hwyz.iov.cloud.framework.web.timezone.RequestTimeZoneHeaders;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Feign 透传拦截器：内部白名单/排除/未绑定/唯一值（FW-WEB-DSN-CR-002，TC-TZ-06 单测部分）。
 */
class IovRequestTimeZoneFeignRequestInterceptorTest {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    @AfterEach
    void cleanup() {
        RequestTimeZoneContext.clear();
    }

    private IovRequestTimeZoneFeignRequestInterceptor interceptor(List<String> internal, List<String> excluded) {
        IovRequestTimeZoneProperties p = new IovRequestTimeZoneProperties();
        p.setInternalFeignTargets(internal);
        p.setFeignExcludedTargets(excluded);
        return new IovRequestTimeZoneFeignRequestInterceptor(p);
    }

    private RequestTemplate template(String clientName, String url) {
        RequestTemplate template = new RequestTemplate()
                .method(Request.HttpMethod.GET)
                .target(url);
        template.feignTarget(new Target.HardCodedTarget<>(Object.class, clientName, url));
        return template;
    }

    private void bindZone() {
        RequestTimeZoneContext.set(new Snapshot(SHANGHAI, Source.HEADER, FallbackReason.NONE));
    }

    private Map<String, Collection<String>> apply(IovRequestTimeZoneFeignRequestInterceptor interceptor,
                                                  RequestTemplate template) {
        interceptor.apply(template);
        return template.headers();
    }

    @Test
    void 内部白名单命中时注入唯一值() {
        bindZone();
        RequestTemplate template = template("svc-a", "http://localhost:8080/api");
        Map<String, Collection<String>> headers = apply(interceptor(List.of("svc-a"), List.of()), template);
        Collection<String> values = headers.get(RequestTimeZoneHeaders.TIME_ZONE);
        assertThat(values).containsExactly("Asia/Shanghai");
    }

    @Test
    void 白名单为空默认不向任何目标注入() {
        bindZone();
        RequestTemplate template = template("svc-a", "http://localhost:8080/api");
        Map<String, Collection<String>> headers = apply(interceptor(List.of(), List.of()), template);
        assertThat(headers.get(RequestTimeZoneHeaders.TIME_ZONE)).isNull();
    }

    @Test
    void 未登记第三方目标不注入() {
        bindZone();
        RequestTemplate template = template("external-third-party", "https://ext.example.com/api");
        Map<String, Collection<String>> headers = apply(interceptor(List.of("svc-a"), List.of()), template);
        assertThat(headers.get(RequestTimeZoneHeaders.TIME_ZONE)).isNull();
    }

    @Test
    void 排除列表覆盖白名单() {
        bindZone();
        RequestTemplate template = template("svc-a", "http://localhost:8080/api");
        Map<String, Collection<String>> headers =
                apply(interceptor(List.of("svc-a"), List.of("svc-a")), template);
        assertThat(headers.get(RequestTimeZoneHeaders.TIME_ZONE)).isNull();
    }

    @Test
    void 未绑定快照不注入保留后台契约() {
        RequestTemplate template = template("svc-a", "http://localhost:8080/api");
        Map<String, Collection<String>> headers = apply(interceptor(List.of("svc-a"), List.of()), template);
        assertThat(headers.get(RequestTimeZoneHeaders.TIME_ZONE)).isNull();
    }

    @Test
    void 覆盖同名旧值且不追加多个值() {
        bindZone();
        RequestTemplate template = template("svc-a", "http://localhost:8080/api");
        template.header(RequestTimeZoneHeaders.TIME_ZONE, "Europe/Berlin");
        Map<String, Collection<String>> headers = apply(interceptor(List.of("svc-a"), List.of()), template);
        assertThat(headers.get(RequestTimeZoneHeaders.TIME_ZONE)).containsExactly("Asia/Shanghai");
    }

    @Test
    void URL子串匹配与host匹配() {
        bindZone();
        RequestTemplate template = template("some-client", "http://internal-gw:8080/api");
        Map<String, Collection<String>> headers =
                apply(interceptor(List.of("internal-gw"), List.of()), template);
        assertThat(headers.get(RequestTimeZoneHeaders.TIME_ZONE)).containsExactly("Asia/Shanghai");
    }
}
