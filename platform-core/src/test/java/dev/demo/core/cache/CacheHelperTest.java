package dev.demo.core.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

class CacheHelperTest {
  @Test
  void getOrLoadStoresMiss() {
    CacheManager manager = Mockito.mock(CacheManager.class);
    Cache cache = Mockito.mock(Cache.class);
    when(manager.getCache("catalog:item")).thenReturn(cache);
    when(cache.get("id", String.class)).thenReturn(null);
    CacheHelper helper = new CacheHelper(manager);
    assertEquals("value", helper.getOrLoad("catalog:item", "id", String.class, () -> "value"));
    verify(cache).put("id", "value");
  }

  @Test
  void typedDefinitionCachesLoadedValue() {
    CacheManager manager = Mockito.mock(CacheManager.class);
    Cache cache = Mockito.mock(Cache.class);
    var definition = new CacheDefinition<>("catalog:item", String.class, Duration.ofSeconds(45));
    when(manager.getCache(definition.name())).thenReturn(cache);
    when(cache.get("item-1", String.class)).thenReturn(null);
    CacheHelper helper = new CacheHelper(manager);
    assertEquals("Mouse", helper.getOrLoad(definition, "item-1", () -> "Mouse"));
    verify(cache).put("item-1", "Mouse");
    assertEquals(Duration.ofSeconds(45), definition.ttl());
  }

  @Test
  void typedDefinitionSkipsStoreWhenLoaderReturnsNull() {
    CacheManager manager = Mockito.mock(CacheManager.class);
    Cache cache = Mockito.mock(Cache.class);
    var definition = new CacheDefinition<>("optional", String.class, Duration.ofSeconds(15));
    when(manager.getCache(definition.name())).thenReturn(cache);
    when(cache.get("missing", String.class)).thenReturn(null);
    CacheHelper helper = new CacheHelper(manager);
    assertNull(helper.getOrLoad(definition, "missing", () -> null));
    verify(cache, never()).put(Mockito.any(), Mockito.any());
  }

  @Test
  void typedGetReturnsHitAndDoesNotInvokeLoader() {
    CacheManager manager = Mockito.mock(CacheManager.class);
    Cache cache = Mockito.mock(Cache.class);
    var definition = new CacheDefinition<>("catalog:item", String.class, Duration.ofMinutes(2));
    when(manager.getCache(definition.name())).thenReturn(cache);
    when(cache.get("item-1", String.class)).thenReturn("cached");
    CacheHelper helper = new CacheHelper(manager);
    assertEquals(
        "cached",
        helper.getOrLoad(
            definition,
            "item-1",
            () -> {
              throw new AssertionError("Cache hit must not call loader");
            }));
    verify(cache, never()).put(Mockito.any(), Mockito.any());
  }

  @Test
  void typedRemoveEvictsRegisteredKey() {
    CacheManager manager = Mockito.mock(CacheManager.class);
    Cache cache = Mockito.mock(Cache.class);
    var definition = new CacheDefinition<>("catalog:item", String.class, Duration.ofSeconds(90));
    when(manager.getCache(definition.name())).thenReturn(cache);
    CacheHelper helper = new CacheHelper(manager);
    helper.remove(definition, "item-1");
    verify(cache).evict("item-1");
  }

  @Test
  void rejectsUnknownCache() {
    CacheManager manager = Mockito.mock(CacheManager.class);
    CacheHelper helper = new CacheHelper(manager);
    assertThrows(NullPointerException.class, () -> helper.remove("missing", "id"));
  }
}
