package dev.demo.core.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CustomPreAuthorizerTest {

  @Test
  void matchesAnyGrantedPermission() {
    var context = new UserContext(UUID.randomUUID(), Set.of("CATALOG_READ"));
    var authorizer = new CustomPreAuthorizer(context);

    assertTrue(authorizer.hasAnyPermission(List.of("CATALOG_WRITE", "CATALOG_READ")));
    assertFalse(authorizer.hasAnyPermission(List.of("USERS_MANAGE", "CATALOG_WRITE")));
    assertFalse(authorizer.hasAnyPermission(List.of()));
  }

  @Test
  void adminBypassesAnyRequestedPermission() {
    var context = new UserContext(UUID.randomUUID(), Set.of("ROLE_ADMIN"));
    var authorizer = new CustomPreAuthorizer(context);

    assertTrue(authorizer.hasAnyPermission(List.of("USERS_MANAGE")));
    assertTrue(authorizer.hasPermission("CATALOG_WRITE"));
    assertFalse(authorizer.hasAnyPermission(List.of()));
  }

  @Test
  void systemAdminBypassesAnyRequestedPermission() {
    var context = new UserContext(UUID.randomUUID(), Set.of("ROLE_SYSTEM_ADMIN"));
    var authorizer = new CustomPreAuthorizer(context);
    assertTrue(authorizer.hasAnyPermission(List.of("CATALOG_WRITE")));
  }
}
