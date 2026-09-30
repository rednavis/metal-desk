package com.rednavis.metaldesk.api.checkout.delivery;

import com.rednavis.metaldesk.api.auth.AuthenticatedCustomer;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * A stand-in for a T-037 payment endpoint, used only by tests: it does what every payment endpoint
 * must, asking the {@link PaymentGate} first and calling the provider only if the gate lets it. The
 * provider is a WireMock server, so a test can count how many requests reached it.
 */
@RestController
@RequiredArgsConstructor
public class PaymentProbeController {

  /** The base URL of the provider stub; set by the test that runs it. */
  public static final AtomicReference<String> PROVIDER = new AtomicReference<>();

  private final PaymentGate gate;

  /**
   * Charges through the provider, if the session allows payment.
   *
   * @param id the checkout id
   * @param customer the signed-in customer, if any
   * @return the provider's answer
   */
  @PostMapping("/api/checkout/sessions/{id}/payment-probe")
  public Mono<Map<String, String>> pay(
      @PathVariable String id, @AuthenticationPrincipal AuthenticatedCustomer customer) {
    return gate.require(id, customer)
        .flatMap(
            session ->
                WebClient.create(PROVIDER.get())
                    .post()
                    .uri("/charge")
                    .retrieve()
                    .bodyToMono(String.class)
                    .map(body -> Map.of("provider", body)));
  }
}
