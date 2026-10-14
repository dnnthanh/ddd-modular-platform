package dev.demo.catalog.domain.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.demo.core.exception.type.DomainException;
import org.junit.jupiter.api.Test;

class CatalogItemTest {
  @Test
  void createsValidItem() {
    var item = CatalogItem.create("SKU-001", "Keyboard");
    assertNotNull(item.id());
    assertEquals("SKU-001", item.sku());
  }

  @Test
  void rejectsInvalidSku() {
    assertThrows(DomainException.class, () -> CatalogItem.create("bad sku", "Keyboard"));
  }
}
