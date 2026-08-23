package com.bookingsaas.auth;

import com.bookingsaas.common.error.ApiErrorResponse;
import com.bookingsaas.common.error.ErrorCode;
import com.bookingsaas.common.security.AuthenticatedUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Turns a {@code Bearer} token into an {@link AuthenticatedUser} principal.
 *
 * <p>A missing token is not an error here — the filter simply leaves the
 * context empty and lets the authorization rules decide, which is what keeps
 * the public booking endpoints open. An invalid or expired token, on the other
 * hand, is answered immediately so the frontend can distinguish "log in" from
 * "your session ended".
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final PrincipalLoader principalLoader;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(JwtService jwtService, PrincipalLoader principalLoader, ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.principalLoader = principalLoader;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(BEARER_PREFIX.length()).trim();
        try {
            Claims claims = jwtService.parse(token).getPayload();
            Long userId = Long.valueOf(claims.getSubject());
            Long businessId = claims.get(JwtService.CLAIM_BUSINESS_ID, Number.class) instanceof Number number
                    ? number.longValue()
                    : null;

            AuthenticatedUser principal = principalLoader.loadByUserId(userId, businessId);
            if (!principal.isEnabled()) {
                reject(request, response, ErrorCode.ACCESS_DENIED, "This account has been deactivated.");
                return;
            }

            var authentication = new UsernamePasswordAuthenticationToken(
                    principal, null, principal.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (ExpiredJwtException ex) {
            reject(request, response, ErrorCode.TOKEN_EXPIRED, "Your session has expired. Please sign in again.");
            return;
        } catch (JwtException | IllegalArgumentException ex) {
            // Never log the token itself.
            log.debug("Rejected token on {}: {}", request.getRequestURI(), ex.getMessage());
            reject(request, response, ErrorCode.UNAUTHENTICATED, "Invalid authentication token.");
            return;
        } catch (RuntimeException ex) {
            log.debug("Could not build principal on {}: {}", request.getRequestURI(), ex.getMessage());
            reject(request, response, ErrorCode.UNAUTHENTICATED, "Invalid authentication token.");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void reject(HttpServletRequest request, HttpServletResponse response, ErrorCode code, String message)
            throws IOException {
        SecurityContextHolder.clearContext();
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(),
                ApiErrorResponse.of(code, message, request.getRequestURI()));
    }
}
