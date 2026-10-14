package dev.demo.gateway;

import dev.demo.core.security.jwt.JwtKeyResources;
import dev.demo.core.security.jwt.JwtProperties;
import java.security.interfaces.RSAPublicKey;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;

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
  SecurityWebFilterChain gatewayChain(ServerHttpSecurity http) {
    return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
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
