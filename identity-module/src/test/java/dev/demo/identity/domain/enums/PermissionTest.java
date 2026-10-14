package dev.demo.identity.domain.enums;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PermissionTest {
  @Test
  void enumProvidesStableCodesForRoleAssignments() {
    assertEquals("CATALOG_READ", Permission.CATALOG_READ.getCode());
    assertTrue(Permission.fromCode("USERS_MANAGE").isPresent());
    assertTrue(Permission.fromCode("custom:unknown").isEmpty());
  }
}
