/**
 * The shapes `services/api` sends, written once as zod schemas: the schema is the runtime check the
 * client applies to a response, and the TypeScript type is inferred from it, so a type cannot drift
 * from the check.
 *
 * These mirror Java records by hand, so they are kept honest by `contract.test.ts`, which reads the
 * Java sources in this repository and fails when a record's components or an enum's constants no
 * longer match. A field added on the server turns that test red before it turns a screen blank.
 *
 * `contract` at the bottom is the registry that test walks: one entry per mirrored Java type.
 */
import { z } from "zod";

// ---- enums (libs/share) --------------------------------------------------------------------

export const metalSchema = z.enum([
  "GOLD",
  "SILVER",
  "PLATINUM",
  "PALLADIUM",
  "RHODIUM",
  "RUTHENIUM",
]);
export type Metal = z.infer<typeof metalSchema>;

export const taxCategorySchema = z.enum(["INVESTMENT_GRADE", "STANDARD"]);
export type TaxCategory = z.infer<typeof taxCategorySchema>;

export const weightUnitSchema = z.enum(["GRAM", "KILOGRAM", "TROY_OUNCE"]);
export type WeightUnit = z.infer<typeof weightUnitSchema>;

export const stockStatusSchema = z.enum(["IN_STOCK", "ON_REQUEST", "OUT_OF_STOCK"]);
export type StockStatus = z.infer<typeof stockStatusSchema>;

export const pricingModeSchema = z.enum(["FIXED", "ON_REQUEST"]);
export type PricingMode = z.infer<typeof pricingModeSchema>;

export const directionSchema = z.enum(["UP", "DOWN", "UNCHANGED"]);
export type Direction = z.infer<typeof directionSchema>;

// ---- the error envelope (api/web) ----------------------------------------------------------

export const fieldViolationSchema = z.object({
  field: z.string(),
  code: z.string(),
  message: z.string(),
});
export type FieldViolation = z.infer<typeof fieldViolationSchema>;

export const errorEnvelopeSchema = z.object({
  code: z.string(),
  message: z.string(),
  correlationId: z.string(),
  violations: z.array(fieldViolationSchema).optional(),
});
export type ErrorEnvelope = z.infer<typeof errorEnvelopeSchema>;

// ---- catalog (api/catalog/dto) -------------------------------------------------------------

export const priceViewSchema = z.object({ amount: z.string(), currency: z.string() });
export type PriceView = z.infer<typeof priceViewSchema>;

export const weightViewSchema = z.object({ amount: z.string(), unit: weightUnitSchema });
export type WeightView = z.infer<typeof weightViewSchema>;

export const taxTreatmentViewSchema = z.object({
  category: taxCategorySchema,
  ratePercent: z.string(),
});
export type TaxTreatmentView = z.infer<typeof taxTreatmentViewSchema>;

export const categoryViewSchema = z.object({
  id: z.string(),
  name: z.string(),
  parentId: z.string().optional(),
  taxCategory: taxCategorySchema,
});
export type CategoryView = z.infer<typeof categoryViewSchema>;

export const productSummaryViewSchema = z.object({
  id: z.string(),
  name: z.string(),
  categoryId: z.string(),
  metal: metalSchema,
  stock: stockStatusSchema,
  pricingMode: pricingModeSchema,
  /** Absent for an on-request product: never a zero price. */
  price: priceViewSchema.optional(),
});
export type ProductSummaryView = z.infer<typeof productSummaryViewSchema>;

export const productDetailViewSchema = z.object({
  id: z.string(),
  name: z.string(),
  categoryId: z.string(),
  categoryName: z.string(),
  metal: metalSchema,
  purity: z.string(),
  weight: weightViewSchema,
  dimensions: z.string().optional(),
  stock: stockStatusSchema,
  pricingMode: pricingModeSchema,
  price: priceViewSchema.optional(),
  tax: taxTreatmentViewSchema,
});
export type ProductDetailView = z.infer<typeof productDetailViewSchema>;

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

