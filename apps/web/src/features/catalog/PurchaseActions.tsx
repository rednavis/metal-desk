import { useNavigate } from "react-router";
import type { PricingMode, StockStatus } from "../../api/types";
import { usePreferences } from "../../preferences/usePreferences";
import { ErrorState } from "../../ui";
import { Button } from "../../ui";
import { useCart } from "../cart/useCart";
import { isPriced } from "./pricing";
import "../cart/cart.css";

interface PurchaseActionsProps {
  productId: string;
  name: string;
  pricingMode: PricingMode;
  stock: StockStatus;
}

/**
 * Add to cart and Buy now (BRD FR-3.1, FR-3.2), offered only where they can succeed: only an
 * in-stock, priced product can be bought. An on-request product has neither, and an out-of-stock
 * one shows its price but cannot be added (the server refuses both with a 400, and a button that
 * always fails is worse than none).
 *
 * Add to cart sends the request and the badge follows the response, so a repeat add shows whatever
 * the server did with it. Buy now does not touch the cart: it opens checkout step 1 for one unit of
 * this product, by way of the URL.
 */
export function PurchaseActions({ productId, name, pricingMode, stock }: PurchaseActionsProps) {
  const { t } = usePreferences();
  const { cart, busy, failure, add } = useCart();
  const navigate = useNavigate();

  if (!isPriced(pricingMode) || stock !== "IN_STOCK") return null;

  const inCart = cart?.lines.some((line) => line.productId === productId) ?? false;
  return (
    <div className="md-actions">
      <Button
        aria-label={`${t("cart.add")}: ${name}`}
        disabled={busy}
        onClick={() => {
          void add(productId);
        }}
      >
        {t("cart.add")}
      </Button>
      <Button
        variant="secondary"
        aria-label={`${t("cart.buyNow")}: ${name}`}
        onClick={() => {
          void navigate({
            pathname: "/checkout",
            search: `?buyNow=${encodeURIComponent(productId)}`,
          });
        }}
      >
        {t("cart.buyNow")}
      </Button>
      {inCart ? <span role="status">{t("cart.inCart")}</span> : null}
      {failure?.productId === productId ? <ErrorState error={failure.error} /> : null}
    </div>
  );
}
