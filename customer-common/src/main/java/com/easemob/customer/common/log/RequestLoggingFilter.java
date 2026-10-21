package com.easemob.customer.common.log;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 统一请求日志过滤器:每个 HTTP 请求记录一行 INFO 日志,
 * 包含方法、路径(query string 拼在路径后)、状态码、耗时、IP,
 * 以及 application/json 的 POST/PUT 请求体(截断到 500 字符)。
 */
@Slf4j
@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final int MAX_BODY_LENGTH = 500;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long start = System.currentTimeMillis();
        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);
        try {
            filterChain.doFilter(wrappedRequest, wrappedResponse);
        } finally {
            long cost = System.currentTimeMillis() - start;
            String body = resolveBody(wrappedRequest);
            log.info("[REQ] {} {} status={} cost={}ms ip={}{}",
                    wrappedRequest.getMethod(), buildPath(wrappedRequest), wrappedResponse.getStatus(),
                    cost, clientIp(request), body == null ? "" : " body=" + body);
            wrappedResponse.copyBodyToResponse();
        }
    }

    private String buildPath(ContentCachingRequestWrapper request) {
        String uri = request.getRequestURI();
        String query = request.getQueryString();
        return StringUtils.hasText(query) ? uri + "?" + query : uri;
    }

    private String resolveBody(ContentCachingRequestWrapper request) {
        String method = request.getMethod();
        if (!"POST".equals(method) && !"PUT".equals(method)) {
            return null;
        }
        String contentType = request.getContentType();
        if (contentType == null || !contentType.contains("application/json")) {
            return null;
        }
        byte[] buf = request.getContentAsByteArray();
        if (buf.length == 0) {
            return null;
        }
        String body = new String(buf, StandardCharsets.UTF_8);
        if (body.length() > MAX_BODY_LENGTH) {
            return body.substring(0, MAX_BODY_LENGTH) + "...(truncated)";
        }
        return body;
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
