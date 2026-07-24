package com.example.sharepointdocusign.config;

import com.example.sharepointdocusign.dto.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Optional shared-secret protection for the /api/** endpoints. The REST API
 * has no other authentication of its own, so once this service is reachable
 * from anywhere beyond localhost (e.g. deployed for SAP to call), a shared
 * API key is the minimum bar to stop anyone who finds the URL from triggering
 * real SharePoint retrieval / DocuSign envelopes using this service's
 * credentials.
 *
 * Disabled (fully permissive) when app.security.api-key / API_KEY is blank -
 * this keeps local development and the existing test suite working without
 * any extra setup. Once a value is configured, every /api/** request must
 * include a matching X-Api-Key header or it is rejected with 401. The key
 * itself is never logged.
 */
@Component
@Order(2)
public class ApiKeyFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyFilter.class);
    public static final String HEADER_NAME = "X-Api-Key";
    private static final String PROTECTED_PATH_PREFIX = "/api/";

    private final String configuredApiKey;
    private final ObjectMapper objectMapper;

    public ApiKeyFilter(@Value("${app.security.api-key:}") String configuredApiKey, ObjectMapper objectMapper) {
        this.configuredApiKey = configuredApiKey;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        boolean protectionEnabled = configuredApiKey != null && !configuredApiKey.isBlank();
        boolean isProtectedPath = request.getRequestURI().startsWith(PROTECTED_PATH_PREFIX);

        if (!protectionEnabled || !isProtectedPath) {
            filterChain.doFilter(request, response);
            return;
        }

        String providedKey = request.getHeader(HEADER_NAME);
        if (configuredApiKey.equals(providedKey)) {
            filterChain.doFilter(request, response);
            return;
        }

        log.warn("Rejected request to {} - missing or invalid {} header", request.getRequestURI(), HEADER_NAME);
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(
                ErrorResponse.of("UNAUTHORIZED", "Missing or invalid API key.", correlationId)));
    }
}
