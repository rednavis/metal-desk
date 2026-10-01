/**
 * The shapes `apps/admin` sends, written once as zod schemas (see `apps/web`'s `types.ts` for why):
 * the schema is the runtime check the client applies, the TypeScript type is inferred from it, and
 * `contract.test.ts` reads the Java records in this repository and fails when one drifts from its
 * schema.
 *
 * What is mirrored: the error envelope, pages, the tier API, the quote queue, the order views and
 * the staff identity, each with its contract entry.
 */
import { z } from "zod";

export const errorEnvelopeSchema = z.object({
  code: z.string(),
  message: z.string(),
  correlationId: z.string(),
});
export type ErrorEnvelope = z.infer<typeof errorEnvelopeSchema>;

export const tierViewSchema = z.object({
  id: z.string(),
  region: z.string(),
  valueCeiling: z.string(),
  currency: z.string(),
  weightGrams: z.string(),
  deliveryPrice: z.string(),
  minDays: z.number().int(),
  maxDays: z.number().int(),
});
export type TierView = z.infer<typeof tierViewSchema>;

/** What a tier change means for checkout: `region.uncovered`, `region.narrowed`, `tier.shadowed`. */
export const tierWarningSchema = z.object({ code: z.string(), message: z.string() });
export type TierWarning = z.infer<typeof tierWarningSchema>;

export const tierResultSchema = z.object({
  tier: tierViewSchema,
  warnings: z.array(tierWarningSchema),
});
export type TierResult = z.infer<typeof tierResultSchema>;

export const tierRemovalSchema = z.object({
  removedId: z.string(),
  warnings: z.array(tierWarningSchema),
});
export type TierRemoval = z.infer<typeof tierRemovalSchema>;

/** A page of results; the item schema is the only thing that varies. */
export function pageViewSchema<T extends z.ZodType>(item: T) {
  return z.object({
    items: z.array(item),
    page: z.number().int(),
    size: z.number().int(),
    total: z.number().int(),
  });
}
export type PageView<T> = { items: T[]; page: number; size: number; total: number };

// ---- orders, quotes and the staff identity -------------------------------------------------

export const orderStatusSchema = z.enum([
  "CREATED",
  "AWAITING_PAYMENT",
  "AWAITING_MANAGER_QUOTE",
  "PAID",
  "FULFILLING",
  "SHIPPED",
  "DELIVERED",
  "CANCELLED",
]);
export type OrderStatus = z.infer<typeof orderStatusSchema>;

/** The state machine's triggers; `actions` of an order lists the ones it accepts now. */
export const transitionTriggerSchema = z.enum([
  "CHECKOUT_SUBMITTED",
  "PAYMENT_CAPTURED",
  "PAYMENT_FAILED",
  "PAYMENT_ABANDONED",
  "CUSTOMER_CANCELLED",
  "TIER_EXCEEDED",
  "QUOTE_SET",
  "QUOTE_DECLINED",
  "FULFILLMENT_STARTED",
  "SHIPPED",
  "DELIVERED",
]);
export type TransitionTrigger = z.infer<typeof transitionTriggerSchema>;

export const boundCeilingSchema = z.enum(["VALUE", "WEIGHT", "NO_TIER_FOR_REGION", "WITHIN_TIERS"]);
export type BoundCeiling = z.infer<typeof boundCeilingSchema>;

export const orderSummaryViewSchema = z.object({
  id: z.string(),
  number: z.string(),
  status: orderStatusSchema,
  customerId: z.string(),
  itemCount: z.number().int(),
  total: z.string(),
  currency: z.string(),
  createdAt: z.string(),
  updatedAt: z.string(),
});
export type OrderSummaryView = z.infer<typeof orderSummaryViewSchema>;

export const contactViewSchema = z.object({ email: z.string(), phone: z.string().optional() });
export type ContactView = z.infer<typeof contactViewSchema>;

export const lineViewSchema = z.object({
  productId: z.string(),
  name: z.string(),
  quantity: z.number().int(),
  unitPrice: z.string(),
  lineNet: z.string(),
  lineTax: z.string(),
});
export type LineView = z.infer<typeof lineViewSchema>;

export const quoteInfoSchema = z.object({
  tierId: z.string(),
  cost: z.string(),
  minDays: z.number().int(),
  maxDays: z.number().int(),
  quotedAt: z.string(),
});
export type QuoteInfo = z.infer<typeof quoteInfoSchema>;

export const shipmentViewSchema = z.object({ carrier: z.string(), trackingReference: z.string() });
export type ShipmentView = z.infer<typeof shipmentViewSchema>;

/** Absent weight and ceiling mean a product is gone from the catalog: they are not guessed. */
export const handoffContextSchema = z.object({
  region: z.string(),
  weightGrams: z.string().optional(),
  boundCeiling: boundCeilingSchema.optional(),
});
export type HandoffContext = z.infer<typeof handoffContextSchema>;

export const orderDetailViewSchema = z.object({
  summary: orderSummaryViewSchema,
  customerName: z.string().optional(),
  contact: contactViewSchema.optional(),
  destination: z.string(),
  lines: z.array(lineViewSchema),
  net: z.string(),
  tax: z.string(),
  delivery: z.string(),
  quote: quoteInfoSchema.optional(),
  shipment: shipmentViewSchema.optional(),
  /** Present only while the order awaits a manager quote. */
  handoff: handoffContextSchema.optional(),
  /** What the server's state machine accepts now; the screens offer exactly these. */
  actions: z.array(transitionTriggerSchema),
});
export type OrderDetailView = z.infer<typeof orderDetailViewSchema>;

