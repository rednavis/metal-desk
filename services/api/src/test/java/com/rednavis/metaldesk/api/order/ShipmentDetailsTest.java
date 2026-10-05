package com.rednavis.metaldesk.api.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.rednavis.metaldesk.share.error.ValidationException;
import org.junit.jupiter.api.Test;

/** A shipment needs a carrier and a tracking reference, both stripped. */
class ShipmentDetailsTest {

  private static final String CARRIER = "DHL";
  private static final String SPACE = " ";
  private static final String BLANK = "shipment.field-blank";
  private static final String TRACKING = "T1";

  private static String codeOf(String carrier, String tracking) {
    return assertThrows(ValidationException.class, () -> new ShipmentDetails(carrier, tracking))
        .code();
  }

  @Test
  void blankOrMissingPartsAreRefused() {
    assertEquals(BLANK, codeOf(null, TRACKING));
    assertEquals(BLANK, codeOf(SPACE, TRACKING));
    assertEquals(BLANK, codeOf(CARRIER, null));
    assertEquals(BLANK, codeOf(CARRIER, SPACE));
  }

  @Test
  void partsAreStripped() {
    final ShipmentDetails details =
        new ShipmentDetails(SPACE + CARRIER + SPACE, SPACE + TRACKING + SPACE);
    assertEquals(CARRIER, details.carrier());
    assertEquals(TRACKING, details.trackingReference());
  }
}
