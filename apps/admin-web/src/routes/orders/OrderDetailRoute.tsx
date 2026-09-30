import { Link, useParams } from "react-router";
import { isAvailable, STAFF_OPERATIONS } from "../../features/orders/staffActions";
import { useOrder, useOrderActions } from "../../features/orders/useOrders";
import { Button, ErrorState, Spinner } from "../../ui";
import { OrderFacts } from "./OrderFacts";
import { ShipmentForm } from "./ShipmentForm";

/**
 * One order and what can be done to it. The operations offered are the triggers the server lists for
 * this order, and only those are enabled: an operation that is not legal now is shown disabled with
 * the reason, instead of being enabled and refused with a 409, and the shipment form exists only
 * while the shipped trigger is accepted. Nothing here knows which status follows which.
 */
export function OrderDetailRoute() {
  const { orderId = "" } = useParams();
  const order = useOrder(orderId);
  const actions = useOrderActions(orderId);

  if (order.isPending) return <Spinner />;
  if (order.isError) return <ErrorState error={order.error} />;
  const { data } = order;
  const failure = actions.startFulfillment.error ?? actions.markDelivered.error;

  return (
    <>
      <p>
        <Link to="/orders">Back to the orders</Link>
      </p>
      <h1>{`Order ${data.summary.number}`}</h1>
      <OrderFacts order={data} />

      <h2>Actions</h2>
      {failure ? <ErrorState error={failure} /> : null}
      <div className="md-actions">
        {STAFF_OPERATIONS.map((operation) => {
          const available = isAvailable(data.actions, operation.trigger);
          if (operation.kind === "shipment") return null;
          if (operation.kind === "quote") {
            return available ? (
              <Link key={operation.trigger} to={`/quotes/${encodeURIComponent(orderId)}`}>
                {operation.label}
              </Link>
            ) : null;
          }
          const run =
            operation.trigger === "FULFILLMENT_STARTED"
              ? actions.startFulfillment
              : actions.markDelivered;
          return (
            <Button
              key={operation.trigger}
              variant="secondary"
              disabled={!available || run.isPending}
              title={
                available ? undefined : `Not available while the order is ${data.summary.status}`
              }
              onClick={() => {
                run.mutate();
              }}
            >
              {operation.label}
            </Button>
          );
        })}
      </div>
      {isAvailable(data.actions, "SHIPPED") ? (
        <ShipmentForm
          busy={actions.enterShipment.isPending}
          error={actions.enterShipment.error}
          onSubmit={(entry) => {
            actions.enterShipment.mutate(entry);
          }}
        />
      ) : null}
    </>
  );
}
