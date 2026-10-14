package dev.demo.core.json;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/** Global Jackson String normalization; never strip fields containing opaque secrets. */
public final class TrimmedStringDeserializer extends ValueDeserializer<String> {

  private final boolean enabled;
  private final Set<String> excludedFields;

  public TrimmedStringDeserializer(JsonTrimProperties properties) {
    this.enabled = properties.isEnabled();
    this.excludedFields =
        properties.getExcludedFields().stream()
            .filter(Objects::nonNull)
            .map(TrimmedStringDeserializer::normalizeName)
            .collect(Collectors.toUnmodifiableSet());
  }

  @Override
  public String deserialize(JsonParser parser, DeserializationContext context)
      throws JacksonException {
    String value = parser.getValueAsString();
    if (!enabled || Objects.isNull(value)) {
      return value;
    }
    String propertyName = parser.currentName();
    if (StringUtils.isNotBlank(propertyName)
        && excludedFields.contains(normalizeName(propertyName))) {
      return value;
    }
    return StringUtils.strip(value);
  }

  private static String normalizeName(String name) {
    return name.replace("_", "").replace("-", "").toLowerCase(Locale.ROOT);
  }
}
