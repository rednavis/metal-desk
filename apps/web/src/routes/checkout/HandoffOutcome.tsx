import { usePreferences } from "../../preferences/usePreferences";

/**
 * The receipt of a manager handoff (BRD FR-5.3): the reference number to quote, and what happens
 * next. No payment was taken and none is offered; it is a success, not a waiting room.
 */
export function HandoffOutcome({ reference }: { reference: string }) {
  const { t } = usePreferences();
  return (
    <section aria-labelledby="handoff-done-title" data-testid="handoff-receipt">
      <h2 id="handoff-done-title">{t("checkout.handoff.done.title")}</h2>
      <p data-testid="handoff-reference">{t("checkout.handoff.done.reference", { reference })}</p>
      <p>{t("checkout.handoff.done.next")}</p>
    </section>
  );
}
