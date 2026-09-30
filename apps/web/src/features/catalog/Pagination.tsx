import { Link } from "react-router";
import { usePreferences } from "../../preferences/usePreferences";

interface PaginationProps {
  /** The current page, counted from 1 as the customer sees it. */
  page: number;
  pages: number;
  /** The link to a page, counted from 1. */
  hrefFor: (page: number) => string;
}

/** Previous/next links with a "page x of y" label; absent when everything fits on one page. */
export function Pagination({ page, pages, hrefFor }: PaginationProps) {
  const { t } = usePreferences();
  if (pages <= 1) return null;
  return (
    <nav className="md-pagination" aria-label={t("catalog.pagination")}>
      {page > 1 ? <Link to={hrefFor(page - 1)}>{t("catalog.prev")}</Link> : null}
      <span aria-current="page">{t("catalog.pageOf", { page, pages })}</span>
      {page < pages ? <Link to={hrefFor(page + 1)}>{t("catalog.next")}</Link> : null}
    </nav>
  );
}
