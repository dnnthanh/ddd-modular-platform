package dev.demo.catalog.domain.entity;

import dev.demo.core.exception.ErrorCode;
import dev.demo.core.exception.type.DomainException;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;

/** Domain aggregate root (JPA-agnostic, not a persistence entity). */
public record CatalogItem(UUID id, String sku, String name) {

  public static CatalogItem create(String sku, String name) {
    if (StringUtils.isBlank(sku) || !sku.matches("[A-Z0-9_-]{2,32}")) {
      throw new DomainException(ErrorCode.INVALID_SKU);
    }
    if (StringUtils.isBlank(name) || name.length() > 160) {
      throw new DomainException(ErrorCode.INVALID_NAME);
    }
    return new CatalogItem(UUID.randomUUID(), sku, name);
  }
}
