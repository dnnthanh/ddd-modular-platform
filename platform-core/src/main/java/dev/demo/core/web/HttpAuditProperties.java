package dev.demo.core.web;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Local HTTP audit preferences. Never enable raw secret logging. */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.logging.http")
public class HttpAuditProperties {

  private boolean enabled = true;
  private boolean logBodies = true;
  private int maxBodyBytes = 8192;
  private int maxQueryChars = 2048;
  // Body previews are opt-in by route; never default to logging user/login APIs.
  private List<String> bodyPaths = List.of("/api/private/catalog/**");
  private List<String> excludedPaths =
      List.of("/actuator/**", "/error", "/api/public/auth/**", "/api/private/auth/**");
}
