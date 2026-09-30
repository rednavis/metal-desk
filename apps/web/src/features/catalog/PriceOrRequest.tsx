import { Link } from "react-router";
import type { PriceView, PricingMode } from "../../api/types";
import { usePreferences } from "../../preferences/usePreferences";
import { isPriced } from "./pricing";

interface PriceOrRequestProps {
  productId: string;
  pricingMode: PricingMode;
  /** Absent for an on-request product; ignored unless the product is priced. */
  price?: PriceView;
}

/**
 * The single place the priced/unpriced decision is rendered (BRD FR-1.2). A priced product shows its
 * price, formatted in the currency it arrived in; any other shows the "request price" affordance,
 * which leads to the inquiry flow, and never a price or a zero. The decision is `pricingMode`'s; the
 * price is only ever formatted, never compared.
 */
export function PriceOrRequest({ productId, pricingMode, price }: PriceOrRequestProps) {
  const { t, format } = usePreferences();
  if (isPriced(pricingMode) && price !== undefined) {
    return <span data-testid="price">{format.money(price)}</span>;
  }
  return (
    <span data-testid="price-on-request">
      <span>{t("product.price.request")}</span>
      <Link
        className="md-price-request-link"
        to={{ pathname: "/inquiry", search: `?productId=${encodeURIComponent(productId)}` }}
      >
        {t("product.price.requestLink")}
      </Link>
    </span>
  );
}
