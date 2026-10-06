import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { z } from "zod";
import { useApi } from "../../api/useApi";
import {
  categoryViewSchema,
  pageViewSchema,
  productDetailViewSchema,
  productSummaryViewSchema,
} from "../../api/types";
import { usePreferences } from "../../preferences/usePreferences";

const categoriesSchema = z.array(categoryViewSchema);
const productsSchema = z.array(productSummaryViewSchema);
const productPageSchema = pageViewSchema(productSummaryViewSchema);

/*
 * Every query key carries the display currency: the server converts prices (BRD FR-1.8), so a
 * switch is a different query, not a recalculation here.
 */

/** The categories, for the grouped overview. */
export function useCategories() {
  const client = useApi();
  const { currency } = usePreferences();
  return useQuery({
    queryKey: ["catalog", currency, "categories"],
    queryFn: () => client.get("/catalog/categories", { schema: categoriesSchema }),
  });
}

/** One page (zero-based, as the API counts) of a category's products. */
export function useCategoryProducts(categoryId: string, page: number, size: number) {
  const client = useApi();
  const { currency } = usePreferences();
  return useQuery({
    queryKey: ["catalog", currency, "category", categoryId, page, size],
    queryFn: () =>
      client.get(`/catalog/categories/${encodeURIComponent(categoryId)}/products`, {
        query: { page, size },
        schema: productPageSchema,
      }),
    placeholderData: keepPreviousData,
  });
}

/** A product's detail. */
export function useProduct(productId: string) {
  const client = useApi();
  const { currency } = usePreferences();
  return useQuery({
    queryKey: ["catalog", currency, "product", productId],
    queryFn: () =>
      client.get(`/catalog/products/${encodeURIComponent(productId)}`, {
        schema: productDetailViewSchema,
      }),
  });
}

/** The products related to one. */
export function useRelatedProducts(productId: string) {
  const client = useApi();
  const { currency } = usePreferences();
  return useQuery({
    queryKey: ["catalog", currency, "related", productId],
    queryFn: () =>
      client.get(`/catalog/products/${encodeURIComponent(productId)}/related`, {
        schema: productsSchema,
      }),
  });
}

/** Products whose name contains `query`; only asked once the query is long enough. */
export function useProductSearch(query: string, enabled: boolean) {
  const client = useApi();
  const { currency } = usePreferences();
  return useQuery({
    queryKey: ["catalog", currency, "search", query],
    queryFn: () => client.get("/catalog/search", { query: { q: query }, schema: productsSchema }),
    enabled,
  });
}
