package dev.demo.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.net.InetSocketAddress;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AnonymousRateLimitKeyTest {
  @Test
  void ignoresSpoofedHeaderFromDirectClients() {
    var source = new InetSocketAddress("127.0.0.1", 1234);
    assertEquals(
        "anonymous:127.0.0.1",
        AnonymousRateLimitKey.key(source, "203.0.113.2", Set.of("172.20.0.2")));
  }

  @Test
  void trustedEdgeSeparatesClientsAndRejectsHostnameHeaders() {
    var edge = new InetSocketAddress("172.20.0.2", 1234);
    var trusted = Set.of("172.20.0.2");
    assertNotEquals(
        AnonymousRateLimitKey.key(edge, "203.0.113.2", trusted),
        AnonymousRateLimitKey.key(edge, "203.0.113.3", trusted));
    assertEquals(
        "anonymous:172.20.0.2", AnonymousRateLimitKey.key(edge, "attacker.example", trusted));
  }
}
