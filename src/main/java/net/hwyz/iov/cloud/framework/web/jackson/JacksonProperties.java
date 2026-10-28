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
     * 时间输出模式（RD-001-1）：
     * <ul>
     *   <li>epoch-millis（默认）：复现存量 epoch 毫秒输出，兼容优先；</li>
     *   <li>iso：ISO-8601 输出，仅在显式配置且契约验收后使用。</li>
     * </ul>
     */
    private DateFormat dateFormat = DateFormat.EPOCH_MILLIS;

    /**
     * 显式业务时区（LocalDateTime/OffsetDateTime 数字转换用）。
     * <p>
     * 未配置时相关数字路径 fail-closed（抛异常），禁止使用 systemDefault 猜测业务语义。
     * 默认值不预设：Asia/Shanghai 未经全字段核验前不得作为兜底（P0 阻断项）。
     */
    private ZoneId zoneId;

    public enum DateFormat {
        EPOCH_MILLIS, ISO
    }
}
