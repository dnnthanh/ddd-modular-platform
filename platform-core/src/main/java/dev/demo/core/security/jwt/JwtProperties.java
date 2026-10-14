package dev.demo.core.security.jwt;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

/** JWT signing/verification locations. A resource server must not configure a private key. */
@ConfigurationProperties(prefix = "app.security.jwt")
public record JwtProperties(Resource publicKey, Resource privateKey, String issuer, Duration ttl) {}
