package dev.demo.core.cache;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import tools.jackson.databind.ObjectMapper;

/** Typed per-cache JSON serialization; new modules contribute only a CacheDefinition bean. */
@Configuration(proxyBeanMethods = false)
@EnableCaching
public class CoreCacheConfiguration {

  @Bean
  CacheHelper cacheHelper(CacheManager cacheManager) {
    return new CacheHelper(cacheManager);
  }

  @Bean
  CacheManager cacheManager(
      RedisConnectionFactory factory,
      ObjectMapper mapper,
      List<CacheDefinition<?>> definitions,
      @Value("${spring.application.name}") String applicationName) {
    Map<String, RedisCacheConfiguration> caches = new HashMap<>();
    for (CacheDefinition<?> definition : definitions) {
      RedisCacheConfiguration settings = forDefinition(mapper, definition, applicationName);
      if (Objects.nonNull(caches.putIfAbsent(definition.name(), settings))) {
        throw new IllegalStateException("Duplicate cache name: " + definition.name());
      }
    }
    return RedisCacheManager.builder(factory)
        .withInitialCacheConfigurations(caches)
        .disableCreateOnMissingCache()
        .build();
  }

  private static <T> RedisCacheConfiguration forDefinition(
      ObjectMapper mapper, CacheDefinition<T> definition, String applicationName) {
    var serializer = new TypedJacksonRedisSerializer<>(mapper, definition.type());
    return RedisCacheConfiguration.defaultCacheConfig()
        .entryTtl(definition.ttl())
        .disableCachingNullValues()
        .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer))
        .computePrefixWith(cacheName -> applicationName + ":" + cacheName + ":");
  }
}
