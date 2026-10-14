package dev.demo.catalog.application.model;

import dev.demo.core.exception.ErrorCode;
import dev.demo.core.exception.type.BadRequestException;
import org.apache.commons.lang3.StringUtils;

/** Search model passed to the repository; independent from HTTP DTO and JPA entity. */
public record CatalogSearchCriteria(String keyword) {

  public CatalogSearchCriteria {
    keyword = StringUtils.trimToEmpty(keyword);
    if (keyword.length() > 160) {
      throw new BadRequestException(ErrorCode.BAD_REQUEST);
    }
  }
}
