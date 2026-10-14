package dev.demo.identity.domain.entity;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Persistent refresh-token metadata. The browser secret itself is never stored. */
public record RefreshToken(
    UUID id, UUID userId, UUID familyId, String tokenHash, Instant expiresAt, Instant revokedAt) {

  public boolean isUsableAt(Instant now) {
    return Objects.isNull(revokedAt) && expiresAt.isAfter(now);
  }

  public static RefreshToken issue(
      UUID userId, UUID familyId, String tokenHash, Instant expiresAt) {
    return new RefreshToken(UUID.randomUUID(), userId, familyId, tokenHash, expiresAt, null);
  }

  /** Creates the replacement token while retaining the family and its absolute expiration. */
  public static RefreshToken rotate(RefreshToken token, String replacementHash) {
    return new RefreshToken(
        UUID.randomUUID(), token.userId, token.familyId, replacementHash, token.expiresAt, null);
  }

  /** Returns revoked metadata for the presented token at the given instant. */
  public static RefreshToken revoke(RefreshToken token, Instant now) {
    return new RefreshToken(
        token.id, token.userId, token.familyId, token.tokenHash, token.expiresAt, now);
  }
}
