package com.rednavis.metaldesk.api.cart;

import com.rednavis.metaldesk.api.auth.AuthenticatedCustomer;
import com.rednavis.metaldesk.api.cart.dto.AddressView;
import com.rednavis.metaldesk.api.cart.dto.CartView;
import com.rednavis.metaldesk.api.persistence.repository.CustomerRepository;
import com.rednavis.metaldesk.persistence.mapper.CustomerMapper;
import com.rednavis.metaldesk.share.domain.customer.Address;
import com.rednavis.metaldesk.share.domain.customer.AddressKind;
import com.rednavis.metaldesk.share.domain.id.ProductId;
import com.rednavis.metaldesk.share.domain.order.Quantity;
import com.rednavis.metaldesk.share.error.ValidationException;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * What checkout starts from (BRD FR-3.2, FR-3.5): a {@link CheckoutBasket}, whether it comes from
 * the cart or from a buy-now that skips the cart, and the saved delivery address that pre-fills the
 * form.
 */
@Service
@RequiredArgsConstructor
public class BasketService {

  private final CartResolver resolver;
  private final CartPricing pricing;
  private final CartProducts cartProducts;
  private final CustomerRepository customers;
  private final CustomerMapper customerMapper;

  /**
   * Builds the checkout basket of the cart.
   *
   * @param reference the cart reference, or null
   * @param customer the signed-in customer, or null
   * @return the basket; an empty one if there is no cart
   */
  public Mono<CheckoutBasket> basketFor(String reference, AuthenticatedCustomer customer) {
    return resolver
        .resolve(reference, customer, false)
        .flatMap(
            resolved ->
                resolved
                    .cart()
                    .map(this::basketOf)
                    .orElseGet(
                        () ->
                            Mono.just(
                                CheckoutBasket.of(
                                    CheckoutBasket.Source.CART, Optional.empty(), List.of()))));
  }

  private Mono<CheckoutBasket> basketOf(Cart cart) {
    return pricing
        .price(cart.lines())
        .map(lines -> CheckoutBasket.of(CheckoutBasket.Source.CART, Optional.of(cart.id()), lines));
  }

  /**
   * Starts a buy-now of one unit (BRD FR-3.2): a basket of a single line that never touches the
   * cart.
   *
   * @param productId the product's id
   * @return the one-line basket
   * @throws ValidationException {@code cart.product-unpriced} if the catalog sells the product on
   *     request, or {@code cart.price-unavailable} if it has no sellable price at the moment
   */
  public Mono<CheckoutBasket> buyNow(String productId) {
    return buyNowLines(productId)
        .map(lines -> CheckoutBasket.of(CheckoutBasket.Source.BUY_NOW, Optional.empty(), lines))
        .filter(CheckoutBasket::checkoutable)
        .switchIfEmpty(Mono.error(priceUnavailable()));
  }

  /**
   * Previews a buy-now as the storefront's cart view, without a cart reference.
   *
   * @param productId the product's id
   * @return the one-line view
   */
  public Mono<CartView> buyNowView(String productId) {
    return buyNowLines(productId)
        .map(
            lines ->
                CartViews.of(
                    Optional.empty(),
                    lines,
                    CheckoutBasket.of(CheckoutBasket.Source.BUY_NOW, Optional.empty(), lines)))
        .filter(CartView::complete)
        .switchIfEmpty(Mono.error(priceUnavailable()));
  }

  /**
   * Reads the signed-in customer's saved delivery address, to pre-fill checkout (BRD FR-3.5). It is
   * a suggestion; nothing may treat it as authoritative.
   *
   * @param customerId the customer's id
   * @return the address, or empty if the customer has none saved
   */
  public Mono<AddressView> deliveryProfile(String customerId) {
    return customers
        .findById(customerId)
        .map(customerMapper::toDomain)
        .flatMap(customer -> Mono.justOrEmpty(customer.primaryAddress(AddressKind.DELIVERY)))
        .map(BasketService::addressView);
  }

  private Mono<List<PricedLine>> buyNowLines(String productId) {
    final ProductId id = cartProducts.idOf(productId);
    return cartProducts
        .loadSellable(productId)
        .then(pricing.price(List.of(new CartLine(id, Quantity.of(1)))));
  }

  private static ValidationException priceUnavailable() {
    return new ValidationException(
        "cart.price-unavailable", "That product has no price at the moment");
  }

  private static AddressView addressView(Address address) {
    return new AddressView(
        address.street(),
        address.city(),
        address.country().code(),
        address.postalCode(),
        address.companyName(),
        address.companyAddress());
  }
}
