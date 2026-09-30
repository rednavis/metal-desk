package com.rednavis.metaldesk.api.cart;

import com.rednavis.metaldesk.api.auth.AuthenticatedCustomer;
import com.rednavis.metaldesk.api.cart.dto.CartView;
import com.rednavis.metaldesk.share.domain.id.ProductId;
import com.rednavis.metaldesk.share.domain.order.Quantity;
import com.rednavis.metaldesk.share.error.NotFoundException;
import com.rednavis.metaldesk.share.error.ValidationException;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * The cart's operations (BRD FR-3.1, FR-3.3, FR-3.4).
 *
 * <p>Every method takes the cart <em>reference</em> from the request and the signed-in customer, if
 * any, and lets {@link CartResolver} decide which cart that means. The semantics the BRD leaves
 * open are stated on {@link Cart}: add-to-cart is a no-op when the line exists, removing an absent
 * line is a no-op, an over-cap quantity is refused, and a read never creates a cart. What checkout
 * takes, a {@link CheckoutBasket}, is {@link BasketService}'s.
 */
@Service
@RequiredArgsConstructor
public class CartService {

  private final CartResolver resolver;
  private final CartStore store;
  private final CartPricing pricing;
  private final CartProducts cartProducts;

  /**
   * Shows the cart. Reading never creates one: with none, the answer is the empty state.
   *
   * @param reference the cart reference from the cookie, or null
   * @param customer the signed-in customer, or null
   * @return the cart, possibly empty
   */
  public Mono<CartView> view(String reference, AuthenticatedCustomer customer) {
    return resolver.resolve(reference, customer, false).flatMap(this::present);
  }

  /**
   * Adds one unit of a product, unless the cart already has a line for it (BRD FR-3.1).
   *
   * @param reference the cart reference, or null
   * @param customer the signed-in customer, or null
   * @param productId the product's id
   * @return the cart after the add
   * @throws ValidationException {@code id.malformed}, or {@code cart.product-unpriced} for a
   *     product the catalog sells on request
   * @throws NotFoundException {@code product.not-found}
   */
  public Mono<CartView> add(String reference, AuthenticatedCustomer customer, String productId) {
    final ProductId id = cartProducts.idOf(productId);
    return cartProducts
        .loadSellable(productId)
        .then(resolver.resolve(reference, customer, true))
        .flatMap(resolved -> change(resolved, cart -> cart.addIfAbsent(id)));
  }

  /**
   * Changes the quantity of a line (BRD FR-3.3).
   *
   * @param reference the cart reference, or null
   * @param customer the signed-in customer, or null
   * @param productId the product's id
   * @param quantity the new quantity, 1 up to {@link Quantity#MAX}
   * @return the cart after the change
   * @throws ValidationException {@code quantity.above-cap} or {@code quantity.not-positive}; the
   *     cart is unchanged
   * @throws NotFoundException {@code cart.line-not-found} if the cart has no such line
   */
  public Mono<CartView> changeQuantity(
      String reference, AuthenticatedCustomer customer, String productId, int quantity) {
    final ProductId id = cartProducts.idOf(productId);
    final Quantity wanted = Quantity.of(quantity);
    return resolver
        .resolve(reference, customer, false)
        .flatMap(
            resolved ->
                resolved.cart().isPresent()
                    ? change(resolved, cart -> cart.withQuantity(id, wanted))
                    : Mono.error(
                        new NotFoundException(
                            "cart.line-not-found", "The cart has no line for that product")));
  }

  /**
   * Removes a line. Removing a line that is not there is a no-op, not an error.
   *
   * @param reference the cart reference, or null
   * @param customer the signed-in customer, or null
   * @param productId the product's id
   * @return the cart after the removal
   */
  public Mono<CartView> remove(String reference, AuthenticatedCustomer customer, String productId) {
    final ProductId id = cartProducts.idOf(productId);
    return resolver
        .resolve(reference, customer, false)
        .flatMap(resolved -> change(resolved, cart -> cart.without(id)));
  }

  /**
   * Empties the cart.
   *
   * @param reference the cart reference, or null
   * @param customer the signed-in customer, or null
   * @return the empty cart
   */
  public Mono<CartView> clear(String reference, AuthenticatedCustomer customer) {
    return resolver
        .resolve(reference, customer, false)
        .flatMap(resolved -> change(resolved, Cart::cleared));
  }

  private Mono<CartView> change(ResolvedCart resolved, UnaryOperator<Cart> change) {
    return resolved
        .cart()
        .map(cart -> store.update(cart.id().value(), change))
        .orElseGet(Mono::empty)
        .flatMap(cart -> present(new ResolvedCart(Optional.of(cart))))
        .switchIfEmpty(Mono.defer(() -> present(new ResolvedCart(Optional.empty()))));
  }

  private Mono<CartView> present(ResolvedCart resolved) {
    return resolved
        .cart()
        .map(
            cart -> pricing.price(cart.lines()).map(lines -> viewOf(Optional.of(cart.id()), lines)))
        .orElseGet(() -> Mono.just(viewOf(Optional.empty(), List.of())));
  }

  private static CartView viewOf(Optional<CartId> cartId, List<PricedLine> lines) {
    return CartViews.of(
        cartId, lines, CheckoutBasket.of(CheckoutBasket.Source.CART, cartId, lines));
  }
}
