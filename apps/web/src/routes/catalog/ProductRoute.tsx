import { Link, useParams } from "react-router";
import { PriceOrRequest } from "../../features/catalog/PriceOrRequest";
import { PurchaseActions } from "../../features/catalog/PurchaseActions";
import { RelatedProducts } from "../../features/catalog/RelatedProducts";
import { StockLabel } from "../../features/catalog/StockLabel";
import { useProduct } from "../../features/catalog/useCatalog";
import { usePreferences } from "../../preferences/usePreferences";
import { ErrorState, Spinner } from "../../ui";
import "../../features/catalog/catalog.css";

/** A product's page (BRD FR-1.3): the full specification, the price or the request affordance, and related products. */
export function ProductRoute() {
  const { t, format } = usePreferences();
  const { productId = "" } = useParams();
  const product = useProduct(productId);

  if (product.isPending) return <Spinner label={t("product.loading")} />;
  if (product.isError) return <ErrorState error={product.error} />;

  const detail = product.data;
  return (
    <>
      <p>
        <Link to={`/catalog/categories/${encodeURIComponent(detail.categoryId)}`}>
          {detail.categoryName}
        </Link>
      </p>
      <h1>{detail.name}</h1>
      <section aria-labelledby="spec-heading">
        <h2 id="spec-heading">{t("product.spec.title")}</h2>
        <dl className="md-spec">
          <dt>{t("product.spec.metal")}</dt>
          <dd>{t(`metal.${detail.metal}`)}</dd>
          <dt>{t("product.spec.purity")}</dt>
          <dd>{detail.purity}</dd>
          <dt>{t("product.spec.weight")}</dt>
          <dd>
            {t(`product.weight.${detail.weight.unit}`, {
              amount: format.number(Number(detail.weight.amount)),
            })}
          </dd>
          {detail.dimensions ? (
            <>
              <dt>{t("product.spec.dimensions")}</dt>
              <dd>{detail.dimensions}</dd>
            </>
          ) : null}
          <dt>{t("product.spec.stock")}</dt>
          <dd>
            <StockLabel stock={detail.stock} />
          </dd>
          <dt>{t("product.spec.price")}</dt>
          <dd>
            <PriceOrRequest
              productId={detail.id}
              pricingMode={detail.pricingMode}
              price={detail.price}
            />
          </dd>
          <dt>{t("product.spec.tax")}</dt>
          <dd>
            {t(`product.tax.${detail.tax.category}`, {
              rate: format.percent(detail.tax.ratePercent),
            })}
          </dd>
        </dl>
      </section>
      <PurchaseActions
        productId={detail.id}
        name={detail.name}
        pricingMode={detail.pricingMode}
        stock={detail.stock}
      />
      <p>
        <Link to={{ pathname: "/inquiry", search: `?productId=${encodeURIComponent(detail.id)}` }}>
          {t("product.ask")}
        </Link>
      </p>
      <RelatedProducts productId={detail.id} />
    </>
  );
}
