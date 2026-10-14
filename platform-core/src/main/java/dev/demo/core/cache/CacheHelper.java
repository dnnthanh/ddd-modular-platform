package dev.demo.core.cache;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

/**
 * Typed facade over Spring CacheManager. Each registered CacheDefinition controls the TTL,
 * serializer, and application-scoped Redis key prefix. Do not create ad-hoc Redis keys or bypass
 * CacheManager here.
 */
@RequiredArgsConstructor
public class CacheHelper {

  private final CacheManager cacheManager;

  public <T> Optional<T> get(CacheDefinition<T> definition, Object key) {
    return get(definition.name(), key, definition.type());
  }

  public <T> T getOrLoad(CacheDefinition<T> definition, Object key, Supplier<? extends T> loader) {
    return get(definition, key)
        .orElseGet(
            () -> {
              T loaded = Objects.requireNonNull(loader, "loader").get();
              if (Objects.nonNull(loaded)) {
                put(definition, key, loaded);
              }
              return loaded;
            });
  }

  public <T> void put(CacheDefinition<T> definition, Object key, T value) {
    put(definition.name(), key, value);
  }

  public void remove(CacheDefinition<?> definition, Object key) {
    remove(definition.name(), key);
  }

  public void clear(CacheDefinition<?> definition) {
    clear(definition.name());
  }

  // Name-based overloads support existing integrations, but prefer typed definitions.
  public <T> Optional<T> get(String cacheName, Object key, Class<T> type) {
    return Optional.ofNullable(cache(cacheName).get(key, type));
  }

  public <T> T getOrLoad(
      String cacheName, Object key, Class<T> type, Supplier<? extends T> loader) {
    return get(cacheName, key, type)
        .orElseGet(
            () -> {
              T loaded = Objects.requireNonNull(loader, "loader").get();
              if (Objects.nonNull(loaded)) {
                put(cacheName, key, loaded);
              }
              return loaded;
            });
  }

  public void put(String cacheName, Object key, Object value) {
    if (Objects.nonNull(value)) {
      cache(cacheName).put(key, value);
    }
  }

  public void remove(String cacheName, Object key) {
    cache(cacheName).evict(key);
  }

  public void clear(String cacheName) {
    cache(cacheName).clear();
  }

  private Cache cache(String name) {
    return Objects.requireNonNull(cacheManager.getCache(name), () -> "Unknown cache: " + name);
  }
}
