package dev.demo.identity.infrastructure.persistence;

import dev.demo.core.constants.AppConstants;
import dev.demo.core.exception.ErrorCode;
import dev.demo.core.exception.type.ForbiddenException;
import dev.demo.core.exception.type.NotFoundException;
import dev.demo.core.security.UserContext;
import dev.demo.core.stereotype.Persistence;
import dev.demo.identity.application.port.UserAccountPort;
import dev.demo.identity.domain.entity.UserAccount;
import dev.demo.identity.infrastructure.persistence.repository.UserAccountRepository;
import dev.demo.identity.mapper.IdentityMapper;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Persistence
@RequiredArgsConstructor
public class UserAccountPersistenceAdapter implements UserAccountPort {

  private final UserAccountRepository userAccountRepository;
  private final IdentityMapper identityMapper;
  private final UserContext userContext;

  @Override
  public Optional<UserAccount> findByUsername(String username) {
    return userAccountRepository.findByUsername(username).map(identityMapper::toDomain);
  }

  @Override
  public Optional<UserAccount> findById(UUID id) {
    return userAccountRepository.findById(id).map(identityMapper::toDomain);
  }

  @Override
  public void insert(UserAccount user) {
    userAccountRepository.saveAndFlush(identityMapper.toEntity(user));
  }

  @Override
  @Transactional
  public void insertBootstrapAdmin(UserAccount user) {
    // One-time dev bootstrap. Concurrent replica startup is guarded by DB username UNIQUE.
    if (userAccountRepository.existsByUsername(user.username())) {
      return;
    }
    var entity = identityMapper.toEntity(user);
    entity.getRoles().add(AppConstants.Roles.ADMIN);
    userAccountRepository.saveAndFlush(entity);
  }

  @Override
  @Transactional
  public void setRoles(UUID userId, List<String> roleCodes) {
    var user =
        userAccountRepository
            .lockById(userId)
            .orElseThrow(() -> new NotFoundException(ErrorCode.USER_NOT_FOUND));
    // Recheck under the row lock so a concurrent grant cannot bypass the service guard.
    if (!userContext.isPrivileged()
        && (user.getRoles().contains(AppConstants.Roles.ADMIN)
            || user.getRoles().contains(AppConstants.Roles.SYSTEM_ADMIN))) {
      throw new ForbiddenException(ErrorCode.FORBIDDEN);
    }
    user.getRoles().clear();
    user.getRoles().addAll(new HashSet<>(roleCodes));
  }

  @Override
  @Transactional(readOnly = true)
  public List<String> roles(UUID userId) {
    return userAccountRepository
        .findById(userId)
        .map(user -> user.getRoles().stream().sorted().toList())
        .orElseGet(List::of);
  }

  @Override
  public List<String> permissions(UUID userId) {
    return userAccountRepository.permissionCodes(userId);
  }
}