// ---- market data (api/marketdata) ----------------------------------------------------------

export const changeViewSchema = z.object({
  direction: directionSchema,
  /** Unsigned size of the change per gram. */
  amount: z.string(),
  /** Unsigned size of the change in percent, to two places. */
  percent: z.string(),
});
export type ChangeView = z.infer<typeof changeViewSchema>;

export const referencePriceViewSchema = z.object({
  metal: metalSchema,
  pricePerGram: z.string(),
  currency: z.string(),
  observedAt: z.string(),
  /** Absent, not zero, until a second observation exists: an unknown change is not "no change". */
  change: changeViewSchema.optional(),
});
export type ReferencePriceView = z.infer<typeof referencePriceViewSchema>;

// ---- auth (api/auth) -----------------------------------------------------------------------

export const signInResponseSchema = z.object({
  accessToken: z.string(),
  tokenType: z.string(),
  expiresInSeconds: z.number().int(),
});
export type SignInResponse = z.infer<typeof signInResponseSchema>;

// ---- cart (api/cart/dto) -------------------------------------------------------------------

export const totalsViewSchema = z.object({
  net: priceViewSchema,
  tax: priceViewSchema,
  total: priceViewSchema,
});
export type TotalsView = z.infer<typeof totalsViewSchema>;

export const cartLineViewSchema = z.object({
  productId: z.string(),
  name: z.string(),
  quantity: z.number().int(),
  maxQuantity: z.number().int(),
  pricingMode: pricingModeSchema,
  unitPrice: priceViewSchema.optional(),
  lineNet: priceViewSchema.optional(),
  taxRatePercent: z.string().optional(),
  lineTax: priceViewSchema.optional(),
});
export type CartLineView = z.infer<typeof cartLineViewSchema>;

export const cartViewSchema = z.object({
  cartId: z.string().optional(),
  empty: z.boolean(),
  itemCount: z.number().int(),
  complete: z.boolean(),
  lines: z.array(cartLineViewSchema),
  /** Absent when no line has a price. */
  totals: totalsViewSchema.optional(),
});
export type CartView = z.infer<typeof cartViewSchema>;

// ---- display currency and preferences (api/currency, api/account/preferences) ---------------

export const currencyOptionSchema = z.object({ code: z.string(), perSettlementUnit: z.string() });
export type CurrencyOption = z.infer<typeof currencyOptionSchema>;

export const currencyOptionsViewSchema = z.object({
  /** The currency orders are priced and charged in; checkout is always in this one. */
  settlement: z.string(),
  /** `FAKE` means the rates are demo rates and the UI must say so. */
  rateSource: z.string(),
  options: z.array(currencyOptionSchema),
});
export type CurrencyOptionsView = z.infer<typeof currencyOptionsViewSchema>;

export const preferencesViewSchema = z.object({
  theme: z.enum(["LIGHT", "DARK"]).optional(),
  locale: z.string().optional(),
  currency: z.string().optional(),
});
export type PreferencesView = z.infer<typeof preferencesViewSchema>;

// ---- checkout (api/checkout) ---------------------------------------------------------------

export const checkoutStageSchema = z.enum(["PAYMENT_ALLOWED", "HANDOFF_REQUIRED"]);
export type CheckoutStage = z.infer<typeof checkoutStageSchema>;

export const handoffReasonSchema = z.enum([
  "VALUE_CEILING_EXCEEDED",
  "WEIGHT_CEILING_EXCEEDED",
  "NO_TIER_FOR_REGION",
]);
export type HandoffReason = z.infer<typeof handoffReasonSchema>;

export const paymentMethodSchema = z.enum([
  "CARD",
  "BANK_DEBIT",
  "BANK_REDIRECT",
  "BANK_TRANSFER",
  "SAVED_WALLET",
  "WALLET_ACCOUNT",
  "INVOICE",
]);
export type PaymentMethod = z.infer<typeof paymentMethodSchema>;

