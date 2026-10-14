package dev.demo.identity.interfaces.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateRoleRequest {

  @NotBlank(message = "{validation.required}")
  @Pattern(regexp = "[A-Z][A-Z0-9_]{1,63}", message = "{validation.role_code}")
  private String code;

  @NotEmpty(message = "{validation.not_empty}")
  private Set<@NotBlank(message = "{validation.required}") String> permissions;
}
