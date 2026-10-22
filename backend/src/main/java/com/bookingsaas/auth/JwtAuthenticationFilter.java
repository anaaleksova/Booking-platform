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
import java.util.Set;

/**
 * Turns the session token (the browser's cookie, or a {@code Bearer} header)
 * into an {@link AuthenticatedUser} principal.
 *
 * <p>A missing token is not an error here — the filter simply leaves the
 * context empty and lets the authorization rules decide, which is what keeps
 * the public booking endpoints open. An invalid or expired Bearer token, on the
 * other hand, is answered immediately so a client can distinguish "log in" from
 * "your session ended". A dead session cookie is cleared and the request goes
 * on signed out, so a stale cookie never blocks the login form.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    private final JwtService jwtService;
    private final PrincipalLoader principalLoader;
    private final ObjectMapper objectMapper;
    private final SessionCookies sessionCookies;

    public JwtAuthenticationFilter(JwtService jwtService, PrincipalLoader principalLoader, ObjectMapper objectMapper,
                                   SessionCookies sessionCookies) {
        this.jwtService = jwtService;
        this.principalLoader = principalLoader;
        this.objectMapper = objectMapper;
        this.sessionCookies = sessionCookies;
    }

    /** The integration API authenticates with its own tokens, which are not JWTs. */
    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return request.getRequestURI().startsWith(request.getContextPath() + "/api/integrations/");
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        // A Bearer header (API clients, tests) wins; the browser sends the session cookie.
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        boolean fromCookie = header == null || !header.startsWith(BEARER_PREFIX);
        String token = fromCookie
                ? sessionCookies.read(request).orElse(null)
                : header.substring(BEARER_PREFIX.length()).trim();
        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }
        // A cookie is sent by the browser whoever made the page; a custom
        // header only by our own scripts. Writes need both.
        if (fromCookie && !SAFE_METHODS.contains(request.getMethod())
                && request.getHeader(SessionCookies.CSRF_HEADER) == null) {
            reject(request, response, ErrorCode.ACCESS_DENIED,
                    "Missing the " + SessionCookies.CSRF_HEADER + " header.");
            return;
        }

        ErrorCode failure = null;
        String message = null;
        try {
            Claims claims = jwtService.parse(token).getPayload();
            Long userId = Long.valueOf(claims.getSubject());
            Long businessId = claims.get(JwtService.CLAIM_BUSINESS_ID, Number.class) instanceof Number number
                    ? number.longValue()
                    : null;

            AuthenticatedUser principal = principalLoader.loadByUserId(userId, businessId);
            // Issued before the password last changed: that session is over.
            String passwordVersion = claims.get(JwtService.CLAIM_PASSWORD_VERSION, String.class);
            if (!principal.isEnabled()) {
                failure = ErrorCode.ACCESS_DENIED;
                message = "This account has been deactivated.";
            } else if (passwordVersion != null
                    && !passwordVersion.equals(JwtService.passwordFingerprint(principal.getPassword()))) {
                failure = ErrorCode.TOKEN_EXPIRED;
                message = "Your password was changed. Please sign in again.";
            } else {
                var authentication = new UsernamePasswordAuthenticationToken(
                        principal, null, principal.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (ExpiredJwtException ex) {
            failure = ErrorCode.TOKEN_EXPIRED;
            message = "Your session has expired. Please sign in again.";
        } catch (JwtException | IllegalArgumentException ex) {
            // Never log the token itself.
            log.debug("Rejected token on {}: {}", request.getRequestURI(), ex.getMessage());
            failure = ErrorCode.UNAUTHENTICATED;
            message = "Invalid authentication token.";
        } catch (RuntimeException ex) {
            log.debug("Could not build principal on {}: {}", request.getRequestURI(), ex.getMessage());
            failure = ErrorCode.UNAUTHENTICATED;
            message = "Invalid authentication token.";
        }

        if (failure != null) {
            if (!fromCookie) {
                reject(request, response, failure, message);
                return;
            }
            // A dead browser session: forget it and carry on signed out, so the
            // login form and public pages keep working. Anything that needs a
            // session then answers 401 through the usual entry point.
            SecurityContextHolder.clearContext();
            response.addHeader(HttpHeaders.SET_COOKIE, sessionCookies.clear());
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
