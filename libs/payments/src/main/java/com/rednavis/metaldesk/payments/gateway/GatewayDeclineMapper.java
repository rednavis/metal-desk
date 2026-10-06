package com.rednavis.metaldesk.payments.gateway;

import com.rednavis.metaldesk.payments.http.LogSafe;
import com.rednavis.metaldesk.payments.provider.DeclineReason;
import com.rednavis.metaldesk.payments.provider.PaymentOutcome;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/**
 * Translates the gateway's own decline codes into the vendor-neutral {@link DeclineReason}, so
 * nothing above the adapter ever sees a gateway code.
 *
 * <p><strong>An unknown code is still a decline.</strong> A code this mapper has not heard of
 * becomes {@link DeclineReason#OTHER} with a message written here, never the gateway's own text,
 * and is logged at warn so the table can be extended. It never throws: turning a recoverable
 * decline into an error would send the customer to an error page for what is an ordinary outcome
 * (BRD FR-6.3).
 */
@Slf4j
final class GatewayDeclineMapper {

  private static final String OTHER_MESSAGE = "The payment was declined.";

  private static final Map<String, DeclineReason> KNOWN_CODES =
      Map.of(
          "insufficient_funds", DeclineReason.INSUFFICIENT_FUNDS,
          "instrument_refused", DeclineReason.INSTRUMENT_REJECTED,
          "authentication_failed", DeclineReason.AUTHENTICATION_FAILED,
          "risk_blocked", DeclineReason.RISK_BLOCKED,
          "instrument_expired", DeclineReason.EXPIRED);

  /**
   * Maps a gateway decline code to a decline outcome.
   *
   * @param code the gateway's decline code, possibly null or unknown
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
      log.warn("Unmapped gateway decline code: {}", LogSafe.code(code));
    }
  }
}
