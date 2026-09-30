package com.rednavis.metaldesk.api.checkout.payment;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.rednavis.metaldesk.api.checkout.delivery.DeliveryTestSupport;
import com.rednavis.metaldesk.api.persistence.repository.OrderRepository;
import com.rednavis.metaldesk.persistence.document.OrderDocument;
import com.rednavis.metaldesk.share.domain.catalog.TaxCategory;
import com.rednavis.metaldesk.share.domain.payment.PaymentMethod;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The base of the payment tests: one WireMock server plays both the gateway and the wallet provider
 * (ADR-0002), and helpers drive a checkout to the point of paying. Each test uses a destination
 * country of its own, because the fulfillment tiers live in a database the tests share.
 */
public class PaymentTestSupport extends DeliveryTestSupport {

  /** The gateway's authorisation path on the stub. */
  protected static final String GATEWAY = "/v1/payments";

  /** The wallet's authorisation path on the stub. */
  protected static final String WALLET = "/v1/wallet/payments";

  /** A product whose order is well under the BR-9 ceiling. */
  protected static final String SMALL = "pay-small";

  private static final List<String> FIRST =
      List.of(
          "AD", "AE", "AF", "AG", "AL", "AM", "AO", "AR", "AU", "AZ", "BA", "BB", "BD", "BF", "BH",
          "BI", "BJ", "BN", "BO", "BR", "BS", "BT", "BW", "BY", "BZ", "CA", "CD", "CF", "CG", "CI",
          "CL", "CM", "CN", "CO", "CR", "CU", "CV", "CY", "DJ", "DM", "DO", "DZ", "EC", "EG", "ER",
          "ET", "FJ", "GA", "GD", "GE");

  /** Countries other tests configure tiers for, which a unique region here must not reuse. */
  private static final Set<String> TAKEN =
      Set.of(
          "SI", "NL", "SE", "LT", "BE", "SK", "RO", "PT", "PL", "IT", "IE", "HU", "HR", "FR", "ES",
          "CZ", "CH", "BG", "AT", "MT", "LV", "LU", "GR", "DE", "DK", "FI", "NO", "US", "GB", "EE");

  private static final List<String> COUNTRIES = regionPool();
  private static final AtomicInteger NEXT = new AtomicInteger();
  private static final WireMockServer SERVER =
      new WireMockServer(WireMockConfiguration.options().dynamicPort());

  static {
    SERVER.start();
  }

  @Autowired protected OrderRepository orderRepo;
  @Autowired protected RecordingSettlement settlement;
  @Autowired protected RecordingInvoiceSink invoices;

  /** Creates the base; subclasses are the tests. */
  protected PaymentTestSupport() {
    super();
  }

  /**
   * Points both providers at the stub, with a short timeout.
   *
   * @param registry the property registry
   */
  @DynamicPropertySource
  public static void registerProviders(DynamicPropertyRegistry registry) {
    final String base = "http://127.0.0.1:" + SERVER.port();
    registry.add("metaldesk.payments.gateway.base-url", () -> base);
    registry.add("metaldesk.payments.wallet.base-url", () -> base);
    registry.add("metaldesk.payments.gateway.timeout", () -> "1s");
    registry.add("metaldesk.payments.wallet.timeout", () -> "1s");
  }

  /**
   * The provider stub.
   *
   * @return the WireMock server
   */
  protected static WireMockServer providers() {
    return SERVER;
  }

  /**
   * A destination country no other test has used.
   *
   * @return a two-letter country code
   */
  protected static String nextRegion() {
    return COUNTRIES.get(NEXT.getAndIncrement());
  }

  private static List<String> regionPool() {
    final List<String> all = new ArrayList<>(FIRST);
    Arrays.stream(Locale.getISOCountries())
        .filter(code -> "GE".compareTo(code) < 0 && !TAKEN.contains(code))
        .forEach(all::add);
    return List.copyOf(all);
  }

  /** Seeds the catalog, a small product, and forgets earlier requests to the stub. */
  protected void seedPaymentCatalog() {
    seedCatalog();
    seed.pricedProduct(
        SMALL, "Small gold bar", seed.category(BARS, TaxCategory.INVESTMENT_GRADE), "5");
    SERVER.resetAll();
  }

