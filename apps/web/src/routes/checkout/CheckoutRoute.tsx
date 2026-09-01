import { Link, useNavigate, useParams, useSearchParams } from "react-router";
import { usePreferences } from "../../preferences/usePreferences";
import { ErrorState, Spinner } from "../../ui";
import { Step1CustomerDetails } from "./steps/Step1CustomerDetails";
import { Step2Delivery } from "./steps/Step2Delivery";
import { Step3PaymentMethod } from "./steps/Step3PaymentMethod";
import { Step4Overview } from "./steps/Step4Overview";
import { Step5Confirmation } from "./steps/Step5Confirmation";
import type { Step } from "./steps";
import { useCheckoutSession } from "./useCheckoutSession";
import "./checkout.css";

/**
 * The checkout wizard (BRD sections 7.4 to 7.8) at `/checkout/:checkoutId`.
 *
 * Which step is shown is the step in the URL, limited to the furthest one the server's session
 * allows (see `reachableStep`): a reload, a pasted link or a hand-edited `?step=` can never show a
 * step the server has not unlocked, and a session with no `?step=` opens at its furthest one. Going
 * back to any earlier step is always possible (FR-7.1).
 */
export function CheckoutRoute() {
  const { t } = usePreferences();
  const { checkoutId = "" } = useParams();
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const checkout = useCheckoutSession(checkoutId);

  if (checkout.session.isError) {
    return (
      <>
        <h1>{t("page.checkout.title")}</h1>
        <ErrorState
          error={checkout.session.error}
          action={<Link to="/cart">{t("checkout.backToCart")}</Link>}
        />
      </>
    );
  }
  if (!checkout.settled || checkout.session.data === undefined) {
    return (
      <>
        <h1>{t("page.checkout.title")}</h1>
        <Spinner label={t("checkout.loading")} />
      </>
    );
  }

  const requested = Number(params.get("step"));
  const step: Step =
    Number.isInteger(requested) && requested >= 1
      ? (Math.min(requested, checkout.reachable) as Step)
      : checkout.reachable;
  const goTo = (target: Step) => {
    void navigate({ search: `?step=${String(target)}` });
  };
  const handedOff = checkout.session.data.delivery?.stage === "HANDOFF_REQUIRED";

  let body;
  switch (step) {
    case 1:
      body = (
        <Step1CustomerDetails
          checkout={checkout}
          onDone={() => {
            goTo(2);
          }}
        />
      );
      break;
    case 2:
      body = (
        <Step2Delivery
          checkout={checkout}
          onContinue={() => {
            goTo(3);
          }}
          onEditDetails={() => {
            goTo(1);
          }}
        />
      );
      break;
    case 3:
      body = (
        <Step3PaymentMethod
          checkout={checkout}
          onBack={() => {
            goTo(2);
          }}
          onChosen={() => {
            goTo(4);
          }}
        />
      );
      break;
    case 4:
      body = (
        <Step4Overview
          checkout={checkout}
          goTo={goTo}
          onPlaced={async () => {
            await checkout.confirmation.refetch();
            goTo(5);
          }}
        />
      );
      break;
    case 5:
      body = <Step5Confirmation checkout={checkout} />;
      break;
  }

  return (
    <>
      <h1>{t("page.checkout.title")}</h1>
      <Progress current={step} handoff={handedOff} />
      {body}
    </>
  );
}

/** The steps as an ordered list; a handoff has only two, because it ends in a manager's quote, not a payment. */
function Progress({ current, handoff }: { current: Step; handoff: boolean }) {
  const { t } = usePreferences();
  const steps: { step: Step; label: string }[] = handoff
    ? [
        { step: 1, label: t("checkout.step.1") },
        { step: 2, label: t("checkout.step.handoff") },
      ]
    : ([1, 2, 3, 4, 5] as const).map((step) => ({ step, label: t(`checkout.step.${step}`) }));
  return (
    <nav aria-label={t("checkout.steps")}>
      <ol className="md-progress">
        {steps.map(({ step, label }) => (
          <li key={step} aria-current={step === current ? "step" : undefined}>
            <span className="md-sr-only">
              {t("checkout.stepOf", { number: step, total: steps.length })}
            </span>
            <span>{label}</span>
          </li>
        ))}
      </ol>
    </nav>
  );
}
