package dev.demo.catalog.infrastructure.persistence;

import dev.demo.catalog.application.model.CatalogSearchCriteria;
import dev.demo.catalog.application.port.CatalogPort;
import dev.demo.catalog.domain.entity.CatalogItem;
import dev.demo.catalog.infrastructure.persistence.repository.CatalogRepository;
import dev.demo.catalog.mapper.CatalogMapper;
import dev.demo.core.stereotype.Persistence;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Persistence
@RequiredArgsConstructor
public class CatalogPersistenceAdapter implements CatalogPort {

  private final CatalogRepository catalogRepository;
  private final CatalogMapper catalogMapper;

  @Override
  public void insert(CatalogItem item) {
    catalogRepository.save(catalogMapper.toEntity(item));
  }

  @Override
  public Optional<CatalogItem> findById(UUID id) {
    return catalogRepository.findById(id).map(catalogMapper::toDomain);
  }

  @Override
  public Page<CatalogItem> search(CatalogSearchCriteria criteria, Pageable pageable) {
    return catalogMapper.toDomainPage(catalogRepository.search(criteria, pageable));
  }
}