export const paymentMethodGroupSchema = z.enum(["GATEWAY", "WALLET", "INVOICE"]);
export type PaymentMethodGroup = z.infer<typeof paymentMethodGroupSchema>;

export const confirmationKindSchema = z.enum(["PAID", "INVOICE", "MANAGER_QUOTE"]);
export type ConfirmationKind = z.infer<typeof confirmationKindSchema>;

export const detailsViewSchema = z.object({
  name: z.string(),
  email: z.string(),
  phone: z.string(),
  street: z.string(),
  city: z.string(),
  country: z.string(),
  postalCode: z.string(),
  companyName: z.string().optional(),
  companyAddress: z.string().optional(),
  note: z.string().optional(),
});
export type DetailsView = z.infer<typeof detailsViewSchema>;

export const consentViewSchema = z.object({ policyVersion: z.string(), acceptedAt: z.string() });
export type ConsentView = z.infer<typeof consentViewSchema>;

export const conversionViewSchema = z.object({ reference: z.string(), verified: z.boolean() });
export type ConversionView = z.infer<typeof conversionViewSchema>;

export const quoteViewSchema = z.object({
  tierId: z.string(),
  cost: priceViewSchema,
  minDays: z.number().int(),
  maxDays: z.number().int(),
  quotedAt: z.string(),
});
export type QuoteView = z.infer<typeof quoteViewSchema>;

export const deliveryEvaluationViewSchema = z.object({
  stage: checkoutStageSchema,
  quote: quoteViewSchema.optional(),
  reason: handoffReasonSchema.optional(),
  boundCeiling: z.enum(["VALUE", "WEIGHT"]).optional(),
  exTaxValue: priceViewSchema,
  weightGrams: z.string(),
  handoffReference: z.string().optional(),
});
export type DeliveryEvaluationView = z.infer<typeof deliveryEvaluationViewSchema>;

export const handoffViewSchema = z.object({
  reference: z.string(),
  reason: handoffReasonSchema,
  boundCeiling: z.enum(["VALUE", "WEIGHT"]).optional(),
});
export type HandoffView = z.infer<typeof handoffViewSchema>;

export const sessionViewSchema = z.object({
  checkoutId: z.string(),
  source: z.enum(["CART", "BUY_NOW"]),
  basket: cartViewSchema,
  details: detailsViewSchema.optional(),
  consent: consentViewSchema.optional(),
  conversion: conversionViewSchema.optional(),
  delivery: deliveryEvaluationViewSchema.optional(),
});
export type SessionView = z.infer<typeof sessionViewSchema>;

export const step1ResponseSchema = z.object({
  checkoutId: z.string(),
  step1Complete: z.boolean(),
  details: detailsViewSchema,
  consent: consentViewSchema,
  conversion: conversionViewSchema.optional(),
});
export type Step1Response = z.infer<typeof step1ResponseSchema>;

export const paymentMethodViewSchema = z.object({
  method: paymentMethodSchema,
  group: paymentMethodGroupSchema,
});
export type PaymentMethodView = z.infer<typeof paymentMethodViewSchema>;

export const paymentMethodsViewSchema = z.object({
  methods: z.array(paymentMethodViewSchema),
  highValue: z.boolean(),
  grandTotal: priceViewSchema,
  selected: paymentMethodSchema.optional(),
});
export type PaymentMethodsView = z.infer<typeof paymentMethodsViewSchema>;

export const overviewTotalsViewSchema = z.object({
  net: priceViewSchema,
  tax: priceViewSchema,
  delivery: priceViewSchema,
  grandTotal: priceViewSchema,
});
export type OverviewTotalsView = z.infer<typeof overviewTotalsViewSchema>;

