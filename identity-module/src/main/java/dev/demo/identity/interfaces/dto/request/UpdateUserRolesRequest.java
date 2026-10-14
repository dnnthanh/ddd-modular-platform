package dev.demo.identity.interfaces.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateUserRolesRequest {
  @NotEmpty(message = "{validation.not_empty}")
  private List<
          @NotBlank(message = "{validation.required}")
          @Pattern(regexp = "[A-Z][A-Z0-9_]{1,63}", message = "{validation.role_code}") String>
      roles;
}
