package dev.demo.catalog.mapper;

import dev.demo.catalog.application.command.CreateCatalogCommand;
import dev.demo.catalog.application.model.CatalogItemModel;
import dev.demo.catalog.application.model.CatalogPatchModel;
import dev.demo.catalog.application.model.CatalogSearchCriteria;
import dev.demo.catalog.domain.entity.CatalogItem;
import dev.demo.catalog.infrastructure.persistence.entity.CatalogEntity;
import dev.demo.catalog.interfaces.dto.request.CatalogSearchRequest;
import dev.demo.catalog.interfaces.dto.request.CreateCatalogItemRequest;
import dev.demo.catalog.interfaces.dto.response.CatalogItemResponse;
import dev.demo.core.mapper.CommonMapperConfig;
import dev.demo.core.mapper.EntityMapper;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.springframework.data.domain.Page;

/** One mapper per bounded context, with explicit methods for each model boundary. */
@Mapper(config = CommonMapperConfig.class)
public interface CatalogMapper extends EntityMapper<CatalogItem, CatalogEntity, CatalogItemModel> {

  /** Null values in patch input do not overwrite fields on the JPA entity. */
  void applyPatch(CatalogPatchModel source, @MappingTarget CatalogEntity target);

  /** Converts domain objects to application models; enrichment happens in service. */
  @Mapping(target = "displayLabel", ignore = true)
  CatalogItemModel toModel(CatalogItem source);

  /** Converts a list of domain objects to models. */
  List<CatalogItemModel> toModels(List<CatalogItem> source);

  /** Converts Spring Data pages to application models. */
  default Page<CatalogItemModel> toModelPage(Page<CatalogItem> page) {
    return page.map(this::toModel);
  }

  /** Converts the API request to an application command. */
  CreateCatalogCommand toCommand(CreateCatalogItemRequest source);

  /** Converts an HTTP search request to neutral criteria. */
  CatalogSearchCriteria toCriteria(CatalogSearchRequest source);

  /** Converts the model into the response DTO. */
  CatalogItemResponse toResponse(CatalogItemModel source);

  /** Converts a list of application models to DTOs. */
  List<CatalogItemResponse> toResponses(List<CatalogItemModel> source);

  /** Converts a model page to DTO page while preserving metadata. */
  default Page<CatalogItemResponse> toResponsePage(Page<CatalogItemModel> page) {
    return page.map(this::toResponse);
  }
}
