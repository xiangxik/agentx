package com.agentx.backend.auth.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agentx.bootstrap.super-admin")
public record BootstrapProperties(String email, String password, String displayName) {}
