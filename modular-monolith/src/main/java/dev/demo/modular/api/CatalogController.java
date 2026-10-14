package dev.demo.modular.api;

import dev.demo.catalog.application.usecase.CatalogUseCase;
import dev.demo.catalog.interfaces.dto.request.CatalogSearchRequest;
import dev.demo.catalog.interfaces.dto.request.CreateCatalogItemRequest;
import dev.demo.catalog.interfaces.dto.response.CatalogItemResponse;
import dev.demo.catalog.mapper.CatalogMapper;
import dev.demo.core.web.ApiResponse;
import dev.demo.core.web.PageQuery;
import dev.demo.modular.api.contract.CatalogApi;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CatalogController implements CatalogApi {

  private final CatalogUseCase catalogUseCase;
  private final CatalogMapper catalogMapper;

  @Override
  public ResponseEntity<ApiResponse<CatalogItemResponse>> create(CreateCatalogItemRequest request) {
    var response =
        catalogMapper.toResponse(catalogUseCase.create(catalogMapper.toCommand(request)));
    return ResponseEntity.created(URI.create("/api/private/catalog/" + response.id()))
        .body(ApiResponse.ok(response));
  }

  @Override
  public ApiResponse<CatalogItemResponse> get(UUID id) {
    return ApiResponse.ok(catalogMapper.toResponse(catalogUseCase.get(id)));
  }

  @Override
  public ApiResponse<Page<CatalogItemResponse>> search(CatalogSearchRequest request) {
    var models =
        catalogUseCase.search(
            catalogMapper.toCriteria(request), new PageQuery(request.getPage(), request.getSize()));
    return ApiResponse.ok(catalogMapper.toResponsePage(models));
  }
}
