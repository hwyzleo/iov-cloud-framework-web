package net.hwyz.iov.cloud.framework.web.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StopWatch;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;

/**
 * 请求日志拦截器。
 * <p>
 * 记录请求的方法、URI、客户端 IP、请求参数以及执行时长。
 */
@Slf4j
public class RequestLogInterceptor implements HandlerInterceptor {

    private static final String STOPWATCH_ATTR = RequestLogInterceptor.class.getName() + ".StopWatch";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        request.setAttribute(STOPWATCH_ATTR, stopWatch);

        String method = request.getMethod();
        String uri = request.getRequestURI();
        String remoteAddr = getRemoteAddr(request);
        Map<String, String[]> parameterMap = request.getParameterMap();

        if (log.isInfoEnabled()) {
            log.info("开始请求: [{}] {}, IP: {}, 参数: {}", method, uri, remoteAddr, formatParameters(parameterMap));
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        StopWatch stopWatch = (StopWatch) request.getAttribute(STOPWATCH_ATTR);
        if (stopWatch != null) {
            stopWatch.stop();
            long duration = stopWatch.getTotalTimeMillis();
            String method = request.getMethod();
            String uri = request.getRequestURI();
            int status = response.getStatus();

            if (log.isInfoEnabled()) {
                log.info("结束请求: [{}] {}, 状态码: {}, 耗时: {}ms", method, uri, status, duration);
            }
        }
    }

    private String getRemoteAddr(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }

    private String formatParameters(Map<String, String[]> parameterMap) {
        if (parameterMap == null || parameterMap.isEmpty()) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder("{");
        parameterMap.forEach((k, v) -> {
            sb.append(k).append("=");
            if (v.length == 1) {
                sb.append(v[0]);
            } else {
                sb.append("[");
                for (int i = 0; i < v.length; i++) {
                    sb.append(v[i]);
                    if (i < v.length - 1) {
                        sb.append(",");
                    }
                }
                sb.append("]");
            }
            sb.append(", ");
        });
        if (sb.length() > 1) {
            sb.setLength(sb.length() - 2);
        }
        sb.append("}");
        return sb.toString();
    }
}
