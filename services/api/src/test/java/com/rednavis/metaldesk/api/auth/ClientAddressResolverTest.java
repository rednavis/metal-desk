package com.rednavis.metaldesk.api.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

/** The forwarded header is believed only from a trusted proxy, and read from the right. */
class ClientAddressResolverTest {

  private static final String FORWARDED = "X-Forwarded-For";
  private static final String OUTSIDER = "198.51.100.9";
  private static final String CLIENT = "203.0.113.5";
  private static final String PROXY = "10.1.2.3";
  private static final String NETWORK = "10.0.0.0/8";
  private static final String SPOOF = "1.2.3.4";

  private static ServerHttpRequest request(String peer, String forwarded) {
    final MockServerHttpRequest.BaseBuilder<?> builder =
        MockServerHttpRequest.get("/")
            .remoteAddress(new InetSocketAddress(InetAddress.ofLiteral(peer), 4000));
    return (forwarded == null ? builder : builder.header(FORWARDED, forwarded)).build();
  }

  private static ClientAddressResolver resolver(String... trusted) {
    return new ClientAddressResolver(
        new ThrottleProperties(3, java.time.Duration.ofMinutes(15), List.of(trusted)));
  }

  @Test
  void withNoTrustedProxiesThePeerIsUsedAndTheHeaderIgnored() {
    assertEquals(OUTSIDER, resolver().resolve(request(OUTSIDER, SPOOF)));
  }

  @Test
  void untrustedPeerCannotVouchForForwardedAddress() {
    assertEquals(OUTSIDER, resolver(NETWORK).resolve(request(OUTSIDER, SPOOF)));
  }

  @Test
  void trustedProxyVouchesForTheClientBehindIt() {
    assertEquals(CLIENT, resolver(NETWORK).resolve(request(PROXY, CLIENT)));
  }

  @Test
  void forgedLeftmostEntryIsNotBelieved() {
    assertEquals(CLIENT, resolver(NETWORK).resolve(request(PROXY, "6.6.6.6, 203.0.113.5")));
  }

  @Test
  void trustedHopsAtTheRightAreSkipped() {
    assertEquals(CLIENT, resolver(NETWORK).resolve(request(PROXY, "203.0.113.5, 10.9.9.9")));
  }

  @Test
  void trustedProxyWithNoHeaderIsItsOwnClient() {
    assertEquals(PROXY, resolver(NETWORK).resolve(request(PROXY, null)));
  }

  @Test
  void singleAddressIsTrustedExactly() {
    assertEquals(CLIENT, resolver(PROXY).resolve(request(PROXY, CLIENT)));
    assertEquals("10.1.2.4", resolver(PROXY).resolve(request("10.1.2.4", CLIENT)));
  }

  @Test
  void anIpv6RangeIsMatched() {
    assertEquals(CLIENT, resolver("fd00::/8").resolve(request("fd12::1", CLIENT)));
  }
}
