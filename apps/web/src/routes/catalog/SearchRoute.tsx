import { useSearchParams } from "react-router";
import { SEARCH_MIN_LENGTH } from "../../features/catalog/limits";
import { ProductGrid } from "../../features/catalog/ProductCard";
import { useProductSearch } from "../../features/catalog/useCatalog";
import { usePreferences } from "../../preferences/usePreferences";
import { EmptyState, ErrorState, Spinner } from "../../ui";

/**
 * Search results (BRD FR-1.5). The page says what the search does, which is to match part of a
 * product's name and list the matches alphabetically, rather than implying relevance ranking. No
 * match is an empty state, not an error: the server answers 200 with an empty list.
 */
export function SearchRoute() {
  const { t } = usePreferences();
  const [params] = useSearchParams();
  const query = (params.get("q") ?? "").trim();
  const searchable = query.length >= SEARCH_MIN_LENGTH;
  const results = useProductSearch(query, searchable);

  let body;
  if (!searchable) {
    body = <p>{t("search.prompt", { min: SEARCH_MIN_LENGTH })}</p>;
  } else if (results.isPending) {
    body = <Spinner label={t("catalog.loading")} />;
  } else if (results.isError) {
    body = <ErrorState error={results.error} />;
  } else if (results.data.length === 0) {
    body = (
      <EmptyState
        title={t("search.empty.title")}
        description={t("search.empty.description", { query })}
      />
    );
  } else {
    body = (
      <>
        <p role="status">{t("search.count", { count: results.data.length, query })}</p>
        <ProductGrid products={results.data} />
      </>
    );
  }
  return (
    <>
      <h1>{t("search.title")}</h1>
      <p>{t("search.semantics")}</p>
      {body}
    </>
  );
}
