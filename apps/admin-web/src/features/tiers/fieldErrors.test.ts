import { describe, expect, it } from "vitest";
import { fieldsFor } from "./fieldErrors";

describe("fieldsFor", () => {
  it.each([
    ["region.unknown", ["region"]],
    ["fulfillment-tier.ceiling-invalid", ["valueCeiling", "weightCeiling"]],
    ["fulfillment-tier.price-invalid", ["deliveryPrice"]],
    ["fulfillment-tier.currency-mismatch", ["currency"]],
    ["transit-time.inverted", ["minDays", "maxDays"]],
    ["weight.negative", ["weightCeiling"]],
    ["something.else", []],
  ])("maps %s to its inputs", (code, fields) => {
    expect(fieldsFor(code)).toEqual(fields);
  });
});
