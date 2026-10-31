package net.hwyz.iov.cloud.framework.web.timezone.feign;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import net.hwyz.iov.cloud.framework.common.time.RequestTimeZoneContext;
import net.hwyz.iov.cloud.framework.common.time.Snapshot;
import net.hwyz.iov.cloud.framework.web.properties.IovRequestTimeZoneProperties;
import net.hwyz.iov.cloud.framework.web.timezone.RequestTimeZoneHeaders;
import net.hwyz.iov.cloud.framework.web.timezone.RequestTimeZoneParser;

import java.util.List;
import java.util.Optional;

/**
 * Feign 请求时区透传拦截器（FW-WEB-DSN-CR-002，RD-TZ-006）。
 * <p>
 * <ul>
 *   <li>仅当当前线程已绑定快照时才注入 header（未绑定不注入，保留后台显式调用契约；
 *       不复制原始 HttpServletRequest 字符串）；</li>
 *   <li>目标隔离：默认不向任何 Feign 目标透传（外部/未登记目标不注入用户时区）；
 *       命中 {@code iov.web.timezone.internal-feign-targets} 内部白名单（client 名或
 *       host/URL 子串）且未命中 {@code feign-excluded-targets} 补充排除列表才透传；</li>
 *   <li>先移除模板同名旧 header，再写唯一规范化值（不追加多个值）；</li>
 *   <li>接收方（B）仍执行自己的信任策略（B trust-header=false 时忽略传值）。</li>
 * </ul>
 * 本拦截器由自动配置按 {@code feign.RequestInterceptor} classpath 与
 * {@code feign-propagation-enabled} 条件装配；异步/隔离线程须使用捕获绑定适配
 * （{@code IovRequestTimeZonePropagator.wrap} 或显式 Scope）。
 *
 * @author hwyz_leo
 * @since 2026-10-31
 */
public class IovRequestTimeZoneFeignRequestInterceptor implements RequestInterceptor {

    private final IovRequestTimeZoneProperties properties;

    public IovRequestTimeZoneFeignRequestInterceptor(IovRequestTimeZoneProperties properties) {
        this.properties = properties;
    }

    @Override
    public void apply(RequestTemplate template) {
        Optional<Snapshot> snapshot = RequestTimeZoneContext.capture();
        if (snapshot.isEmpty()) {
            // 未绑定快照不注入（后台显式调用契约，不把残留线程值当依据）
            return;
        }
        if (!isTargetAllowed(template)) {
            return;
        }
        // 先移除同名旧 header，再写唯一值
        template.removeHeader(RequestTimeZoneHeaders.TIME_ZONE);
        template.header(RequestTimeZoneHeaders.TIME_ZONE, RequestTimeZoneParser.normalize(snapshot.get().zoneId()));
    }

    private boolean isTargetAllowed(RequestTemplate template) {
        List<String> candidates = targetCandidates(template);
        List<String> internal = properties.getInternalFeignTargets();
        // 内部白名单为空 → 默认不向任何目标透传（第三方/未登记目标不注入）
        if (internal == null || internal.isEmpty()) {
            return false;
        }
        boolean whitelisted = internal.stream()
                .anyMatch(pattern -> candidates.stream().anyMatch(candidate -> matches(pattern, candidate)));
        if (!whitelisted) {
            return false;
        }
        List<String> excluded = properties.getFeignExcludedTargets();
        if (excluded != null) {
            boolean excludedHit = excluded.stream()
                    .anyMatch(pattern -> candidates.stream().anyMatch(candidate -> matches(pattern, candidate)));
            if (excludedHit) {
                return false;
            }
        }
        return true;
    }

    private static List<String> targetCandidates(RequestTemplate template) {
        List<String> candidates = new java.util.ArrayList<>();
        feign.Target<?> target = template.feignTarget();
        if (target != null && target.name() != null && !target.name().isBlank()) {
            candidates.add(target.name());
        }
        String url = template.url();
        if (url != null && !url.isBlank()) {
            candidates.add(url);
        }
        return candidates;
    }

    private static boolean matches(String pattern, String target) {
        if (pattern == null || pattern.isBlank()) {
            return false;
        }
        String p = pattern.trim();
        return p.equalsIgnoreCase(target) || target.contains(p) || p.contains(target);
    }
}
