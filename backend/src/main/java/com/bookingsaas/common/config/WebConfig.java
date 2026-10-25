package com.bookingsaas.common.config;

import com.bookingsaas.common.security.AdminAccessInterceptor;
import com.bookingsaas.common.security.PlatformAccessInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AdminAccessInterceptor adminAccessInterceptor;
    private final PlatformAccessInterceptor platformAccessInterceptor;

    public WebConfig(AdminAccessInterceptor adminAccessInterceptor, PlatformAccessInterceptor platformAccessInterceptor) {
        this.adminAccessInterceptor = adminAccessInterceptor;
        this.platformAccessInterceptor = platformAccessInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminAccessInterceptor).addPathPatterns("/api/admin/**");
        registry.addInterceptor(platformAccessInterceptor).addPathPatterns("/api/platform/**");
    }
}
