package com.travel_system.backend_app.config;

import com.travel_system.backend_app.infrastructure.BootstrapRateLimitInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final BootstrapRateLimitInterceptor bootstrapRateLimitInterceptor;

    public WebConfig(BootstrapRateLimitInterceptor bootstrapRateLimitInterceptor) {
        this.bootstrapRateLimitInterceptor = bootstrapRateLimitInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(bootstrapRateLimitInterceptor)
                .addPathPatterns("/v1/platform-admin/bootstrap")
                .addPathPatterns("/v1/security/sensitive-operations/**");
    }
}
