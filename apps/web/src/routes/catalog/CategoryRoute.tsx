import { Link, useParams, useSearchParams } from "react-router";
import { CATEGORY_PAGE_SIZE } from "../../features/catalog/limits";
import { Pagination } from "../../features/catalog/Pagination";
import { ProductGrid } from "../../features/catalog/ProductCard";
import { useCategories, useCategoryProducts } from "../../features/catalog/useCatalog";
import { usePreferences } from "../../preferences/usePreferences";
import { EmptyState, ErrorState, Spinner } from "../../ui";

/** One category's products, a page at a time (the API paginates, so a whole category is never requested at once). */
export function CategoryRoute() {
  const { t } = usePreferences();
  const { categoryId = "" } = useParams();
  const [params] = useSearchParams();
  const requested = Number(params.get("page"));
  const page = Number.isInteger(requested) && requested >= 1 ? requested : 1;

  const products = useCategoryProducts(categoryId, page - 1, CATEGORY_PAGE_SIZE);
  const categories = useCategories();
  const name = categories.data?.find((category) => category.id === categoryId)?.name;

  if (products.isPending) return <Spinner label={t("catalog.loading")} />;
  if (products.isError) return <ErrorState error={products.error} />;

  const { items, total, size } = products.data;
  return (
    <>
      <p>
        <Link to="/catalog">{t("catalog.allCategories")}</Link>
      </p>
      <h1>{name ?? t("page.catalog.title")}</h1>
      {items.length === 0 ? (
        <EmptyState title={t("catalog.category.empty")} />
      ) : (
        <ProductGrid products={items} />
      )}
      <Pagination
        page={page}
        pages={Math.max(1, Math.ceil(total / size))}
        hrefFor={(target) => `?page=${target}`}
      />
    </>
  );
}
