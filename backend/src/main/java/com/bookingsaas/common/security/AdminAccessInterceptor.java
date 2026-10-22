package com.bookingsaas.common.security;

import com.bookingsaas.common.error.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

/**
 * Who may use the admin API, decided in one place.
 *
 * <p>An owner may use all of it. A STAFF member may use only the short list
 * below: seeing the business, their own reservations, and marking them done.
 * Which reservations are "their own" is then decided per row by
 * {@code ReservationAccess}. Everything not on the list is owner-only, so a
 * new admin endpoint is closed to staff unless it is deliberately added here.
 *
 * <p>An interceptor rather than URL rules in the security chain, so refusals
 * come back in the application's usual error format.
 */
@Component
public class AdminAccessInterceptor implements HandlerInterceptor {

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    private record Allowed(HttpMethod method, String pattern) {
    }

    private static final List<Allowed> STAFF_ALLOWED = List.of(
            new Allowed(HttpMethod.GET, "/api/admin/business"),
            new Allowed(HttpMethod.GET, "/api/admin/services"),
            new Allowed(HttpMethod.GET, "/api/admin/reservations"),
            new Allowed(HttpMethod.GET, "/api/admin/reservations/{id}"),
            // Confirm, complete or mark a no-show; ReservationService refuses a cancellation from staff.
            new Allowed(HttpMethod.PATCH, "/api/admin/reservations/{id}"));

    private final CurrentTenant currentTenant;

    public AdminAccessInterceptor(CurrentTenant currentTenant) {
        this.currentTenant = currentTenant;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        BusinessMembership membership = currentTenant.membership();   // no business: NO_BUSINESS_CONTEXT
        if (membership.isOwner() || HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        boolean allowed = STAFF_ALLOWED.stream().anyMatch(rule -> rule.method().matches(request.getMethod())
                && MATCHER.match(rule.pattern(), path));
        if (!allowed) {
            throw ApiException.accessDenied("Only a business owner may do this.");
        }
        return true;
    }
}
