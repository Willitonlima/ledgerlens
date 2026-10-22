package com.ledgerlens.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;

    public WebConfig(Environment environment) {
        String origins = environment.getProperty("ledgerlens.allowed-origins");
        if (origins == null || origins.isBlank()) {
            origins = environment.getProperty("ALLOWED_ORIGINS");
        }
        if (environment.acceptsProfiles(Profiles.of("prod"))
                && (origins == null || origins.isBlank())) {
            throw new IllegalStateException("ALLOWED_ORIGINS é obrigatório no perfil prod.");
        }
        if (origins == null || origins.isBlank()) {
            origins = "http://localhost:5173";
        }
        this.allowedOrigins = StringUtils.commaDelimitedListToStringArray(origins);
        if (environment.acceptsProfiles(Profiles.of("prod"))
                && java.util.Arrays.stream(allowedOrigins).anyMatch("*"::equals)) {
            throw new IllegalStateException("ALLOWED_ORIGINS não pode conter curinga no perfil prod.");
        }
        if (allowedOrigins.length == 0 || java.util.Arrays.stream(allowedOrigins).anyMatch(String::isBlank)) {
            throw new IllegalStateException("ALLOWED_ORIGINS deve conter ao menos uma origem válida.");
        }
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "DELETE", "OPTIONS")
                .allowedHeaders("Content-Type", "X-Workspace-Id");
    }
}
