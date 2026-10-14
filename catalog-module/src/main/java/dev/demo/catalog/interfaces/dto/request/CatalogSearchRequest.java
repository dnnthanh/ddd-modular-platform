package dev.demo.catalog.interfaces.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CatalogSearchRequest {
  @Min(value = 0, message = "{validation.page_min}")
  private int page;

  @Min(value = 1, message = "{validation.size_min}")
  @Max(value = 100, message = "{validation.size_max}")
  private int size = 20;

  @Size(max = 160, message = "{validation.catalog_name}")
  private String keyword;
}
