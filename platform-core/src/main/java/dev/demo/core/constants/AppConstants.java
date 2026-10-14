package dev.demo.core.constants;

import lombok.experimental.UtilityClass;

/** Centralized code-level constants. Group by purpose rather than by technical layer. */
@UtilityClass
public class AppConstants {

  @UtilityClass
  public static class Permissions {

    public static final String CATALOG_READ = "CATALOG_READ";
    public static final String CATALOG_WRITE = "CATALOG_WRITE";
    public static final String USERS_READ = "USERS_READ";
    public static final String USERS_MANAGE = "USERS_MANAGE";
  }

  @UtilityClass
  public static class Roles {

    public static final String ADMIN = "ADMIN";
    public static final String SYSTEM_ADMIN = "SYSTEM_ADMIN";
    public static final String PREFIX = "ROLE_";
  }

  @UtilityClass
  public static class RedisKeys {

    public static final String REVOKED_JWT_PREFIX = "auth:revoked:";
  }
}