  /**
   * Makes the stub answer an authorisation.
   *
   * @param path the authorisation path
   * @param body the JSON answer
   */
  protected static void stubAuthorise(String path, String body) {
    SERVER.stubFor(WireMock.post(WireMock.urlPathEqualTo(path)).willReturn(WireMock.okJson(body)));
  }

  /**
   * Makes the stub answer a confirmation.
   *
   * @param reference the provider reference being confirmed
   * @param body the JSON answer
   */
  protected static void stubConfirm(String reference, String body) {
    SERVER.stubFor(
        WireMock.post(WireMock.urlPathEqualTo(GATEWAY + "/" + reference + "/confirm"))
            .willReturn(WireMock.okJson(body)));
  }

  /**
   * Makes the stub answer an authorisation only after a delay, so that it times out.
   *
   * @param path the authorisation path
   */
  protected static void stubSlow(String path) {
    SERVER.stubFor(
        WireMock.post(WireMock.urlPathEqualTo(path))
            .willReturn(
                WireMock.okJson("{\"status\":\"captured\",\"reference\":\"slow\"}")
                    .withFixedDelay(2500)));
  }

  /**
   * Drives a checkout to the point of paying: step 1, delivery evaluated within a wide tier, and a
   * payment method chosen.
   *
   * @param product the product in the cart
   * @param quantity how many
   * @param method the method to choose
   * @return the checkout id
   */
  protected String readyCheckout(String product, int quantity, PaymentMethod method) {
    final String region = nextRegion();
    configureWideTier(region);
    final String id = checkoutTo(region, product, quantity);
    evaluate(id);
    final Called chosen = select(id, method);
    if (chosen.status() != HttpStatus.OK.value()) {
      throw new IllegalStateException("could not select " + method + ": " + chosen.body());
    }
    return id;
  }

  /**
   * Configures one wide tier for a region, so nothing evaluated there is handed to a manager.
   *
   * @param region the two-letter country code
   */
  protected void configureWideTier(String region) {
    configureTier(region, "1000000.00", "100000", "10.00");
  }

  /**
   * A valid step-1 form that keeps the destination of an existing checkout.
   *
   * @param id the checkout id
   * @return a mutable form for the same country
   */
  protected Map<String, Object> formSameCountry(String id) {
    final Object country = ((Map<?, ?>) session(id, null).body().get("details")).get("country");
    return with(validForm(freshEmail()), "country", country);
  }

  /**
   * Chooses a payment method.
   *
   * @param id the checkout id
   * @param method the method
   * @return the response
   */
  protected Called select(String id, PaymentMethod method) {
    return call(
        HttpMethod.PUT,
        SESSIONS + "/" + id + "/payment/method",
        null,
        null,
        Map.of("method", method.name()));
  }

  /**
   * The methods on offer.
   *
   * @param id the checkout id
   * @return the response
   */
  protected Called methods(String id) {
    return call(HttpMethod.GET, SESSIONS + "/" + id + "/payment/methods", null, null, null);
  }

  /**
   * The overview.
   *
   * @param id the checkout id
   * @return the response
   */
  protected Called overview(String id) {
    return call(HttpMethod.GET, SESSIONS + "/" + id + "/overview", null, null, null);
  }

  /**
   * The grand total on the overview.
   *
   * @param id the checkout id
   * @return the amount as decimal text
   */
  protected String totalOf(String id) {
    final Map<?, ?> totals = (Map<?, ?>) overview(id).body().get("totals");
    return (String) ((Map<?, ?>) totals.get("grandTotal")).get("amount");
  }

  /**
   * Pays, confirming the total shown on the overview.
   *
   * @param id the checkout id
   * @return the response
   */
  protected Called pay(String id) {
    return pay(id, totalOf(id));
  }

  /**
   * Pays, confirming a given total.
   *
   * @param id the checkout id
   * @param confirmed the total to confirm
   * @return the response
   */
  protected Called pay(String id, String confirmed) {
    return call(
        HttpMethod.POST,
        SESSIONS + "/" + id + "/payment/execute",
        null,
        null,
        Map.of("confirmedTotal", confirmed));
  }

  /**
   * Finds the order a payment result refers to.
   *
   * @param result the payment response
   * @return the stored order
   */
  protected OrderDocument orderOf(Called result) {
    return Objects.requireNonNull(
        orderRepo.findByNumber((String) result.body().get("orderReference")).block());
  }
}
