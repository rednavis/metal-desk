package com.rednavis.metaldesk.api.checkout.payment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.rednavis.metaldesk.api.catalog.dto.PriceView;
import java.util.List;

/**
 * The payment methods on offer for this checkout (BRD FR-6.1), which depend on the order total
 * (BR-9): above the ceiling gateway methods are absent and invoice remains.
 *
 * @param methods the methods that may be chosen
 * @param highValue whether the order is above the high-value ceiling
 * @param grandTotal the order total the offer was computed for
 * @param selected the method chosen so far, if any
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaymentMethodsView(
    List<PaymentMethodView> methods, boolean highValue, PriceView grandTotal, String selected) {

  /** Copies the list, so the view cannot be changed through it. */
  public PaymentMethodsView {
    methods = List.copyOf(methods);
  }
}
