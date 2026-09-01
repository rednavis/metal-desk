package com.rednavis.metaldesk.api.cart.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * The cart as the storefront shows it.
 *
 * <p>An empty cart is an ordinary answer, not an error (BRD FR-3.4): {@code empty} is true, the
 * line list is empty and there are no totals. {@code complete} says whether every line could be
 * priced; when it is false the totals cover only the priced lines and checkout must not proceed.
 *
 * @param cartId the cart's reference; absent if the request has no cart yet
 * @param empty whether the cart has no lines
 * @param itemCount the number of units across all lines, for the badge
 * @param complete whether every line has a sellable price right now
 * @param lines the lines
 * @param totals the running total of the priced lines; absent when none is priced
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CartView(
    String cartId,
    boolean empty,
    int itemCount,
    boolean complete,
    List<CartLineView> lines,
    TotalsView totals) {

  /** Copies the list, so a view cannot be changed through it. */
  public CartView {
    lines = List.copyOf(lines);
  }
}
