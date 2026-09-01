package com.rednavis.metaldesk.api.checkout.delivery;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

/**
 * The server-side refusal to take payment on a handoff session (BRD FR-5.3): a payment request made
 * directly, whatever the client offers, is a 409 and reaches no provider.
 */
class PaymentGateTest extends DeliveryTestSupport {

  private static final String HEAVY = "5000";
  private static final String PRICE = "15.00";

  private static final String CHARGE = "/charge";
  private static final String PROBE = "/payment-probe";
  private static WireMockServer provider;

  @BeforeAll
  static void startProvider() {
    provider = new WireMockServer(WireMockConfiguration.options().dynamicPort());
    provider.start();
    provider.stubFor(
        WireMock.post(WireMock.urlEqualTo(CHARGE))
            .willReturn(WireMock.aResponse().withStatus(200).withBody("charged")));
    PaymentProbeController.PROVIDER.set("http://127.0.0.1:" + provider.port());
  }

  @AfterAll
  static void stopProvider() {
    provider.stop();
  }

  @BeforeEach
  void reset() {
    seedCatalog();
    provider.resetRequests();
  }

  private Called pay(String id) {
    return call(HttpMethod.POST, SESSIONS + "/" + id + PROBE, null, null, null);
  }

  @Test
  void paymentOnHandoffSessionIs409AndReachesNoProvider() {
    configureTier("RO", "1000.00", HEAVY, PRICE);
    final String id = checkoutTo("RO", GOLD_1, 1);
    evaluate(id);

    final Called called = pay(id);

    assertEquals(409, called.status());
    assertEquals("checkout.handoff-required", called.body().get("code"));
    provider.verify(0, WireMock.postRequestedFor(WireMock.urlEqualTo(CHARGE)));
  }

  @Test
  void paymentOnNoTierSessionIsRefusedToo() {
    removeTiers("CZ");
    final String id = checkoutTo("CZ", GOLD_1, 1);
    evaluate(id);

    assertEquals(409, pay(id).status());
    provider.verify(0, WireMock.postRequestedFor(WireMock.urlEqualTo(CHARGE)));
  }

  @Test
  void paymentStaysRefusedAfterTheHandoff() {
    configureTier("HU", "1000.00", HEAVY, PRICE);
    final String id = checkoutTo("HU", GOLD_1, 1);
    handoff(id, null);

    assertEquals(409, pay(id).status());
    provider.verify(0, WireMock.postRequestedFor(WireMock.urlEqualTo(CHARGE)));
  }

  @Test
  void paymentBeforeAnyEvaluationIsRefused() {
    configureTier("SI", "10000.00", HEAVY, PRICE);
    final String id = checkoutTo("SI", GOLD_1, 1);

    final Called called = pay(id);

    assertEquals(409, called.status());
    assertEquals("checkout.delivery-not-evaluated", called.body().get("code"));
    provider.verify(0, WireMock.postRequestedFor(WireMock.urlEqualTo(CHARGE)));
  }

  @Test
  void paymentOnSessionWithinTheCeilingsProceedsToTheProvider() {
    configureTier("LT", "10000.00", HEAVY, PRICE);
    final String id = checkoutTo("LT", GOLD_1, 1);
    evaluate(id);

    final Called called = pay(id);

    provider.verify(1, WireMock.postRequestedFor(WireMock.urlEqualTo(CHARGE)));
    assertEquals(200, called.status());
  }
}
