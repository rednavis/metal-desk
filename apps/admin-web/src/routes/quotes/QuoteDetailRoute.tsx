import { Link, useParams } from "react-router";
import { CEILING_EXPLANATION } from "../../features/orders/labels";
import { isAvailable } from "../../features/orders/staffActions";
import { useOrderActions, useQuote } from "../../features/orders/useOrders";
import { ErrorState, Spinner } from "../../ui";
import { OrderFacts } from "../orders/OrderFacts";
import { DeclineForm } from "./DeclineForm";
import { TermsForm } from "./TermsForm";

/**
 * One handed-off order with everything staff need to price it (BRD FR-5.3): the lines, the
 * destination, the value before tax, the weight, which ceiling the order exceeds and how to reach the
 * customer, then the two decisions. Nothing has to be looked up elsewhere.
 */
export function QuoteDetailRoute() {
  const { orderId = "" } = useParams();
  const quote = useQuote(orderId);
  const actions = useOrderActions(orderId);

  if (quote.isPending) return <Spinner />;
  if (quote.isError) return <ErrorState error={quote.error} />;
  const { data: order } = quote;
  const { handoff } = order;
  const open = isAvailable(order.actions, "QUOTE_SET");
  const termsSet = actions.setTerms.data;
  const declined = actions.decline.data;

  // Once a decision is made the order leaves the queue, so the forms give way to what the server
  // said about it; the page must not vanish under the person who just acted.
  let decision;
  if (termsSet || declined) {
    decision = (
      <>
        {termsSet ? (
          <TermsForm
            order={order}
            busy={false}
            error={undefined}
            outcome={termsSet}
            onSubmit={() => undefined}
          />
        ) : null}
        {declined ? (
          <DeclineForm
            number={order.summary.number}
            busy={false}
            error={undefined}
            outcome={declined}
            onSubmit={() => undefined}
          />
        ) : null}
      </>
    );
  } else if (open) {
    decision = (
      <>
        <TermsForm
          order={order}
          busy={actions.setTerms.isPending}
          error={actions.setTerms.error}
          outcome={undefined}
          onSubmit={(request) => {
            actions.setTerms.mutate(request);
          }}
        />
        <DeclineForm
          number={order.summary.number}
          busy={actions.decline.isPending}
          error={actions.decline.error}
          outcome={undefined}
          onSubmit={(reason) => {
            actions.decline.mutate(reason);
          }}
        />
      </>
    );
  } else {
    decision = <p>No decision can be made on this order now.</p>;
  }

  return (
    <>
      <p>
        <Link to="/quotes">Back to the quotes</Link>
      </p>
      <h1>{`Quote for order ${order.summary.number}`}</h1>

      <h2>Why it is here</h2>
      {handoff ? (
        <dl>
          <dt>Destination region</dt>
          <dd data-testid="region">{handoff.region}</dd>
          <dt>Total weight</dt>
          <dd data-testid="weight">
            {handoff.weightGrams === undefined
              ? "Unknown: a product is no longer in the catalog"
              : `${handoff.weightGrams} g`}
          </dd>
          <dt>Ceiling exceeded</dt>
          <dd data-testid="bound-ceiling">
            {handoff.boundCeiling === undefined
              ? "Unknown: the weight could not be worked out"
              : CEILING_EXPLANATION[handoff.boundCeiling]}
          </dd>
        </dl>
      ) : (
        <p>This order is not waiting for a quote.</p>
      )}
      <OrderFacts order={order} />

      {decision}
    </>
  );
}
