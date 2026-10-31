package net.hwyz.iov.cloud.framework.web.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.beans.factory.InitializingBean;

import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 请求时区配置属性（FW-WEB-DSN-CR-002，RD-TZ-002）。
 * <p>
 * 前缀 {@code iov.web.timezone}，与 {@code iov.web.jackson}（CR-001）风格一致，
 * 与 {@code framework.web}（过滤器等）独立。
 * <p>
 * 语义澄清（RD-TZ-002）：
 * <ul>
 *   <li>{@code default-zone-id} 是服务的默认展示/本地日历语义兜底时区，不是 JVM/
 *       容器运行时区，也不是数据库默认时区；仅在更高优先级有效来源缺失时生效；</li>
 *   <li>{@code trust-header=true} 满足页面/手机/车机开箱请求展示，不表示该 header
 *       可用于安全信任；管理后台建议 false；false 同时关闭客户端 header 与参数输入；</li>
 *   <li>{@code enabled=false} 不注册请求解析/清理/透传链，展示 facade 回到显式服务
 *       fallback；{@code feign-propagation-enabled} 可独立关闭；</li>
 *   <li>非法服务默认 zone、空默认或重复/空参数名 → 启动失败（fail-closed），
 *       客户端输入错误才 fail-safe 回退。</li>
 * </ul>
 * 新增键说明（实现回填）：{@code interceptor-order} 用于时区拦截器排序（须在认证
 * interceptor 之后）；{@code filter-order} 用于最外层清理 Filter 排序；
 * {@code async-propagation-enabled} 控制 MVC Callable 执行器（默认 applicationTaskExecutor）
 * 是否应用快照 TaskDecorator；{@code internal-feign-targets} 为内部 Feign 目标白名单
 * （默认空 = 不向任何 Feign 目标透传，第三方默认不注入），{@code feign-excluded-targets}
 * 为补充排除列表。
 *
 * @author hwyz_leo
 * @since 2026-10-31
 */
@Data
@ConfigurationProperties(prefix = "iov.web.timezone")
public class IovRequestTimeZoneProperties implements InitializingBean {

    /**
     * 是否启用请求时区解析/绑定/透传链（facade 与兜底能力不受影响）。
     */
    private boolean enabled = true;

    /**
     * 默认展示/本地日历语义兜底时区（IANA 地区 ID 或 ±HH:mm，非法启动失败）。
     */
    private ZoneId defaultZoneId = ZoneId.of("Asia/Shanghai");

    /**
     * 是否信任客户端 {@code X-Timezone} 请求头；false 同时忽略 header 与参数。
     */
    private boolean trustHeader = true;

    /**
     * 显式启用的 query/form 参数名（默认空 = 不读取参数；只读单值，不读 JSON body）。
     */
    private List<String> paramNames = List.of();

    /**
     * 是否向内部 Feign 目标透传已解析的有效时区（可独立关闭）。
     */
    private boolean feignPropagationEnabled = true;

    /**
     * 时区拦截器排序（默认 0；须排在认证拦截器之后，认证完成才可执行用户 SPI）。
     */
    private int interceptorOrder = 0;

    /**
     * 最外层清理 Filter 排序（默认 Integer.MIN_VALUE，作为请求入口的最外层兜底）。
     */
    private int filterOrder = Integer.MIN_VALUE;

    /**
     * 是否对 MVC Callable 执行器（默认 applicationTaskExecutor）应用快照 TaskDecorator。
     * <p>
     * 注意：默认共享执行器同时被 @Async 使用，开启后两者都会随提交线程快照传播；
     * 服务如不希望 @Async 继承，应使用独立执行器或显式 wrap 控制。
     */
    private boolean asyncPropagationEnabled = true;

    /**
     * 内部 Feign 目标白名单（client 名或 host/URL 子串）。默认空 = 不向任何 Feign
     * 目标透传（外部/未登记目标默认不注入用户时区）；命中白名单且未命中排除列表才透传。
     */
    private List<String> internalFeignTargets = List.of();

    /**
     * 补充排除列表：即使命中内部白名单，命中排除列表的目标也不透传。
     */
    private List<String> feignExcludedTargets = List.of();

    @Override
    public void afterPropertiesSet() {
        // 非法/空默认 zone：Spring 绑定失败已阻断大部分情况；这里兜底拒绝短别名等
        // （ZoneId.of("CST") 会成功，但违反 RD-TZ-003 校验约定）。
        if (defaultZoneId == null
                || net.hwyz.iov.cloud.framework.web.timezone.RequestTimeZoneParser.parse(
                        defaultZoneId.toString()).isEmpty()) {
            throw new IllegalStateException(
                    "iov.web.timezone.default-zone-id 必须为有效的 IANA 地区 ID 或 ±HH:mm（拒绝短别名/数字猜测）");
        }
        // 重复/空参数名 → 启动失败（fail-closed）
        Set<String> seen = new HashSet<>();
        if (paramNames != null) {
            for (String name : paramNames) {
                if (name == null || name.isBlank()) {
                    throw new IllegalStateException("iov.web.timezone.param-names 不允许空参数名");
                }
                if (!seen.add(name)) {
                    throw new IllegalStateException("iov.web.timezone.param-names 不允许重复参数名: " + name);
                }
            }
        }
    }
}
