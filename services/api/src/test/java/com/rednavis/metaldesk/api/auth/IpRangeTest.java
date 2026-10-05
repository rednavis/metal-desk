package com.rednavis.metaldesk.api.auth;

import java.util.Optional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/** Reading and matching trusted-proxy addresses without any name lookup. */
class IpRangeTest {

  private static byte[] ip(String text) {
    return IpRange.literal(text).orElseThrow();
  }

  @Test
  void bareAddressMatchesOnlyItself() {
    final IpRange range = IpRange.parse("10.0.0.1");

    Assertions.assertTrue(range.contains(ip("10.0.0.1")));
    Assertions.assertFalse(range.contains(ip("10.0.0.2")));
  }

  @Test
  void blockMatchesEveryAddressInIt() {
    final IpRange range = IpRange.parse("10.1.0.0/16");

    Assertions.assertTrue(range.contains(ip("10.1.200.3")));
    Assertions.assertFalse(range.contains(ip("10.2.0.1")));
  }

  @Test
  void prefixOffByteBoundaryIsMasked() {
    final IpRange range = IpRange.parse("192.168.0.0/20");

    Assertions.assertTrue(range.contains(ip("192.168.15.9")));
    Assertions.assertFalse(range.contains(ip("192.168.16.0")));
  }

  @Test
  void ipv6BlocksWork() {
    final IpRange range = IpRange.parse("fd00::/8");

    Assertions.assertTrue(range.contains(ip("fd12::1")));
    Assertions.assertFalse(range.contains(ip("fe80::1")));
  }

  @Test
  void otherFamilyNeverMatches() {
    Assertions.assertFalse(IpRange.parse("10.0.0.0/8").contains(ip("fd00::1")));
  }

  @Test
  void zeroLengthPrefixMatchesWholeFamily() {
    Assertions.assertTrue(IpRange.parse("0.0.0.0/0").contains(ip("203.0.113.9")));
  }

  @Test
  void textThatIsNoAddressOrHasBadPrefixIsRefused() {
    Assertions.assertThrows(IllegalArgumentException.class, () -> IpRange.parse("example.com"));
    Assertions.assertThrows(IllegalArgumentException.class, () -> IpRange.parse("10.0.0.0/33"));
    Assertions.assertThrows(IllegalArgumentException.class, () -> IpRange.parse("10.0.0.0/-1"));
  }

  @Test
  void literalsAreReadWithoutLookupsAndNonLiteralsAreEmpty() {
    Assertions.assertEquals(Optional.empty(), IpRange.literal(null));
    Assertions.assertEquals(Optional.empty(), IpRange.literal("localhost"));
    Assertions.assertEquals(Optional.empty(), IpRange.literal("1:2:3:4:5:6:7:8:9"));
    Assertions.assertEquals(4, ip("127.0.0.1").length);
  }

  @Test
  void networkIsCopiedInAndOut() {
    final byte[] bytes = ip("10.0.0.1");
    final IpRange range = new IpRange(bytes, 32);
    bytes[0] = 0;

    Assertions.assertEquals(10, range.network()[0]);
    Assertions.assertNotSame(range.network(), range.network());
  }
}
