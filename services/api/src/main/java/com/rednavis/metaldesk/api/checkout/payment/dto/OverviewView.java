package com.rednavis.metaldesk.api.checkout.payment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.rednavis.metaldesk.api.cart.dto.CartLineView;
import com.rednavis.metaldesk.api.checkout.delivery.dto.QuoteView;
import com.rednavis.metaldesk.api.checkout.dto.DetailsView;
import java.util.List;

/**
 * The final order overview shown before payment is captured (BRD FR-7.1): the customer and delivery
 * details, the selected payment method, the line items, tax, delivery cost and grand total. It is
 * computed from the session each time, never stored, and its grand total is exactly the amount the
 * provider is asked to charge. Any earlier step can still be edited.
 *
 * @param checkoutId the session's id
 * @param details the customer and delivery details
 * @param paymentMethod the selected method, if one is chosen
 * @param lines the line items with their prices and tax
 * @param delivery the delivery quote
 * @param totals the totals
 * @param orderReference the order number, once payment was started
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OverviewView(
    String checkoutId,
    DetailsView details,
    String paymentMethod,
    List<CartLineView> lines,
    QuoteView delivery,
    OverviewTotalsView totals,
    String orderReference) {

  /** Copies the list, so the view cannot be changed through it. */
  public OverviewView {
    lines = List.copyOf(lines);
  }
}
