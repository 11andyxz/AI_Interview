package com.aiinterview.interceptor;

import com.aiinterview.service.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtService jwtService;

    @Autowired
    private Environment environment;

    private static final String[] PUBLIC_PATHS = {
        "/api/auth/login",
        "/api/auth/register",
        "/api/health",
        "/api/payment/webhook"
    };

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String path = request.getRequestURI();

        // Allow public paths
        for (String publicPath : PUBLIC_PATHS) {
            if (path.startsWith(publicPath)) {
                return true;
            }
        }

        // In test profile, bypass authentication entirely (tests will set userId manually)
        if (Arrays.asList(environment.getActiveProfiles()).contains("test")) {
            // If userId not set, set a default test user
            if (request.getAttribute("userId") == null) {
                request.setAttribute("userId", 1L);
                request.setAttribute("username", "testuser");
            }
            return true;
        }

        // Extract token from Authorization header
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }

        String token = authHeader.substring(7);

        // Validate token
        if (!jwtService.validateToken(token)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }

        // Add user info to request attributes
        Long userId = jwtService.extractUserId(token);
        String username = jwtService.extractUsername(token);
        request.setAttribute("userId", userId);
        request.setAttribute("username", username);

        return true;
    }
}

