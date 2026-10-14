package dev.demo.core.web;

import dev.demo.core.exception.ErrorCode;
import dev.demo.core.exception.type.BadRequestException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

public record PageQuery(int page, int size) {

  public PageQuery {
    if (page < 0 || size < 1 || size > 100) {
      throw new BadRequestException(ErrorCode.INVALID_PAGINATION);
    }
  }

  public Pageable toPageable() {
    return PageRequest.of(page, size);
  }
}
