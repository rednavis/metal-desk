package com.rednavis.metaldesk.payments.provider;

import java.io.Serial;

/**
 * Signals that the provider could not be <em>reached or understood</em>: the transport failed, as
 * opposed to the provider answering. It is reserved for that, and is never used for a decline.
 *
 * <p>The distinction is by type, deliberately. A decline ({@link PaymentOutcome.Declined}) is "the
 * bank said no": an expected outcome that returns the customer to payment selection (BRD FR-6.3).
 * This exception is "we could not reach the bank": an incident to be logged and alerted on. Sending
 * both down the same path would force a {@code catch} in checkout for an outcome that is not
 * exceptional, or hide an outage as a decline.
 *
 * <p>It is unchecked and travels as the error signal of the {@code Mono} an operation returns. It
 * is not a {@code DomainException}: that hierarchy is for failures the caller can fix or the state
 * forbids, whereas this is infrastructure. The message must not include anything the provider
 * echoed that could hold customer data.
 */
public final class PaymentProviderException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  private final Kind failureKind;

  /**
   * Creates the failure.
   *
   * @param kind how the transport failed
   * @param message what happened, without any provider-supplied text
   * @param cause the underlying transport error, or {@code null} when there is none
   */
  public PaymentProviderException(Kind kind, String message, Throwable cause) {
    super(message, cause);
    this.failureKind = kind;
  }

  /**
   * Returns how the transport failed.
   *
   * @return the kind, never null
   */
  public Kind kind() {
    return failureKind;
  }

  /** How the transport failed. */
  public enum Kind {

    /** The provider did not answer in time. */
    TIMEOUT,

    /** The provider could not be connected to. */
    UNREACHABLE,

    /** The provider answered, but not with something that could be understood. */
    MALFORMED_RESPONSE
  }
}
