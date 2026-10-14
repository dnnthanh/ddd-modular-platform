package dev.demo.identity.application.port;

import dev.demo.identity.domain.entity.RoleDefinition;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Repository contract for dynamic database-defined roles. */
public interface RolePort {

  /** Finds a role by its stable code. */
  Optional<RoleDefinition> findByCode(String code);

  /** Lists roles without exposing JPA entities. */
  List<RoleDefinition> findAll();

  /** Returns existing role codes for validation before role assignment. */
  Set<String> existingCodes(Collection<String> codes);

  /** Persists a new role and its permission associations. */
  RoleDefinition create(RoleDefinition role);

  /** Replaces the permissions of an existing role. */
  RoleDefinition updatePermissions(String code, Set<String> permissions);
}
