package dev.demo.modular;

import dev.demo.catalog.application.model.CatalogItemModel;
import dev.demo.core.cache.CacheDefinition;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CatalogCacheProperties.class)
public class CatalogCacheConfiguration {

  @Bean
  CacheDefinition<CatalogItemModel> catalogItemCache(CatalogCacheProperties properties) {
    return new CacheDefinition<>("catalog:item", CatalogItemModel.class, properties.itemTtl());
  }
}
