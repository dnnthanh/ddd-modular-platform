package dev.demo.identity.domain.enums;

import dev.demo.core.constants.AppConstants;
import java.util.Arrays;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Permission codes are UPPER_SNAKE_CASE and must match the role-permission seed data. */
@Getter
@RequiredArgsConstructor
public enum Permission {
  CATALOG_READ(AppConstants.Permissions.CATALOG_READ),
  CATALOG_WRITE(AppConstants.Permissions.CATALOG_WRITE),
  USERS_READ(AppConstants.Permissions.USERS_READ),
  USERS_MANAGE(AppConstants.Permissions.USERS_MANAGE);

  private final String code;

  public static Optional<Permission> fromCode(String value) {
    return Arrays.stream(values()).filter(permission -> permission.code.equals(value)).findFirst();
  }
}
