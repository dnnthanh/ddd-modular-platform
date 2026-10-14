package dev.demo.catalog.application.model;

import java.util.UUID;

public record CatalogItemModel(UUID id, String sku, String name, String displayLabel) {

  /** Returns an enriched copy while preserving the fields produced by MapStruct. */
  public static CatalogItemModel withDisplayLabel(CatalogItemModel model, String displayLabel) {
    return new CatalogItemModel(model.id, model.sku, model.name, displayLabel);
  }
}
