package dev.demo.core.i18n;

import java.util.List;
import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

@Configuration(proxyBeanMethods = false)
public class InternationalizationConfiguration {

  public static final Locale DEFAULT_LOCALE = Locale.forLanguageTag("vi");

  @Bean
  MessageSource messageSource() {
    var source = new ReloadableResourceBundleMessageSource();
    source.setBasename("classpath:messages");
    source.setDefaultEncoding("UTF-8");
    source.setFallbackToSystemLocale(false);
    return source;
  }

  /** Uses the application MessageSource for Jakarta Bean Validation messages. */
  @Bean
  LocalValidatorFactoryBean validator(MessageSource messageSource) {
    var validator = new LocalValidatorFactoryBean();
    validator.setValidationMessageSource(messageSource);
    return validator;
  }

  @Bean
  MessageResolver messageResolver(MessageSource messageSource) {
    return new SpringMessageResolver(messageSource);
  }

  @Bean
  LocaleResolver localeResolver() {
    var resolver = new AcceptHeaderLocaleResolver();
    resolver.setDefaultLocale(DEFAULT_LOCALE);
    resolver.setSupportedLocales(List.of(DEFAULT_LOCALE, Locale.ENGLISH));
    return resolver;
  }
}
