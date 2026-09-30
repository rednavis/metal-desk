import type { TransitionTrigger } from "../../api/types";

/**
 * The state-machine triggers a staff member can fire from the back office, and how. This is a
 * catalogue of *operations*, not of statuses or their order: whether one is available for an order is
 * decided by the server, which lists the triggers it accepts in `actions`, and the screens offer
 * exactly those. Nothing here says what follows what, so a status added to the state machine needs no
 * change here.
 */
export type StaffOperation =
  | { kind: "advance"; trigger: "FULFILLMENT_STARTED" | "DELIVERED"; label: string }
  | { kind: "shipment"; trigger: "SHIPPED"; label: string }
  | { kind: "quote"; trigger: "QUOTE_SET" | "QUOTE_DECLINED"; label: string };

export const STAFF_OPERATIONS: readonly StaffOperation[] = [
  { kind: "advance", trigger: "FULFILLMENT_STARTED", label: "Start fulfilment" },
  { kind: "shipment", trigger: "SHIPPED", label: "Enter shipment" },
  { kind: "advance", trigger: "DELIVERED", label: "Mark as delivered" },
  { kind: "quote", trigger: "QUOTE_SET", label: "Set terms" },
  { kind: "quote", trigger: "QUOTE_DECLINED", label: "Decline quote" },
];

/** Whether the server accepts `trigger` for an order whose `actions` are given. */
export function isAvailable(actions: readonly TransitionTrigger[], trigger: TransitionTrigger) {
  return actions.includes(trigger);
}
