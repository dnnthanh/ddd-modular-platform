package dev.demo.core.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserContextTest {

  @Test
  void permissionsAreCheckedWithoutRolesForOrdinaryUsers() {
    var user = new UserContext(UUID.randomUUID(), Set.of("CATALOG_READ", "ROLE_CATALOG_VIEWER"));
    assertTrue(user.hasPermission("CATALOG_READ"));
    assertFalse(user.hasPermission("CATALOG_WRITE"));
    assertFalse(user.isPrivileged());
  }

  @Test
  void anonymousPrincipalHasEmptyUserIdAndNoPrivileges() {
    var anonymous = new UserContext(null, Set.of());
    assertTrue(anonymous.userIdOptional().isEmpty());
    assertFalse(anonymous.hasPermission("CATALOG_READ"));
    assertThrows(RuntimeException.class, anonymous::userId);
  }

  @Test
  void administratorBypassesPermissions() {
    var admin = new UserContext(UUID.randomUUID(), Set.of("ROLE_ADMIN"));
    var systemAdmin = new UserContext(UUID.randomUUID(), Set.of("ROLE_SYSTEM_ADMIN"));
    assertTrue(admin.hasPermission("USERS_MANAGE"));
    assertTrue(systemAdmin.hasPermission("CATALOG_WRITE"));
  }
}
