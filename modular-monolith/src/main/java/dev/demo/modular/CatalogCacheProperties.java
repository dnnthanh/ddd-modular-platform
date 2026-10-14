package dev.demo.modular;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.cache.catalog")
public record CatalogCacheProperties(Duration itemTtl) {}
