package dev.demo.core.security;

import dev.demo.core.exception.ErrorCode;
import dev.demo.core.exception.type.UnauthorizedException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.experimental.UtilityClass;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Safe accessors for the currently authenticated Spring Security principal. */
@UtilityClass
public class SecurityUtils {

  /** Returns an authenticated principal or an empty optional for anonymous requests. */
  public Optional<Authentication> authentication() {
    return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
        .filter(Authentication::isAuthenticated)
        .filter(auth -> !"anonymousUser".equals(auth.getPrincipal()));
  }

  /** Returns the authenticated UUID, or empty if no valid UUID principal exists. */
  public Optional<UUID> userIdOptional() {
    return authentication()
        .map(Authentication::getName)
        .flatMap(
            name -> {
              try {
                return Optional.of(UUID.fromString(name));
              } catch (IllegalArgumentException ex) {
                return Optional.empty();
              }
            });
  }

  /** Requires a valid authenticated UUID for a protected use case. */
  public UUID requireUserId() {
    return userIdOptional().orElseThrow(() -> new UnauthorizedException(ErrorCode.UNAUTHORIZED));
  }

  /** Checks a Spring Security role authority without depending on application UserContext. */
  public boolean hasRole(String role) {
    return Objects.nonNull(role)
        && authentication().stream()
            .flatMap(auth -> auth.getAuthorities().stream())
            .anyMatch(authority -> ("ROLE_" + role).equals(authority.getAuthority()));
  }

  /** Restricts the privileged bypass to the two system-defined administrator roles. */
  public boolean isPrivileged() {
    return hasRole("ADMIN") || hasRole("SYSTEM_ADMIN");
  }

  /** Uses the same permission policy as UserContext. */
  public boolean hasPermission(String permission) {
    return Objects.nonNull(permission)
        && (isPrivileged()
            || authentication().stream()
                .flatMap(auth -> auth.getAuthorities().stream())
                .anyMatch(authority -> permission.equals(authority.getAuthority())));
  }
}
