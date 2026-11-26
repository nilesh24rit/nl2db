package com.nl2db.gateway.config;

import com.nl2db.auth.ApiKeyService;
import com.nl2db.common.ApiKeyUtils;
import com.nl2db.security.ApiKeyPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private final ApiKeyService apiKeyService;

    public ApiKeyAuthenticationFilter(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // Check for X-API-Key header only - never query/body parameters
        String apiKey = request.getHeader("X-API-Key");

        if (apiKey == null || apiKey.isEmpty()) {
            // No API key header - don't interfere with other auth mechanisms
            filterChain.doFilter(request, response);
            return;
        }

        // Validate the API key format
        if (!ApiKeyUtils.isValidFormat(apiKey)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            try {
                response.getWriter().write("{\"error\":\"invalid_api_key_format\"}");
            } catch (IOException e) {
                // Ignore write error
            }
            return;
        }

        // Validate the API key
        var result = apiKeyService.validateApiKey(apiKey);

        if (!result.isValid()) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            try {
                response.getWriter().write("{\"error\":\"invalid_api_key\",\"reason\":\"" +
                        result.getReason().replace("\"", "\\\"") + "\"}");
            } catch (IOException e) {
                // Ignore write error
            }
            return;
        }

        // Set the authenticated principal
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        result.getPrincipal(),
                        null,
                        result.getPrincipal().getAuthorities());

        authentication.setDetails(
                new WebAuthenticationDetailsSource().buildDetails(request));

        SecurityContextHolder.getContext().setAuthentication(authentication);
        filterChain.doFilter(request, response);
    }
}