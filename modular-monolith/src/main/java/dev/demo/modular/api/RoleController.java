package dev.demo.modular.api;

import dev.demo.core.web.ApiResponse;
import dev.demo.identity.application.model.RoleModel;
import dev.demo.identity.application.usecase.RoleUseCase;
import dev.demo.identity.interfaces.dto.request.CreateRoleRequest;
import dev.demo.identity.interfaces.dto.request.UpdateRolePermissionsRequest;
import dev.demo.identity.mapper.IdentityMapper;
import dev.demo.modular.api.contract.RoleApi;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class RoleController implements RoleApi {

  private final RoleUseCase roleUseCase;
  private final IdentityMapper identityMapper;

  @Override
  public ApiResponse<List<RoleModel>> list() {
    return ApiResponse.ok(roleUseCase.list());
  }

  @Override
  public ApiResponse<RoleModel> create(CreateRoleRequest request) {
    return ApiResponse.ok(roleUseCase.create(identityMapper.toCommand(request)));
  }

  @Override
  public ApiResponse<RoleModel> update(String code, UpdateRolePermissionsRequest request) {
    return ApiResponse.ok(roleUseCase.updatePermissions(identityMapper.toCommand(code, request)));
  }
}
