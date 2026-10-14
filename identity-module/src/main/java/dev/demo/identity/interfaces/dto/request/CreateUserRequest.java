package dev.demo.identity.interfaces.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateUserRequest {
  @NotBlank(message = "{validation.required}")
  @Size(min = 3, max = 64, message = "{validation.username}")
  private String username;

  @NotBlank(message = "{validation.required}")
  @Size(min = 12, max = 72, message = "{validation.password}")
  private String password;

  @NotEmpty(message = "{validation.not_empty}")
  private List<
          @NotBlank(message = "{validation.required}")
          @Pattern(regexp = "[A-Z][A-Z0-9_]{1,63}", message = "{validation.role_code}") String>
      roles;
}
