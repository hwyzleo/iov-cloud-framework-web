package net.hwyz.iov.cloud.framework.web.handler;

import jakarta.servlet.http.HttpServletRequest;
import net.hwyz.iov.cloud.framework.common.bean.ApiResponse;
import net.hwyz.iov.cloud.framework.common.exception.ErrorCode;
import net.hwyz.iov.cloud.framework.common.exception.HttpStatusAware;
import org.springframework.core.ResolvableType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

/**
 * 错误响应面解析（FW-WEB-DSN-CR-004）。
 * <p>
 * 统一异常出口必须区分端点的错误响应面，否则要么管理面前端拿不到业务码，
 * 要么机器面调用方把错误体反序列化成空数据而误判成功：
 * </p>
 * <ul>
 *   <li><b>管理面</b>：端点声明返回 {@link ApiResponse}（含 {@code ResponseEntity<ApiResponse<...>>}），
 *       业务失败返回 HTTP 200 + {@code ApiResponse}，前端按 code/message 提示；</li>
 *   <li><b>机器面</b>：端点声明返回裸 DTO/{@code void}/{@code List}，业务失败返回
 *       {@link ErrorCode} 声明的状态（未声明则 422），错误体仍为 {@code ApiResponse}，
 *       由 Feign 统一 ErrorDecoder 还原业务码。</li>
 * </ul>
 * <p>
 * 判定依据是当前请求实际命中的 {@link HandlerMethod} 声明返回类型（由
 * {@code RequestMappingHandlerMapping} 写入的 {@code BEST_MATCHING_HANDLER_ATTRIBUTE}），
 * 不需要每个服务重复声明，也不会因 advice 作用域遗漏分支。
 * </p>
 *
 * @author hwyz_leo
 */
public final class ErrorResponseSurface {

    /**
     * 管理面业务错误状态：HTTP 200 + ApiResponse。
     */
    public static final int ENVELOPE_STATUS = 200;

    /**
     * 机器面业务错误默认状态：HTTP 422。
     */
    public static final int MACHINE_STATUS = HttpStatusAware.DEFAULT_BUSINESS_ERROR_STATUS;

    /**
     * 机器面入参/语义错误状态：HTTP 400。
     */
    public static final int MACHINE_CLIENT_ERROR_STATUS = 400;

    private ErrorResponseSurface() {
    }

    /**
     * 当前请求是否为管理面（信封）端点。
     *
     * @param request 当前请求；允许为 null
     * @return true 表示端点声明返回 ApiResponse
     */
    public static boolean isEnvelope(HttpServletRequest request) {
        Object handler = request == null ? null
                : request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE);
        return handler instanceof HandlerMethod handlerMethod && isEnvelope(handlerMethod);
    }

    /**
     * 端点是否声明返回 {@link ApiResponse}（含 ResponseEntity 包装）。
     *
     * @param handlerMethod 命中的处理器方法
     * @return true 表示管理面契约
     */
    public static boolean isEnvelope(HandlerMethod handlerMethod) {
        Class<?> returnType = handlerMethod.getReturnType().getParameterType();
        if (ResponseEntity.class.isAssignableFrom(returnType)) {
            returnType = ResolvableType.forMethodParameter(handlerMethod.getReturnType())
                    .getGeneric(0).resolve(Object.class);
        }
        return ApiResponse.class.isAssignableFrom(returnType);
    }

    /**
     * 业务错误状态：管理面恒为 200；机器面取错误码声明（仅 4xx/5xx 生效），默认 422。
     *
     * @param request   当前请求
     * @param errorCode 业务错误码
     * @return HTTP 状态码
     */
    public static int resolveBusinessStatus(HttpServletRequest request, ErrorCode errorCode) {
        if (isEnvelope(request)) {
            return ENVELOPE_STATUS;
        }
        if (errorCode instanceof HttpStatusAware aware) {
            int declared = aware.getHttpStatus();
            if (declared >= 400 && declared <= 599) {
                return declared;
            }
        }
        return MACHINE_STATUS;
    }

    /**
     * 入参/语义错误状态：管理面为 200（与 Bean Validation 既有语义一致）；机器面为 400。
     *
     * @param request 当前请求
     * @return HTTP 状态码
     */
    public static int resolveClientErrorStatus(HttpServletRequest request) {
        return isEnvelope(request) ? ENVELOPE_STATUS : MACHINE_CLIENT_ERROR_STATUS;
    }
}
