package com.agentx.backend.auth.application;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(BootstrapProperties.class)
public class AuthBootstrapConfiguration {}
