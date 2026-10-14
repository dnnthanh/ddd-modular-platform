package dev.demo.identity.application.service;

import dev.demo.core.constants.AppConstants;
import dev.demo.core.exception.ErrorCode;
import dev.demo.core.exception.type.BadRequestException;
import dev.demo.core.exception.type.ConflictException;
import dev.demo.core.exception.type.ForbiddenException;
import dev.demo.core.security.UserContext;
import dev.demo.core.stereotype.UseCase;
import dev.demo.identity.application.command.CreateRoleCommand;
import dev.demo.identity.application.command.UpdateRolePermissionsCommand;
import dev.demo.identity.application.model.RoleModel;
import dev.demo.identity.application.port.RolePort;
import dev.demo.identity.application.usecase.RoleUseCase;
import dev.demo.identity.domain.entity.RoleDefinition;
import dev.demo.identity.domain.enums.Permission;
import dev.demo.identity.mapper.IdentityMapper;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

@UseCase
@RequiredArgsConstructor
public class RoleApplicationService implements RoleUseCase {

  private final RolePort rolePort;
  private final IdentityMapper identityMapper;
  private final UserContext userContext;

  @Override
  @Transactional
  public RoleModel create(CreateRoleCommand command) {
    validateRoleCode(command.code());
    validatePermissions(command.permissions());
    if (rolePort.findByCode(command.code()).isPresent()) {
      throw new ConflictException(ErrorCode.DUPLICATE_ROLE);
    }
    try {
      return identityMapper.toRoleModel(
          rolePort.create(new RoleDefinition(command.code(), command.permissions())));
    } catch (DataIntegrityViolationException ex) {
      throw new ConflictException(ErrorCode.DUPLICATE_ROLE);
    }
  }

  @Override
  @Transactional(readOnly = true)
  public List<RoleModel> list() {
    return identityMapper.toRoleModels(rolePort.findAll());
  }

  @Override
  @Transactional
  public RoleModel updatePermissions(UpdateRolePermissionsCommand command) {
    validateRoleCode(command.code());
    validatePermissions(command.permissions());
    return identityMapper.toRoleModel(
        rolePort.updatePermissions(command.code(), command.permissions()));
  }

  private void validateRoleCode(String code) {
    if (AppConstants.Roles.ADMIN.equals(code) || AppConstants.Roles.SYSTEM_ADMIN.equals(code)) {
      throw new ForbiddenException(ErrorCode.FORBIDDEN);
    }
    if (StringUtils.isBlank(code) || !code.matches("[A-Z][A-Z0-9_]{1,63}")) {
      throw new BadRequestException(ErrorCode.INVALID_ROLE, code);
    }
  }

  private void validatePermissions(Set<String> permissions) {
    if (CollectionUtils.isEmpty(permissions)) {
      throw new BadRequestException(ErrorCode.INVALID_PERMISSION);
    }
    for (String permission : permissions) {
      if (Permission.fromCode(permission).isEmpty()) {
        throw new BadRequestException(ErrorCode.INVALID_PERMISSION);
      }
      // USERS_MANAGE alone cannot grant rights the caller does not already hold.
      if (!userContext.hasPermission(permission)) {
        throw new ForbiddenException(ErrorCode.FORBIDDEN);
      }
    }
  }
}
