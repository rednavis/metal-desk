package com.rednavis.metaldesk.api.checkout.payment;

import com.rednavis.metaldesk.api.checkout.payment.dto.ExecutePaymentRequest;
import com.rednavis.metaldesk.api.web.FieldViolation;
import com.rednavis.metaldesk.api.web.FieldViolationsException;
import java.math.BigDecimal;
import java.util.List;

/** Reads the total a customer confirmed, refusing a request that does not carry one. */
public final class ConfirmedTotal {

  private static final String FIELD = "confirmedTotal";
  private static final String MESSAGE = "The total you saw on the overview is required";

  private ConfirmedTotal() {}

  /**
   * Reads the confirmed total.
   *
   * @param request the payment request, possibly missing
   * @return the amount
   * @throws FieldViolationsException {@code required} if it is absent, {@code format} if it is not
   *     a decimal amount
   */
  public static BigDecimal of(ExecutePaymentRequest request) {
    final String text = request == null ? null : request.confirmedTotal();
    if (text == null || text.isBlank()) {
      throw new FieldViolationsException(List.of(new FieldViolation(FIELD, "required", MESSAGE)));
    }
    try {
      return new BigDecimal(text.strip());
    } catch (NumberFormatException malformed) {
      throw new FieldViolationsException(
          List.of(new FieldViolation(FIELD, "format", MESSAGE)), malformed);
    }
  }
}
