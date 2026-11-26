package com.nl2db.gateway.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;

/**
 * Correlation ID filter for request tracking.
 * <p>
 * Responsibilities:
 * - Accept a valid inbound correlation ID when supplied via {@code X-Correlation-ID} header.
 * - Generate a new ID when one is absent or invalid.
 * - Validate length (max 256 characters) and allowed characters (no control characters).
 * - Reject or replace values containing control characters or unsafe content.
 * - Add the correlation ID to the HTTP response header.
 * - Populate the logging MDC for the duration of request processing.
 * - Ensure MDC is always cleared, including exceptional request paths.
 * - Avoid leaking MDC values between reused server threads.
 * <p>
 * This filter is registered as a Spring bean via {@link CorrelationIdFilterConfig}.
 */
public class CorrelationIdFilter implements Filter {

    private static final int MAX_CORRELATION_ID_LENGTH = 256;
    private static final Pattern VALID_CHARACTERS =
            Pattern.compile("^[ -~]{1," + MAX_CORRELATION_ID_LENGTH + "}$");

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // No initialization required
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse,
                         jakarta.servlet.FilterChain filterChain) throws IOException, ServletException {

        jakarta.servlet.http.HttpServletRequest request =
                (jakarta.servlet.http.HttpServletRequest) servletRequest;
        jakarta.servlet.http.HttpServletResponse response =
                (jakarta.servlet.http.HttpServletResponse) servletResponse;

        // Step 1: Extract correlation ID from inbound request header
        String inboundId = request.getHeader("X-Correlation-ID");
        String correlationId;

        if (isValidCorrelationId(inboundId)) {
            correlationId = inboundId;
        } else {
            // Step 2: Generate a new ID when absent or invalid
            correlationId = UUID.randomUUID().toString();
        }

        // Step 3: Populate MDC for the duration of request processing
        MDC.put("correlationId", correlationId);

        try {
            // Step 4: Add correlation ID to HTTP response header
            response.setHeader("X-Correlation-ID", correlationId);

            // Step 5: Continue filter chain
            filterChain.doFilter(request, response);
        } finally {
            // Step 6: Always clear MDC after request processing (including exceptions)
            MDC.remove("correlationId");
        }
    }

    @Override
    public void destroy() {
        // No cleanup required
    }

    /**
     * Validate a correlation ID value.
     * <p>
     * Rules:
     * - Must not be null or empty.
     * - Maximum length of 256 characters.
     * - Must not contain control characters.
     * - Must consist only of printable ASCII characters (space 0x20 through tilde 0x7E).
     */
    private boolean isValidCorrelationId(String id) {
        if (id == null || id.isEmpty()) {
            return false;
        }
        if (id.length() > MAX_CORRELATION_ID_LENGTH) {
            return false;
        }
        return VALID_CHARACTERS.matcher(id).matches();
    }
}