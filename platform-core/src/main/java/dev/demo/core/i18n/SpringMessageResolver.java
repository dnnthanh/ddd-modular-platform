package dev.demo.core.i18n;

import dev.demo.core.exception.ErrorCode;
import java.util.Locale;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;

@RequiredArgsConstructor
public class SpringMessageResolver implements MessageResolver {

  private final MessageSource source;

  @Override
  public String resolve(ErrorCode code, Object... args) {
    return resolve(code, activeLocale(), args);
  }

  @Override
  public String resolve(String key, Object... args) {
    return resolve(key, activeLocale(), args);
  }

  @Override
  public String resolve(ErrorCode code, Locale locale, Object... args) {
    return resolve(code.messageKey(), locale, args);
  }

  @Override
  public String resolve(String key, Locale locale, Object... args) {
    return source.getMessage(key, args, key, locale);
  }

  /** Uses the request locale when present; defaults to Vietnamese outside HTTP requests. */
  private Locale activeLocale() {
    return Objects.nonNull(LocaleContextHolder.getLocaleContext())
        ? LocaleContextHolder.getLocale()
        : InternationalizationConfiguration.DEFAULT_LOCALE;
  }
}
