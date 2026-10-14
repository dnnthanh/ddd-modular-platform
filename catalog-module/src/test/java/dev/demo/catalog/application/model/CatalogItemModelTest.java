package dev.demo.catalog.application.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class CatalogItemModelTest {

  @Test
  void enrichesCopyWithoutReplacingMappedValues() {
    var id = UUID.randomUUID();
    var original = new CatalogItemModel(id, "SKU01", "Mouse", null);
    var enriched =
        CatalogItemModel.withDisplayLabel(original, original.sku() + " / " + original.name());

    assertEquals(id, enriched.id());
    assertEquals(original.sku(), enriched.sku());
    assertEquals(original.name(), enriched.name());
    assertEquals("SKU01 / Mouse", enriched.displayLabel());
    assertNull(original.displayLabel());
  }
}
