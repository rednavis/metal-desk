package com.rednavis.metaldesk.payments.wallet;

import com.rednavis.metaldesk.payments.http.LogSafe;
import com.rednavis.metaldesk.payments.provider.DeclineReason;
import com.rednavis.metaldesk.payments.provider.PaymentOutcome;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/**
 * Translates the wallet provider's own decline codes into the vendor-neutral {@link DeclineReason}.
 * Its table adds the account-based case: an insufficient balance is {@link
 * DeclineReason#INSUFFICIENT_FUNDS}.
 *
 * <p>As with the gateway, an unknown code is still a decline: it becomes {@link
 * DeclineReason#OTHER} with a message written here, never the provider's own text, and is logged at
 * warn. It never throws (BRD FR-6.3).
 */
@Slf4j
final class WalletDeclineMapper {

  private static final String OTHER_MESSAGE = "The payment was declined.";

  private static final Map<String, DeclineReason> KNOWN_CODES =
      Map.of(
          "insufficient_balance", DeclineReason.INSUFFICIENT_FUNDS,
          "account_blocked", DeclineReason.RISK_BLOCKED,
          "account_closed", DeclineReason.INSTRUMENT_REJECTED);

  /**
   * Maps a wallet decline code to a decline outcome.
   *
   * @param code the provider's decline code, possibly null or unknown
   * @return a {@link PaymentOutcome.Declined} with the mapped reason, or {@link
   *     DeclineReason#OTHER} and a safe message when the code is missing or not recognised
   */
  /* default */ PaymentOutcome.Declined map(String code) {
    final DeclineReason reason = code == null ? null : KNOWN_CODES.get(code);
    if (reason == null) {
      warnUnmapped(code);
    }
    return reason == null
        ? new PaymentOutcome.Declined(DeclineReason.OTHER, Optional.of(OTHER_MESSAGE))
        : new PaymentOutcome.Declined(reason, Optional.empty());
  }

  private static void warnUnmapped(String code) {
    if (log.isWarnEnabled()) {
      log.warn("Unmapped wallet decline code: {}", LogSafe.code(code));
    }
  }
}
