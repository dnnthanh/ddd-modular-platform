package dev.demo.identity.application.usecase;

import dev.demo.identity.application.command.CreateRoleCommand;
import dev.demo.identity.application.command.UpdateRolePermissionsCommand;
import dev.demo.identity.application.model.RoleModel;
import java.util.List;

/** Application entry points for managing dynamic non-privileged roles. */
public interface RoleUseCase {

  /** Creates a new database role with permitted hardcoded permissions. */
  RoleModel create(CreateRoleCommand command);

  /** Lists existing roles and their permissions. */
  List<RoleModel> list();

  /** Updates permissions on a non-privileged role. */
  RoleModel updatePermissions(UpdateRolePermissionsCommand command);
}
