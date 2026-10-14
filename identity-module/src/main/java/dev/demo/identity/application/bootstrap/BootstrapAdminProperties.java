package dev.demo.identity.application.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security.bootstrap-admin")
public record BootstrapAdminProperties(String username, String password) {}
