package net.hwyz.iov.cloud.framework.web.timezone;

import jakarta.servlet.http.HttpServletRequest;
import net.hwyz.iov.cloud.framework.common.time.RequestTimeZoneContext;
import net.hwyz.iov.cloud.framework.common.time.Snapshot;
import net.hwyz.iov.cloud.framework.common.util.DisplayTimeUtil;
import net.hwyz.iov.cloud.framework.web.properties.IovRequestTimeZoneProperties;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * 展示时间 Facade（FW-WEB-DSN-CR-002，RD-TZ-005/007）。
 * <p>
 * 决定 effective zone 并显式传给 COMMON {@link DisplayTimeUtil}；实体、Repository、
 * codec、digest/安全代码不得读取 {@link RequestTimeZoneContext}。
 * <ul>
 *   <li>{@code enabled=false} 时本 facade 仍可用：不读取上下文绑定（无绑定时不会
 *       因 current() 抛错而失效），回到显式服务 fallback / 框架 default-zone-id；</li>
 *   <li>{@code resolveEffectiveZone()}：已绑定快照 &gt; 框架默认（无请求场景）；
 *       {@code resolveEffectiveZone(request)}：已绑定快照 &gt; 服务/端点 fallback &gt; 默认；</li>
 *   <li>「无绑定时不隐式上海」= 不读取 JVM 默认时区、不在 COMMON 写死上海；
 *       WEB 侧最终兜底来自显式配置的 {@code default-zone-id}；</li>
 *   <li>异步导出：应在生成任务时捕获 zone（见 {@link IovRequestTimeZonePropagator}），
 *       不能执行时读任意工作线程。</li>
 * </ul>
 * 普通事件时间展示仍由前端按展示时区格式化；本能力不保证所有 API 的机器时间字符串
 * 随 header 改变。
 *
 * @author hwyz_leo
 * @since 2026-10-31
 */
public class DisplayTimeFacade {

    private final IovRequestTimeZoneProperties properties;
    private final ObjectProvider<List<RequestTimeZoneFallbackResolver>> fallbackResolverProvider;

    public DisplayTimeFacade(IovRequestTimeZoneProperties properties,
                             ObjectProvider<List<RequestTimeZoneFallbackResolver>> fallbackResolverProvider) {
        this.properties = properties;
        this.fallbackResolverProvider = fallbackResolverProvider;
    }

    /**
     * 无请求场景的有效展示时区：已绑定快照 &gt; 框架 default-zone-id。
     */
    public ZoneId resolveEffectiveZone() {
        return RequestTimeZoneContext.currentOptional()
                .map(Snapshot::zoneId)
                .orElse(properties.getDefaultZoneId());
    }

    /**
     * 请求场景的有效展示时区：已绑定快照 &gt; 服务/端点 fallback &gt; 框架默认。
     */
    public ZoneId resolveEffectiveZone(HttpServletRequest request) {
        Optional<Snapshot> bound = RequestTimeZoneContext.currentOptional();
        if (bound.isPresent()) {
            return bound.get().zoneId();
        }
        List<RequestTimeZoneFallbackResolver> resolvers = fallbackResolverProvider.getIfAvailable();
        if (resolvers != null) {
            for (RequestTimeZoneFallbackResolver resolver : resolvers) {
                try {
                    Optional<ZoneId> zone = resolver.resolveFallbackZone(request);
                    if (zone.isPresent()) {
                        return zone.get();
                    }
                } catch (Exception ignored) {
                    // 兜底失败继续候选，不抛给业务
                }
            }
        }
        return properties.getDefaultZoneId();
    }

    /** 展示转换：当前有效时区。 */
    public LocalDateTime toLocalDateTime(Instant value) {
        return DisplayTimeUtil.toLocalDateTime(value, resolveEffectiveZone());
    }

    /** 展示转换：当前有效时区（秒精度展示文本）。 */
    public String format(Instant value) {
        return DisplayTimeUtil.format(value, resolveEffectiveZone());
    }

    /** 展示转换：已确认 UTC 墙钟 → 当前有效时区。 */
    public LocalDateTime utcWallClockToLocal(LocalDateTime value) {
        return DisplayTimeUtil.utcWallClockToLocal(value, resolveEffectiveZone());
    }

    /** 展示转换：显式 zone（服务端先定 zone 再逐行格式化等场景）。 */
    public LocalDateTime toLocalDateTime(Instant value, ZoneId zone) {
        return DisplayTimeUtil.toLocalDateTime(value, zone);
    }

    /** 展示转换：显式 zone（秒精度展示文本）。 */
    public String format(Instant value, ZoneId zone) {
        return DisplayTimeUtil.format(value, zone);
    }

    /** 展示转换：已确认 UTC 墙钟 → 显式 zone。 */
    public LocalDateTime utcWallClockToLocal(LocalDateTime value, ZoneId zone) {
        return DisplayTimeUtil.utcWallClockToLocal(value, zone);
    }
}
