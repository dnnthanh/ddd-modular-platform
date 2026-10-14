package dev.demo.core.security.jwt;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Objects;
import lombok.experimental.UtilityClass;
import org.springframework.core.io.Resource;

/** Reads PKCS#8 private keys and X.509 public keys from Spring Resource locations. */
@UtilityClass
public class JwtKeyResources {

  public PublicKey readPublicKey(Resource resource) {
    try {
      byte[] der = pemBytes(resource, "PUBLIC KEY");
      return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
    } catch (Exception ex) {
      throw new IllegalStateException(
          "Cannot load RSA JWT public key from configured resource", ex);
    }
  }

  /** For trusted token issuers only; never load a private key into resource servers. */
  public PrivateKey readPrivateKey(Resource resource) {
    try {
      byte[] der = pemBytes(resource, "PRIVATE KEY");
      return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    } catch (Exception ex) {
      throw new IllegalStateException(
          "Cannot load RSA JWT private key from configured resource", ex);
    }
  }

  private byte[] pemBytes(Resource resource, String type) throws IOException {
    Objects.requireNonNull(resource, "resource");
    String pem;
    try (var in = resource.getInputStream()) {
      pem = new String(in.readAllBytes(), StandardCharsets.US_ASCII);
    }
    String begin = "-----BEGIN " + type + "-----";
    String end = "-----END " + type + "-----";
    if (!pem.contains(begin) || !pem.contains(end)) {
      throw new IllegalArgumentException("Invalid PEM boundary: " + type);
    }
    String body = pem.substring(pem.indexOf(begin) + begin.length(), pem.indexOf(end));
    return Base64.getMimeDecoder().decode(body);
  }
}
