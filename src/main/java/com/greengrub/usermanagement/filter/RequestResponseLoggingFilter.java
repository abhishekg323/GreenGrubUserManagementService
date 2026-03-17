package com.greengrub.usermanagement.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.util.UUID;

/**
 * Filter for logging HTTP requests and responses
 * Logs request/response details for debugging and auditing
 */
@Component
@Slf4j
public class RequestResponseLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        // Generate unique request ID
        String requestId = UUID.randomUUID().toString().substring(0, 8);

        // Wrap request and response for logging (10KB limit for request body caching)
        ContentCachingRequestWrapper requestWrapper = new ContentCachingRequestWrapper(request, 10240);
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);

        long startTime = System.currentTimeMillis();

        // Log incoming request
        logRequest(requestWrapper, requestId);

        try {
            // Continue with the filter chain
            filterChain.doFilter(requestWrapper, responseWrapper);
        } finally {
            long duration = System.currentTimeMillis() - startTime;

            // Log response
            logResponse(responseWrapper, requestId, duration);

            // Copy response body to original response
            responseWrapper.copyBodyToResponse();
        }
    }

    private void logRequest(HttpServletRequest request, String requestId) {
        String method = request.getMethod();
        String uri = request.getRequestURI();
        String queryString = request.getQueryString();
        String remoteAddr = request.getRemoteAddr();

        log.info(">>> [{}] {} {} {} from {}",
                requestId,
                method,
                uri,
                queryString != null ? "?" + queryString : "",
                remoteAddr);
    }

    private void logResponse(
            ContentCachingResponseWrapper response,
            String requestId,
            long duration) {

        int status = response.getStatus();
        String statusText = getStatusText(status);

        if (status >= 400) {
            log.warn("<<< [{}] {} - {}ms - {}", requestId, status, duration, statusText);
        } else {
            log.info("<<< [{}] {} - {}ms - {}", requestId, status, duration, statusText);
        }
    }

    private String getStatusText(int status) {
        if (status >= 200 && status < 300) return "SUCCESS";
        if (status >= 300 && status < 400) return "REDIRECT";
        if (status >= 400 && status < 500) return "CLIENT_ERROR";
        if (status >= 500) return "SERVER_ERROR";
        return "UNKNOWN";
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Skip filtering for actuator endpoints
        String path = request.getRequestURI();
        return path.startsWith("/actuator");
    }
}
