package dev.demo.core.security.jwt;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

class JwtKeyResourcesTest {

  @Test
  void readsRsaPublicAndPrivatePemResources() throws Exception {
    var generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    var pair = generator.generateKeyPair();
    var publicPem = resource("PUBLIC KEY", pair.getPublic().getEncoded());
    var privatePem = resource("PRIVATE KEY", pair.getPrivate().getEncoded());

    assertArrayEquals(
        pair.getPublic().getEncoded(), JwtKeyResources.readPublicKey(publicPem).getEncoded());
    assertArrayEquals(
        pair.getPrivate().getEncoded(), JwtKeyResources.readPrivateKey(privatePem).getEncoded());
  }

  @Test
  void rejectsInvalidResource() {
    var resource = new ByteArrayResource("not pem".getBytes(StandardCharsets.US_ASCII));
    assertThrows(IllegalStateException.class, () -> JwtKeyResources.readPublicKey(resource));
  }

  private static ByteArrayResource resource(String type, byte[] encoded) {
    String pem =
        "-----BEGIN "
            + type
            + "-----\n"
            + Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(encoded)
            + "\n-----END "
            + type
            + "-----\n";
    return new ByteArrayResource(pem.getBytes(StandardCharsets.US_ASCII));
  }
}
