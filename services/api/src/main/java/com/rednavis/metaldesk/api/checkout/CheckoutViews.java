package com.rednavis.metaldesk.api.checkout;

import com.rednavis.metaldesk.api.cart.CartLine;
import com.rednavis.metaldesk.api.cart.CartViews;
import com.rednavis.metaldesk.api.cart.CheckoutBasket;
import com.rednavis.metaldesk.api.cart.PricedLine;
import com.rednavis.metaldesk.api.checkout.dto.ConsentView;
import com.rednavis.metaldesk.api.checkout.dto.ConversionView;
import com.rednavis.metaldesk.api.checkout.dto.DetailsView;
import com.rednavis.metaldesk.api.checkout.dto.SessionView;
import com.rednavis.metaldesk.api.checkout.dto.Step1Response;
import com.rednavis.metaldesk.api.checkout.step1.ConsentRecord;
import com.rednavis.metaldesk.api.checkout.step1.ConversionState;
import com.rednavis.metaldesk.api.checkout.step1.CustomerDetails;
import com.rednavis.metaldesk.share.domain.customer.Address;
import java.util.List;
import java.util.Optional;

/** Turns a checkout session into the views the client gets. No domain type leaves here. */
public final class CheckoutViews {

  private CheckoutViews() {}

  /**
   * The whole session.
   *
   * @param session the session
   * @return the view
   */
  public static SessionView session(CheckoutSession session) {
    final List<PricedLine> lines =
        session.lines().stream()
            .map(
                line ->
                    new PricedLine(
                        new CartLine(line.productId(), line.quantity()),
                        line.productName(),
                        Optional.of(line)))
            .toList();
    final CheckoutBasket basket =
        new CheckoutBasket(
            session.source() == CheckoutSession.Source.CART
                ? CheckoutBasket.Source.CART
                : CheckoutBasket.Source.BUY_NOW,
            session.cart(),
            session.lines(),
            List.of());
    return new SessionView(
        session.id(),
        session.source().name(),
        CartViews.of(session.cart(), lines, basket),
        session.details().map(CheckoutViews::details).orElse(null),
        session.consent().map(CheckoutViews::consent).orElse(null),
        session.conversion().map(CheckoutViews::conversion).orElse(null));
  }

  /**
   * The answer to a successful step 1.
   *
   * @param session the session after step 1
   * @return the response
   */
  public static Step1Response step1(CheckoutSession session) {
    return new Step1Response(
        session.id(),
        true,
        session.details().map(CheckoutViews::details).orElse(null),
        session.consent().map(CheckoutViews::consent).orElse(null),
        session.conversion().map(CheckoutViews::conversion).orElse(null));
  }

  private static DetailsView details(CustomerDetails details) {
    final Address address = details.deliveryAddress();
    return new DetailsView(
        details.name(),
        details.email().value(),
        details.phone().value(),
        address.street(),
        address.city(),
        address.country().code(),
        address.postalCode(),
        address.companyName(),
        address.companyAddress(),
        details.note().orElse(null));
  }

  private static ConsentView consent(ConsentRecord consent) {
    return new ConsentView(consent.policyVersion(), consent.acceptedAt());
  }

  private static ConversionView conversion(ConversionState state) {
    return new ConversionView(state.reference(), state.verified());
  }
}
