package com.rednavis.metaldesk.payments.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rednavis.metaldesk.payments.provider.DeclineReason;
import com.rednavis.metaldesk.payments.provider.PaymentOutcome;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class GatewayDeclineMapperTest {

  private final GatewayDeclineMapper mapper = new GatewayDeclineMapper();

  @ParameterizedTest
  @CsvSource({
    "insufficient_funds,INSUFFICIENT_FUNDS",
    "instrument_refused,INSTRUMENT_REJECTED",
    "authentication_failed,AUTHENTICATION_FAILED",
    "risk_blocked,RISK_BLOCKED",
    "instrument_expired,EXPIRED"
  })
  void everyMappedCodeGivesItsReasonAndNeedsNoMessage(String code, DeclineReason reason) {
    assertEquals(new PaymentOutcome.Declined(reason, Optional.empty()), mapper.map(code));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"something_new", "INSUFFICIENT_FUNDS", "  ", "a\nb"})
  void anythingElseIsOtherWithSafeMessageAndNeverThrows(String code) {
    final PaymentOutcome.Declined declined = mapper.map(code);
    assertEquals(DeclineReason.OTHER, declined.reason());
    assertTrue(declined.message().isPresent());
  }

  @Test
  void theSafeMessageDoesNotEchoTheGatewaysCode() {
    final String hostile = "leaked-detail-" + "x".repeat(100);
    assertFalse(mapper.map(hostile).message().orElseThrow().contains("leaked-detail"));
  }
}
