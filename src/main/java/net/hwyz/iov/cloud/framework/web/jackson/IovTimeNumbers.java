package net.hwyz.iov.cloud.framework.web.jackson;

import java.time.Instant;

/**
 * 时间戳数字单位判定（RD-001-2）。
 * <p>
 * 明确规则：10 位整数为 epoch 秒，13 位整数为 epoch 毫秒，二者覆盖同一现代时间窗口
 * （2001-09-09 ~ 2286）。0、负值、其他位数、小数与数字字符串不做无依据猜测，
 * 一律 fail-closed；若历史契约确实存在窗口外样本，须由服务 CR 按实际历史契约
 * 显式适配/登记，框架层不缩窄也不猜测。
 *
 * @author hwyz_leo
 * @since 2026-10-28
 */
final class IovTimeNumbers {

    private IovTimeNumbers() {
    }

    static Instant toInstant(long value) {
        if (value >= 1_000_000_000L && value <= 9_999_999_999L) {
            return Instant.ofEpochSecond(value);
        }
        if (value >= 1_000_000_000_000L && value <= 9_999_999_999_999L) {
            return Instant.ofEpochMilli(value);
        }
        throw new IllegalArgumentException(
                "时间戳不在 10 位 epoch 秒或 13 位 epoch 毫秒窗口内（0/负值/其他位数不做猜测）: " + value);
    }
}
