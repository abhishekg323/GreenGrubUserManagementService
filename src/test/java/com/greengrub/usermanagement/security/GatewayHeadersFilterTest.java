package com.greengrub.usermanagement.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GatewayHeadersFilterTest {

    private GatewayHeadersFilter filter;

    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private FilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new GatewayHeadersFilter();
    }

    @Test
    void withUserId_setsAllAttributes() throws ServletException, IOException {
        when(request.getHeader("X-User-Id")).thenReturn("user-001");
        when(request.getHeader("X-User-Email")).thenReturn("john@example.com");
        when(request.getHeader("X-User-Role")).thenReturn("DONOR");

        filter.doFilterInternal(request, response, chain);

        verify(request).setAttribute("userId", "user-001");
        verify(request).setAttribute("userEmail", "john@example.com");
        verify(request).setAttribute("userRole", "DONOR");
        verify(chain).doFilter(request, response);
    }

    @Test
    void nullUserId_skipsAttributeSetting() throws ServletException, IOException {
        when(request.getHeader("X-User-Id")).thenReturn(null);

        filter.doFilterInternal(request, response, chain);

        verify(request, never()).setAttribute(anyString(), any());
        verify(chain).doFilter(request, response);
    }

    @Test
    void blankUserId_skipsAttributeSetting() throws ServletException, IOException {
        when(request.getHeader("X-User-Id")).thenReturn("   ");

        filter.doFilterInternal(request, response, chain);

        verify(request, never()).setAttribute(anyString(), any());
        verify(chain).doFilter(request, response);
    }

    @Test
    void chainAlwaysCalled_evenWithoutHeaders() throws ServletException, IOException {
        when(request.getHeader("X-User-Id")).thenReturn(null);

        filter.doFilterInternal(request, response, chain);

        verify(chain, times(1)).doFilter(request, response);
    }
}
