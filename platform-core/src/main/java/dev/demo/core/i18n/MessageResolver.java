package dev.demo.core.i18n;

import dev.demo.core.exception.ErrorCode;
import java.util.Locale;

/** Type-safe error messages and generic message keys, with optional formatting arguments. */
public interface MessageResolver {

  /** Resolves the error code for the active locale. */
  String resolve(ErrorCode code, Object... args);

  /** Resolves a translation key for the active locale. */
  String resolve(String key, Object... args);

  /** Resolves an error code with the explicit locale and formatting arguments. */
  String resolve(ErrorCode code, Locale locale, Object... args);

  /** Resolves a translation key with an explicit locale and arguments. */
  String resolve(String key, Locale locale, Object... args);
}
