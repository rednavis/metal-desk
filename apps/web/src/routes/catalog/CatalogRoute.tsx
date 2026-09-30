import { Link } from "react-router";
import type { CategoryView } from "../../api/types";
import { OVERVIEW_PAGE_SIZE } from "../../features/catalog/limits";
import { ProductGrid } from "../../features/catalog/ProductCard";
import { useCategories, useCategoryProducts } from "../../features/catalog/useCatalog";
import { usePreferences } from "../../preferences/usePreferences";
import { EmptyState, ErrorState, Spinner } from "../../ui";

/** The catalog grouped by category (BRD FR-1.2): each category's first products, and a link to all of them. */
export function CatalogRoute() {
  const { t } = usePreferences();
  const categories = useCategories();

  let body;
  if (categories.isPending) {
    body = <Spinner label={t("catalog.loading")} />;
  } else if (categories.isError) {
    body = <ErrorState error={categories.error} />;
  } else if (categories.data.length === 0) {
    body = (
      <EmptyState title={t("catalog.empty.title")} description={t("catalog.empty.description")} />
    );
  } else {
    body = categories.data.map((category) => (
      <CategorySection key={category.id} category={category} />
    ));
  }
  return (
    <>
      <h1>{t("page.catalog.title")}</h1>
      {body}
    </>
  );
}

function CategorySection({ category }: { category: CategoryView }) {
  const { t } = usePreferences();
  const products = useCategoryProducts(category.id, 0, OVERVIEW_PAGE_SIZE);
  const heading = `category-${category.id}`;
  return (
    <section aria-labelledby={heading}>
      <h2 id={heading}>{category.name}</h2>
      {products.isPending ? <Spinner label={t("catalog.loading")} /> : null}
      {products.isError ? <ErrorState error={products.error} /> : null}
      {products.data?.items.length === 0 ? <p>{t("catalog.category.empty")}</p> : null}
      {products.data ? <ProductGrid products={products.data.items} /> : null}
      {products.data && products.data.total > products.data.items.length ? (
        <Link to={`/catalog/categories/${encodeURIComponent(category.id)}`}>
          {t("catalog.seeAll", { category: category.name })}
        </Link>
      ) : null}
    </section>
  );
}
