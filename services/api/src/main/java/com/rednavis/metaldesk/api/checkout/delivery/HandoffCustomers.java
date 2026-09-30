package com.rednavis.metaldesk.api.checkout.delivery;

import com.rednavis.metaldesk.api.account.AccountCreation;
import com.rednavis.metaldesk.api.checkout.CheckoutSession;
import com.rednavis.metaldesk.api.checkout.step1.ConversionState;
import com.rednavis.metaldesk.api.checkout.step1.CustomerDetails;
import com.rednavis.metaldesk.api.persistence.repository.CustomerRepository;
import com.rednavis.metaldesk.persistence.document.CustomerDocument;
import com.rednavis.metaldesk.share.domain.id.CustomerId;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Finds the customer an order belongs to (an {@code Order} always has one).
 *
 * <p>In order: the signed-in customer who started the checkout; the account this guest created with
 * "remember me"; the customer that already has the guest's email address, so the order lands with
 * whoever owns that mailbox and nothing about the account is disclosed to the guest; and otherwise
 * a new {@code UNVERIFIED} contact record with no credential. Nothing is created for a checkout
 * that never reaches an order.
 */
@Component
@RequiredArgsConstructor
public class HandoffCustomers {

  private final CustomerRepository customers;
  private final AccountCreation accounts;

  /**
   * Resolves the customer of a session's order.
   *
   * @param session a session whose step 1 is complete
   * @return the customer's id
   */
  public Mono<CustomerId> resolve(CheckoutSession session) {
    final CustomerDetails details = session.details().orElseThrow();
    return Mono.justOrEmpty(session.owner())
        .switchIfEmpty(Mono.justOrEmpty(session.conversion().flatMap(ConversionState::customer)))
        .switchIfEmpty(Mono.defer(() -> byEmailOrNew(details)));
  }

  private Mono<CustomerId> byEmailOrNew(CustomerDetails details) {
    return customers
        .findByEmail(details.email().value())
        .switchIfEmpty(
            Mono.defer(
                () ->
                    accounts
                        .createGuest(details.name(), details.email(), details.phone())
                        .onErrorResume(
                            DuplicateKeyException.class,
                            race -> customers.findByEmail(details.email().value()))))
        .map(CustomerDocument::id)
        .map(CustomerId::new);
  }
}
