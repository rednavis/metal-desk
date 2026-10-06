import { marketDataConfig, type MarketDataConfig } from "../../config";
import { usePreferences } from "../../preferences/usePreferences";
import { Button, EmptyState, ErrorState, Spinner } from "../../ui";
import "./marketdata.css";
import { PriceTicker } from "./PriceTicker";
import { useReferencePrices } from "./useReferencePrices";

/**
 * The live reference prices of BRD FR-1.1: one row per metal, refreshed on a short interval, with
 * the latest change of each. Public: it needs no sign-in.
 *
 * It never blanks on a failed refresh (the last values stay, under an error notice with a retry),
 * and it never presents frozen prices as live (past the staleness threshold it says so, and mutes
 * the numbers).
 */
export function MarketDataPanel({ config = marketDataConfig }: { config?: MarketDataConfig }) {
  const { t, format } = usePreferences();
  const prices = useReferencePrices(config);
  const time = (epoch: number) => format.dateTime(new Date(epoch).toISOString());

  let body;
  if (prices.prices === undefined) {
    body = prices.isError ? (
      <ErrorState
        error={prices.error}
        action={<Button onClick={prices.retryNow}>{t("marketdata.retry")}</Button>}
      />
    ) : (
      <Spinner label={t("marketdata.loading")} />
    );
  } else if (prices.prices.length === 0) {
    body = (
      <EmptyState
        title={t("marketdata.empty.title")}
        description={t("marketdata.empty.description")}
      />
    );
  } else {
    body = (
      <>
        {prices.isError ? (
          <p role="alert" className="md-banner md-banner--warning">
            {t("marketdata.error")}{" "}
            <Button variant="secondary" onClick={prices.retryNow}>
              {t("marketdata.retry")}
            </Button>
          </p>
        ) : null}
        {prices.staleReason ? (
          <p role="status" className="md-banner md-banner--warning">
            {prices.staleReason === "fetch"
              ? t("marketdata.stale.fetch", { time: time(prices.fetchedAt) })
              : t("marketdata.stale.feed", { time: time(prices.newestObservedAt) })}
          </p>
        ) : null}
        <table className="md-prices" data-stale={prices.staleReason !== null}>
          <caption>{t("marketdata.caption")}</caption>
          <thead>
            <tr>
              <th scope="col">{t("marketdata.col.metal")}</th>
              <th scope="col">{t("marketdata.col.price")}</th>
              <th scope="col">{t("marketdata.col.change")}</th>
            </tr>
          </thead>
          <tbody>
            {prices.prices.map((price) => (
              <PriceTicker key={price.metal} price={price} />
            ))}
          </tbody>
        </table>
        <p className="md-prices__unit">
          {t("marketdata.updated", { time: time(prices.fetchedAt) })}
        </p>
      </>
    );
  }

  return (
    <section aria-labelledby="market-data-title">
      <h2 id="market-data-title">{t("marketdata.title")}</h2>
      {body}
    </section>
  );
}
