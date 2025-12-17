package com.hmdp.config;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Allows frontend to call backend via /api/** while keeping existing controller mappings unchanged.
 * Example: /api/shop/of/type -> /shop/of/type
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiPrefixForwardFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String uri = request.getRequestURI();
        if (uri == null) {
            filterChain.doFilter(request, response);
            return;
        }
        if (uri.equals("/api") || uri.equals("/api/")) {
            request.getRequestDispatcher("/").forward(request, response);
            return;
        }
        if (uri.startsWith("/api/")) {
            String forwardUri = uri.substring(4);
            if (forwardUri.isEmpty()) {
                forwardUri = "/";
            }
            request.getRequestDispatcher(forwardUri).forward(request, response);
            return;
        }
        filterChain.doFilter(request, response);
    }
}