export const overviewViewSchema = z.object({
  checkoutId: z.string(),
  details: detailsViewSchema,
  paymentMethod: paymentMethodSchema.optional(),
  lines: z.array(cartLineViewSchema),
  delivery: quoteViewSchema,
  totals: overviewTotalsViewSchema,
  orderReference: z.string().optional(),
});
export type OverviewView = z.infer<typeof overviewViewSchema>;

export const paymentResultKindSchema = z.enum([
  "CAPTURED",
  "REDIRECT",
  "ELEMENT",
  "DOCUMENT_ISSUED",
  "DECLINED",
  "ERROR",
]);
export type PaymentResultKind = z.infer<typeof paymentResultKindSchema>;

/** One wire shape for every outcome; `result` says which other fields are present. */
export const paymentResultViewSchema = z.object({
  result: paymentResultKindSchema,
  orderReference: z.string().optional(),
  redirectUrl: z.string().optional(),
  clientHandle: z.string().optional(),
  invoiceReference: z.string().optional(),
  declineReason: z.string().optional(),
  errorCode: z.string().optional(),
  message: z.string().optional(),
});
export type PaymentResultView = z.infer<typeof paymentResultViewSchema>;

export const confirmationViewSchema = z.object({
  kind: confirmationKindSchema,
  orderNumber: z.string(),
  status: z.string(),
  statusLabel: z.string(),
  itemCount: z.number().int(),
  total: priceViewSchema.optional(),
  invoiceNumber: z.string().optional(),
  message: z.string(),
});
export type ConfirmationView = z.infer<typeof confirmationViewSchema>;

export const addressViewSchema = z.object({
  street: z.string(),
  city: z.string(),
  country: z.string(),
  postalCode: z.string(),
  companyName: z.string().optional(),
  companyAddress: z.string().optional(),
});
export type AddressView = z.infer<typeof addressViewSchema>;

/** Checkout step 1 as sent (`Step1Request`): always the full explicit field set. */
export interface Step1Request {
  name: string;
  contact: { email: string; phone: string };
  street: string;
  city: string;
  country: string;
  postalCode: string;
  company: { name: string; address: string };
  note: string;
  privacyPolicyAccepted: boolean;
  policyVersion: string;
  account: { rememberMe: boolean; password?: string };
}

// ---- accounts, orders and inquiries --------------------------------------------------------

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

export const verificationStateSchema = z.enum(["UNVERIFIED", "VERIFIED"]);
export type VerificationState = z.infer<typeof verificationStateSchema>;

export const inquirySourceSchema = z.enum(["CATALOG", "PRODUCT", "HANDOFF"]);
export type InquirySource = z.infer<typeof inquirySourceSchema>;

export const customerViewSchema = z.object({
  customerId: z.string(),
  verification: verificationStateSchema,
});
export type CustomerView = z.infer<typeof customerViewSchema>;

export const registrationAcceptedSchema = z.object({ reference: z.string(), message: z.string() });
export type RegistrationAccepted = z.infer<typeof registrationAcceptedSchema>;

/** `status` is kept a string on the wire so a status added on the server still renders (by its label). */
export const orderSummaryViewSchema = z.object({
  orderNumber: z.string(),
  createdAt: z.string(),
  itemCount: z.number().int(),
  total: priceViewSchema,
  status: z.string(),
  statusLabel: z.string(),
});
export type OrderSummaryView = z.infer<typeof orderSummaryViewSchema>;

export const orderHistoryViewSchema = z.object({ orders: z.array(orderSummaryViewSchema) });
export type OrderHistoryView = z.infer<typeof orderHistoryViewSchema>;

export const orderLineViewSchema = z.object({
  productName: z.string(),
  quantity: z.number().int(),
  unitPrice: priceViewSchema,
  lineNet: priceViewSchema,
  taxRatePercent: z.string(),
  lineTax: priceViewSchema,
});
export type OrderLineView = z.infer<typeof orderLineViewSchema>;

