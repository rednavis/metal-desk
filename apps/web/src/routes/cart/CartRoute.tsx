import { useState } from "react";
import { Link } from "react-router";
import { isApiError } from "../../api/errors";
import type { CartLineView } from "../../api/types";
import { PriceOrRequest } from "../../features/catalog/PriceOrRequest";
import { QuantityField } from "../../features/cart/QuantityField";
import { RemoveLineDialog } from "../../features/cart/RemoveLineDialog";
import { useCart } from "../../features/cart/useCart";
import { usePreferences } from "../../preferences/usePreferences";
import { Button, EmptyState, ErrorState, Spinner, TotalsSummary, TrashIcon } from "../../ui";
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
      <div className="md-split">
        <ul className="md-cart-lines" aria-label={t("cart.caption")}>
          {cart.lines.map((line) => {
            const refused = failure?.productId === line.productId;
            return (
              <li key={line.productId} className="md-cart-line" data-testid="cart-line">
                <div className="md-cart-line__head">
                  <h2 className="md-cart-line__name">
                    <Link to={`/catalog/products/${encodeURIComponent(line.productId)}`}>
                      {line.name}
                    </Link>
                  </h2>
                  <Button
                    variant="secondary"
                    className="md-button--icon"
                    aria-label={t("cart.remove", { name: line.name })}
                    title={t("cart.remove", { name: line.name })}
                    disabled={busy}
                    onClick={() => {
                      setRemoving(line);
                    }}
                  >
                    <TrashIcon />
                  </Button>
                </div>
                <dl className="md-cart-line__facts">
                  <div className="md-cart-line__quantity">
                    <dt>{t("cart.col.quantity")}</dt>
                    <dd>
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
                    </dd>
                  </div>
                  <div>
                    <dt>{t("cart.col.unitPrice")}</dt>
                    <dd>
                      <PriceOrRequest
                        productId={line.productId}
                        pricingMode={line.pricingMode}
                        price={line.unitPrice}
                      />
                    </dd>
                  </div>
                  <div>
                    <dt>{t("cart.col.lineNet")}</dt>
                    <dd>{line.lineNet ? format.money(line.lineNet) : null}</dd>
                  </div>
                  <div>
                    <dt>{t("cart.col.lineTax")}</dt>
                    <dd>
                      {line.lineTax && line.taxRatePercent
                        ? `${format.money(line.lineTax)} (${format.percent(line.taxRatePercent)})`
                        : null}
                    </dd>
                  </div>
                </dl>
              </li>
            );
          })}
        </ul>
        <aside className="md-summary" aria-labelledby="cart-totals">
          <h2 id="cart-totals">{t("cart.totals")}</h2>
          {cart.totals ? <TotalsSummary totals={cart.totals} /> : null}
          <div className="md-summary__block">
            {cart.complete ? (
              <Link className="md-button md-button--lg md-button--block" to="/checkout">
                {t("cart.checkout")}
              </Link>
            ) : (
              <p role="status">{t("cart.incomplete")}</p>
            )}
          </div>
        </aside>
      </div>
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
