package dev.demo.core.security;

import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.annotation.RequestScope;

@Configuration(proxyBeanMethods = false)
public class UserContextConfiguration {

  /**
   * Creates request-local security data.
   *
   * <p>{@code @RequestScope} defaults to a TARGET_CLASS scoped proxy. Keep the proxy because
   * singleton use cases and the singleton CustomPreAuthorizer inject this request-scoped bean. With
   * ScopedProxyMode.NO, singleton injection would resolve the bean outside a request.
   */
  @Bean
  @RequestScope
  UserContext userContext() {
    var userId = SecurityUtils.userIdOptional();
    var authorities =
        userId
            .flatMap(ignored -> SecurityUtils.authentication())
            .map(
                authentication ->
                    authentication.getAuthorities().stream()
                        .map(authority -> authority.getAuthority())
                        .collect(Collectors.toUnmodifiableSet()))
            .orElseGet(Set::of);
    return new UserContext(userId.orElse(null), authorities);
  }

  @Bean("customPreAuthorizer")
  CustomPreAuthorizer customPreAuthorizer(UserContext currentUser) {
    return new CustomPreAuthorizer(currentUser);
  }
}
