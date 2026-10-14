package dev.demo.identity.infrastructure.persistence.repository;

import dev.demo.identity.infrastructure.persistence.entity.RefreshTokenEntity;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, UUID> {
  Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select t from RefreshTokenEntity t where t.tokenHash = :tokenHash")
  Optional<RefreshTokenEntity> lockByTokenHash(@Param("tokenHash") String tokenHash);

  @Modifying
  @Query(
      "update RefreshTokenEntity t set t.revokedAt = :revokedAt where t.familyId = :familyId and t.revokedAt is null")
  void revokeFamily(@Param("familyId") UUID familyId, @Param("revokedAt") Instant revokedAt);
}
