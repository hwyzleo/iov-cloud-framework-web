package net.hwyz.iov.cloud.framework.web.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.hwyz.iov.cloud.framework.common.exception.CommonErrorCode;
import net.hwyz.iov.cloud.framework.common.exception.ErrorCode;
import org.slf4j.MDC;

/**
 * CIAM 统一响应体封装。
 * <p>
 * 所有对外接口统一使用此结构返回，包含错误码、消息、数据、追踪 ID 与时间戳。
 *
 * @param <T> 响应数据类型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    /**
     * 错误码，"000000" 表示成功
     */
    private String code;

    /**
     * 描述信息
     */
    private String message;

    /**
     * 响应数据
     */
    private T data;

    /**
     * 链路追踪 ID
     */
    private String traceId;

    /**
     * 响应时间戳（毫秒）
     */
    private long timestamp;

    /**
     * 成功响应（无数据）
     */
    public static <T> ApiResponse<T> ok() {
        return build(CommonErrorCode.SUCCESS, null);
    }

    /**
     * 成功响应（带数据）
     */
    public static <T> ApiResponse<T> ok(T data) {
        return build(CommonErrorCode.SUCCESS, data);
    }

    /**
     * 失败响应（使用错误码）
     */
    public static <T> ApiResponse<T> fail(ErrorCode errorCode) {
        return build(errorCode, null);
    }

    /**
     * 失败响应（使用错误码 + 自定义消息）
     */
    public static <T> ApiResponse<T> fail(ErrorCode errorCode, String message) {
        return ApiResponse.<T>builder()
                .code(errorCode.getCode())
                .message(message)
                .traceId(MDC.get("traceId"))
                .timestamp(System.currentTimeMillis())
                .build();
    }

    private static <T> ApiResponse<T> build(ErrorCode errorCode, T data) {
        return ApiResponse.<T>builder()
                .code(errorCode.getCode())
                .message(errorCode.getMessage())
                .data(data)
                .traceId(MDC.get("traceId"))
                .timestamp(System.currentTimeMillis())
                .build();
    }
}
