package dev.demo.core.json;

import java.util.LinkedHashSet;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Global JSON String normalization settings. Exclusions protect opaque/signed values. */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.json.trim")
public class JsonTrimProperties {

  private boolean enabled = true;

  private Set<String> excludedFields =
      new LinkedHashSet<>(
          Set.of(
              "password",
              "passwordConfirmation",
              "currentPassword",
              "newPassword",
              "token",
              "accessToken",
              "refreshToken",
              "idToken",
              "authorization",
              "clientSecret",
              "secret",
              "signature",
              "signedPayload",
              "payload",
              "otp",
              "pin",
              "privateKey",
              "publicKey",
              "certificate",
              "rawBody"));
}
