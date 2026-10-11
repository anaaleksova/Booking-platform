package com.bookingsaas.integration.auth;

import com.bookingsaas.common.error.ApiErrorResponse;
import com.bookingsaas.common.error.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Authenticates {@code /api/integrations/**} with an integration token — and
 * only with one: a signed-in user's JWT is not accepted here, and an
 * integration token is not accepted anywhere else.
 *
 * <p>Also puts the tracing context n8n sends (execution, conversation and
 * external user ids) into the log MDC, so one automated booking can be
 * followed through the logs.
 *
 * <p>Not a Spring bean on purpose: as a bean it would also be registered as a
 * servlet filter for every request.
 */
public class IntegrationAuthenticationFilter extends OncePerRequestFilter {

    public static final String EXECUTION_HEADER = "X-Workflow-Execution-Id";
    public static final String CONVERSATION_HEADER = "X-Conversation-Id";
    public static final String EXTERNAL_USER_HEADER = "X-External-User-Id";
    /** The authority every authenticated integration principal carries. */
    public static final String AUTHORITY = "INTEGRATION";
    static final String MDC_KEY = "integration";
    private static final String BEARER_PREFIX = "Bearer ";

    private final IntegrationTokenService tokenService;
    private final ObjectMapper objectMapper;

    public IntegrationAuthenticationFilter(IntegrationTokenService tokenService, ObjectMapper objectMapper) {
        this.tokenService = tokenService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            reject(request, response, "An integration token is required.");
            return;
        }
        Optional<IntegrationPrincipal> principal = tokenService.authenticate(header.substring(BEARER_PREFIX.length()).trim());
        if (principal.isEmpty()) {
            reject(request, response, "Invalid or revoked integration token.");
            return;
        }

        var authentication = new UsernamePasswordAuthenticationToken(principal.get(), null,
                List.of(new SimpleGrantedAuthority(AUTHORITY)));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        MDC.put(MDC_KEY, traceOf(principal.get(), request));
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    /** e.g. " business=1 token=3 execution=812 conversation=44 externalUser=1789" */
    private static String traceOf(IntegrationPrincipal principal, HttpServletRequest request) {
        StringBuilder trace = new StringBuilder(" business=").append(principal.businessId())
                .append(" token=").append(principal.tokenId());
        append(trace, "execution", request.getHeader(EXECUTION_HEADER));
        append(trace, "conversation", request.getHeader(CONVERSATION_HEADER));
        append(trace, "externalUser", request.getHeader(EXTERNAL_USER_HEADER));
        return trace.toString();
    }

    private static void append(StringBuilder trace, String name, String value) {
        if (value != null && !value.isBlank()) {
            // Header values end up in log lines: keep them short and on one line.
            String clean = value.replaceAll("[^A-Za-z0-9_.:-]", "");
            trace.append(' ').append(name).append('=').append(clean, 0, Math.min(clean.length(), 64));
        }
    }

    private void reject(HttpServletRequest request, HttpServletResponse response, String message) throws IOException {
        SecurityContextHolder.clearContext();
        response.setStatus(ErrorCode.UNAUTHENTICATED.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(),
                ApiErrorResponse.of(ErrorCode.UNAUTHENTICATED, message, request.getRequestURI()));
    }
}
