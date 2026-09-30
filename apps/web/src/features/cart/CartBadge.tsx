import { usePreferences } from "../../preferences/usePreferences";
import { useCart } from "./useCart";

/**
 * The cart link's label with the number of items in it, as the server last reported it. The count is
 * never incremented here: it changes when a response arrives (BRD FR-3.1), in place, so adding an
 * item needs no navigation or reload.
 */
export function CartBadge() {
  const { t } = usePreferences();
  const { cart } = useCart();
  const count = cart?.itemCount ?? 0;
  return (
    <>
      <span>{t("nav.cart")}</span>
      {count > 0 ? (
        <>
          <span className="md-cart-badge" data-testid="cart-count" aria-hidden="true">
            {count}
          </span>
          <span className="md-sr-only">{t("cart.itemCount", { count })}</span>
        </>
      ) : null}
    </>
  );
}
