import type { StockStatus } from "../../api/types";
import { usePreferences } from "../../preferences/usePreferences";

/** A product's stock status in words, on the card and on the detail page (BRD FR-1.3). */
export function StockLabel({ stock }: { stock: StockStatus }) {
  const { t } = usePreferences();
  return (
    <span className="md-stock" data-stock={stock}>
      {t(`product.stock.${stock}`)}
    </span>
  );
}
