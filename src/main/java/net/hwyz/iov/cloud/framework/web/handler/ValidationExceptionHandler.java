package net.hwyz.iov.cloud.framework.web.handler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import net.hwyz.iov.cloud.framework.common.bean.ApiResponse;
import net.hwyz.iov.cloud.framework.common.exception.CommonErrorCode;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 参数校验异常处理器（FW-WEB-DSN-CR-004：错误响应面感知）。
 * <p>
 * Bean Validation 失败统一返回 {@code 100001}：管理面端点（返回 ApiResponse）保持
 * HTTP 200 + 错误码；机器面端点（裸 DTO/void，服务间调用）返回 HTTP 400，
 * 避免调用方把 {@code ApiResponse} 错误体解成空数据。
 * </p>
 *
 * @author hwyz leo
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class ValidationExceptionHandler {

    /**
     * 处理 Bean Validation (@Valid/@Validated) 在控制器方法参数校验失败时抛出的异常
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException e, HttpServletRequest request) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: uri={}, {}", request.getRequestURI(), detail);
        return invalidParam(request, detail);
    }

    /**
     * 处理表单绑定或 @Valid 校验在非 JSON 请求体时抛出的异常
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Void>> handleBindException(BindException e, HttpServletRequest request) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("表单绑定/校验失败: uri={}, {}", request.getRequestURI(), detail);
        return invalidParam(request, detail);
    }

    /**
     * 处理在 Service 层或使用 @Validated 校验单个参数时抛出的约束违反异常
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolationException(
            ConstraintViolationException e, HttpServletRequest request) {
        String detail = e.getConstraintViolations().stream()
                .map(violation -> violation.getMessage())
                .collect(Collectors.joining("; "));
        log.warn("约束校验失败: uri={}, {}", request.getRequestURI(), detail);
        return invalidParam(request, detail);
    }

    /**
     * 参数校验失败响应：管理面 200，机器面 400。
     */
    private static ResponseEntity<ApiResponse<Void>> invalidParam(HttpServletRequest request, String detail) {
        return ResponseEntity.status(ErrorResponseSurface.resolveClientErrorStatus(request))
                .body(ApiResponse.fail(CommonErrorCode.INVALID_PARAM, detail));
    }
}
