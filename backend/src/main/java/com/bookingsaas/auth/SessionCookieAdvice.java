package com.bookingsaas.auth;

import com.bookingsaas.auth.dto.AuthResponse;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * Every endpoint that starts a session (login, sign-up, password reset,
 * switching business, accepting an invitation) returns an {@link AuthResponse};
 * this sets its token as the session cookie on the way out, so no endpoint can
 * forget to, and the token never appears in a response body.
 */
@RestControllerAdvice
public class SessionCookieAdvice implements ResponseBodyAdvice<Object> {

    private final SessionCookies cookies;

    public SessionCookieAdvice(SessionCookies cookies) {
        this.cookies = cookies;
    }

    @Override
    public boolean supports(MethodParameter returnType, @NonNull Class<? extends HttpMessageConverter<?>> converterType) {
        return AuthResponse.class.isAssignableFrom(returnType.getParameterType());
    }

    @Override
    public Object beforeBodyWrite(Object body,
                                  @NonNull MethodParameter returnType,
                                  @NonNull MediaType selectedContentType,
                                  @NonNull Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  @NonNull ServerHttpRequest request,
                                  @NonNull ServerHttpResponse response) {
        if (body instanceof AuthResponse session && session.accessToken() != null) {
            response.getHeaders().add(HttpHeaders.SET_COOKIE, cookies.issue(session.accessToken(), session.expiresAt()));
        }
        return body;
    }
}
