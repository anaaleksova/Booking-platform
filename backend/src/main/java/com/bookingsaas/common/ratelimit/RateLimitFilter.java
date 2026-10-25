package com.bookingsaas.common.ratelimit;

import com.bookingsaas.common.error.ApiErrorResponse;
import com.bookingsaas.common.error.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

/**
 * Per-address limits on the endpoints anyone can call without signing in.
 *
 * <p>Runs before security, so a flood is turned away before any token is
 * parsed or any query is made. The address is the client's, not the proxy's:
 * {@code server.forward-headers-strategy} resolves it from X-Forwarded-For.
 * Limits per account (failed logins, codes sent) live in {@code AuthService}.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final AntPathMatcher MATCHER = new AntPathMatcher();
    private static final Duration MINUTE = Duration.ofMinutes(1);

    /** First match wins. */
    private record Rule(String name, HttpMethod method, String pattern, int perMinute) {
    }

    private static final List<Rule> RULES = List.of(
            // Login, sign-up, codes, password reset, invitations: guessing territory.
            new Rule("auth", HttpMethod.POST, "/api/auth/**", 20),
            // Availability runs the engine over up to a few weeks: the most expensive public read.
            new Rule("availability", HttpMethod.GET, "/api/public/businesses/*/services/*/availability", 120),
            // Events run the same engine to tell whether they are fully booked.
            new Rule("events", HttpMethod.GET, "/api/public/businesses/*/events", 60),
            // Quotes, bookings and cancellations.
            new Rule("public-write", HttpMethod.POST, "/api/public/**", 30));

    private final RateLimiter limiter;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(RateLimiter limiter, ObjectMapper objectMapper) {
        this.limiter = limiter;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        Rule rule = RULES.stream()
                .filter(candidate -> candidate.method().matches(request.getMethod())
                        && MATCHER.match(candidate.pattern(), path))
                .findFirst()
                .orElse(null);
        if (rule != null) {
            String key = "ip:" + rule.name() + ":" + request.getRemoteAddr();
            if (!limiter.tryAcquire(key, rule.perMinute(), MINUTE)) {
                reject(request, response, limiter.secondsUntilReset(key));
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private void reject(HttpServletRequest request, HttpServletResponse response, long retryAfterSeconds)
            throws IOException {
        response.setStatus(ErrorCode.TOO_MANY_REQUESTS.status().value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), ApiErrorResponse.of(ErrorCode.TOO_MANY_REQUESTS,
                "Too many requests. Please wait a moment and try again.", request.getRequestURI()));
    }
}
