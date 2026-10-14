package dev.demo.core.cache;

import java.time.Duration;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;

public record CacheDefinition<T>(String name, Class<T> type, Duration ttl) {
  public CacheDefinition {
    if (StringUtils.isBlank(name)
        || Objects.isNull(type)
        || Objects.isNull(ttl)
        || ttl.isNegative()
        || ttl.isZero()) {
      throw new IllegalArgumentException("Invalid cache definition");
    }
  }
}
