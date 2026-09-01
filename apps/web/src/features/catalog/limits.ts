/**
 * The most related products a detail page shows (BRD BR-1, illustrative: 20). The server already caps
 * the list at this figure (`CatalogService.RELATED_CAP`); the client holds the same bound so that it
 * can never show more than the rule allows, and it never pads a shorter list up to it.
 */
export const RELATED_CAP = 20;

/** Products per page on a category's own listing. The server accepts 1 to 100. */
export const CATEGORY_PAGE_SIZE = 12;

/** Products shown per category on the grouped catalog overview, before "see all". */
export const OVERVIEW_PAGE_SIZE = 4;

/** The shortest search the server accepts (`search.query-invalid` below this). */
export const SEARCH_MIN_LENGTH = 2;
