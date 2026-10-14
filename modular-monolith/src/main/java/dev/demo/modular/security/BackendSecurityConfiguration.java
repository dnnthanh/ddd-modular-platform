package dev.demo.modular.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import dev.demo.core.cache.CacheDefinition;
import dev.demo.core.cache.CacheHelper;
import dev.demo.core.constants.AppConstants;
import dev.demo.core.exception.ErrorCode;
import dev.demo.core.i18n.MessageResolver;
import dev.demo.core.security.jwt.JwtKeyResources;
import dev.demo.core.security.jwt.JwtProperties;
import dev.demo.core.web.ApiFailure;
import dev.demo.core.web.ApiResponse;
import dev.demo.identity.application.RefreshTokenProperties;
import dev.demo.identity.application.port.UserAccountPort;
import dev.demo.identity.domain.entity.UserAccount;
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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
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
import org.springframework.security.web.access.ExceptionTranslationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.LocaleResolver;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
@EnableConfigurationProperties({
  JwtProperties.class,
  RefreshTokenProperties.class,
  AuthCookieProperties.class
})
@RequiredArgsConstructor
public class BackendSecurityConfiguration {

  private final MessageResolver messages;
  private final LocaleResolver localeResolver;

  @Bean
  CacheDefinition<Boolean> revokedJwtCache(JwtProperties properties) {
    return new CacheDefinition<>(
        "auth:revoked-jwt", Boolean.class, properties.ttl().plusSeconds(60));
  }

  /** Spring Security positions this filter; prevent the servlet container from adding it again. */
  @Bean
  FilterRegistrationBean<RefreshCsrfFilter> refreshCsrfFilterRegistration(
      RefreshCsrfFilter refreshCsrfFilter) {
    var registration = new FilterRegistrationBean<>(refreshCsrfFilter);
    registration.setEnabled(false);
    return registration;
  }

  @Bean
  JwtDecoder jwtDecoder(
      JwtProperties properties,
      CacheHelper cacheHelper,
      @Qualifier("revokedJwtCache") CacheDefinition<Boolean> revokedJwtCache) {
    var key = (RSAPublicKey) JwtKeyResources.readPublicKey(properties.publicKey());
    var decoder =
        NimbusJwtDecoder.withPublicKey(key).signatureAlgorithm(SignatureAlgorithm.RS256).build();
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(properties.issuer()),
            jwt -> {
              String jti = jwt.getId();
              if (StringUtils.isBlank(jti)
                  || cacheHelper
                      .get(revokedJwtCache, jti)
                      .filter(Boolean::booleanValue)
                      .isPresent()) {
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
          } catch (IllegalArgumentException _) {
            throw new InvalidBearerTokenException("Invalid token subject");
          }
          var account =
              users
                  .findById(userId)
                  .filter(UserAccount::enabled)
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
      HttpSecurity http,
      ObjectMapper mapper,
      JwtAuthenticationConverter converter,
      RefreshCsrfFilter refreshCsrfFilter,
      CorsConfigurationSource corsConfigurationSource) {
    http.csrf(AbstractHttpConfigurer::disable)
        .cors(cors -> cors.configurationSource(corsConfigurationSource))
        .addFilterAfter(refreshCsrfFilter, ExceptionTranslationFilter.class)
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
                        (request, response, _) ->
                            writeProblem(
                                request,
                                response,
                                mapper,
                                HttpStatus.UNAUTHORIZED,
                                ErrorCode.UNAUTHORIZED))
                    .accessDeniedHandler(
                        (request, response, _) ->
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
                        (request, response, _) ->
                            writeProblem(
                                request,
                                response,
                                mapper,
                                HttpStatus.UNAUTHORIZED,
                                ErrorCode.UNAUTHORIZED)));
    return http.build();
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource(AuthCookieProperties properties) {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(properties.allowedOrigins());
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Origin"));
    configuration.setAllowCredentials(true);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", configuration);
    return source;
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
