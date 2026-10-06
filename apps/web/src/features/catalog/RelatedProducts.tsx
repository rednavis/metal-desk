import { usePreferences } from "../../preferences/usePreferences";
import { ErrorState, Spinner } from "../../ui";
import { RELATED_CAP } from "./limits";
import { ProductGrid } from "./ProductCard";
import { useRelatedProducts } from "./useCatalog";

/**
 * Related products from the same category (BRD FR-1.4, BR-1): at most {@link RELATED_CAP}, however
 * many arrive, and exactly what arrives if fewer. With none, the section is absent rather than a
 * heading over an empty grid.
 */
export function RelatedProducts({ productId }: { productId: string }) {
  const { t } = usePreferences();
  const related = useRelatedProducts(productId);

  if (related.isPending) return <Spinner label={t("catalog.loading")} />;
  if (related.isError) return <ErrorState error={related.error} />;
  const shown = related.data.slice(0, RELATED_CAP);
  if (shown.length === 0) return null;
  return (
    <section aria-labelledby="related-heading">
      <h2 id="related-heading">{t("product.related.title")}</h2>
      <ProductGrid products={shown} />
    </section>
  );
}
