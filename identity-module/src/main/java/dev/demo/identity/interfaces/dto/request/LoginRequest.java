package dev.demo.identity.interfaces.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginRequest {
  @NotBlank(message = "{validation.required}")
  private String username;

  @NotBlank(message = "{validation.required}")
  private String password;
}
