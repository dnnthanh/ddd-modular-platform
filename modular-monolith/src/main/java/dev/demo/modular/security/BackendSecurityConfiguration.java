package dev.demo.modular.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import dev.demo.core.constants.AppConstants;
import dev.demo.core.exception.ErrorCode;
import dev.demo.core.i18n.MessageResolver;
import dev.demo.core.security.jwt.JwtKeyResources;
import dev.demo.core.security.jwt.JwtProperties;
import dev.demo.core.web.ApiFailure;
import dev.demo.core.web.ApiResponse;
import dev.demo.identity.application.port.UserAccountPort;
import dev.demo.identity.domain.enums.Permission;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.LocaleResolver;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
@EnableConfigurationProperties(JwtProperties.class)
@RequiredArgsConstructor
public class BackendSecurityConfiguration {

  private final MessageResolver messages;
  private final LocaleResolver localeResolver;

  @Bean
  JwtDecoder jwtDecoder(JwtProperties properties, StringRedisTemplate redis) {
    var key = (RSAPublicKey) JwtKeyResources.readPublicKey(properties.publicKey());
    var decoder =
        NimbusJwtDecoder.withPublicKey(key).signatureAlgorithm(SignatureAlgorithm.RS256).build();
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(properties.issuer()),
            jwt -> {
              String jti = jwt.getId();
              if (StringUtils.isBlank(jti)
                  || Boolean.TRUE.equals(
                      redis.hasKey(AppConstants.RedisKeys.REVOKED_JWT_PREFIX + jti))) {
                return OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token", "JWT revoked or missing jti", null));
              }
              return OAuth2TokenValidatorResult.success();
            }));
    return decoder;
  }

  @Bean
  JwtEncoder jwtEncoder(JwtProperties properties) {
    var privateKey = (RSAPrivateKey) JwtKeyResources.readPrivateKey(properties.privateKey());
    var publicKey = (RSAPublicKey) JwtKeyResources.readPublicKey(properties.publicKey());
    var jwk = new RSAKey.Builder(publicKey).privateKey(privateKey).build();
    return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  JwtAuthenticationConverter jwtAuthenticationConverter(UserAccountPort users) {
    var converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(
        jwt -> {
          UUID userId;
          try {
            if (StringUtils.isBlank(jwt.getSubject())) {
              throw new IllegalArgumentException("Missing subject");
            }
            userId = UUID.fromString(jwt.getSubject());
          } catch (IllegalArgumentException ex) {
            throw new InvalidBearerTokenException("Invalid token subject");
          }
          var account =
              users
                  .findById(userId)
                  .filter(user -> user.enabled())
                  .orElseThrow(() -> new InvalidBearerTokenException("Inactive or unknown user"));
          List<GrantedAuthority> authorities = new ArrayList<>();
          users.roles(account.id()).stream()
              .filter(StringUtils::isNotBlank)
              .map(role -> new SimpleGrantedAuthority(AppConstants.Roles.PREFIX + role))
              .forEach(authorities::add);
          users.permissions(account.id()).stream()
              .filter(permission -> Permission.fromCode(permission).isPresent())
              .map(SimpleGrantedAuthority::new)
              .forEach(authorities::add);
          return authorities;
        });
    return converter;
  }

  @Bean
  SecurityFilterChain backendChain(
      HttpSecurity http, ObjectMapper mapper, JwtAuthenticationConverter converter)
      throws Exception {
    http.csrf(csrf -> csrf.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        "/actuator/health/**", "/actuator/prometheus", "/api/public/**")
                    .permitAll()
                    .requestMatchers("/api/private/**")
                    .authenticated()
                    .anyRequest()
                    .denyAll())
        .exceptionHandling(
            handler ->
                handler
                    .authenticationEntryPoint(
                        (request, response, ex) ->
                            writeProblem(
                                request,
                                response,
                                mapper,
                                HttpStatus.UNAUTHORIZED,
                                ErrorCode.UNAUTHORIZED))
                    .accessDeniedHandler(
                        (request, response, ex) ->
                            writeProblem(
                                request,
                                response,
                                mapper,
                                HttpStatus.FORBIDDEN,
                                ErrorCode.FORBIDDEN)))
        .oauth2ResourceServer(
            oauth ->
                oauth
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(converter))
                    .authenticationEntryPoint(
                        (request, response, ex) ->
                            writeProblem(
                                request,
                                response,
                                mapper,
                                HttpStatus.UNAUTHORIZED,
                                ErrorCode.UNAUTHORIZED)));
    return http.build();
  }

  private void writeProblem(
      HttpServletRequest request,
      HttpServletResponse response,
      ObjectMapper mapper,
      HttpStatus status,
      ErrorCode code)
      throws IOException {
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    if (status == HttpStatus.UNAUTHORIZED) {
      response.setHeader("WWW-Authenticate", "Bearer");
    }
    String message = messages.resolve(code, localeResolver.resolveLocale(request));
    var failure =
        new ApiFailure(
            code.name(), code.getKind().getCategory(), message, request.getRequestURI(), Map.of());
    response.getOutputStream().write(mapper.writeValueAsBytes(ApiResponse.failure(failure)));
  }
}
