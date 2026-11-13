package net.hwyz.iov.cloud.framework.web.jackson;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.ZoneId;

/**
 * iov.web.jackson 独立配置属性（FW-WEB-DSN-CR-001）。
 * <p>
 * 与 framework.web 前缀独立，避免与既有过滤器等配置混用。
 * 仅启动时生效：属性绑定完成后构建 mapper，禁止热修改共享 mapper（RD-001-4）。
 * 非法 date-format 值在属性绑定阶段抛异常，导致启动失败。
 *
 * @author hwyz_leo
 * @since 2026-10-28
 */
@Data
@ConfigurationProperties(prefix = "iov.web.jackson")
public class JacksonProperties {

    /**
     * 时间输出模式（FW-WEB-DSN-CR-003 RD-003-1）。
     * <ul>
     *   <li>{@code ISO}（默认）：ISO-8601，固定 3 位毫秒 + `Z`/offset，四类（含 {@code java.util.Date}）
     *       均由框架注册序列化器；</li>
     *   <li>{@code EPOCH_MILLIS}：兼容模式，四类输出 epoch 毫秒数字，供消费方未就绪时临时回退。</li>
     * </ul>
     */
    private DateFormat dateFormat = DateFormat.ISO;

    /**
     * 显式业务时区（LocalDateTime/OffsetDateTime 数字转换用）。
     * <p>
     * 未配置时相关数字路径 fail-closed（抛异常），禁止使用 systemDefault 猜测业务语义。
     * 默认值不预设：Asia/Shanghai 未经全字段核验前不得作为兜底（P0 阻断项）。
     */
    private ZoneId zoneId;

    /**
     * JVM/容器默认时区与 {@link #zoneId} 不一致时是否阻断启动（FW-WEB-DSN-CR-003 RD-003-9）。
     * <p>
     * 默认 false（仅告警）：两者不一致时 `LocalDateTime.now()` 写入值与 JSON 解释会静默偏移。
     * 部署侧应固化 `TZ` 与 {@code zone-id} 一致（如全 UTC 栈：`TZ=UTC` + `zone-id: UTC`），
     * 需要强约束时打开本开关。
     */
    private boolean strictJvmZone = false;

    public enum DateFormat {
        EPOCH_MILLIS, ISO
    }
}
