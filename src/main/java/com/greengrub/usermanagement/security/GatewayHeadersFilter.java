package com.greengrub.usermanagement.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Copies the API Gateway's identity headers into request attributes so controllers
 * can read them via @RequestAttribute. The gateway has already validated the JWT
 * upstream — this filter performs no signature checks and makes no authz decisions.
 *
 * Trust boundary: this service must never be exposed directly to the public internet
 * in production. Any client could otherwise spoof these headers.
 */
@Component
@Slf4j
public class GatewayHeadersFilter extends OncePerRequestFilter {

    private static final String H_USER_ID    = "X-User-Id";
    private static final String H_USER_EMAIL = "X-User-Email";
    private static final String H_USER_ROLE  = "X-User-Role";

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain chain) throws ServletException, IOException {

        String userId = request.getHeader(H_USER_ID);
        if (userId != null && !userId.isBlank()) {
            request.setAttribute("userId", userId);
            request.setAttribute("userEmail", request.getHeader(H_USER_EMAIL));
            request.setAttribute("userRole", request.getHeader(H_USER_ROLE));
            log.debug("Gateway identity headers applied for userId: {}", userId);
        }

        chain.doFilter(request, response);
    }
}
