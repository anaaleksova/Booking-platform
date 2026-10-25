package com.bookingsaas.common.security;

import com.bookingsaas.common.error.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * The platform API ({@code /api/platform/**}) is for platform administrators
 * only, whatever business they are in — decided in one place, like
 * {@link AdminAccessInterceptor} for the business admin.
 */
@Component
public class PlatformAccessInterceptor implements HandlerInterceptor {

    private final CurrentTenant currentTenant;

    public PlatformAccessInterceptor(CurrentTenant currentTenant) {
        this.currentTenant = currentTenant;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        if (!currentTenant.user().isPlatformAdmin()) {
            throw ApiException.accessDenied("Only a platform administrator may do this.");
        }
        return true;
    }
}
