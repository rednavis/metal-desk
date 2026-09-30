import { useState } from "react";
import { Link } from "react-router";
import { isApiError } from "../../api/errors";
import type { CartLineView } from "../../api/types";
import { PriceOrRequest } from "../../features/catalog/PriceOrRequest";
import { QuantityField } from "../../features/cart/QuantityField";
import { RemoveLineDialog } from "../../features/cart/RemoveLineDialog";
import { useCart } from "../../features/cart/useCart";
import { usePreferences } from "../../preferences/usePreferences";
import { Button, EmptyState, ErrorState, Spinner, TotalsSummary } from "../../ui";
import "../../features/cart/cart.css";

/**
 * The cart (BRD FR-3.3, FR-3.4): each line's identity, editable quantity, unit price and tax, the
 * running totals, and removal behind a confirmation. Everything shown is the server's latest answer;
 * an empty cart is a designed state with a way back to the catalog.
 */
export function CartRoute() {
  const { t, format } = usePreferences();
  const { cart, isLoading, loadError, reload, busy, failure, setQuantity, remove } = useCart();
  const [removing, setRemoving] = useState<CartLineView | undefined>();

  let body;
  if (cart === undefined) {
    body = isLoading ? (
      <Spinner label={t("cart.loading")} />
    ) : (
      <ErrorState error={loadError} action={<Button onClick={reload}>{t("cart.retry")}</Button>} />
    );
  } else if (cart.empty) {
    body = (
      <EmptyState
        title={t("page.cart.empty.title")}
        description={t("page.cart.empty.description")}
        action={<Link to="/catalog">{t("page.cart.empty.browse")}</Link>}
      />
    );
  } else {
    body = (
      <>
        <table className="md-cart-table">
          <caption className="md-sr-only">{t("cart.caption")}</caption>
          <thead>
            <tr>
              <th scope="col">{t("cart.col.product")}</th>
              <th scope="col">{t("cart.col.quantity")}</th>
              <th scope="col">{t("cart.col.unitPrice")}</th>
              <th scope="col">{t("cart.col.lineNet")}</th>
              <th scope="col">{t("cart.col.lineTax")}</th>
              <th scope="col">
                <span className="md-sr-only">{t("cart.col.actions")}</span>
              </th>
            </tr>
          </thead>
          <tbody>
            {cart.lines.map((line) => {
              const refused = failure?.productId === line.productId;
              return (
                <tr key={line.productId} data-testid="cart-line">
                  <th scope="row">
                    <Link to={`/catalog/products/${encodeURIComponent(line.productId)}`}>
                      {line.name}
                    </Link>
                  </th>
                  <td>
                    <QuantityField
                      name={line.name}
                      quantity={line.quantity}
                      max={line.maxQuantity}
                      disabled={busy}
                      serverError={
                        refused && isApiError(failure.error) ? failure.error.message : undefined
                      }
                      onChange={(quantity) => {
                        void setQuantity(line.productId, quantity);
                      }}
                    />
                    {refused && !isApiError(failure.error) ? (
                      <ErrorState error={failure.error} />
                    ) : null}
                  </td>
                  <td>
                    <PriceOrRequest
                      productId={line.productId}
                      pricingMode={line.pricingMode}
                      price={line.unitPrice}
                    />
                  </td>
                  <td>{line.lineNet ? format.money(line.lineNet) : null}</td>
                  <td>
                    {line.lineTax && line.taxRatePercent
                      ? `${format.money(line.lineTax)} (${format.percent(line.taxRatePercent)})`
                      : null}
                  </td>
                  <td>
                    <Button
                      variant="secondary"
                      aria-label={t("cart.remove", { name: line.name })}
                      disabled={busy}
                      onClick={() => {
                        setRemoving(line);
                      }}
                    >
                      {t("cart.remove.confirm")}
                    </Button>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
        {cart.totals ? (
          <section aria-labelledby="cart-totals">
            <h2 id="cart-totals">{t("cart.totals")}</h2>
            <TotalsSummary totals={cart.totals} />
          </section>
        ) : null}
        {cart.complete ? (
          <Link to="/checkout">{t("cart.checkout")}</Link>
        ) : (
          <p role="status">{t("cart.incomplete")}</p>
        )}
      </>
    );
  }

  return (
    <>
      <h1>{t("page.cart.title")}</h1>
      {body}
      {removing ? (
        <RemoveLineDialog
          name={removing.name}
          onCancel={() => {
            setRemoving(undefined);
          }}
          onConfirm={() => {
            void remove(removing.productId);
            setRemoving(undefined);
          }}
        />
      ) : null}
    </>
  );
}