export const orderAddressViewSchema = z.object({
  street: z.string(),
  postalCode: z.string(),
  city: z.string(),
  country: z.string(),
});
export type OrderAddressView = z.infer<typeof orderAddressViewSchema>;

export const orderTotalsViewSchema = z.object({
  net: priceViewSchema,
  tax: priceViewSchema,
  delivery: priceViewSchema,
  grandTotal: priceViewSchema,
});
export type OrderTotalsView = z.infer<typeof orderTotalsViewSchema>;

export const shipmentViewSchema = z.object({ carrier: z.string(), trackingReference: z.string() });
export type ShipmentView = z.infer<typeof shipmentViewSchema>;

export const orderDetailViewSchema = z.object({
  orderNumber: z.string(),
  createdAt: z.string(),
  itemCount: z.number().int(),
  status: z.string(),
  statusLabel: z.string(),
  lines: z.array(orderLineViewSchema),
  deliveryAddress: orderAddressViewSchema.optional(),
  totals: orderTotalsViewSchema,
  paymentMethod: z.string().optional(),
  paymentStatus: z.string().optional(),
  /** Absent, not empty, before the order ships. */
  shipment: shipmentViewSchema.optional(),
});
export type OrderDetailView = z.infer<typeof orderDetailViewSchema>;

export const resumedPaymentViewSchema = z.object({ checkoutId: z.string() });
export type ResumedPaymentView = z.infer<typeof resumedPaymentViewSchema>;

export const inquiryReceiptSchema = z.object({ reference: z.string(), message: z.string() });
export type InquiryReceipt = z.infer<typeof inquiryReceiptSchema>;

/** An inquiry as sent (`InquiryRequest`); the context field that does not apply is left out. */
export interface InquiryRequest {
  source: InquirySource;
  name: string;
  email: string;
  topic: string;
  message: string;
  productId?: string;
  handoffReference?: string;
}

// ---- the registry the contract test walks --------------------------------------------------

const API = "services/api/src/main/java/com/rednavis/metaldesk/api/";
const SHARE = "libs/share/src/main/java/com/rednavis/metaldesk/share/domain/";

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

