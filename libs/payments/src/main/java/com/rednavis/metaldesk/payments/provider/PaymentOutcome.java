package com.rednavis.metaldesk.payments.provider;

import com.rednavis.metaldesk.share.domain.payment.ProviderReference;
import com.rednavis.metaldesk.share.error.ValidationException;
import java.net.URI;
import java.util.Optional;

/**
 * What a provider answered to a request to take a payment. Sealed, so a {@code switch} over it is
 * exhaustive: when a new flow is added, every caller gets a compile error instead of a runtime
 * fall-through.
 *
 * <p>The variants are the flows BRD FR-7.2 names (redirect, embedded element, invoice generation)
 * plus the two ends a payment can reach at once:
 *
 * <ul>
 *   <li>{@link Captured}: the funds were received.
 *   <li>{@link RedirectRequired}: send the customer's browser to the provider, then {@link
 *       PaymentProvider#confirm confirm}.
 *   <li>{@link ElementRequired}: mount the provider's embedded element, then {@link
 *       PaymentProvider#confirm confirm}.
 *   <li>{@link DocumentIssued}: an invoice was issued (BRD FR-6.2); no gateway call was made.
 *   <li>{@link Declined}: the provider said no. An ordinary outcome, not an error (FR-6.3).
 *   <li>{@link Failed}: the provider answered that it could not complete the payment for a reason
 *       that is not the customer's instrument. It is recoverable like a decline, but worth logging.
 * </ul>
 *
 * <p>None of these is a {@link Throwable}. A provider that cannot be reached or understood is
 * signalled by {@link PaymentProviderException}, so "the bank said no" and "we could not reach the
 * bank" differ by type alone. No variant carries anything vendor-shaped: no HTTP status, no vendor
 * error string, and no payment instrument.
 */
public sealed interface PaymentOutcome
    permits PaymentOutcome.Captured,
        PaymentOutcome.RedirectRequired,
        PaymentOutcome.ElementRequired,
        PaymentOutcome.DocumentIssued,
        PaymentOutcome.Declined,
        PaymentOutcome.Failed {

  /**
   * The funds were received.
   *
   * @param reference the provider's handle for the payment, never null
   */
  record Captured(ProviderReference reference) implements PaymentOutcome {

    /**
     * Validates the reference.
     *
     * @throws ValidationException if the reference is null
     */
    public Captured {
      Checks.reference(reference);
    }
  }

  /**
   * The customer must complete the payment at the provider: hand {@code redirectUri} to the
   * browser, then confirm using {@code reference}.
   *
   * @param reference the provider's handle for the pending payment, never null
   * @param redirectUri where to send the browser, absolute {@code http} or {@code https}
   */
  record RedirectRequired(ProviderReference reference, URI redirectUri) implements PaymentOutcome {

    /**
     * Validates the fields.
     *
     * @throws ValidationException if the reference is null or the URI is not absolute {@code http}
     *     or {@code https}
     */
    public RedirectRequired {
      Checks.reference(reference);
      Checks.webUri(
          redirectUri,
          "payment-outcome.redirect-invalid",
          "Redirect target must be an absolute http(s) URI");
    }
  }

  /**
   * The customer completes the payment in an element the provider supplies inside the page: mount
   * it with {@code clientHandle}, then confirm using {@code reference}.
   *
   * <p>The handle is opaque and only for the customer's browser session. Its {@code toString} is
   * redacted so it does not reach a log by accident.
   *
   * @param reference the provider's handle for the pending payment, never null
   * @param clientHandle the opaque handle that lets the element talk to the provider, never blank
   */
  record ElementRequired(ProviderReference reference, String clientHandle)
      implements PaymentOutcome {

    /**
     * Validates the fields.
     *
     * @throws ValidationException if the reference is null or the handle is null or blank
     */
    public ElementRequired {
      Checks.reference(reference);
      if (clientHandle == null || clientHandle.isBlank()) {
        throw new ValidationException(
            "payment-outcome.handle-blank", "Client handle must not be null or blank");
      }
    }

    /**
     * Describes the outcome without the handle.
     *
     * @return text naming the reference and hiding the handle
     */
    @Override
    public String toString() {
      return "ElementRequired[reference=" + reference + ", clientHandle=***]";
    }
  }

  /**
   * An invoice was issued (BRD FR-6.2). No gateway was called; payment is settled outside the
   * checkout.
   *
   * @param reference the invoice number, never null
   */
  record DocumentIssued(ProviderReference reference) implements PaymentOutcome {

    /**
     * Validates the reference.
     *
     * @throws ValidationException if the reference is null
     */
    public DocumentIssued {
      Checks.reference(reference);
    }
  }

  /**
   * The provider refused the payment. The customer returns to payment selection with everything
   * they entered intact (BRD FR-6.3).
   *
   * @param reason the vendor-neutral reason, never null
   * @param message text safe to show the customer, required when the reason is {@link
   *     DeclineReason#OTHER} and otherwise optional; never null, and when present trimmed, not
   *     blank and at most 200 characters. An adapter must write it, not pass through a raw vendor
   *     string.
   */
  record Declined(DeclineReason reason, Optional<String> message) implements PaymentOutcome {

    /**
     * Validates the fields.
     *
     * @throws ValidationException if the reason or message optional is null, the message is blank
     *     or too long, or the reason is {@code OTHER} and there is no message
     */
    public Declined {
      if (reason == null || message == null) {
        throw new ValidationException(
            "payment-outcome.decline-missing", "Decline requires a reason and a message optional");
      }
      message = message.map(Checks::safeMessage);
      if (reason == DeclineReason.OTHER && message.isEmpty()) {
        throw new ValidationException(
            "payment-outcome.decline-message-required", "An OTHER decline requires a message");
      }
    }
  }

  /**
   * The provider answered that the payment could not be completed, for a reason that is not the
   * customer's instrument, such as a provider-side processing error. Recoverable like a decline;
   * unlike one, it deserves a log entry.
   *
   * @param message text safe to show the customer, trimmed, not blank and at most 200 characters
   */
  record Failed(String message) implements PaymentOutcome {

    /**
     * Validates the message.
     *
     * @throws ValidationException if the message is null, blank or too long
     */
    public Failed {
      message = Checks.safeMessage(message);
    }
  }
}
