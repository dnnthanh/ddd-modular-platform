package dev.demo.identity.application.port;

import dev.demo.identity.domain.entity.UserAccount;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserAccountPort {

  /** Looks up an account by username. */
  Optional<UserAccount> findByUsername(String username);

  /** Finds an account by its ID. */
  Optional<UserAccount> findById(UUID id);

  /**
   * Locks a user row to serialize refresh-token rotation across every token family of that user.
   */
  Optional<UserAccount> lockById(UUID id);

  /** Persists a new account. */
  void insert(UserAccount user);

  /** Creates the initial privileged account if missing. */
  void insertBootstrapAdmin(UserAccount user);

  /** Replaces role assignments for a user. */
  void setRoles(UUID userId, List<String> roleCodes);

  /** Returns the role codes assigned to a user. */
  List<String> roles(UUID userId);

  /** Reads effective hardcoded permissions from user roles. */
  List<String> permissions(UUID userId);
}
