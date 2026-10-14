package dev.demo.catalog.interfaces.dto.response;

import java.util.UUID;

/** API representation; keeps enriched presentation fields separate from persistence. */
public record CatalogItemResponse(UUID id, String sku, String name, String displayLabel) {}
