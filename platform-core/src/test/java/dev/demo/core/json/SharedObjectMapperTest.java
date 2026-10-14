package dev.demo.core.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.demo.core.cache.TypedJacksonRedisSerializer;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/** Verifies serialization policy for API responses and typed Redis cache payloads. */
class SharedObjectMapperTest {

  record Payload(String name, String description, List<String> tags, boolean active) {}

  record StrictPayload(int quantity, String name) {}

  private final ObjectMapper mapper =
      new CoreJsonConfiguration()
          .objectMapper(
              List.of(new CoreJsonConfiguration().stringTrimModule(new JsonTrimProperties())));

  @Test
  void skipsEmptyFieldsButKeepsBooleanFalse() {
    String json = mapper.writeValueAsString(new Payload("keyboard", null, List.of(), false));
    assertTrue(json.contains("\"name\":\"keyboard\""));
    assertTrue(json.contains("\"active\":false"));
    assertFalse(json.contains("description"));
    assertFalse(json.contains("tags"));
  }

  @Test
  void ignoresUnknownPropertiesForCompatibility() {
    var payload =
        mapper.readValue(
            "{\"quantity\":1,\"name\":\"Keyboard\",\"extra\":true}", StrictPayload.class);
    assertEquals(1, payload.quantity());
    assertEquals("Keyboard", payload.name());
  }

  @Test
  void rejectsNullForPrimitiveFields() {
    assertThrows(
        Exception.class,
        () -> mapper.readValue("{\"quantity\":null,\"name\":\"Keyboard\"}", StrictPayload.class));
  }

  @Test
  void rejectsTrailingJsonTokens() {
    assertThrows(
        Exception.class,
        () -> mapper.readValue("{\"quantity\":1,\"name\":\"Keyboard\"} true", StrictPayload.class));
  }

  @Test
  void typedRedisReusesConfiguredMapper() {
    var serializer = new TypedJacksonRedisSerializer<>(mapper, Payload.class);
    Payload payload =
        serializer.deserialize(
            serializer.serialize(new Payload("keyboard", null, List.of("hardware"), false)));
    assertEquals("keyboard", payload.name());
    assertEquals(List.of("hardware"), payload.tags());
    assertNull(payload.description());
  }

  @Test
  void emptyRedisListIsOmittedNotRoundTrippedAsEmpty() {
    var serializer = new TypedJacksonRedisSerializer<>(mapper, Payload.class);
    Payload payload =
        serializer.deserialize(
            serializer.serialize(new Payload("keyboard", null, List.of(), false)));
    assertNull(payload.tags());
  }
}
