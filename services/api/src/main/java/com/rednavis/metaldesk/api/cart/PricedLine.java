package com.rednavis.metaldesk.api.cart;

import com.rednavis.metaldesk.share.domain.order.OrderLine;
import java.util.Optional;

/**
 * A cart line as priced at this moment: the display name, and the order line it would become if it
 * has a sellable price now. Nothing here is stored.
 *
 * @param line the cart line
 * @param name the product's name, or its id if the product is no longer in the catalog
 * @param priced the line as it would be ordered now, empty if it has no sellable price (on request,
 *     no margin rule, no reference price yet, or the product is gone)
 */
public record PricedLine(CartLine line, String name, Optional<OrderLine> priced) {}
