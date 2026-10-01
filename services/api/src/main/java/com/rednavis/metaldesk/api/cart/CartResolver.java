package com.rednavis.metaldesk.api.cart;

import com.rednavis.metaldesk.api.auth.AuthenticatedCustomer;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Decides which cart a request acts on, so that a cart survives signing in and out (BRD FR-2.7).
 *
 * <p>A cart is found by its <strong>reference</strong>, which the browser holds in a cookie. For an
 * <em>anonymous</em> cart the reference works as a capability: whoever presents it can act on that
 * cart. A cart that belongs to a customer is not: it is reachable only while signed in as that
 * customer, so after a sign-out the next visitor to the browser sees no cart, and the customer
 * finds theirs again by signing in (BRD FR-2.7). In order, the request's cart is:
 *
 * <ol>
 *   <li><em>Anonymous request:</em> the anonymous cart the reference names (a customer's cart is
 *       ignored); otherwise a new one if the request is changing something, or none if it is only
 *       reading.
 *   <li><em>Signed-in request:</em> the customer's own cart. If the reference names an
 *       <em>anonymous</em> cart, it is first brought in: given to the customer if they have no cart
 *       yet, or merged into theirs (union by product, quantity capped) if they do. The merge takes
 *       the anonymous cart out of circulation in one atomic step, so it happens once however many
 *       times or in parallel the request is repeated, and quantities are never doubled. A reference
 *       to a cart owned by <em>someone else</em>, such as one left in a shared browser, is ignored
 *       and never merged.
 * </ol>
 *
 * <p>After bringing a cart in, the reference of the resulting cart replaces the old one in the
 * cookie (the controller does that), so the browser keeps pointing at a cart that still exists. A
 * stale reference to a cart that was merged away finds nothing and starts empty.
 */
@Component
@RequiredArgsConstructor
public class CartResolver {

  private final CartStore store;

  /**
   * Resolves the cart of a request.
   *
   * @param reference the reference from the cookie, or null
   * @param customer the signed-in customer, or null for an anonymous request
   * @param create whether to create a cart if there is none
   * @return the cart, empty only if there is none and {@code create} is false
   */
  public Mono<ResolvedCart> resolve(
      String reference, AuthenticatedCustomer customer, boolean create) {
    return (customer == null ? anonymous(reference) : signedIn(reference, customer.id().value()))
        .switchIfEmpty(Mono.defer(() -> create ? createFor(customer) : Mono.empty()))
        .map(cart -> new ResolvedCart(java.util.Optional.of(cart)))
        .defaultIfEmpty(new ResolvedCart(java.util.Optional.empty()));
  }

  private Mono<Cart> anonymous(String reference) {
    // A cart with an owner belongs to that customer: whoever is left in a browser after they sign
    // out must not see it, so an anonymous request only ever resolves an anonymous cart.
    return reference == null
        ? Mono.empty()
        : store.find(reference).filter(cart -> cart.owner().isEmpty());
  }

  private Mono<Cart> signedIn(String reference, String customerId) {
    final Mono<Cart> owned = store.findByOwner(customerId);
    return reference == null
        ? owned
        : store
            .find(reference)
            .flatMap(referenced -> bringIn(referenced, customerId))
            .switchIfEmpty(owned);
  }

  private Mono<Cart> bringIn(Cart referenced, String customerId) {
    return referenced.owner().isPresent()
        ? sameOwner(referenced, customerId)
        : store
            .findByOwner(customerId)
            .flatMap(existing -> mergeInto(existing, referenced))
            .switchIfEmpty(Mono.defer(() -> adopt(referenced, customerId)));
  }

  private Mono<Cart> sameOwner(Cart referenced, String customerId) {
    return referenced.owner().get().value().equals(customerId)
        ? Mono.just(referenced)
        : Mono.empty();
  }

  private Mono<Cart> mergeInto(Cart existing, Cart anonymous) {
    return store
        .claimAnonymous(anonymous.id().value())
        .flatMap(claimed -> store.update(existing.id().value(), cart -> cart.mergedWith(claimed)))
        .switchIfEmpty(store.find(existing.id().value()));
  }

  private Mono<Cart> adopt(Cart anonymous, String customerId) {
    return store
        .adopt(anonymous.id().value(), customerId)
        .flatMap(
            adopted -> adopted ? store.find(anonymous.id().value()) : store.findByOwner(customerId))
        // The customer got a cart in the meantime: theirs wins, and the anonymous one is left be.
        .onErrorResume(DuplicateKeyException.class, race -> store.findByOwner(customerId));
  }

  private Mono<Cart> createFor(AuthenticatedCustomer customer) {
    return store
        .create(customer == null ? null : customer.id().value())
        .onErrorResume(
            DuplicateKeyException.class, race -> store.findByOwner(customer.id().value()));
  }
}
