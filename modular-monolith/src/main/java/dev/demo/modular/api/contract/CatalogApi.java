package dev.demo.modular.api.contract;

import dev.demo.catalog.interfaces.dto.request.CatalogSearchRequest;
import dev.demo.catalog.interfaces.dto.request.CreateCatalogItemRequest;
import dev.demo.catalog.interfaces.dto.response.CatalogItemResponse;
import dev.demo.core.web.ApiResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

/** Spring MVC contract; implementations use @RestController. */
@RequestMapping("/api/private/catalog")
public interface CatalogApi {

  /** Creates a catalog entry for authorized callers. */
  @PostMapping
  @PreAuthorize("@customPreAuthorizer.hasAnyPermission({'CATALOG_WRITE'})")
  ResponseEntity<ApiResponse<CatalogItemResponse>> create(
      @Valid @RequestBody CreateCatalogItemRequest request);

  /** Returns one catalog entry. */
  @GetMapping("/{id}")
  @PreAuthorize("@customPreAuthorizer.hasAnyPermission({'CATALOG_READ'})")
  ApiResponse<CatalogItemResponse> get(@PathVariable UUID id);

  /** Returns a page of catalog entries. */
  @GetMapping
  @PreAuthorize("@customPreAuthorizer.hasAnyPermission({'CATALOG_READ'})")
  ApiResponse<Page<CatalogItemResponse>> search(
      @Valid @ModelAttribute CatalogSearchRequest request);
}
