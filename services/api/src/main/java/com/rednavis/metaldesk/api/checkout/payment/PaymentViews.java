package com.rednavis.metaldesk.api.checkout.payment;

import com.rednavis.metaldesk.api.catalog.dto.PriceView;
import com.rednavis.metaldesk.api.checkout.CheckoutSession;
import com.rednavis.metaldesk.api.checkout.CheckoutViews;
import com.rednavis.metaldesk.api.checkout.delivery.DeliveryState;
import com.rednavis.metaldesk.api.checkout.delivery.DeliveryViews;
import com.rednavis.metaldesk.api.checkout.delivery.dto.DeliveryEvaluationView;
import com.rednavis.metaldesk.api.checkout.payment.dto.OverviewTotalsView;
import com.rednavis.metaldesk.api.checkout.payment.dto.OverviewView;
import com.rednavis.metaldesk.api.checkout.payment.dto.PaymentMethodView;
import com.rednavis.metaldesk.api.checkout.payment.dto.PaymentMethodsView;
import com.rednavis.metaldesk.share.domain.money.Money;
import com.rednavis.metaldesk.share.domain.order.OrderTotals;
import com.rednavis.metaldesk.share.domain.payment.PaymentMethod;
import java.util.List;
import java.util.Optional;

/** Turns payment state into the views the client gets. No domain type leaves here. */
public final class PaymentViews {

  private PaymentViews() {}

  /**
   * The methods on offer.
   *
   * @param session the session
   * @param offered the methods that may be chosen
   * @param totals the order totals the offer was computed for
   * @param highValue whether the total is above the BR-9 ceiling
   * @return the view
   */
  public static PaymentMethodsView methods(
      CheckoutSession session, List<PaymentMethod> offered, OrderTotals totals, boolean highValue) {
    return new PaymentMethodsView(
        offered.stream()
            .map(method -> new PaymentMethodView(method.name(), method.group().name()))
            .toList(),
        highValue,
        price(totals.grandTotal()),
        session.payment().flatMap(PaymentState::method).map(PaymentMethod::name).orElse(null));
  }

  /**
   * The final overview.
   *
   * @param session an evaluated session
   * @param orderReference the order number, if an order exists
   * @return the view
   */
  public static OverviewView overview(CheckoutSession session, Optional<String> orderReference) {
    final OrderTotals totals = PaymentAmounts.of(session);
    return new OverviewView(
        session.id(),
        CheckoutViews.session(session).details(),
        session.payment().flatMap(PaymentState::method).map(PaymentMethod::name).orElse(null),
        CheckoutViews.session(session).basket().lines(),
        DeliveryViews.evaluation(session).map(DeliveryEvaluationView::quote).orElse(null),
        new OverviewTotalsView(
            price(totals.net()),
            price(totals.tax()),
            price(totals.delivery()),
            price(totals.grandTotal())),
        orderReference.orElse(null));
  }

  /**
   * Formats an amount.
   *
   * @param money the amount
   * @return the view
   */
  public static PriceView price(Money money) {
    return new PriceView(money.amount().toPlainString(), money.currency().code());
  }

  /**
   * The delivery state of a session, for callers that need it typed.
   *
   * @param session the session
   * @return its delivery state
   */
  public static DeliveryState delivery(CheckoutSession session) {
    return session.delivery().orElseThrow();
  }
}
