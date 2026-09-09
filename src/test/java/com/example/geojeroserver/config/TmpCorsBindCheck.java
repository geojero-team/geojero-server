package com.example.geojeroserver.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;

class TmpCorsBindCheck {

    @Configuration
    @EnableConfigurationProperties(SecurityConfig.CorsProperties.class)
    static class Cfg {
    }

    @Test
    void diagnose() {
        try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder(Cfg.class)
                .web(WebApplicationType.NONE)
                .properties("DB_HOST=localhost", "DB_PORT=5432", "DB_NAME=geojero",
                        "DB_USERNAME=u", "DB_PASSWORD=p",
                        "CORS_ALLOWED_ORIGINS=https://geojero.com,https://www.geojero.com")
                .run("--spring.profiles.active=prod")) {

            ConfigurableEnvironment env = ctx.getEnvironment();
            System.out.println("DIAG bound = " + ctx.getBean(SecurityConfig.CorsProperties.class).allowedOrigins());
            System.out.println("DIAG env[cors.allowed-origins]   = " + env.getProperty("cors.allowed-origins"));
            System.out.println("DIAG env[cors.allowed-origins[0]] = " + env.getProperty("cors.allowed-origins[0]"));

            for (PropertySource<?> ps : env.getPropertySources()) {
                if (ps instanceof EnumerablePropertySource<?> eps) {
                    for (String n : eps.getPropertyNames()) {
                        if (n.startsWith("cors")) {
                            System.out.println("DIAG source[" + ps.getName() + "] " + n + " = " + eps.getProperty(n));
                        }
                    }
                }
            }
        }
    }
}
