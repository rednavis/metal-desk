import { Link, Navigate, useLocation } from "react-router";
import { useCart } from "../../features/cart/useCart";
import { usePreferences } from "../../preferences/usePreferences";

/**
 * Where signing out from inside a checkout lands (BRD FR-2.7): the customer is told they are signed
 * out, that the cart was kept (and shown as the server holds it, which is how that is verified), and
 * offered to resume the checkout or go home. Anyone who arrives without having just signed out of a
 * checkout is sent home.
 */
export function SignOutPrompt() {
  const { t } = usePreferences();
  const { cart } = useCart();
  const location = useLocation();
  const midCheckout = (location.state as { midCheckout?: boolean } | null)?.midCheckout === true;
  if (!midCheckout) return <Navigate to="/" replace />;

  const count = cart?.itemCount ?? 0;
  return (
    <section aria-labelledby="signed-out-title">
      <h1 id="signed-out-title">{t("auth.signedOut.title")}</h1>
      <p data-testid="cart-kept">
        {t("auth.signedOut.cart", { items: t("cart.itemCount", { count }) })}
      </p>
      <div className="md-actions">
        <Link to="/checkout">{t("auth.signedOut.resume")}</Link>
        <Link to="/">{t("auth.signedOut.home")}</Link>
      </div>
    </section>
  );
}
