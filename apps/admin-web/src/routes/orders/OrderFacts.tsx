import type { OrderDetailView } from "../../api/types";
import { amount, STATUS_LABEL } from "../../features/orders/labels";

/** The order's identity, customer, destination, lines and totals: what staff see on every order screen. */
export function OrderFacts({ order }: { order: OrderDetailView }) {
  const { summary } = order;
  const currency = summary.currency;
  return (
    <>
      <p>
        <span className="md-status" data-testid="order-status" data-status={summary.status}>
          {STATUS_LABEL[summary.status]}
        </span>
      </p>
      <dl>
        <dt>Customer</dt>
        <dd data-testid="customer-name">{order.customerName ?? "Customer record not found"}</dd>
        {order.contact ? (
          <>
            <dt>Email</dt>
            <dd data-testid="customer-email">{order.contact.email}</dd>
            {order.contact.phone ? (
              <>
                <dt>Phone</dt>
                <dd data-testid="customer-phone">{order.contact.phone}</dd>
              </>
            ) : null}
          </>
        ) : null}
        <dt>Delivery address</dt>
        <dd data-testid="destination">{order.destination}</dd>
      </dl>

      <h2>Lines</h2>
      <table className="md-table">
        <thead>
          <tr>
            <th scope="col">Product</th>
            <th scope="col">Quantity</th>
            <th scope="col">Unit price</th>
            <th scope="col">Net</th>
            <th scope="col">Tax</th>
          </tr>
        </thead>
        <tbody>
          {order.lines.map((line) => (
            <tr key={line.productId} data-testid="order-line">
              <th scope="row">{line.name}</th>
              <td>{line.quantity}</td>
              <td>{amount(line.unitPrice, currency)}</td>
              <td>{amount(line.lineNet, currency)}</td>
              <td>{amount(line.lineTax, currency)}</td>
            </tr>
          ))}
        </tbody>
      </table>

      <dl>
        <dt>Net (ex tax)</dt>
        <dd data-testid="ex-tax-value">{amount(order.net, currency)}</dd>
        <dt>Tax</dt>
        <dd>{amount(order.tax, currency)}</dd>
        <dt>Delivery</dt>
        <dd data-testid="delivery">{amount(order.delivery, currency)}</dd>
        <dt>Order total</dt>
        <dd data-testid="order-total">{amount(summary.total, currency)}</dd>
      </dl>
      {order.quote ? (
        <p>{`Delivery quote ${order.quote.tierId}: ${String(order.quote.minDays)} to ${String(order.quote.maxDays)} days.`}</p>
      ) : null}
      {order.shipment ? (
        <dl data-testid="shipment">
          <dt>Carrier</dt>
          <dd>{order.shipment.carrier}</dd>
          <dt>Tracking</dt>
          <dd>{order.shipment.trackingReference}</dd>
        </dl>
      ) : null}
    </>
  );
}
