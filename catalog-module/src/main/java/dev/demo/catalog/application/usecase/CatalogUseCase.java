package dev.demo.catalog.application.usecase;

import dev.demo.catalog.application.command.CreateCatalogCommand;
import dev.demo.catalog.application.model.CatalogItemModel;
import dev.demo.catalog.application.model.CatalogSearchCriteria;
import dev.demo.core.web.PageQuery;
import java.util.UUID;
import org.springframework.data.domain.Page;

/** Application operations for managing catalog items. */
public interface CatalogUseCase {

  /** Creates a catalog item. */
  CatalogItemModel create(CreateCatalogCommand command);

  /** Loads a catalog item by ID. */
  CatalogItemModel get(UUID id);

  /** Searches catalog items with pagination. */
  Page<CatalogItemModel> search(CatalogSearchCriteria criteria, PageQuery page);
}
