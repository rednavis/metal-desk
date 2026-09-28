package com.rednavis.metaldesk.payments.wallet;

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

class WalletDeclineMapperTest {

  private final WalletDeclineMapper mapper = new WalletDeclineMapper();

  @ParameterizedTest
  @CsvSource({
    "insufficient_balance,INSUFFICIENT_FUNDS",
    "account_blocked,RISK_BLOCKED",
    "account_closed,INSTRUMENT_REJECTED"
  })
  void everyMappedCodeGivesItsReasonAndNeedsNoMessage(String code, DeclineReason reason) {
    assertEquals(new PaymentOutcome.Declined(reason, Optional.empty()), mapper.map(code));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"something_new", "INSUFFICIENT_BALANCE", "  ", "a\nb"})
  void anythingElseIsOtherWithSafeMessageAndNeverThrows(String code) {
    final PaymentOutcome.Declined declined = mapper.map(code);
    assertEquals(DeclineReason.OTHER, declined.reason());
    assertTrue(declined.message().isPresent());
  }

  @Test
  void safeMessageDoesNotEchoTheProvidersCode() {
    final String hostile = "leaked-detail-" + "x".repeat(100);
    assertFalse(mapper.map(hostile).message().orElseThrow().contains("leaked-detail"));
  }
}
