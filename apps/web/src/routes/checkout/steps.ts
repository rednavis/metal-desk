import type { PaymentMethod, SessionView } from "../../api/types";

/** The checkout's steps, numbered as the customer sees them. */
export type Step = 1 | 2 | 3 | 4 | 5;

/**
 * The furthest step the **server** says the customer may be on; the client never unlocks one on its
 * own (a reload mid-checkout resumes from here).
 *
 * - no details yet: step 1
 * - details but no delivery evaluation, or an order that needs a manager: step 2 (there is nothing
 *   further for a handoff: it ends at step 2's receipt)
 * - delivery allowed but no payment method chosen: step 3
 * - a method chosen: step 4, and once an order is placed and confirmable: step 5
 */
export function reachableStep(
  session: SessionView,
  selectedMethod: PaymentMethod | undefined,
  orderPlaced: boolean,
): Step {
  if (session.details === undefined) return 1;
  if (session.delivery?.stage !== "PAYMENT_ALLOWED") return 2;
  if (selectedMethod === undefined) return 3;
  return orderPlaced ? 5 : 4;
}
