package dev.demo.identity.mapper;

import dev.demo.core.mapper.CommonMapperConfig;
import dev.demo.core.mapper.EntityMapper;
import dev.demo.identity.application.command.ChangeUserRolesCommand;
import dev.demo.identity.application.command.CreateRoleCommand;
import dev.demo.identity.application.command.CreateUserCommand;
import dev.demo.identity.application.command.LoginCommand;
import dev.demo.identity.application.command.UpdateRolePermissionsCommand;
import dev.demo.identity.application.model.RoleModel;
import dev.demo.identity.application.model.UserModel;
import dev.demo.identity.domain.entity.RoleDefinition;
import dev.demo.identity.domain.entity.UserAccount;
import dev.demo.identity.infrastructure.persistence.entity.RoleEntity;
import dev.demo.identity.infrastructure.persistence.entity.UserAccountEntity;
import dev.demo.identity.interfaces.dto.request.CreateRoleRequest;
import dev.demo.identity.interfaces.dto.request.CreateUserRequest;
import dev.demo.identity.interfaces.dto.request.LoginRequest;
import dev.demo.identity.interfaces.dto.request.UpdateRolePermissionsRequest;
import dev.demo.identity.interfaces.dto.request.UpdateUserRolesRequest;
import java.util.List;
import java.util.UUID;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** All Identity conversions in one Spring-managed MapStruct mapper. */
@Mapper(config = CommonMapperConfig.class)
public interface IdentityMapper extends EntityMapper<UserAccount, UserAccountEntity, UserModel> {

  /** Converts a domain account into a JPA entity; role assignment is handled separately. */
  @Override
  @Mapping(target = "roles", ignore = true)
  UserAccountEntity toEntity(UserAccount source);

  @Mapping(target = "id", source = "account.id")
  @Mapping(target = "username", source = "account.username")
  @Mapping(target = "enabled", source = "account.enabled")
  @Mapping(target = "roles", source = "roles")
  @Mapping(target = "permissions", source = "permissions")
  UserModel toModel(UserAccount account, List<String> roles, List<String> permissions);

  /** Converts a JPA role into the domain role definition. */
  RoleDefinition toRoleDefinition(RoleEntity source);

  /** Converts a domain role into the JPA role entity. */
  RoleEntity toRoleEntity(RoleDefinition source);

  /** Converts multiple roles without hand-written loops. */
  List<RoleDefinition> toRoleDefinitions(List<RoleEntity> source);

  /** Converts the domain role into the application response model. */
  RoleModel toRoleModel(RoleDefinition source);

  /** Converts multiple domain roles into application models. */
  List<RoleModel> toRoleModels(List<RoleDefinition> source);

  /** Maps a role creation request to the application command. */
  CreateRoleCommand toCommand(CreateRoleRequest source);

  /** Maps a role update request and path value to the application command. */
  @Mapping(target = "code", source = "code")
  @Mapping(target = "permissions", source = "request.permissions")
  UpdateRolePermissionsCommand toCommand(String code, UpdateRolePermissionsRequest request);

  /** Converts login request into an application command. */
  LoginCommand toCommand(LoginRequest source);

  /** Converts a user creation request into an application command. */
  CreateUserCommand toCommand(CreateUserRequest source);

  /** Converts role assignment request and user ID into an application command. */
  @Mapping(target = "userId", source = "userId")
  @Mapping(target = "roles", source = "request.roles")
  ChangeUserRolesCommand toCommand(UUID userId, UpdateUserRolesRequest request);
}
