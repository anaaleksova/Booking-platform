package com.bookingsaas.common.security;

import com.bookingsaas.auth.JwtAuthenticationFilter;
import com.bookingsaas.common.config.AppProperties;
import com.bookingsaas.integration.auth.IntegrationAuthenticationFilter;
import com.bookingsaas.integration.auth.IntegrationTokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.config.Customizer;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Stateless JWT security.
 *
 * <p>Open areas: authentication, the customer-facing {@code /api/public/**}
 * API (except making a reservation, which needs a signed-in account), the API documentation and the health probe.
 * {@code /api/integrations/**} has its own chain and accepts integration tokens only. Everything else requires a principal — and business-level
 * authorization then happens in the service layer through {@link CurrentTenant},
 * because URL patterns cannot express "this row belongs to your tenant".
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final AppProperties properties;

    public SecurityConfig(AppProperties properties) {
        this.properties = properties;
    }

    /**
     * The integration API: integration tokens only, no sessions, no browser
     * CORS — it is called server to server by automation tools. Ordered first
     * so its matcher wins over the catch-all chain below.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain integrationFilterChain(HttpSecurity http,
                                                      IntegrationTokenService tokenService,
                                                      ObjectMapper objectMapper,
                                                      RestAuthenticationEntryPoint entryPoint) throws Exception {
        http
                .securityMatcher("/api/integrations/**")
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(entryPoint))
                .authorizeHttpRequests(auth -> auth.anyRequest().hasAuthority(IntegrationAuthenticationFilter.AUTHORITY))
                .addFilterBefore(new IntegrationAuthenticationFilter(tokenService, objectMapper),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthenticationFilter jwtAuthenticationFilter,
                                                   RestAuthenticationEntryPoint entryPoint) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(entryPoint))
                // API responses: no sniffing, no framing, no referrer leaking a booking reference.
                .headers(headers -> headers
                        .contentTypeOptions(Customizer.withDefaults())
                        .frameOptions(frame -> frame.deny())
                        .referrerPolicy(referrer -> referrer.policy(
                                ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/auth/register", "/api/auth/register/verify",
                                "/api/auth/customer/register", "/api/auth/customer/verify", "/api/auth/signup/resend",
                                "/api/auth/password/forgot", "/api/auth/password/reset",
                                "/api/auth/login", "/api/auth/logout", "/api/auth/session").permitAll()
                        // An invitation link is its own credential; accepting as an existing account needs a sign-in.
                        .requestMatchers(HttpMethod.GET, "/api/auth/invitations/*").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/invitations/*/accept-new").permitAll()
                        // Browsing is open; booking needs an account.
                        .requestMatchers(HttpMethod.POST, "/api/public/businesses/*/reservations").authenticated()
                        .requestMatchers("/api/public/**").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        List<String> origins = properties.cors() == null || properties.cors().allowedOrigins() == null
                ? List.of()
                : properties.cors().allowedOrigins();
        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("Location", "Retry-After"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
