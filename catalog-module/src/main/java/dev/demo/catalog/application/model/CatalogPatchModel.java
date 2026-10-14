package dev.demo.catalog.application.model;

/** Null means 'unchanged'; clearing a value needs an explicit domain operation. */
public record CatalogPatchModel(String sku, String name) {}
