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
    java: API + "auth/SignInResponse.java",
    record: "SignInResponse",
    schema: signInResponseSchema,
  },
];
