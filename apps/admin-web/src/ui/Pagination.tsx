import { Button } from "./Button";

interface PaginationProps {
  /** The current page, from 0 as the API counts. */
  page: number;
  size: number;
  total: number;
  onPage: (page: number) => void;
}

/** Previous and next, with "page x of y"; absent when everything fits on one page. */
export function Pagination({ page, size, total, onPage }: PaginationProps) {
  const pages = Math.max(1, Math.ceil(total / size));
  if (pages <= 1) return null;
  return (
    <nav className="md-pagination" aria-label="Pages">
      <Button
        variant="secondary"
        disabled={page <= 0}
        onClick={() => {
          onPage(page - 1);
        }}
      >
        Previous page
      </Button>
      <span aria-current="page">{`Page ${String(page + 1)} of ${String(pages)}`}</span>
      <Button
        variant="secondary"
        disabled={page >= pages - 1}
        onClick={() => {
          onPage(page + 1);
        }}
      >
        Next page
      </Button>
    </nav>
  );
}
