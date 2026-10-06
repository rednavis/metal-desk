package com.rednavis.metaldesk.pricingbridge;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.rednavis.metaldesk.share.domain.pricing.Margin;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * The Phase 2 exit gate for {@code services/pricing-bridge}: it compiles against, and really uses,
 * a type from the library it declares. The bridge is where the sellable price is computed (BRD
 * BR-3), so the type is the margin that computation takes.
 */
class LibraryWiringTest {

  @Test
  void usesTheSharedPricingDomain() {
    assertEquals(new BigDecimal("0.05"), Margin.of("5").asFraction());
  }
}
