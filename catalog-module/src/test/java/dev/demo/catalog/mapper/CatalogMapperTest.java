package dev.demo.catalog.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.demo.catalog.application.model.CatalogItemModel;
import dev.demo.catalog.application.model.CatalogPatchModel;
import dev.demo.catalog.infrastructure.persistence.entity.CatalogEntity;
import dev.demo.catalog.interfaces.dto.request.CreateCatalogItemRequest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class CatalogMapperTest {

  private final CatalogMapper mapper = Mappers.getMapper(CatalogMapper.class);

  @Test
  void mapsPersistenceAndPreservesPage() {
    var entity = new CatalogEntity();
    entity.setId(UUID.randomUUID());
    entity.setSku("SKU02");
    entity.setName("Desk");
    var domain = mapper.toDomain(entity);
    assertEquals(List.of(domain), mapper.toDomains(List.of(entity)));
    assertEquals(domain.id(), mapper.toEntities(List.of(domain)).getFirst().getId());
    var page = mapper.toDomainPage(new PageImpl<>(List.of(entity), PageRequest.of(1, 2), 3));
    assertEquals(3L, page.getTotalElements());
    assertEquals(1, page.getNumber());
    assertEquals(domain, page.getContent().getFirst());
  }

  @Test
  void partialUpdateKeepsExistingValues() {
    var entity = new CatalogEntity();
    entity.setSku("SKU01");
    entity.setName("Old");
    mapper.applyPatch(new CatalogPatchModel(null, "New"), entity);
    assertEquals("SKU01", entity.getSku());
    assertEquals("New", entity.getName());
  }

  @Test
  void mapsApiRequestAndResponseCollectionsAndPage() {
    var request = new CreateCatalogItemRequest();
    request.setSku("SKU01");
    request.setName("Mouse");
    assertEquals("SKU01", mapper.toCommand(request).sku());
    var model = new CatalogItemModel(UUID.randomUUID(), "SKU01", "Mouse", "SKU01 / Mouse");
    assertEquals("SKU01", mapper.toResponses(List.of(model)).getFirst().sku());
    var response = mapper.toResponsePage(new PageImpl<>(List.of(model), PageRequest.of(1, 2), 3));
    assertEquals(3L, response.getTotalElements());
    assertEquals("SKU01 / Mouse", response.getContent().getFirst().displayLabel());
  }
}
