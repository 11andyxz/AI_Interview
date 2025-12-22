package com.aiinterview.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Test filter that automatically adds userId to all requests in test environment
 */
@Component
@Profile("test")
@Order(1)
public class TestAuthFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (request instanceof HttpServletRequest) {
            HttpServletRequest httpRequest = (HttpServletRequest) request;

            // Check if userId is already set (from test request attributes)
            if (httpRequest.getAttribute("userId") == null) {
                // Set a default test userId
                request.setAttribute("userId", 1L);
                request.setAttribute("username", "testuser");
            }
        }

        chain.doFilter(request, response);
    }
}
