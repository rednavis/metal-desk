import { errorEnvelopeSchema, type FieldViolation } from "./types";

/**
 * Every failed call, as one typed error.
 *
 * The server's error envelope (`{code, message, correlationId}`, plus `violations` for a form) is
 * parsed here and nowhere else, so a screen never looks inside an error body: it reads `code` to
 * decide what to do, shows `message`, and shows `correlationId` so that a person reporting the
 * problem gives support something to search for (BRD FR-8.1, "support-actionable").
 *
 * Failures that are not the server's envelope still become an `ApiError`: a proxy's HTML 502 gets
 * the code `http.502`, a dropped connection `network.unreachable`, a success body that does not
 * match its schema `response.malformed`. The correlation id of those is the one the client sent.
 */
export class ApiError extends Error {
  /** The HTTP status, or 0 if no response arrived. */
  readonly status: number;
  /** The server's machine-readable code, or one of the client's own (see the class comment). */
  readonly code: string;
  /** What to quote when asking for help. */
  readonly correlationId: string;
  /** Per-field problems of a rejected form; empty otherwise. */
  readonly violations: readonly FieldViolation[];

  constructor(
    status: number,
    code: string,
    message: string,
    correlationId: string,
    violations: readonly FieldViolation[] = [],
  ) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
    this.correlationId = correlationId;
    this.violations = violations;
  }

  /**
   * Builds the error for a response that was not a success.
   *
   * @param status the HTTP status
   * @param body the parsed body, or anything if it was not JSON
   * @param sentCorrelationId the id the client sent, used when the body carries none
   */
  static fromResponse(status: number, body: unknown, sentCorrelationId: string): ApiError {
    const envelope = errorEnvelopeSchema.safeParse(body);
    if (envelope.success) {
      const { code, message, correlationId, violations } = envelope.data;
      return new ApiError(status, code, message, correlationId, violations ?? []);
    }
    return new ApiError(
      status,
      `http.${status}`,
      "The server could not complete the request.",
      sentCorrelationId,
    );
  }

  /** The error for a call that never got a response. */
  static unreachable(correlationId: string): ApiError {
    return new ApiError(
      0,
      "network.unreachable",
      "The server could not be reached. Check your connection and try again.",
      correlationId,
    );
  }

  /** The error for a success response that does not have the promised shape. */
  static malformed(correlationId: string): ApiError {
    return new ApiError(
      200,
      "response.malformed",
      "The server sent a response this page could not understand.",
      correlationId,
    );
  }
}

/** Whether a thrown value is an {@link ApiError}. */
export function isApiError(value: unknown): value is ApiError {
  return value instanceof ApiError;
}
