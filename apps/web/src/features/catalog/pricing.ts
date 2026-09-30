import type { PricingMode } from "../../api/types";

/**
 * Whether a product is sold at a fixed price. The one question the storefront asks about a price, and
 * it is answered from the server's `pricingMode`, never from the price itself: an on-request product
 * has no price at all (T-012, T-031), and a product that is genuinely free must not turn into a
 * quote request because its amount happens to be small.
 */
export function isPriced(pricingMode: PricingMode): boolean {
  return pricingMode === "FIXED";
}
