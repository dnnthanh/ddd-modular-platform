package dev.demo.catalog.application.port;

import dev.demo.catalog.application.model.CatalogSearchCriteria;
import dev.demo.catalog.domain.entity.CatalogItem;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Persistence operations required by Catalog use cases. */
public interface CatalogPort {

  /** Saves a new catalog item. */
  void insert(CatalogItem item);

  /** Finds an item by its identifier. */
  Optional<CatalogItem> findById(UUID id);

  /** Searches items while retaining pagination information. */
  Page<CatalogItem> search(CatalogSearchCriteria criteria, Pageable pageable);

  /** Checks whether an item exists. */
  default boolean exists(UUID id) {
    return findById(id).isPresent();
  }
}
