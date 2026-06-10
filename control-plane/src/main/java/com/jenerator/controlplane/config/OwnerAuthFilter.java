package com.jenerator.controlplane.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class OwnerAuthFilter extends OncePerRequestFilter {
    public static final String OWNER_KEY_HEADER = "X-Jenerator-Owner-Key";

    private final String ownerApiKey;

    public OwnerAuthFilter(@Value("${jenerator.owner-api-key:change-me}") String ownerApiKey) {
        this.ownerApiKey = ownerApiKey == null ? "" : ownerApiKey.trim();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        if (!path.startsWith("/api/")) {
            return true;
        }
        if (path.startsWith("/api/owner/")) {
            return false;
        }
        if (path.startsWith("/api/workers/") || path.equals("/api/settings")) {
            return true;
        }
        if ("GET".equalsIgnoreCase(request.getMethod()) && path.matches("/api/jobs/[^/]+/artifacts/[^/]+/file")) {
            return true;
        }
        return !path.startsWith("/api/jobs") && !path.startsWith("/api/assets");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (ownerApiKey.isBlank() || ownerApiKey.equals(providedKey(request))) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"message\":\"Missing or invalid owner API key.\"}");
    }

    private String providedKey(HttpServletRequest request) {
        String header = request.getHeader(OWNER_KEY_HEADER);
        if (header != null && !header.isBlank()) {
            return header.trim();
        }
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return authorization.substring(7).trim();
        }
        return "";
    }
}
