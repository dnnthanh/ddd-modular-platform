package dev.demo.identity.application.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.demo.identity.application.RefreshTokenProperties;
import dev.demo.identity.application.port.RefreshTokenPort;
import dev.demo.identity.application.port.UserAccountPort;
import dev.demo.identity.domain.entity.RefreshToken;
import dev.demo.identity.domain.entity.UserAccount;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RefreshTokenApplicationServiceTest {
  private final RefreshTokenPort tokens = mock(RefreshTokenPort.class);
  private final UserAccountPort users = mock(UserAccountPort.class);
  private final RefreshTokenApplicationService service =
      new RefreshTokenApplicationService(
          tokens, users, new RefreshTokenProperties(Duration.ofDays(30)));

  @Test
  void rotationPreservesFamilyAndAbsoluteExpiry() {
    UUID userId = UUID.randomUUID();
    String presented = "presented";
    Instant expiry = Instant.now().plus(Duration.ofDays(7));
    RefreshToken current =
        RefreshToken.issue(
            userId, UUID.randomUUID(), RefreshTokenApplicationService.hash(presented), expiry);
    when(tokens.findByHash(RefreshTokenApplicationService.hash(presented)))
        .thenReturn(Optional.of(current));
    when(users.lockById(userId))
        .thenReturn(Optional.of(new UserAccount(userId, "user", "hash", true)));
    when(tokens.lockByHash(RefreshTokenApplicationService.hash(presented)))
        .thenReturn(Optional.of(current));

    var result = service.rotate(presented);

    ArgumentCaptor<RefreshToken> replacement = ArgumentCaptor.forClass(RefreshToken.class);
    verify(tokens).insert(replacement.capture());
    assertTrue(result.accepted());
    assertTrue(current.familyId().equals(replacement.getValue().familyId()));
    assertTrue(current.expiresAt().equals(replacement.getValue().expiresAt()));
    verify(tokens).markRevoked(eq(current.id()), any(Instant.class));
  }

  @Test
  void replayRevokesFamilyWithoutMintingAnotherSecret() {
    UUID userId = UUID.randomUUID();
    String presented = "replayed";
    RefreshToken revoked =
        new RefreshToken(
            UUID.randomUUID(),
            userId,
            UUID.randomUUID(),
            RefreshTokenApplicationService.hash(presented),
            Instant.now().plus(Duration.ofDays(7)),
            Instant.now());
    when(tokens.findByHash(RefreshTokenApplicationService.hash(presented)))
        .thenReturn(Optional.of(revoked));
    when(users.lockById(userId))
        .thenReturn(Optional.of(new UserAccount(userId, "user", "hash", true)));
    when(tokens.lockByHash(RefreshTokenApplicationService.hash(presented)))
        .thenReturn(Optional.of(revoked));

    var result = service.rotate(presented);

    assertFalse(result.accepted());
    verify(tokens).revokeFamily(eq(revoked.familyId()), any(Instant.class));
    verify(tokens, never()).insert(any());
  }

  @Test
  void revokeTakesTheSharedUserLockBeforeTheTokenLock() {
    UUID userId = UUID.randomUUID();
    String presented = "logout";
    RefreshToken token =
        RefreshToken.issue(
            userId,
            UUID.randomUUID(),
            RefreshTokenApplicationService.hash(presented),
            Instant.now().plus(Duration.ofDays(7)));
    when(tokens.findByHash(RefreshTokenApplicationService.hash(presented)))
        .thenReturn(Optional.of(token));
    when(users.lockById(userId))
        .thenReturn(Optional.of(new UserAccount(userId, "user", "hash", true)));
    when(tokens.lockByHash(RefreshTokenApplicationService.hash(presented)))
        .thenReturn(Optional.of(token));

    service.revoke(presented);

    var ordered = inOrder(tokens, users);
    ordered.verify(tokens).findByHash(RefreshTokenApplicationService.hash(presented));
    ordered.verify(users).lockById(userId);
    ordered.verify(tokens).lockByHash(RefreshTokenApplicationService.hash(presented));
    ordered.verify(tokens).revokeFamily(eq(token.familyId()), any(Instant.class));
  }
}
