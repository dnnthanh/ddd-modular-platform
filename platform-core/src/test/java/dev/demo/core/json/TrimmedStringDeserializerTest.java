package dev.demo.core.json;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class TrimmedStringDeserializerTest {

  record Input(String displayName, String sku, String password, String accessToken) {}

  @Test
  void trimsJsonStringsGloballyButNotSecrets() {
    var properties = new JsonTrimProperties();
    var module = new CoreJsonConfiguration().stringTrimModule(properties);
    var mapper = JsonMapper.builder().addModule(module).build();
    Input parsed =
        mapper.readValue(
            """
        {"displayName":"  Alice  ","sku":"  SKU-01  ",
         "password":"  preserve password  ","accessToken":"  preserve token  "}
        """,
            Input.class);
    assertEquals("Alice", parsed.displayName());
    assertEquals("SKU-01", parsed.sku());
    assertEquals("  preserve password  ", parsed.password());
    assertEquals("  preserve token  ", parsed.accessToken());
  }

  @Test
  void canDisableGlobalTrimFromProperties() {
    var properties = new JsonTrimProperties();
    properties.setEnabled(false);
    var mapper =
        JsonMapper.builder()
            .addModule(new CoreJsonConfiguration().stringTrimModule(properties))
            .build();
    assertEquals(
        "  Alice  ",
        mapper
            .readValue(
                """
        {"displayName":"  Alice  ","sku":"x","password":"x","accessToken":"x"}
        """,
                Input.class)
            .displayName());
  }
}
