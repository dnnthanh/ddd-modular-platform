package dev.demo.gateway;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Objects;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;

/** Forwarded identity is accepted only from the explicitly trusted edge socket address. */
final class AnonymousRateLimitKey {
  private AnonymousRateLimitKey() {}

  static String key(InetSocketAddress remote, String realIp, Set<String> trustedProxyAddresses) {
    String socketIp =
        Objects.isNull(remote) || Objects.isNull(remote.getAddress())
            ? "unknown"
            : remote.getAddress().getHostAddress();
    String clientIp = socketIp;
    if (trustedProxyAddresses.contains(socketIp)
        && StringUtils.isNotBlank(realIp)
        && realIp.matches("[0-9a-fA-F:.]{2,45}")) {
      try {
        clientIp = InetAddress.getByName(realIp).getHostAddress();
      } catch (UnknownHostException _) {
        // Malformed forwarded identity falls back to the actual network peer.
      }
    }
    return "anonymous:" + clientIp;
  }
}
