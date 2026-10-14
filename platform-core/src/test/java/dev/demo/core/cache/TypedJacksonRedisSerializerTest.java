package dev.demo.core.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class TypedJacksonRedisSerializerTest {

  record View(String name, int count) {}

  @Test
  void roundTripsDtoWithoutEmbeddingClassName() {
    var serializer = new TypedJacksonRedisSerializer<>(JsonMapper.builder().build(), View.class);
    View result = serializer.deserialize(serializer.serialize(new View("test", 3)));
    assertEquals(new View("test", 3), result);
  }

  @Test
  void rejectsWrongJsonType() {
    var serializer = new TypedJacksonRedisSerializer<>(JsonMapper.builder().build(), View.class);
    assertThrows(RuntimeException.class, () -> serializer.deserialize("[]".getBytes()));
  }
}
