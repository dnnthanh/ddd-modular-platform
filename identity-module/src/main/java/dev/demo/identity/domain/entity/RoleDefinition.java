package dev.demo.identity.domain.entity;

import java.util.Set;

/** Database-defined role; permission codes remain a closed list declared in source. */
public record RoleDefinition(String code, Set<String> permissions) {

  public RoleDefinition {
    permissions = Set.copyOf(permissions);
  }
}
