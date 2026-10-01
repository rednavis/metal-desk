import { Link } from "react-router";
import type { ProductSummaryView } from "../../api/types";
import { usePreferences } from "../../preferences/usePreferences";
import { PriceOrRequest } from "./PriceOrRequest";
import { PurchaseActions } from "./PurchaseActions";
import { StockLabel } from "./StockLabel";
import "./catalog.css";

/** One product in a listing: name (a link to its page), metal, stock, price or request, and the buy actions. */
export function ProductCard({ product }: { product: ProductSummaryView }) {
  const { t } = usePreferences();
  return (
    <article className="md-product-card" data-testid="product-card" data-metal={product.metal}>
      <h3>
        <Link to={`/catalog/products/${encodeURIComponent(product.id)}`}>{product.name}</Link>
      </h3>
      <p className="md-product-card__metal">{t(`metal.${product.metal}`)}</p>
      <StockLabel stock={product.stock} />
      <PriceOrRequest
        productId={product.id}
        pricingMode={product.pricingMode}
        price={product.price}
      />
      <PurchaseActions
        productId={product.id}
        name={product.name}
        pricingMode={product.pricingMode}
        stock={product.stock}
      />
    </article>
  );
}

/** A grid of {@link ProductCard}s. */
export function ProductGrid({ products }: { products: readonly ProductSummaryView[] }) {
  return (
    <ul className="md-product-grid">
      {products.map((product) => (
        <li key={product.id}>
          <ProductCard product={product} />
        </li>
      ))}
    </ul>
  );
}