export const quoteOutcomeSchema = z.object({
  orderId: z.string(),
  number: z.string(),
  status: orderStatusSchema,
  deliveryPrice: z.string().optional(),
  total: z.string().optional(),
  currency: z.string().optional(),
});
export type QuoteOutcome = z.infer<typeof quoteOutcomeSchema>;

/** What a user may do: `UserRole` on the server. A new role is added there first. */
export const userRoleSchema = z.enum(["ADMIN", "MANAGER"]);
export type UserRole = z.infer<typeof userRoleSchema>;

export const staffViewSchema = z.object({
  login: z.string(),
  email: z.string(),
  role: userRoleSchema,
});
export type StaffView = z.infer<typeof staffViewSchema>;

export const signInResponseSchema = z.object({
  accessToken: z.string(),
  tokenType: z.string(),
  expiresInSeconds: z.number().int(),
  login: z.string(),
  role: userRoleSchema,
});
export type SignInResponse = z.infer<typeof signInResponseSchema>;

/** A tier as sent (`TierRequest`). Amounts are decimal text, never numbers. */
export interface TierRequest {
  region: string;
  valueCeiling: string;
  weightCeiling: string;
  weightUnit: "GRAM" | "KILOGRAM" | "TROY_OUNCE";
  currency: string;
  deliveryPrice: string;
  minDays: number;
  maxDays: number;
}

/** The terms of a manager quote as sent (`ManagerQuoteRequest`). */
export interface ManagerQuoteRequest {
  deliveryPrice: string;
  terms: string;
  transitMinDays: number;
  transitMaxDays: number;
  validUntil: string;
}

const SHARE = "libs/share/src/main/java/com/rednavis/metaldesk/share/domain/";
const ADMIN = "apps/admin/src/main/java/com/rednavis/metaldesk/admin/";

/** A Java record mirrored by an object schema. */
export interface RecordContract {
  kind: "record";
  java: string;
  record: string;
  schema: z.ZodObject;
}

/** A Java enum mirrored by an enum schema. */
export interface EnumContract {
  kind: "enum";
  java: string;
  enumName: string;
  schema: z.ZodEnum;
}

/** The registry `contract.test.ts` walks: one entry per mirrored Java type. */
export const contract: (RecordContract | EnumContract)[] = [
  {
    kind: "record",
    java: ADMIN + "web/ErrorEnvelope.java",
    record: "ErrorEnvelope",
    schema: errorEnvelopeSchema,
  },
  {
    kind: "record",
    java: ADMIN + "tier/dto/TierView.java",
    record: "TierView",
    schema: tierViewSchema,
  },
  {
    kind: "record",
    java: ADMIN + "tier/dto/TierWarning.java",
    record: "TierWarning",
    schema: tierWarningSchema,
  },
  {
    kind: "record",
    java: ADMIN + "tier/dto/TierResult.java",
    record: "TierResult",
    schema: tierResultSchema,
  },
  {
    kind: "record",
    java: ADMIN + "tier/dto/TierRemoval.java",
    record: "TierRemoval",
    schema: tierRemovalSchema,
  },
  {
    kind: "record",
    java: ADMIN + "order/dto/PageView.java",
    record: "PageView",
    schema: pageViewSchema(z.unknown()),
  },
  {
    kind: "enum",
    java: SHARE + "order/OrderStatus.java",
    enumName: "OrderStatus",
    schema: orderStatusSchema,
  },
  {
    kind: "enum",
    java: SHARE + "order/TransitionTrigger.java",
    enumName: "TransitionTrigger",
    schema: transitionTriggerSchema,
  },
  {
    kind: "enum",
    java: ADMIN + "order/dto/BoundCeiling.java",
    enumName: "BoundCeiling",
    schema: boundCeilingSchema,
  },
  {
    kind: "record",
    java: ADMIN + "order/dto/OrderSummaryView.java",
    record: "OrderSummaryView",
    schema: orderSummaryViewSchema,
  },
  {
    kind: "record",
    java: ADMIN + "order/dto/OrderDetailView.java",
    record: "ContactView",
    schema: contactViewSchema,
  },
  {
    kind: "record",
    java: ADMIN + "order/dto/OrderDetailView.java",
    record: "LineView",
    schema: lineViewSchema,
  },
  {
    kind: "record",
    java: ADMIN + "order/dto/OrderDetailView.java",
    record: "QuoteInfo",
    schema: quoteInfoSchema,
  },
  {
    kind: "record",
    java: ADMIN + "order/dto/OrderDetailView.java",
    record: "ShipmentView",
    schema: shipmentViewSchema,
  },
  {
    kind: "record",
    java: ADMIN + "order/dto/OrderDetailView.java",
    record: "HandoffContext",
    schema: handoffContextSchema,
  },
  {
    kind: "record",
    java: ADMIN + "order/dto/OrderDetailView.java",
    record: "OrderDetailView",
    schema: orderDetailViewSchema,
  },
  {
    kind: "record",
    java: ADMIN + "quote/dto/QuoteOutcome.java",
    record: "QuoteOutcome",
    schema: quoteOutcomeSchema,
  },
  {
    kind: "record",
    java: ADMIN + "security/StaffView.java",
    record: "StaffView",
    schema: staffViewSchema,
  },
  {
    kind: "record",
    java: ADMIN + "security/SignInResponse.java",
    record: "SignInResponse",
    schema: signInResponseSchema,
  },
  {
    kind: "enum",
    java: SHARE + "user/UserRole.java",
    enumName: "UserRole",
    schema: userRoleSchema,
  },
];
