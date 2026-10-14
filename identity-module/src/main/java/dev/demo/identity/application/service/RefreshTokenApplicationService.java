package dev.demo.identity.application.service;

import dev.demo.core.exception.ErrorCode;
import dev.demo.core.exception.type.UnauthorizedException;
import dev.demo.core.stereotype.UseCase;
import dev.demo.identity.application.RefreshTokenProperties;
import dev.demo.identity.application.port.RefreshTokenPort;
import dev.demo.identity.application.port.UserAccountPort;
import dev.demo.identity.domain.entity.RefreshToken;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.transaction.annotation.Transactional;

/** Coordinates opaque refresh-token issue, rotation, and replay containment. */
@UseCase
@RequiredArgsConstructor
public class RefreshTokenApplicationService {
  private static final SecureRandom RANDOM = new SecureRandom();
  private final RefreshTokenPort refreshTokens;
  private final UserAccountPort users;
  private final RefreshTokenProperties properties;

  @Transactional
  public IssuedRefreshToken issue(UUID userId) {
    String raw = newSecret();
    Instant now = Instant.now();
    refreshTokens.insert(
        RefreshToken.issue(userId, UUID.randomUUID(), hash(raw), now.plus(properties.ttl())));
    return new IssuedRefreshToken(userId, raw);
  }

  /**
   * Locks the presented row. A replay revokes the entire family and returns normally so that the
   * containment update commits before the outer use case returns its unauthorized response.
   */
  @Transactional
  public RotationResult rotate(String rawToken) {
    if (StringUtils.isBlank(rawToken)) {
      return RotationResult.rejected();
    }
    Instant now = Instant.now();
    String tokenHash = hash(rawToken);
    // Discover the owner before taking the shared user lock. The lock is the family serialization
    // point: rotating token A cannot insert token B while a replay of A revokes that family.
    var discovered = refreshTokens.findByHash(tokenHash);
    if (discovered.isEmpty()) {
      return RotationResult.rejected();
    }
    users
        .lockById(discovered.get().userId())
        .orElseThrow(() -> new UnauthorizedException(ErrorCode.UNAUTHORIZED));
    var current = refreshTokens.lockByHash(tokenHash);
    if (current.isEmpty()) return RotationResult.rejected();
    RefreshToken token = current.get();
    if (!token.isUsableAt(now)) {
      refreshTokens.revokeFamily(token.familyId(), now);
      return RotationResult.rejected();
    }
    refreshTokens.markRevoked(RefreshToken.revoke(token, now).id(), now);
    String replacement = newSecret();
    // Keep the family's original absolute expiry: rotation must not create an endless session.
    refreshTokens.insert(RefreshToken.rotate(token, hash(replacement)));
    return RotationResult.rotated(token.userId(), replacement);
  }

  @Transactional
  public void revoke(String rawToken) {
    if (StringUtils.isBlank(rawToken)) {
      return;
    }
    var discovered = refreshTokens.findByHash(hash(rawToken));
    if (discovered.isEmpty()) {
      return;
    }
    // Take locks in the same order as rotate: user first, then refresh token.
    users.lockById(discovered.get().userId());
    refreshTokens
        .lockByHash(hash(rawToken))
        .ifPresent(token -> refreshTokens.revokeFamily(token.familyId(), Instant.now()));
  }

  private static String newSecret() {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  static String hash(String rawToken) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(rawToken.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 unavailable", exception);
    }
  }

  public record IssuedRefreshToken(UUID userId, String rawToken) {}

  public record RotationResult(UUID userId, String rawToken) {
    static RotationResult rotated(UUID userId, String rawToken) {
      return new RotationResult(userId, rawToken);
    }

    static RotationResult rejected() {
      return new RotationResult(null, null);
    }

    public boolean accepted() {
      return Objects.nonNull(userId);
    }
  }
}
