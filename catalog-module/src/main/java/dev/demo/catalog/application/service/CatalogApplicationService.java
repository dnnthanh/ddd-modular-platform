package dev.demo.catalog.application.service;

import dev.demo.catalog.application.command.CreateCatalogCommand;
import dev.demo.catalog.application.model.CatalogItemModel;
import dev.demo.catalog.application.model.CatalogSearchCriteria;
import dev.demo.catalog.application.port.CatalogPort;
import dev.demo.catalog.application.usecase.CatalogUseCase;
import dev.demo.catalog.domain.entity.CatalogItem;
import dev.demo.catalog.mapper.CatalogMapper;
import dev.demo.core.cache.CacheDefinition;
import dev.demo.core.cache.CacheHelper;
import dev.demo.core.exception.ErrorCode;
import dev.demo.core.exception.type.NotFoundException;
import dev.demo.core.stereotype.UseCase;
import dev.demo.core.web.PageQuery;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.transaction.annotation.Transactional;

@UseCase
@RequiredArgsConstructor
public class CatalogApplicationService implements CatalogUseCase {

  private final CatalogPort catalogPort;
  private final CatalogMapper catalogMapper;
  private final CacheHelper cacheHelper;
  private final CacheDefinition<CatalogItemModel> catalogItemCache;

  @Override
  @Transactional
  public CatalogItemModel create(CreateCatalogCommand command) {
    CatalogItem item = CatalogItem.create(command.sku(), command.name());
    catalogPort.insert(item);
    return enrich(catalogMapper.toModel(item));
  }

  @Override
  @Transactional(readOnly = true)
  public CatalogItemModel get(UUID id) {
    return cacheHelper.getOrLoad(
        catalogItemCache,
        id.toString(),
        () ->
            catalogPort
                .findById(id)
                .map(catalogMapper::toModel)
                .map(this::enrich)
                .orElseThrow(() -> new NotFoundException(ErrorCode.CATALOG_NOT_FOUND)));
  }

  @Override
  @Transactional(readOnly = true)
  public Page<CatalogItemModel> search(CatalogSearchCriteria criteria, PageQuery query) {
    return catalogMapper
        .toModelPage(catalogPort.search(criteria, query.toPageable()))
        .map(this::enrich);
  }

  private CatalogItemModel enrich(CatalogItemModel model) {
    return CatalogItemModel.withDisplayLabel(model, model.sku() + " / " + model.name());
  }
}
