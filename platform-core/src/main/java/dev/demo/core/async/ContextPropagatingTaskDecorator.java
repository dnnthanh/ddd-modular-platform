package dev.demo.core.async;

import io.opentelemetry.context.Context;
import java.util.Map;
import java.util.Objects;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

/** Preserve OTel trace, SecurityContext and SLF4J MDC across executor threads. */
public class ContextPropagatingTaskDecorator implements TaskDecorator {

  @Override
  public Runnable decorate(Runnable task) {
    Context otel = Context.current();
    Map<String, String> parentMdc = MDC.getCopyOfContextMap();
    SecurityContext caller = SecurityContextHolder.getContext();
    SecurityContext snapshot = SecurityContextHolder.createEmptyContext();
    snapshot.setAuthentication(caller.getAuthentication());

    return otel.wrap(
        () -> {
          SecurityContext previousSecurity = SecurityContextHolder.getContext();
          Map<String, String> previousMdc = MDC.getCopyOfContextMap();
          try {
            SecurityContextHolder.setContext(snapshot);
            if (Objects.isNull(parentMdc)) {
              MDC.clear();
            } else {
              MDC.setContextMap(parentMdc);
            }
            task.run();
          } finally {
            SecurityContextHolder.setContext(previousSecurity);
            if (Objects.isNull(previousMdc)) {
              MDC.clear();
            } else {
              MDC.setContextMap(previousMdc);
            }
          }
        });
  }
}
