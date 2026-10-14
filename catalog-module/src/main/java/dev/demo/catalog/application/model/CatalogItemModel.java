package dev.demo.catalog.application.model;

import java.util.UUID;

public record CatalogItemModel(UUID id, String sku, String name, String displayLabel) {

  /** Returns an enriched copy while preserving mapped fields. */
  public CatalogItemModel withDisplayLabel(String displayLabel) {
    return new CatalogItemModel(id, sku, name, displayLabel);
  }
}
