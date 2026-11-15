package com.stocknews.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Lets the Angular UI (folio-pro, http://localhost:4200 by default) call this service's /api endpoints
 * directly from the browser. Origins are comma-separated in cors.allowed-origins.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final String[] allowedOrigins;

    public WebConfig(@Value("${cors.allowed-origins:http://localhost:4200}") String[] allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("Content-Type", "Authorization")
                .maxAge(3600);
    }
}
