package net.hwyz.iov.cloud.framework.web.handler;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import net.hwyz.iov.cloud.framework.common.bean.ApiResponse;
import net.hwyz.iov.cloud.framework.common.exception.BusinessException;
import net.hwyz.iov.cloud.framework.common.exception.CommonErrorCode;
import net.hwyz.iov.cloud.framework.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器（统一异常出口，FW-WEB-DSN-CR-004）。
 * <p>
 * 平台唯一的异常→响应转换点，业务服务不得自建 {@code @RestControllerAdvice} 重复映射错误码：
 * </p>
 * <ul>
 *   <li>{@link BusinessException}（携带 {@link ErrorCode}）：管理面端点返回 HTTP 200 + 业务码；
 *       机器面端点返回错误码声明的非 2xx 状态 + 同一 {@code ApiResponse} 错误体；</li>
 *   <li>其余异常：HTTP 500 + {@code 100002}，记录堆栈，不对外暴露内部信息。</li>
 * </ul>
 *
 * @author hwyz_leo
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 业务异常：可预期的业务失败，按端点错误面决定 HTTP 状态。
     *
     * @param ex      业务异常
     * @param request 当前请求
     * @return 统一错误响应
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException ex,
                                                                     HttpServletRequest request) {
        ErrorCode errorCode = ex.getErrorCode();
        int status = ErrorResponseSurface.resolveBusinessStatus(request, errorCode);
        log.warn("业务异常: uri={}, code={}, httpStatus={}, message={}",
                requestUri(request), errorCode.getCode(), status, ex.getMessage());
        return ResponseEntity.status(status).body(ApiResponse.fail(errorCode, ex.getMessage()));
    }

    /**
     * 未预期异常：HTTP 500 + 100002，禁止把异常细节返回调用方。
     *
     * @param ex      未预期异常
     * @param request 当前请求
     * @return 统一错误响应
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception ex, HttpServletRequest request) {
        log.error("系统异常: uri={}", requestUri(request), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail(CommonErrorCode.INTERNAL_ERROR));
    }

    /**
     * 请求地址（异常发生在过滤器/无法解析处理器时可能为空）。
     */
    private static String requestUri(HttpServletRequest request) {
        return request == null ? "" : request.getRequestURI();
    }
}
