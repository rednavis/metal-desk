package com.rednavis.metaldesk.payments.gateway;

import com.rednavis.metaldesk.payments.provider.PaymentIntent;
import com.rednavis.metaldesk.payments.provider.PaymentOutcome;
import com.rednavis.metaldesk.payments.provider.PaymentProvider;
import com.rednavis.metaldesk.payments.provider.PaymentProviderException;
import com.rednavis.metaldesk.payments.provider.ProviderCapability;
import com.rednavis.metaldesk.share.domain.payment.PaymentMethod;
import com.rednavis.metaldesk.share.domain.payment.PaymentMethodGroup;
import com.rednavis.metaldesk.share.domain.payment.ProviderReference;
import com.rednavis.metaldesk.share.error.ValidationException;
import java.net.URI;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import reactor.core.publisher.Mono;

/**
 * The {@link PaymentProvider} for payments processed by a gateway: card, bank debit, bank redirect,
 * bank transfer and saved wallet (BRD FR-6.1, Architecture section 4).
 *
 * <p>It speaks a small JSON protocol to a gateway whose address is configuration; in this reference
 * build every call is answered by WireMock (ADR-0002), and pointing it at a real gateway is a
 * matter of {@link GatewayConfiguration}. It serves exactly the methods of {@link
 * PaymentMethodGroup#GATEWAY}, taken from {@link PaymentMethod} itself so the two cannot drift.
 *
 * <p>What the gateway answers becomes a {@link PaymentOutcome}:
 *
 * <ul>
 *   <li>captured becomes {@link PaymentOutcome.Captured};
 *   <li>redirect needed becomes {@link PaymentOutcome.RedirectRequired};
 *   <li>declined becomes {@link PaymentOutcome.Declined} with a reason from {@link
 *       GatewayDeclineMapper};
 *   <li>could not complete becomes {@link PaymentOutcome.Failed}.
 * </ul>
 *
 * <p><strong>A timeout is never a decline.</strong> If the gateway cannot be reached, does not
 * answer in time or answers with something unintelligible, the {@code Mono} ends with a {@link
 * PaymentProviderException}. Reporting that as a decline would tell the customer their payment was
 * refused when it was not, and would break the recovery that BRD FR-6.3 promises.
 *
 * <p>Messages shown to the customer are written here, never passed through from the gateway.
 */
public final class GatewayProvider implements PaymentProvider {

  private static final String PROVIDER_ID = "gateway";
  private static final String FAILED_MESSAGE =
      "The payment could not be completed. Please try again.";

  private final GatewayClient client;
  private final GatewayDeclineMapper declineMapper = new GatewayDeclineMapper();
  private final ProviderCapability served;

  /**
   * Creates a provider for a gateway.
   *
   * @param configuration where the gateway is, how long to wait and how often to retry
   */
  public GatewayProvider(GatewayConfiguration configuration) {
    this(new GatewayClient(configuration));
  }

  /**
   * Creates a provider over an existing client.
   *
   * @param client the client to use
   */
  /* default */ GatewayProvider(GatewayClient client) {
    this.client = client;
    final Set<PaymentMethod> gatewayMethods =
        Arrays.stream(PaymentMethod.values())
            .filter(method -> method.group() == PaymentMethodGroup.GATEWAY)
            .collect(Collectors.toSet());
    this.served = new ProviderCapability(PROVIDER_ID, gatewayMethods);
  }

  @Override
  public ProviderCapability capability() {
    return served;
  }

  /**
   * Asks the gateway to take a payment. The request is never retried: a timeout does not prove the
   * payment failed, and repeating it could charge twice.
   *
   * @param intent what to take
   * @return a {@code Mono} of the outcome; it errors with a {@code ValidationException} for a
   *     method this provider does not serve, and with {@link PaymentProviderException} if the
   *     gateway cannot be reached or understood
   */
  @Override
  public Mono<PaymentOutcome> authorise(PaymentIntent intent) {
    return intent != null && supports(intent.method())
        ? client.authorise(GatewayRequest.from(intent)).map(this::toOutcome)
        : Mono.error(
            new ValidationException(
                "gateway.method-unsupported", "The gateway does not serve this payment method"));
  }

  /**
   * Completes a payment that finished at the gateway. Retried on a transport failure, up to the
   * configured budget, because it only reads the payment's state.
   *
   * @param reference the handle from the earlier outcome
   * @return a {@code Mono} of the final outcome
   */
  @Override
  public Mono<PaymentOutcome> confirm(ProviderReference reference) {
    return reference != null
        ? client.confirm(reference.value()).map(this::toOutcome)
        : Mono.error(
            new ValidationException("gateway.reference-missing", "Provider reference is required"));
  }

  private PaymentOutcome toOutcome(GatewayResponse response) {
    try {
      return outcomeFor(response);
    } catch (ValidationException | IllegalArgumentException e) {
      throw malformed(e);
    }
  }

  private PaymentOutcome outcomeFor(GatewayResponse response) {
    return switch (Objects.requireNonNullElse(response.status(), "")) {
      case "captured" -> new PaymentOutcome.Captured(reference(response));
      case "redirect_required" ->
          new PaymentOutcome.RedirectRequired(reference(response), redirectTarget(response));
      case "declined" -> declineMapper.map(response.declineCode());
      case "failed" -> new PaymentOutcome.Failed(FAILED_MESSAGE);
      default -> throw malformed(null);
    };
  }

  private static ProviderReference reference(GatewayResponse response) {
    return new ProviderReference(response.reference());
  }

  private static URI redirectTarget(GatewayResponse response) {
    // An absent target becomes an empty, relative URI, which RedirectRequired then refuses.
    return URI.create(Objects.requireNonNullElse(response.redirectUrl(), ""));
  }

  private static PaymentProviderException malformed(Throwable cause) {
    return new PaymentProviderException(
        PaymentProviderException.Kind.MALFORMED_RESPONSE,
        "Gateway answered with something that could not be understood",
        cause);
  }
}
