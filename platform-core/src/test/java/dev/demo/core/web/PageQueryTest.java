package dev.demo.core.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.demo.core.exception.type.BadRequestException;
import org.junit.jupiter.api.Test;

class PageQueryTest {
  @Test
  void convertsToSpringPageableWithoutListAdapter() {
    var page = new PageQuery(2, 20).toPageable();
    assertEquals(2, page.getPageNumber());
    assertEquals(20, page.getPageSize());
  }

  @Test
  void rejectsUnboundedPage() {
    assertThrows(BadRequestException.class, () -> new PageQuery(1, 200));
  }
}
