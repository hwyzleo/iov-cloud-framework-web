package net.hwyz.iov.cloud.framework.web.timezone;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import net.hwyz.iov.cloud.framework.common.time.FallbackReason;
import net.hwyz.iov.cloud.framework.common.time.Snapshot;
import net.hwyz.iov.cloud.framework.common.time.Source;
import net.hwyz.iov.cloud.framework.web.properties.IovRequestTimeZoneProperties;
import org.springframework.beans.factory.ObjectProvider;

import java.time.ZoneId;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;

/**
 * 请求时区解析链（FW-WEB-DSN-CR-002，RD-TZ-003）。
 * <p>
 * 每次逻辑请求生成不可变 {@link Snapshot}；优先级：
 * {@code USER_PROFILE > HEADER > PARAM > SERVICE_FALLBACK > DEFAULT}。
 * <ul>
 *   <li>USER_PROFILE：认证用户 SPI（须认证完成后执行；无配置/异常继续候选）；</li>
 *   <li>HEADER：trust-header=true 时读 {@code X-Timezone}（大小写不敏感）；存在但
 *       非法/空/多值直接回退服务 fallback，不再尝试低优先级参数；</li>
 *   <li>PARAM：header 缺失且 param-names 非空时，按配置顺序选择首个出现项；
 *       多个候选/重复值视为歧义回退；只读 query/form 单值，不读 JSON body；</li>
 *   <li>SERVICE_FALLBACK：服务/端点显式兜底（仅迁移既有展示默认）；</li>
 *   <li>DEFAULT：框架 default-zone-id（仅展示/本地日历语义兜底）。</li>
 * </ul>
 * 非法客户端输入只记录低基数 {@link FallbackReason}，原始输入不落日志。
 *
 * @author hwyz_leo
 * @since 2026-10-31
 */
@Slf4j
public class RequestTimeZoneSnapshotResolver {

    private final IovRequestTimeZoneProperties properties;
    private final ObjectProvider<AuthenticatedUserTimeZoneResolver> userResolverProvider;
    private final ObjectProvider<List<RequestTimeZoneFallbackResolver>> fallbackResolverProvider;

    public RequestTimeZoneSnapshotResolver(IovRequestTimeZoneProperties properties,
                                           ObjectProvider<AuthenticatedUserTimeZoneResolver> userResolverProvider,
                                           ObjectProvider<List<RequestTimeZoneFallbackResolver>> fallbackResolverProvider) {
        this.properties = properties;
        this.userResolverProvider = userResolverProvider;
        this.fallbackResolverProvider = fallbackResolverProvider;
    }

    /**
     * 解析当前请求的有效时区快照（永不返回 null；客户端错误 fail-safe 回退）。
     */
    public Snapshot resolve(HttpServletRequest request) {
        // 1) USER_PROFILE（独立于 trust-header，最高优先级）
        Snapshot userProfile = resolveUserProfile(request);
        if (userProfile != null) {
            return userProfile;
        }
        // 2) HEADER
        if (properties.isTrustHeader()) {
            Enumeration<String> headers = request.getHeaders(RequestTimeZoneHeaders.TIME_ZONE);
            if (headers != null && headers.hasMoreElements()) {
                List<String> values = Collections.list(headers);
                if (values.size() > 1) {
                    return fallback(request, FallbackReason.MULTIPLE_HEADER_VALUES);
                }
                String raw = values.get(0);
                Optional<ZoneId> zone = RequestTimeZoneParser.parse(raw);
                if (zone.isPresent()) {
                    return new Snapshot(zone.get(), Source.HEADER, FallbackReason.NONE);
                }
                return fallback(request, raw == null || raw.isBlank()
                        ? FallbackReason.EMPTY_HEADER : FallbackReason.INVALID_HEADER);
            }
        }
        // 3) PARAM（header 缺失且允许参数时）
        if (properties.isTrustHeader() && properties.getParamNames() != null && !properties.getParamNames().isEmpty()) {
            List<String> present = properties.getParamNames().stream()
                    .filter(name -> request.getParameterMap().containsKey(name))
                    .toList();
            if (!present.isEmpty()) {
                if (present.size() > 1) {
                    return fallback(request, FallbackReason.AMBIGUOUS_PARAMS);
                }
                String[] values = request.getParameterValues(present.get(0));
                if (values == null || values.length == 0 || values.length > 1) {
                    return fallback(request, FallbackReason.AMBIGUOUS_PARAMS);
                }
                Optional<ZoneId> zone = RequestTimeZoneParser.parse(values[0]);
                if (zone.isPresent()) {
                    return new Snapshot(zone.get(), Source.PARAM, FallbackReason.NONE);
                }
                return fallback(request, FallbackReason.INVALID_PARAM);
            }
        }
        // 4/5) SERVICE_FALLBACK → DEFAULT（header 缺失；参数已尝试或无参数通道）
        boolean paramsEnabled = properties.isTrustHeader()
                && properties.getParamNames() != null && !properties.getParamNames().isEmpty();
        return fallback(request, paramsEnabled ? FallbackReason.MISSING_PARAM : FallbackReason.MISSING_HEADER);
    }

    private Snapshot resolveUserProfile(HttpServletRequest request) {
        AuthenticatedUserTimeZoneResolver resolver = userResolverProvider.getIfAvailable();
        if (resolver == null) {
            return null;
        }
        try {
            Optional<ZoneId> zone = resolver.resolveAuthenticatedUserTimeZone(request);
            if (zone.isPresent() && zone.get() != null) {
                return new Snapshot(zone.get(), Source.USER_PROFILE, FallbackReason.NONE);
            }
        } catch (Exception e) {
            // 低基数 reason，不改变认证结果；不落原始输入
            log.warn("认证用户时区 SPI 解析失败: source=USER_PROFILE, reason={}", FallbackReason.USER_PROFILE_ERROR);
        }
        return null;
    }

    private Snapshot fallback(HttpServletRequest request, FallbackReason reason) {
        List<RequestTimeZoneFallbackResolver> resolvers = fallbackResolverProvider.getIfAvailable();
        if (resolvers != null) {
            for (RequestTimeZoneFallbackResolver resolver : resolvers) {
                try {
                    Optional<ZoneId> zone = resolver.resolveFallbackZone(request);
                    if (zone.isPresent() && zone.get() != null) {
                        return new Snapshot(zone.get(), Source.SERVICE_FALLBACK, reason);
                    }
                } catch (Exception e) {
                    log.warn("服务/端点时区兜底解析失败: source=SERVICE_FALLBACK, reason={}",
                            FallbackReason.USER_PROFILE_ERROR);
                }
            }
        }
        return new Snapshot(properties.getDefaultZoneId(), Source.DEFAULT, reason);
    }
}
