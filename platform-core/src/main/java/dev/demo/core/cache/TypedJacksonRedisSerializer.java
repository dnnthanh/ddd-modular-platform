package dev.demo.core.cache;

import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;
import tools.jackson.databind.ObjectMapper;

/** JSON values have a fixed DTO type chosen by cache configuration, not by JSON input. */
@RequiredArgsConstructor
public final class TypedJacksonRedisSerializer<T> implements RedisSerializer<T> {

  private final ObjectMapper objectMapper;
  private final Class<T> type;

  @Override
  public byte[] serialize(T value) throws SerializationException {
    if (Objects.isNull(value)) {
      return new byte[0];
    }
    try {
      return objectMapper.writeValueAsBytes(value);
    } catch (Exception ex) {
      throw new SerializationException("Cannot serialize cache value", ex);
    }
  }

  @Override
  public T deserialize(byte[] bytes) throws SerializationException {
    if (ArrayUtils.isEmpty(bytes)) {
      return null;
    }
    try {
      return objectMapper.readValue(bytes, type);
    } catch (Exception ex) {
      throw new SerializationException("Cannot deserialize cache value", ex);
    }
  }
}
