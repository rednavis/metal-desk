package com.rednavis.metaldesk.api.cart;

import com.rednavis.metaldesk.api.auth.AuthenticatedCustomer;
import com.rednavis.metaldesk.api.cart.dto.AddLineRequest;
import com.rednavis.metaldesk.api.cart.dto.AddressView;
import com.rednavis.metaldesk.api.cart.dto.CartView;
import com.rednavis.metaldesk.api.cart.dto.QuantityRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * The cart endpoints (BRD FR-3.1 to FR-3.5). They are public: a shopper has a cart before signing
 * in, and a signed-in shopper is recognised by their token.
 *
 * <p>The cart's reference travels in the {@value #COOKIE} cookie ({@code HttpOnly}, {@code
 * SameSite=Lax}, limited to {@code /api/cart}), which the response sets whenever the request
 * resolved to a cart. The reference is a capability, so the cookie is what keeps a cart reachable
 * after sign-out. Each response is the whole cart, so the client never has to reassemble it.
 */
@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

  /** The name of the cookie holding the cart reference. */
  public static final String COOKIE = "metal_cart";

  private final CartService service;
  private final BasketService baskets;
  private final CartProperties properties;

  /**
   * Shows the cart. With no cart yet the answer is 200 and the empty state, never 404.
   *
   * @param reference the cart cookie, if any
   * @param customer the signed-in customer, if any
   * @return the cart
   */
  @GetMapping
  public Mono<ResponseEntity<CartView>> cart(
      @CookieValue(name = COOKIE, required = false) String reference,
      @AuthenticationPrincipal AuthenticatedCustomer customer) {
    return service.view(reference, customer).map(this::respond);
  }

  /**
   * Adds one unit of a product; adding one already in the cart leaves the cart as it is.
   *
   * @param reference the cart cookie, if any
   * @param customer the signed-in customer, if any
   * @param request the product
   * @return the cart
   */
  @PostMapping("/lines")
  public Mono<ResponseEntity<CartView>> add(
      @CookieValue(name = COOKIE, required = false) String reference,
      @AuthenticationPrincipal AuthenticatedCustomer customer,
      @RequestBody AddLineRequest request) {
    return service.add(reference, customer, request.productId()).map(this::respond);
  }

  /**
   * Sets a line's quantity.
   *
   * @param reference the cart cookie, if any
   * @param customer the signed-in customer, if any
   * @param productId the product's id
   * @param request the new quantity
   * @return the cart; 400 above the cap, 404 if there is no such line
   */
  @PutMapping("/lines/{productId}")
  public Mono<ResponseEntity<CartView>> quantity(
      @CookieValue(name = COOKIE, required = false) String reference,
      @AuthenticationPrincipal AuthenticatedCustomer customer,
      @PathVariable String productId,
      @RequestBody QuantityRequest request) {
    return service
        .changeQuantity(reference, customer, productId, request.quantity())
        .map(this::respond);
  }

  /**
   * Removes a line; removing one that is not there changes nothing.
   *
   * @param reference the cart cookie, if any
   * @param customer the signed-in customer, if any
   * @param productId the product's id
   * @return the cart
   */
  @DeleteMapping("/lines/{productId}")
  public Mono<ResponseEntity<CartView>> remove(
      @CookieValue(name = COOKIE, required = false) String reference,
      @AuthenticationPrincipal AuthenticatedCustomer customer,
      @PathVariable String productId) {
    return service.remove(reference, customer, productId).map(this::respond);
  }

  /**
   * Empties the cart.
   *
   * @param reference the cart cookie, if any
   * @param customer the signed-in customer, if any
   * @return the empty cart
   */
  @DeleteMapping
  public Mono<ResponseEntity<CartView>> clear(
      @CookieValue(name = COOKIE, required = false) String reference,
      @AuthenticationPrincipal AuthenticatedCustomer customer) {
    return service.clear(reference, customer).map(this::respond);
  }

  /**
   * Previews a buy-now of one unit (BRD FR-3.2): a one-line basket that skips the cart, which is
   * left untouched. Only for products with a price.
   *
   * @param request the product
   * @return the one-line basket, without a cart reference; 400 for an unpriced product
   */
  @PostMapping("/buy-now")
  public Mono<CartView> buyNow(@RequestBody AddLineRequest request) {
    return baskets.buyNowView(request.productId());
  }

  /**
   * The signed-in customer's saved delivery address, to pre-fill checkout (BRD FR-3.5). It is a
   * suggestion only. Needs a token.
   *
   * @param customer the signed-in customer
   * @return 200 with the address, or 204 if none is saved
   */
  @GetMapping("/delivery-profile")
  public Mono<ResponseEntity<AddressView>> deliveryProfile(
      @AuthenticationPrincipal AuthenticatedCustomer customer) {
    return baskets
        .deliveryProfile(customer.id().value())
        .map(ResponseEntity::ok)
        .defaultIfEmpty(ResponseEntity.noContent().build());
  }

  private ResponseEntity<CartView> respond(CartView view) {
    final ResponseEntity.BodyBuilder builder = ResponseEntity.ok();
    if (view.cartId() != null) {
      builder.header(
          HttpHeaders.SET_COOKIE,
          ResponseCookie.from(COOKIE, view.cartId())
              .httpOnly(true)
              .secure(properties.cookieSecure())
              .sameSite("Lax")
              .path("/api/cart")
              .maxAge(properties.cookieMaxAge())
              .build()
              .toString());
    }
    return builder.body(view);
  }
}
