package com.rednavis.metaldesk.api.checkout.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.rednavis.metaldesk.share.domain.payment.PaymentMethod;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

/** The methods on offer (BRD FR-6.1, BR-9) and the gate on every payment endpoint (FR-5.3). */
class PaymentMethodsTest extends PaymentTestSupport {

  private static final String CODE = "code";
  private static final String HANDOFF_REQUIRED = "checkout.handoff-required";

  @BeforeEach
  void seedData() {
    seedPaymentCatalog();
  }

  private Set<String> offered(Called called) {
    return ((List<?>) called.body().get("methods"))
        .stream()
            .map(entry -> (String) ((Map<?, ?>) entry).get("method"))
            .collect(Collectors.toSet());
  }

  private String evaluatedCheckout(String product) {
    final String region = nextRegion();
    configureWideTier(region);
    final String id = checkoutTo(region, product, 1);
    evaluate(id);
    return id;
  }

  @Test
  void belowTheCeilingEverySupportedMethodIsOffered() {
    final Called called = methods(evaluatedCheckout(SMALL));

    assertEquals(200, called.status());
    assertEquals(false, called.body().get("highValue"));
    assertEquals(
        Arrays.stream(PaymentMethod.values()).map(Enum::name).collect(Collectors.toSet()),
        offered(called));
  }

  @Test
  void aboveTheCeilingGatewayMethodsAreGoneAndInvoiceRemains() {
    final Called called = methods(evaluatedCheckout(GOLD_1));

    assertEquals(true, called.body().get("highValue"));
    assertEquals(Set.of("INVOICE"), offered(called));
  }

  @Test
  void theOfferReportsTheTotalItWasComputedFor() {
    final String id = evaluatedCheckout(SMALL);

    final Called called = methods(id);

    assertEquals(totalOf(id), ((Map<?, ?>) called.body().get("grandTotal")).get("amount"));
  }

  @Test
  void offeredMethodCanBeSelectedAndIsRemembered() {
    final String id = evaluatedCheckout(SMALL);

    final Called selected = select(id, PaymentMethod.BANK_TRANSFER);

    assertEquals(200, selected.status());
    assertEquals("BANK_TRANSFER", selected.body().get("selected"));
    assertEquals("BANK_TRANSFER", methods(id).body().get("selected"));
  }

  @Test
  void methodThatIsNotOfferedCannotBeSelected() {
    final String id = evaluatedCheckout(GOLD_1);

    final Called refused = select(id, PaymentMethod.CARD);

    assertEquals(400, refused.status());
    assertEquals("payment.method-not-offered", refused.body().get(CODE));
    assertEquals(200, select(id, PaymentMethod.INVOICE).status());
  }

  @Test
  void sessionThatWasNeverEvaluatedIsRefused() {
    final String region = nextRegion();
    configureWideTier(region);
    final String id = checkoutTo(region, SMALL, 1);

    final Called called = methods(id);

    assertEquals(409, called.status());
    assertEquals("checkout.delivery-not-evaluated", called.body().get(CODE));
  }

  @Test
  void everyPaymentEndpointRefusesHandoffSessionAndNoProviderIsCalled() {
    final String region = nextRegion();
    configureTier(region, "1.00", "100000", "10.00");
    final String id = checkoutTo(region, SMALL, 1);
    assertEquals("HANDOFF_REQUIRED", evaluate(id).body().get("stage"));
    final String base = SESSIONS + "/" + id;

    final List<Called> refusals =
        List.of(
            call(HttpMethod.GET, base + "/payment/methods", null, null, null),
            call(HttpMethod.PUT, base + "/payment/method", null, null, Map.of("method", "INVOICE")),
            call(HttpMethod.GET, base + "/overview", null, null, null),
            call(
                HttpMethod.POST,
                base + "/payment/execute",
                null,
                null,
                Map.of("confirmedTotal", "1.00")),
            call(
                HttpMethod.GET,
                "/api/checkout/payment/callback?checkout=" + id + "&reference=x",
                null,
                null,
                null));

    for (final Called refusal : refusals) {
      assertEquals(409, refusal.status());
      assertEquals(HANDOFF_REQUIRED, refusal.body().get(CODE));
    }
    providers().verify(0, WireMock.anyRequestedFor(WireMock.anyUrl()));
  }

  @Test
  void handedOffSessionStaysRefused() {
    final String region = nextRegion();
    configureTier(region, "1.00", "100000", "10.00");
    final String id = checkoutTo(region, SMALL, 1);
    handoff(id, null);

    assertEquals(409, methods(id).status());
    assertEquals(HANDOFF_REQUIRED, methods(id).body().get(CODE));
    providers().verify(0, WireMock.anyRequestedFor(WireMock.anyUrl()));
  }
}
