package dev.demo.catalog.application.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CatalogSearchCriteriaTest {

  @Test
  void trimsSearchKeyword() {
    assertEquals("mouse", new CatalogSearchCriteria("  mouse  ").keyword());
  }

  @Test
  void turnsNullKeywordIntoEmptyString() {
    assertEquals("", new CatalogSearchCriteria(null).keyword());
  }
}
