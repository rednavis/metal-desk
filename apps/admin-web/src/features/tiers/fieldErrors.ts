export type FieldName =
  | "region"
  | "valueCeiling"
  | "weightCeiling"
  | "currency"
  | "deliveryPrice"
  | "minDays"
  | "maxDays";

/**
 * Which inputs a refusal is about. The API answers one error code and message per failure, not a list
 * by field, so the code decides where the server's own message is shown. A code that does not name a
 * field (an unparseable amount could be any of three) is shown above the form instead of under a
 * guess.
 */
export function fieldsFor(code: string): FieldName[] {
  if (code.startsWith("region.")) return ["region"];
  if (code === "fulfillment-tier.ceiling-invalid") return ["valueCeiling", "weightCeiling"];
  if (code === "fulfillment-tier.price-invalid") return ["deliveryPrice"];
  if (code === "fulfillment-tier.currency-mismatch") return ["currency"];
  if (code.startsWith("transit-time.")) return ["minDays", "maxDays"];
  if (code.startsWith("weight.")) return ["weightCeiling"];
  return [];
}
