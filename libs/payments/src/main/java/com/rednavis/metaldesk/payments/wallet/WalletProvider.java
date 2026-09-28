package com.rednavis.metaldesk.payments.wallet;

import com.rednavis.metaldesk.payments.provider.PaymentIntent;
import com.rednavis.metaldesk.payments.provider.PaymentOutcome;
import com.rednavis.metaldesk.payments.provider.PaymentProvider;
import com.rednavis.metaldesk.payments.provider.PaymentProviderException;
import com.rednavis.metaldesk.payments.provider.ProviderCapability;
import com.rednavis.metaldesk.share.domain.payment.PaymentMethod;
import com.rednavis.metaldesk.share.domain.payment.PaymentMethodGroup;
import com.rednavis.metaldesk.share.domain.payment.ProviderReference;
import com.rednavis.metaldesk.share.error.ValidationException;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import reactor.core.publisher.Mono;

/**
 * The {@link PaymentProvider} for the separate, account-based wallet (BRD FR-6.1, Architecture
 * section 4): the customer pays from a balance held with the wallet provider, not through the
 * gateway. It serves exactly the methods of {@link PaymentMethodGroup#WALLET}, taken from {@link
 * PaymentMethod} itself, and so never overlaps the gateway adapter.
 *
 * <p>It is built the same way as the gateway adapter: a small JSON protocol to a provider whose
 * address is configuration and which WireMock answers in this reference build (ADR-0002); a decline
 * is a value and a transport failure is a {@link PaymentProviderException}; a timeout is never a
 * decline; customer-facing messages are written here, never passed through.
 *
 * <p>An account-based payment normally completes in one call, so {@link #authorise} answers
 * captured, declined (an insufficient balance is {@code INSUFFICIENT_FUNDS}) or failed. {@link
 * #confirm} is still provided and only reads the payment's state.
 */
public final class WalletProvider implements PaymentProvider {

  private static final String PROVIDER_ID = "wallet";
  private static final String FAILED_MESSAGE =
      "The payment could not be completed. Please try again.";

  private final WalletClient client;
  private final WalletDeclineMapper declineMapper = new WalletDeclineMapper();
  private final ProviderCapability served;

  /**
   * Creates a provider for the wallet.
   *
   * @param configuration where the wallet provider is, how long to wait and how often to retry
   */
  public WalletProvider(WalletConfiguration configuration) {
    this(new WalletClient(configuration));
  }

  /**
   * Creates a provider over an existing client.
   *
   * @param client the client to use
   */
  /* default */ WalletProvider(WalletClient client) {
    this.client = client;
    final Set<PaymentMethod> walletMethods =
        Arrays.stream(PaymentMethod.values())
            .filter(method -> method.group() == PaymentMethodGroup.WALLET)
            .collect(Collectors.toSet());
    this.served = new ProviderCapability(PROVIDER_ID, walletMethods);
  }

  @Override
  public ProviderCapability capability() {
    return served;
  }

  /**
   * Asks the wallet provider to take a payment. Never retried: a timeout does not prove the payment
   * failed, and repeating it could charge the wallet twice.
   *
   * @param intent what to take
   * @return a {@code Mono} of the outcome; it errors with a {@code ValidationException} for a
   *     method this provider does not serve, and with {@link PaymentProviderException} if the
   *     provider cannot be reached or understood
   */
  @Override
  public Mono<PaymentOutcome> authorise(PaymentIntent intent) {
    return intent != null && supports(intent.method())
        ? client.authorise(WalletRequest.from(intent)).map(this::toOutcome)
        : Mono.error(
            new ValidationException(
                "wallet.method-unsupported", "The wallet does not serve this payment method"));
  }

  /**
   * Reads the state of a payment at the wallet provider. Retried on a transport failure, up to the
   * configured budget, because it only reads.
   *
   * @param reference the handle from the earlier outcome
   * @return a {@code Mono} of the outcome
   */
  @Override
  public Mono<PaymentOutcome> confirm(ProviderReference reference) {
    return reference != null
        ? client.confirm(reference.value()).map(this::toOutcome)
        : Mono.error(
            new ValidationException("wallet.reference-missing", "Provider reference is required"));
  }

  private PaymentOutcome toOutcome(WalletResponse response) {
    try {
      return outcomeFor(response);
    } catch (ValidationException | IllegalArgumentException e) {
      throw malformed(e);
    }
  }

  private PaymentOutcome outcomeFor(WalletResponse response) {
    return switch (Objects.requireNonNullElse(response.status(), "")) {
      case "captured" -> new PaymentOutcome.Captured(new ProviderReference(response.reference()));
      case "declined" -> declineMapper.map(response.declineCode());
      case "failed" -> new PaymentOutcome.Failed(FAILED_MESSAGE);
      default -> throw malformed(null);
    };
  }

  private static PaymentProviderException malformed(Throwable cause) {
    return new PaymentProviderException(
        PaymentProviderException.Kind.MALFORMED_RESPONSE,
        "Wallet provider answered with something that could not be understood",
        cause);
  }
}
