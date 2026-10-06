import type { BoundCeiling, OrderStatus } from "../../api/types";

/** An order status as staff read it. Keyed by the enum, so a status added on the server is a compile error here. */
export const STATUS_LABEL: Record<OrderStatus, string> = {
  CREATED: "Created",
  AWAITING_PAYMENT: "Awaiting payment",
  AWAITING_MANAGER_QUOTE: "Awaiting manager quote",
  PAID: "Paid",
  FULFILLING: "Being fulfilled",
  SHIPPED: "Shipped",
  DELIVERED: "Delivered",
  CANCELLED: "Cancelled",
};

/** Why an order was handed to a manager, in words, from which ceiling the region's tiers say it exceeds. */
export const CEILING_EXPLANATION: Record<BoundCeiling, string> = {
  VALUE: "The order's value is above the highest value ceiling of any tier for the region.",
  WEIGHT: "The order's weight is above the highest weight ceiling of any tier for the region.",
  NO_TIER_FOR_REGION: "The region has no tier, so every order to it needs a manager.",
  WITHIN_TIERS:
    "A tier as configured now accepts this order: tiers were widened after the handoff. It can still be priced by hand.",
};

/** The money as the server wrote it, with its currency. */
export const amount = (value: string, currency: string) => `${value} ${currency}`;
