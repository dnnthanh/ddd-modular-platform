package dev.demo.gateway;

import dev.demo.core.security.jwt.JwtKeyResources;
import dev.demo.core.security.jwt.JwtProperties;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtProperties.class)
public class GatewaySecurityConfiguration {

  @Bean
  ReactiveJwtDecoder reactiveJwtDecoder(JwtProperties properties) {
    var publicKey = (RSAPublicKey) JwtKeyResources.readPublicKey(properties.publicKey());
    var decoder =
        NimbusReactiveJwtDecoder.withPublicKey(publicKey)
            .signatureAlgorithm(SignatureAlgorithm.RS256)
            .build();
    decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.issuer()));
    return decoder;
  }

  @Bean
  CorsConfigurationSource gatewayCorsConfiguration(
      @Value("${AUTH_ALLOWED_ORIGINS:http://localhost:8088}") String origins) {
    List<String> allowed =
        Arrays.stream(origins.split(","))
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .toList();
    if (allowed.stream().anyMatch(value -> value.contains("*")))
      throw new IllegalArgumentException("Explicit frontend origins required");
    var config = new CorsConfiguration();
    config.setAllowedOrigins(allowed);
    config.setAllowCredentials(true);
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept-Language"));
    var source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", config);
    return source;
  }

  @Bean
  SecurityWebFilterChain gatewayChain(ServerHttpSecurity http, CorsConfigurationSource cors) {
    return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
        .cors(spec -> spec.configurationSource(cors))
        .authorizeExchange(
            auth ->
                auth.pathMatchers("/actuator/health/**", "/actuator/prometheus", "/api/public/**")
                    .permitAll()
                    .pathMatchers("/api/private/**")
                    .authenticated()
                    .anyExchange()
                    .denyAll())
        .oauth2ResourceServer(resource -> resource.jwt(jwt -> {}))
        .build();
  }
}
