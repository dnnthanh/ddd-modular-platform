package dev.demo.identity.infrastructure.persistence.repository;

import dev.demo.identity.infrastructure.persistence.entity.UserAccountEntity;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserAccountRepository extends JpaRepository<UserAccountEntity, UUID> {

  /** Finds the account by its unique username. */
  Optional<UserAccountEntity> findByUsername(String username);

  /** Checks username uniqueness during development bootstrap. */
  boolean existsByUsername(String username);

  // Only role replacement needs serialized read/modify/write; ordinary inserts use save().

  /** Locks the account during role replacement. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select u from UserAccountEntity u where u.id = :id")
  Optional<UserAccountEntity> lockById(@Param("id") UUID id);

  /** Loads effective permissions by joining role associations. */
  @Query(
      value =
          """
      SELECT DISTINCT rp.permission_code
      FROM auth_user_role ur
      JOIN auth_role_permission rp ON rp.role_code = ur.role_code
      WHERE ur.user_id = :userId ORDER BY rp.permission_code
      """,
      nativeQuery = true)
  List<String> permissionCodes(@Param("userId") UUID userId);
}
