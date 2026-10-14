package dev.demo.identity.interfaces.dto.request;

import jakarta.validation.constraints.NotEmpty;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateRolePermissionsRequest {

  @NotEmpty(message = "{validation.not_empty}")
  private Set<String> permissions;
}