export const contract: (RecordContract | EnumContract)[] = [
  { kind: "enum", java: SHARE + "catalog/Metal.java", enumName: "Metal", schema: metalSchema },
  {
    kind: "enum",
    java: SHARE + "catalog/TaxCategory.java",
    enumName: "TaxCategory",
    schema: taxCategorySchema,
  },
  {
    kind: "enum",
    java: SHARE + "measure/WeightUnit.java",
    enumName: "WeightUnit",
    schema: weightUnitSchema,
  },
  {
    kind: "enum",
    java: SHARE + "catalog/StockStatus.java",
    enumName: "StockStatus",
    schema: stockStatusSchema,
  },
  {
    kind: "enum",
    java: SHARE + "catalog/PricingMode.java",
    enumName: "PricingMode",
    schema: pricingModeSchema,
  },
  {
    kind: "enum",
    java: API + "marketdata/ReferencePriceView.java",
    enumName: "Direction",
    schema: directionSchema,
  },
  {
    kind: "record",
    java: API + "web/FieldViolation.java",
    record: "FieldViolation",
    schema: fieldViolationSchema,
  },
  {
    kind: "record",
    java: API + "web/ApiErrorEnvelope.java",
    record: "ApiErrorEnvelope",
    schema: errorEnvelopeSchema,
  },
  {
    kind: "record",
    java: API + "catalog/dto/PriceView.java",
    record: "PriceView",
    schema: priceViewSchema,
  },
  {
    kind: "record",
    java: API + "catalog/dto/WeightView.java",
    record: "WeightView",
    schema: weightViewSchema,
  },
  {
    kind: "record",
    java: API + "catalog/dto/TaxTreatmentView.java",
    record: "TaxTreatmentView",
    schema: taxTreatmentViewSchema,
  },
  {
    kind: "record",
    java: API + "catalog/dto/CategoryView.java",
    record: "CategoryView",
    schema: categoryViewSchema,
  },
  {
    kind: "record",
    java: API + "catalog/dto/ProductSummaryView.java",
    record: "ProductSummaryView",
    schema: productSummaryViewSchema,
  },
  {
    kind: "record",
    java: API + "catalog/dto/ProductDetailView.java",
    record: "ProductDetailView",
    schema: productDetailViewSchema,
  },
  {
    kind: "record",
    java: API + "catalog/dto/PageView.java",
    record: "PageView",
    schema: pageViewSchema(z.unknown()),
  },
  {
    kind: "record",
    java: API + "marketdata/ReferencePriceView.java",
    record: "ChangeView",
    schema: changeViewSchema,
  },
  {
    kind: "record",
    java: API + "marketdata/ReferencePriceView.java",
    record: "ReferencePriceView",
    schema: referencePriceViewSchema,
  },
  {
    kind: "record",
    java: API + "cart/dto/TotalsView.java",
    record: "TotalsView",
    schema: totalsViewSchema,
  },
  {
    kind: "record",
    java: API + "cart/dto/CartLineView.java",
    record: "CartLineView",
    schema: cartLineViewSchema,
  },
  {
    kind: "record",
    java: API + "cart/dto/CartView.java",
    record: "CartView",
    schema: cartViewSchema,
  },
  {
    kind: "record",
    java: API + "currency/CurrencyOptionsView.java",
    record: "CurrencyOption",
    schema: currencyOptionSchema,
  },
  {
    kind: "record",
    java: API + "currency/CurrencyOptionsView.java",
    record: "CurrencyOptionsView",
    schema: currencyOptionsViewSchema,
  },
  {
    kind: "record",
    java: API + "account/preferences/PreferencesView.java",
    record: "PreferencesView",
    schema: preferencesViewSchema,
  },
  {
    kind: "record",
    java: API + "auth/SignInResponse.java",
    record: "SignInResponse",
    schema: signInResponseSchema,
  },
  {
    kind: "enum",
    java: API + "checkout/delivery/CheckoutStage.java",
    enumName: "CheckoutStage",
    schema: checkoutStageSchema,
  },
  {
    kind: "enum",
    java: API + "checkout/delivery/HandoffReason.java",
    enumName: "HandoffReason",
    schema: handoffReasonSchema,
  },
  {
    kind: "enum",
    java: SHARE + "payment/PaymentMethod.java",
    enumName: "PaymentMethod",
    schema: paymentMethodSchema,
  },
  {
    kind: "enum",
    java: SHARE + "payment/PaymentMethodGroup.java",
    enumName: "PaymentMethodGroup",
    schema: paymentMethodGroupSchema,
  },
  {
    kind: "enum",
    java: API + "checkout/confirmation/dto/ConfirmationKind.java",
    enumName: "ConfirmationKind",
    schema: confirmationKindSchema,
  },
  {
    kind: "record",
    java: API + "checkout/dto/DetailsView.java",
    record: "DetailsView",
    schema: detailsViewSchema,
  },
  {
    kind: "record",
    java: API + "checkout/dto/ConsentView.java",
    record: "ConsentView",
    schema: consentViewSchema,
  },
  {
    kind: "record",
    java: API + "checkout/dto/ConversionView.java",
    record: "ConversionView",
    schema: conversionViewSchema,
  },
  {
    kind: "record",
    java: API + "checkout/delivery/dto/QuoteView.java",
    record: "QuoteView",
    schema: quoteViewSchema,
  },
  {
    kind: "record",
    java: API + "checkout/delivery/dto/DeliveryEvaluationView.java",
    record: "DeliveryEvaluationView",
    schema: deliveryEvaluationViewSchema,
  },
  {
    kind: "record",
    java: API + "checkout/delivery/dto/HandoffView.java",
    record: "HandoffView",
    schema: handoffViewSchema,
  },
  {
    kind: "record",
    java: API + "checkout/dto/SessionView.java",
    record: "SessionView",
    schema: sessionViewSchema,
  },
  {
    kind: "record",
    java: API + "checkout/dto/Step1Response.java",
    record: "Step1Response",
    schema: step1ResponseSchema,
  },
  {
    kind: "record",
    java: API + "checkout/payment/dto/PaymentMethodView.java",
    record: "PaymentMethodView",
    schema: paymentMethodViewSchema,
  },
  {
    kind: "record",
    java: API + "checkout/payment/dto/PaymentMethodsView.java",
    record: "PaymentMethodsView",
    schema: paymentMethodsViewSchema,
  },
  {
    kind: "record",
    java: API + "checkout/payment/dto/OverviewTotalsView.java",
    record: "OverviewTotalsView",
    schema: overviewTotalsViewSchema,
  },
  {
    kind: "record",
    java: API + "checkout/payment/dto/OverviewView.java",
    record: "OverviewView",
    schema: overviewViewSchema,
  },
  {
    kind: "record",
    java: API + "checkout/payment/dto/PaymentResultView.java",
    record: "PaymentResultView",
    schema: paymentResultViewSchema,
  },
  {
    kind: "record",
    java: API + "checkout/confirmation/dto/ConfirmationView.java",
    record: "ConfirmationView",
    schema: confirmationViewSchema,
  },
  {
    kind: "record",
    java: API + "cart/dto/AddressView.java",
    record: "AddressView",
    schema: addressViewSchema,
  },
  {
    kind: "enum",
    java: SHARE + "order/OrderStatus.java",
    enumName: "OrderStatus",
    schema: orderStatusSchema,
  },
  {
    kind: "enum",
    java: SHARE + "customer/VerificationState.java",
    enumName: "VerificationState",
    schema: verificationStateSchema,
  },
  {
    kind: "enum",
    java: API + "inquiry/InquirySource.java",
    enumName: "InquirySource",
    schema: inquirySourceSchema,
  },
  {
    kind: "record",
    java: API + "auth/CustomerView.java",
    record: "CustomerView",
    schema: customerViewSchema,
  },
  {
    kind: "record",
    java: API + "account/dto/RegistrationAccepted.java",
    record: "RegistrationAccepted",
    schema: registrationAcceptedSchema,
  },
  {
    kind: "record",
    java: API + "order/dto/OrderSummaryView.java",
    record: "OrderSummaryView",
    schema: orderSummaryViewSchema,
  },
  {
    kind: "record",
    java: API + "order/dto/OrderHistoryView.java",
    record: "OrderHistoryView",
    schema: orderHistoryViewSchema,
  },
  {
    kind: "record",
    java: API + "order/dto/OrderLineView.java",
    record: "OrderLineView",
    schema: orderLineViewSchema,
  },
  {
    kind: "record",
    java: API + "order/dto/OrderAddressView.java",
    record: "OrderAddressView",
    schema: orderAddressViewSchema,
  },
  {
    kind: "record",
    java: API + "order/dto/OrderTotalsView.java",
    record: "OrderTotalsView",
    schema: orderTotalsViewSchema,
  },
  {
    kind: "record",
    java: API + "order/dto/ShipmentView.java",
    record: "ShipmentView",
    schema: shipmentViewSchema,
  },
  {
    kind: "record",
    java: API + "order/dto/OrderDetailView.java",
    record: "OrderDetailView",
    schema: orderDetailViewSchema,
  },
  {
    kind: "record",
    java: API + "checkout/payment/dto/ResumedPaymentView.java",
    record: "ResumedPaymentView",
    schema: resumedPaymentViewSchema,
  },
  {
    kind: "record",
    java: API + "inquiry/dto/InquiryReceipt.java",
    record: "InquiryReceipt",
    schema: inquiryReceiptSchema,
  },
];
