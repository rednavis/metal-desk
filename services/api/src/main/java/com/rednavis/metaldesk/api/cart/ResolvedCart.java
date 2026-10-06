package com.rednavis.metaldesk.api.cart;

import java.util.Optional;

/**
 * The outcome of resolving which cart a request acts on.
 *
 * @param cart the cart, or empty if the request has none and none was asked to be created
 */
public record ResolvedCart(Optional<Cart> cart) {}
