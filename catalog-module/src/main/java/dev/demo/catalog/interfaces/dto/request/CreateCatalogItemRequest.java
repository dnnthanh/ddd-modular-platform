package dev.demo.catalog.interfaces.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CreateCatalogItemRequest {
  @NotBlank(message = "{validation.required}")
  @Pattern(regexp = "[A-Z0-9_-]{2,32}", message = "{validation.catalog_sku}")
  private String sku;

  @NotBlank(message = "{validation.required}")
  @Size(max = 160, message = "{validation.catalog_name}")
  private String name;
}
