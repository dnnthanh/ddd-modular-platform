package dev.demo.core.observability;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import java.util.Optional;
import lombok.experimental.UtilityClass;

/** OpenTelemetry's active span is the sole source of trace/span IDs. */
@UtilityClass
public class TraceContext {

  public static Optional<String> traceId() {
    SpanContext context = Span.current().getSpanContext();
    return context.isValid() ? Optional.of(context.getTraceId()) : Optional.empty();
  }

  public static Optional<String> spanId() {
    SpanContext context = Span.current().getSpanContext();
    return context.isValid() ? Optional.of(context.getSpanId()) : Optional.empty();
  }
}
