/**
 * The shapes `apps/admin` sends, written once as zod schemas (see `apps/web`'s `types.ts` for why):
 * the schema is the runtime check the client applies, the TypeScript type is inferred from it, and
 * `contract.test.ts` reads the Java records in this repository and fails when one drifts from its
 * schema.
 *
 * Only what exists today is mirrored: the error envelope, pages, and the tier API. The quote queue
 * and order views are added by T-056 with the screens that use them, each with its contract entry.
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
];
