package net.hwyz.iov.cloud.framework.web.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;

/**
 * XSS 过滤器
 *
 * @author hwyz_leo
 */
public class XssFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        // 使用包装类过滤 XSS
        chain.doFilter(new XssHttpServletRequestWrapper(req), response);
    }
}
