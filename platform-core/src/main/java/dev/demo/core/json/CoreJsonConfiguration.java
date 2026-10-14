package dev.demo.core.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

/**
 * Builds the shared Jackson 3 mapper for REST, typed Redis caches and future Kafka payloads.
 *
 * <p>One mapper is injected as {@code tools.jackson.databind.ObjectMapper} everywhere. The Java
 * JSON policy applies to all consumers, not just HTTP responses. Additional application {@link
 * SimpleModule} beans are registered on this mapper.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JsonTrimProperties.class)
public class CoreJsonConfiguration {

  /**
   * Creates the single application mapper. Jackson 3 uses builder-based configuration.
   *
   * @param modules application JSON modules, including global input trimming
   * @return shared JSON mapper used by Spring MVC, Redis and other serializers
   */
  @Bean
  @Primary
  JsonMapper objectMapper(List<SimpleModule> modules) {
    var builder =
        JsonMapper.builder()
            .changeDefaultPropertyInclusion(
                inclusion -> inclusion.withValueInclusion(JsonInclude.Include.NON_EMPTY))
            // Tolerate additional fields in evolving REST/Kafka payloads.
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            // Reject multiple JSON root values or trailing garbage.
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            // Reject JSON null for Java primitives instead of coercing to 0/false.
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);
    modules.forEach(builder::addModule);
    return builder.build();
  }

  /**
   * Normalizes input strings without touching configured secret or opaque fields.
   *
   * @param properties normalization settings
   * @return Jackson module applied to the shared mapper
   */
  @Bean
  SimpleModule stringTrimModule(JsonTrimProperties properties) {
    var module = new SimpleModule("global-string-normalization");
    module.addDeserializer(String.class, new TrimmedStringDeserializer(properties));
    return module;
  }
}
