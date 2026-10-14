package dev.demo.core.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.demo.core.exception.ErrorCode;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;

class SpringMessageResolverTest {

  @Test
  void supportsStringAndEnumKeysWithArguments() {
    var source = new StaticMessageSource();
    source.addMessage("example", Locale.ENGLISH, "Hello {0}");
    source.addMessage(ErrorCode.UNAUTHORIZED.messageKey(), Locale.ENGLISH, "Unauthenticated");
    var resolver = new SpringMessageResolver(source);
    assertEquals("Hello world", resolver.resolve("example", Locale.ENGLISH, "world"));
    assertEquals("Unauthenticated", resolver.resolve(ErrorCode.UNAUTHORIZED, Locale.ENGLISH));
  }
}
