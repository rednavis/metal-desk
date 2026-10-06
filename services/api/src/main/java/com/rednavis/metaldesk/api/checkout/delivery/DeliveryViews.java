package com.rednavis.metaldesk.api.checkout.delivery;

import com.rednavis.metaldesk.api.catalog.dto.PriceView;
import com.rednavis.metaldesk.api.checkout.CheckoutSession;
import com.rednavis.metaldesk.api.checkout.delivery.dto.DeliveryEvaluationView;
import com.rednavis.metaldesk.api.checkout.delivery.dto.HandoffView;
import com.rednavis.metaldesk.api.checkout.delivery.dto.QuoteView;
import com.rednavis.metaldesk.share.domain.fulfillment.DeliveryQuote;
import com.rednavis.metaldesk.share.domain.money.Money;
import java.util.Optional;

/** Turns a session's delivery state into the views the client gets. */
public final class DeliveryViews {

  private DeliveryViews() {}

  /**
   * The delivery evaluation of a session.
   *
   * @param session the session
   * @return the view, or empty if the session has not been evaluated
   */
  public static Optional<DeliveryEvaluationView> evaluation(CheckoutSession session) {
    return session
        .delivery()
        .map(
            state ->
                new DeliveryEvaluationView(
                    state.stage().name(),
                    state.quote().map(DeliveryViews::quote).orElse(null),
                    state.reason().map(HandoffReason::name).orElse(null),
                    state.reason().flatMap(DeliveryViews::ceiling).orElse(null),
                    price(state.exTaxValue()),
                    state.weight().amount().toPlainString(),
                    session.handoff().map(HandoffRecord::reference).orElse(null)));
  }

  /**
   * The answer to a handoff.
   *
   * @param session the handed-off session
   * @return the view
   */
  public static HandoffView handoff(CheckoutSession session) {
    final Optional<HandoffReason> reason = session.delivery().flatMap(DeliveryState::reason);
    return new HandoffView(
        session.handoff().map(HandoffRecord::reference).orElseThrow(),
        reason.map(HandoffReason::name).orElse(null),
        reason.flatMap(DeliveryViews::ceiling).orElse(null));
  }

  /**
   * Names the ceiling a reason refers to.
   *
   * @param reason the reason
   * @return {@code VALUE} or {@code WEIGHT}, or empty for a region with no tier
   */
  public static Optional<String> ceiling(HandoffReason reason) {
    return switch (reason) {
      case VALUE_CEILING_EXCEEDED -> Optional.of("VALUE");
      case WEIGHT_CEILING_EXCEEDED -> Optional.of("WEIGHT");
      case NO_TIER_FOR_REGION -> Optional.empty();
    };
  }

  private static QuoteView quote(DeliveryQuote quote) {
    return new QuoteView(
        quote.tierId().value(),
        price(quote.cost()),
        quote.transit().minDays(),
        quote.transit().maxDays(),
        quote.quotedAt());
  }

  private static PriceView price(Money money) {
    return new PriceView(money.amount().toPlainString(), money.currency().code());
  }
}
