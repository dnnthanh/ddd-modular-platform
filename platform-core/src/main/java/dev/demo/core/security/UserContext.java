package dev.demo.core.security;

import dev.demo.core.constants.AppConstants;
import dev.demo.core.exception.ErrorCode;
import dev.demo.core.exception.type.UnauthorizedException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Per-request view of the database-backed principal and granted authorities. */
public class UserContext {

  private final UUID userId;
  private final Set<String> authorities;

  public UserContext(UUID userId, Set<String> authorities) {
    this.userId = userId;
    this.authorities = Objects.isNull(authorities) ? Set.of() : Set.copyOf(authorities);
  }

  /** Current principal ID, if request authentication is present. */
  public Optional<UUID> userIdOptional() {
    return Optional.ofNullable(userId);
  }

  /** Current principal ID or 401 when authentication is required. */
  public UUID userId() {
    return userIdOptional().orElseThrow(() -> new UnauthorizedException(ErrorCode.UNAUTHORIZED));
  }

  /** True when the authenticated user has the given database-defined role. */
  public boolean hasRole(String role) {
    return Objects.nonNull(role) && authorities.contains(AppConstants.Roles.PREFIX + role);
  }

  /** Only these two roles bypass permission checks. */
  public boolean isPrivileged() {
    return hasRole(AppConstants.Roles.ADMIN) || hasRole(AppConstants.Roles.SYSTEM_ADMIN);
  }

  /** Checks hardcoded permission authority, with the privileged-role bypass. */
  public boolean hasPermission(String permission) {
    return Objects.nonNull(permission) && (isPrivileged() || authorities.contains(permission));
  }
}
