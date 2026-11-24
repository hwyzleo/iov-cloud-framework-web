package net.hwyz.iov.cloud.framework.web.handler;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import net.hwyz.iov.cloud.framework.common.bean.ApiResponse;
import net.hwyz.iov.cloud.framework.common.exception.CommonErrorCode;
import net.hwyz.iov.cloud.framework.common.exception.ErrorCode;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

/**
 * Web 请求层异常处理器（FW-WEB-DSN-CR-004）。
 * <p>
 * 框架兜底 {@code Exception} 会把“调用方写错”的请求报成 {@code 100002} + HTTP 500，
 * 调用方无法区分“请求写错”和“服务故障”。本处理器前置收敛请求层错误：
 * 参数缺失/类型不匹配/报文不可读 → {@code 100001}；方法不支持 → 405；路径不存在 → 404。
 * 管理面端点（返回 ApiResponse）保持 HTTP 200 + 错误码，机器面端点返回真实 4xx 状态。
 * </p>
 *
 * @author hwyz_leo
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class WebRequestExceptionHandler {

    /**
     * 缺少必填请求参数。
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParameter(MissingServletRequestParameterException e,
                                                                    HttpServletRequest request) {
        String detail = "缺少必填参数: " + e.getParameterName();
        log.warn("请求参数缺失: uri={}, {}", request.getRequestURI(), detail);
        return clientError(request, CommonErrorCode.INVALID_PARAM, detail);
    }

    /**
     * 请求参数类型不匹配（例如数字型参数传入非数字）。
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e,
                                                                HttpServletRequest request) {
        String detail = "参数类型不匹配: " + e.getName();
        log.warn("请求参数类型不匹配: uri={}, {}", request.getRequestURI(), detail);
        return clientError(request, CommonErrorCode.INVALID_PARAM, detail);
    }

    /**
     * 请求体不可读（JSON 语法错误、枚举/时间格式非法等）。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleMessageNotReadable(HttpMessageNotReadableException e,
                                                                      HttpServletRequest request) {
        String detail = "请求体不可解析";
        log.warn("请求体不可解析: uri={}, err={}", request.getRequestURI(), e.getMostSpecificCause().getMessage());
        return clientError(request, CommonErrorCode.INVALID_PARAM, detail);
    }

    /**
     * HTTP 方法不支持：始终以 405 返回，不做信封化。
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e,
                                                                      HttpServletRequest request) {
        log.warn("请求方法不支持: uri={}, method={}", request.getRequestURI(), e.getMethod());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResponse.fail(CommonErrorCode.METHOD_NOT_ALLOWED, e.getMethod() + " 不被支持"));
    }

    /**
     * 处理器不存在：始终以 404 返回，不做信封化。
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoHandlerFound(NoHandlerFoundException e,
                                                                 HttpServletRequest request) {
        log.warn("访问资源不存在: uri={}", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail(CommonErrorCode.RESOURCE_NOT_FOUND));
    }

    /**
     * 请求层错误：管理面 200，机器面 400。
     */
    private static ResponseEntity<ApiResponse<Void>> clientError(HttpServletRequest request, ErrorCode errorCode,
                                                                 String detail) {
        return ResponseEntity.status(ErrorResponseSurface.resolveClientErrorStatus(request))
                .body(ApiResponse.fail(errorCode, detail));
    }
}
