package dev.demo.identity.application.port;

import dev.demo.identity.domain.entity.RefreshToken;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenPort {

  /** Persists refresh-token metadata without persisting the browser secret. */
  void insert(RefreshToken token);

  /** Finds a refresh token by its SHA-256 hash without taking a database lock. */
  Optional<RefreshToken> findByHash(String tokenHash);

  /** Finds and locks a refresh token so its family can be safely changed. */
  Optional<RefreshToken> lockByHash(String tokenHash);

  /** Marks one token as revoked after a successful rotation. */
  void markRevoked(UUID tokenId, Instant revokedAt);

  /** Revokes every active token in a family after replay detection or logout. */
  void revokeFamily(UUID familyId, Instant revokedAt);
}
