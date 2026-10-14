package dev.demo.core.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.demo.core.exception.ErrorCode;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.LocaleResolver;

class InternationalizationTest {

  record SampleRequest(@NotBlank(message = "{validation.required}") String username) {}

  @Test
  void defaultsToVietnameseAndAllowsEnglishFromAcceptLanguage() {
    try (var context =
        new AnnotationConfigApplicationContext(InternationalizationConfiguration.class)) {
      var localeResolver = context.getBean(LocaleResolver.class);
      var request = new MockHttpServletRequest("POST", "/api/private/users");
      assertEquals(Locale.forLanguageTag("vi"), localeResolver.resolveLocale(request));

      request.addHeader("Accept-Language", "en");
      assertEquals(Locale.ENGLISH, localeResolver.resolveLocale(request));
    }
  }

  @Test
  void beanValidationUsesSameMessageSourceAsApplicationErrors() {
    try (var context =
        new AnnotationConfigApplicationContext(InternationalizationConfiguration.class)) {
      var validator = context.getBean(Validator.class);
      var messages = context.getBean(MessageResolver.class);
      try {
        LocaleContextHolder.setLocale(Locale.forLanguageTag("vi"));
        var violationsVi = validator.validate(new SampleRequest(""));
        assertEquals(1, violationsVi.size());
        assertEquals("Trường này không được bỏ trống", violationsVi.iterator().next().getMessage());
        assertEquals("Cần xác thực", messages.resolve(ErrorCode.UNAUTHORIZED));

        LocaleContextHolder.setLocale(Locale.ENGLISH);
        var violationsEn = validator.validate(new SampleRequest(""));
        assertEquals(1, violationsEn.size());
        assertEquals("This field is required", violationsEn.iterator().next().getMessage());
        assertEquals("Authentication required", messages.resolve(ErrorCode.UNAUTHORIZED));
      } finally {
        LocaleContextHolder.resetLocaleContext();
      }
    }
  }

  @Test
  void messageResolverDefaultsToVietnameseOutsideHttpRequests() {
    LocaleContextHolder.resetLocaleContext();
    try (var context =
        new AnnotationConfigApplicationContext(InternationalizationConfiguration.class)) {
      var messages = context.getBean(MessageResolver.class);
      assertEquals("Cần xác thực", messages.resolve(ErrorCode.UNAUTHORIZED));
    }
  }
}
