package com.rednavis.metaldesk.admin.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

/** A caller's correlation id is kept only when it is short and made of safe characters. */
class CorrelationIdTest {

  @Test
  void safeSuppliedIdIsKept() {
    assertEquals("req-1.a_B", CorrelationId.choose("req-1.a_B"));
  }

  @Test
  void absentUnsafeOrOverlongIdIsReplacedByFreshOne() {
    final String absent = CorrelationId.choose(null);
    final String unsafe = CorrelationId.choose("bad id\nwith newline");
    final String overlong = CorrelationId.choose("x".repeat(65));

    assertEquals(36, absent.length());
    assertNotEquals("bad id\nwith newline", unsafe);
    assertEquals(36, overlong.length());
    assertNotEquals(absent, CorrelationId.choose(null));
  }
}
