/**
 * Exact decimal arithmetic on the amounts the API sends as text (`"4186.12"`). Money is never turned
 * into a floating-point number in this app: amounts are parsed to an integer count of the smallest
 * unit, so a preview total adds up exactly as the server's `BigDecimal` does.
 */
interface Decimal {
  units: bigint;
  scale: number;
}

const PATTERN = /^(-?)(\d+)(?:\.(\d+))?$/;

function parse(text: string): Decimal | undefined {
  const match = PATTERN.exec(text.trim());
  if (!match) return undefined;
  const fraction = match[3] ?? "";
  const units = BigInt(`${match[2] ?? "0"}${fraction}`);
  return { units: match[1] === "-" ? -units : units, scale: fraction.length };
}

function rescale(value: Decimal, scale: number): bigint {
  return value.units * 10n ** BigInt(scale - value.scale);
}

/** Whether `text` is a plain decimal number such as `12`, `12.5` or `-0.25`. */
export function isDecimal(text: string): boolean {
  return parse(text) !== undefined;
}

/** Whether `text` is a decimal strictly greater than zero. */
export function isPositiveDecimal(text: string): boolean {
  const value = parse(text);
  return value !== undefined && value.units > 0n;
}

/** Orders two decimals; text that is not a decimal is a programming error and throws. */
export function compareDecimals(a: string, b: string): -1 | 0 | 1 {
  const left = parse(a);
  const right = parse(b);
  if (!left || !right) throw new Error(`Not a decimal: ${left ? b : a}`);
  const scale = Math.max(left.scale, right.scale);
  const x = rescale(left, scale);
  const y = rescale(right, scale);
  return x < y ? -1 : x > y ? 1 : 0;
}

/** The exact sum of decimals, written with as many places as the most precise of them. */
export function addDecimals(...values: string[]): string {
  const parsed = values.map((value) => {
    const decimal = parse(value);
    if (!decimal) throw new Error(`Not a decimal: ${value}`);
    return decimal;
  });
  const scale = Math.max(0, ...parsed.map((value) => value.scale));
  const total = parsed.reduce((sum, value) => sum + rescale(value, scale), 0n);
  const negative = total < 0n;
  const digits = (negative ? -total : total).toString().padStart(scale + 1, "0");
  const whole = digits.slice(0, digits.length - scale);
  const fraction = scale === 0 ? "" : `.${digits.slice(digits.length - scale)}`;
  return `${negative ? "-" : ""}${whole}${fraction}`;
}
