import type { PaymentResultView } from "../../api/types";

/**
 * What a payment attempt came to, as a discriminated union: the six outcomes of BRD FR-7.2 and
 * nothing else. The server's `PaymentResultView` is one flat shape with optional fields, which a
 * screen cannot switch on safely; {@link toOutcome} is the one place that reads it, and every
 * consumer switches on `kind` with {@link assertNever} in the default, so a seventh outcome added
 * to the server's `result` enum is a compile error here instead of a screen that renders nothing.
 */
export type PaymentOutcome =
  | { kind: "captured"; orderReference?: string }
  | { kind: "redirect"; url: string }
  | { kind: "element"; clientHandle: string }
  | { kind: "documentIssued"; invoiceReference?: string }
  | { kind: "declined"; reason: string }
  | { kind: "failed"; code: string; message: string };

const INCOMPLETE = "response.incomplete";

/** Reads the server's result into an outcome; a result missing what its kind promises is a failure. */
export function toOutcome(view: PaymentResultView): PaymentOutcome {
  switch (view.result) {
    case "CAPTURED":
      return { kind: "captured", orderReference: view.orderReference };
    case "REDIRECT":
      return view.redirectUrl === undefined
        ? incomplete("The payment provider did not say where to send you.")
        : { kind: "redirect", url: view.redirectUrl };
    case "ELEMENT":
      return view.clientHandle === undefined
        ? incomplete("The payment provider did not supply a payment form.")
        : { kind: "element", clientHandle: view.clientHandle };
    case "DOCUMENT_ISSUED":
      return { kind: "documentIssued", invoiceReference: view.invoiceReference };
    case "DECLINED":
      return { kind: "declined", reason: view.declineReason ?? view.message ?? "declined" };
    case "ERROR":
      return {
        kind: "failed",
        code: view.errorCode ?? "payment.failed",
        message: view.message ?? "The payment could not be completed.",
      };
    default:
      return assertNever(view.result);
  }
}

function incomplete(message: string): PaymentOutcome {
  return { kind: "failed", code: INCOMPLETE, message };
}

/** Makes a `switch` exhaustive: reaching it is a type error when a case is missing. */
export function assertNever(value: never): never {
  throw new Error(`Unhandled case: ${JSON.stringify(value)}`);
}
