package dev.demo.core.web;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(HttpAuditProperties.class)
public class HttpAuditConfiguration {}
