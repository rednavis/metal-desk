import { describe, expect, it } from "vitest";
import { addDecimals, compareDecimals, isDecimal, isPositiveDecimal } from "./decimal";

describe("exact decimals", () => {
  it("adds without floating-point error", () => {
    expect(addDecimals("0.1", "0.2")).toBe("0.3");
    expect(addDecimals("3918.64", "267.48", "250.00")).toBe("4436.12");
  });

  it("keeps the most precise scale and handles whole numbers and negatives", () => {
    expect(addDecimals("1", "2.50")).toBe("3.50");
    expect(addDecimals("5", "-7")).toBe("-2");
    expect(addDecimals("0.05", "-0.10")).toBe("-0.05");
  });

  it("compares across scales", () => {
    expect(compareDecimals("2500", "2500.00")).toBe(0);
    expect(compareDecimals("9.99", "10")).toBe(-1);
    expect(compareDecimals("100.01", "100")).toBe(1);
  });

  it("recognises what is and is not an amount", () => {
    expect(isDecimal("12.5")).toBe(true);
    expect(isDecimal("12,5")).toBe(false);
    expect(isDecimal("")).toBe(false);
    expect(isPositiveDecimal("0.00")).toBe(false);
    expect(isPositiveDecimal("0.01")).toBe(true);
    expect(isPositiveDecimal("-1")).toBe(false);
  });
});

describe("decimal edge cases", () => {
  it("rejects text that is not a decimal and throws when asked to compute with it", () => {
    expect(isDecimal("1e3")).toBe(false);
    expect(isDecimal("")).toBe(false);
    expect(isPositiveDecimal("abc")).toBe(false);
    expect(isPositiveDecimal("0.00")).toBe(false);
    expect(() => compareDecimals("x", "1")).toThrow("Not a decimal: x");
    expect(() => compareDecimals("1", "y")).toThrow("Not a decimal: y");
    expect(() => addDecimals("1", "nope")).toThrow("Not a decimal: nope");
  });

  it("compares across scales and sums negatives and whole numbers exactly", () => {
    expect(compareDecimals("1.5", "1.50")).toBe(0);
    expect(compareDecimals("-1", "0.1")).toBe(-1);
    expect(compareDecimals("2", "1.999")).toBe(1);
    expect(addDecimals("1", "2")).toBe("3");
    expect(addDecimals("-1.25", "0.25")).toBe("-1.00");
    expect(addDecimals("0.05", "0.01")).toBe("0.06");
    expect(addDecimals()).toBe("0");
  });
});
