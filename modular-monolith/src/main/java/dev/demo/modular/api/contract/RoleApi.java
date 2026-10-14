package dev.demo.modular.api.contract;

import dev.demo.core.web.ApiResponse;
import dev.demo.identity.application.model.RoleModel;
import dev.demo.identity.interfaces.dto.request.CreateRoleRequest;
import dev.demo.identity.interfaces.dto.request.UpdateRolePermissionsRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

/** HTTP contract for managing dynamic role definitions. */
public interface RoleApi {

  /** Lists all database-defined roles. */
  @GetMapping("/api/private/roles")
  @PreAuthorize("@customPreAuthorizer.hasAnyPermission({'USERS_MANAGE'})")
  ApiResponse<List<RoleModel>> list();

  /** Creates a role whose permissions are limited to the caller's own grants. */
  @PostMapping("/api/private/roles")
  @PreAuthorize("@customPreAuthorizer.hasAnyPermission({'USERS_MANAGE'})")
  ApiResponse<RoleModel> create(@Valid @RequestBody CreateRoleRequest request);

  /** Replaces the permissions of an existing non-privileged role. */
  @PutMapping("/api/private/roles/{code}/permissions")
  @PreAuthorize("@customPreAuthorizer.hasAnyPermission({'USERS_MANAGE'})")
  ApiResponse<RoleModel> update(
      @PathVariable String code, @Valid @RequestBody UpdateRolePermissionsRequest request);
}
