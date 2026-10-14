package dev.demo.identity.infrastructure.persistence;

import dev.demo.core.exception.ErrorCode;
import dev.demo.core.exception.type.NotFoundException;
import dev.demo.core.stereotype.Persistence;
import dev.demo.identity.application.port.RolePort;
import dev.demo.identity.domain.entity.RoleDefinition;
import dev.demo.identity.infrastructure.persistence.entity.RoleEntity;
import dev.demo.identity.infrastructure.persistence.repository.RoleRepository;
import dev.demo.identity.mapper.IdentityMapper;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Persistence
@RequiredArgsConstructor
public class RolePersistenceAdapter implements RolePort {

  private final RoleRepository roleRepository;
  private final IdentityMapper identityMapper;

  @Override
  @Transactional(readOnly = true)
  public Optional<RoleDefinition> findByCode(String code) {
    return roleRepository.findById(code).map(identityMapper::toRoleDefinition);
  }

  @Override
  @Transactional(readOnly = true)
  public List<RoleDefinition> findAll() {
    return identityMapper.toRoleDefinitions(roleRepository.findAll());
  }

  @Override
  @Transactional(readOnly = true)
  public Set<String> existingCodes(Collection<String> codes) {
    return roleRepository.findAllById(codes).stream()
        .map(RoleEntity::getCode)
        .collect(Collectors.toUnmodifiableSet());
  }

  @Override
  @Transactional
  public RoleDefinition create(RoleDefinition role) {
    return identityMapper.toRoleDefinition(
        roleRepository.saveAndFlush(identityMapper.toRoleEntity(role)));
  }

  @Override
  @Transactional
  public RoleDefinition updatePermissions(String code, Set<String> permissions) {
    var entity =
        roleRepository
            .findById(code)
            .orElseThrow(() -> new NotFoundException(ErrorCode.ROLE_NOT_FOUND));
    entity.getPermissions().clear();
    entity.getPermissions().addAll(permissions);
    roleRepository.flush();
    return identityMapper.toRoleDefinition(entity);
  }
}
