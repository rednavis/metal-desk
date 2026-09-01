package com.rednavis.metaldesk.api.auth;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.stream.IntStream;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;

/**
 * Decides which address a request "comes from", for the sign-in throttle (BRD FR-2.2 keys it on the
 * source IP).
 *
 * <p><strong>The decision.</strong> Behind Cloud Run and a load balancer the immediate peer is the
 * proxy, not the client; keying on it would throttle everyone at once. But the {@code
 * X-Forwarded-For} header is written by the client too, so believing it blindly lets an attacker
 * rotate it and never be throttled. So the header is believed <em>only when the immediate peer is a
 * configured trusted proxy</em> ({@code metaldesk.auth.throttle.trusted-proxies}), and even then it
 * is read from the right: the nearest address that is not itself a trusted proxy is the client,
 * because everything to the left of it was supplied by the client and can be forged. With no
 * trusted proxies configured, which is the default, the peer address is used and the header is
 * ignored. A deployment behind a load balancer must list the balancer's ranges.
 */
@Component
public class ClientAddressResolver {

  /** The key used when the request carries no peer address at all. */
  public static final String UNKNOWN = "unknown";

  private static final String FORWARDED_FOR = "X-Forwarded-For";

  private final List<IpRange> trusted;

  /**
   * Creates the resolver.
   *
   * @param properties supplies the trusted proxies
   */
  public ClientAddressResolver(ThrottleProperties properties) {
    this.trusted = properties.trustedProxies().stream().map(IpRange::parse).toList();
  }

  /**
   * Finds the client address of a request.
   *
   * @param request the request
   * @return the client address as text, or {@link #UNKNOWN}
   */
  public String resolve(ServerHttpRequest request) {
    final InetSocketAddress remote = request.getRemoteAddress();
    final String peer =
        remote == null || remote.getAddress() == null
            ? UNKNOWN
            : remote.getAddress().getHostAddress();
    final String forwarded = request.getHeaders().getFirst(FORWARDED_FOR);
    return UNKNOWN.equals(peer) || !isTrusted(peer) || forwarded == null
        ? peer
        : clientBehind(forwarded, peer);
  }

  private String clientBehind(String forwarded, String peer) {
    final String[] hops = forwarded.split(",");
    return IntStream.iterate(hops.length - 1, i -> i >= 0, i -> i - 1)
        .mapToObj(i -> hops[i].strip())
        .filter(hop -> !hop.isEmpty() && !isTrusted(hop))
        .findFirst()
        .orElse(peer);
  }

  private boolean isTrusted(String address) {
    return IpRange.literal(address)
        .map(bytes -> trusted.stream().anyMatch(range -> range.contains(bytes)))
        .orElse(false);
  }
}
