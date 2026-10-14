package dev.demo.identity.infrastructure.persistence;

import dev.demo.core.stereotype.Persistence;
import dev.demo.identity.application.port.RefreshTokenPort;
import dev.demo.identity.domain.entity.RefreshToken;
import dev.demo.identity.infrastructure.persistence.repository.RefreshTokenRepository;
import dev.demo.identity.mapper.IdentityMapper;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

@Persistence
@RequiredArgsConstructor
public class RefreshTokenPersistenceAdapter implements RefreshTokenPort {
  private final RefreshTokenRepository repository;
  private final IdentityMapper identityMapper;

  @Override
  public void insert(RefreshToken token) {
    repository.save(identityMapper.toRefreshTokenEntity(token));
  }

  @Override
  public Optional<RefreshToken> findByHash(String tokenHash) {
    return repository.findByTokenHash(tokenHash).map(identityMapper::toDomain);
  }

  @Override
  public Optional<RefreshToken> lockByHash(String tokenHash) {
    return repository.lockByTokenHash(tokenHash).map(identityMapper::toDomain);
  }

  @Override
  public void markRevoked(UUID tokenId, Instant revokedAt) {
    repository.findById(tokenId).ifPresent(token -> token.setRevokedAt(revokedAt));
  }

  @Override
  public void revokeFamily(UUID familyId, Instant revokedAt) {
    repository.revokeFamily(familyId, revokedAt);
  }
}
