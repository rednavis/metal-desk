package com.rednavis.metaldesk.api.e2e;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.rednavis.metaldesk.api.checkout.confirmation.ConfirmationTestSupport;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.HttpMethod;
import tools.jackson.databind.json.JsonMapper;

/**
 * The harness of the Phase 3 end-to-end tests. It adds no second of anything: the MongoDB container
 * is the suite's shared one, the provider stub is {@link ConfirmationTestSupport}'s WireMock, the
 * market-data feed is the in-process fake, and mail goes to the in-process sender. What it adds is
 * the signed-in customer's side of a checkout, every call of which goes through the HTTP surface,
 * and the real {@code apps/admin} for the staff side.
 *
 * <p>Everything stays on loopback: the stub, the admin application and the database container are
 * all reached through {@code 127.0.0.1}.
 */
@ExtendWith(AdminExtension.class)
public class E2eTestSupport extends ConfirmationTestSupport {

  /** The session's address. */
  protected static final String ORDERS = "/api/orders";

  private static final JsonMapper JSON = JsonMapper.builder().build();

  /** A customer who is registered, verified and signed in. */
  protected record Shopper(String email, String token) {}

  /** Creates the base; subclasses are the tests. */
  protected E2eTestSupport() {
    super();
  }

  /**
   * The running {@code apps/admin}, started once for the run by {@link AdminExtension}.
   *
   * @return the application
   */
  protected static AdminHarnessTestSupport admin() {
    return AdminExtension.admin();
  }

  /**
   * Registers, verifies and signs in a customer.
   *
   * @return the customer
   */
  protected Shopper shopper() {
    final String email = freshEmail();
    registerAndVerify(email);
    return new Shopper(email, Objects.requireNonNull(signIn(email, PASSWORD)));
  }

  /**
   * Starts a checkout for a signed-in customer with one product in the cart.
   *
   * @param shopper the customer
   * @param product the product
   * @return the session id
   */
  protected String startCheckout(Shopper shopper, String product) {
    final Called cart = add(null, shopper.token(), product);
    return checkoutId(call(HttpMethod.POST, SESSIONS, cart.cookie(), shopper.token(), null));
  }

  /**
   * Submits step 1 for a signed-in customer, to a destination.
   *
   * @param id the session
   * @param shopper the customer
   * @param country the destination country
   * @return the response
   */
  protected Called step1(String id, Shopper shopper, String country) {
    return submit(id, shopper.token(), with(validForm(shopper.email()), "country", country));
  }

  /**
   * Evaluates the delivery, as the customer.
   *
   * @param id the session
   * @param shopper the customer
   * @return the response
   */
  protected Called evaluateAs(String id, Shopper shopper) {
    return call(HttpMethod.POST, SESSIONS + "/" + id + EVALUATE_PATH, null, shopper.token(), null);
  }

  /**
   * Hands the session to staff, as the customer.
   *
   * @param id the session
   * @param shopper the customer
   * @return the response
   */
  protected Called handoffAs(String id, Shopper shopper) {
    return call(HttpMethod.POST, SESSIONS + "/" + id + HANDOFF_PATH, null, shopper.token(), null);
  }

  /**
   * Chooses a payment method, as the customer.
   *
   * @param id the session
   * @param shopper the customer
   * @param method the method name
   * @return the response
   */
  protected Called selectAs(String id, Shopper shopper, String method) {
    return call(
        HttpMethod.PUT,
        SESSIONS + "/" + id + "/payment/method",
        null,
        shopper.token(),
        Map.of("method", method));
  }

  /**
   * The methods on offer, as the customer.
   *
   * @param id the session
   * @param shopper the customer
   * @return the response
   */
  protected Called methodsAs(String id, Shopper shopper) {
    return call(
        HttpMethod.GET, SESSIONS + "/" + id + "/payment/methods", null, shopper.token(), null);
  }

  /**
   * The overview, as the customer.
   *
   * @param id the session
   * @param shopper the customer
   * @return the response
   */
  protected Called overviewAs(String id, Shopper shopper) {
    return call(HttpMethod.GET, SESSIONS + "/" + id + "/overview", null, shopper.token(), null);
  }

  /**
   * The grand total on the overview, as decimal text.
   *
   * @param id the session
   * @param shopper the customer
   * @return the total
   */
  protected String totalAs(String id, Shopper shopper) {
    final Map<?, ?> totals = (Map<?, ?>) overviewAs(id, shopper).body().get("totals");
    return (String) ((Map<?, ?>) totals.get("grandTotal")).get("amount");
  }

  /**
   * Pays, confirming the total the overview shows.
   *
   * @param id the session
   * @param shopper the customer
   * @return the response
   */
  protected Called payAs(String id, Shopper shopper) {
    return call(
        HttpMethod.POST,
        SESSIONS + "/" + id + "/payment/execute",
        null,
        shopper.token(),
        Map.of("confirmedTotal", totalAs(id, shopper)));
  }

  /**
   * The confirmation, as the customer.
   *
   * @param id the session
   * @param shopper the customer
   * @return the response
   */
  protected Called confirmAs(String id, Shopper shopper) {
    return call(HttpMethod.GET, SESSIONS + "/" + id + "/confirmation", null, shopper.token(), null);
  }

  /**
   * The customer's order history.
   *
   * @param shopper the customer
   * @return the response
   */
  protected Called history(Shopper shopper) {
    return call(HttpMethod.GET, ORDERS, null, shopper.token(), null);
  }

  /**
   * One order of the customer's history.
   *
   * @param shopper the customer
   * @param number the order number
   * @return the response
   */
  protected Called historyOf(Shopper shopper, String number) {
    return call(HttpMethod.GET, ORDERS + "/" + number, null, shopper.token(), null);
  }

  /**
   * The status the history shows for an order.
   *
   * @param shopper the customer
   * @param number the order number
   * @return the status name
   */
  protected String statusInHistory(Shopper shopper, String number) {
    final Object orders = history(shopper).body().get("orders");
    return ((List<?>) orders)
        .stream()
            .map(row -> (Map<?, ?>) row)
            .filter(row -> number.equals(row.get("orderNumber")))
            .map(row -> (String) row.get("status"))
            .findFirst()
            .orElseThrow();
  }

  /**
   * Computes BR-5 from the lines of an order as the history reports them, without using anything
   * the application computes: the sum of unit price times quantity, plus the tax the lines carry,
   * plus the delivery cost.
   *
   * @param detail the order detail response
   * @return the total
   */
  protected static BigDecimal br5(Called detail) {
    BigDecimal goods = BigDecimal.ZERO;
    BigDecimal tax = BigDecimal.ZERO;
    for (final Object line : (List<?>) detail.body().get("lines")) {
      final Map<?, ?> row = (Map<?, ?>) line;
      goods =
          goods.add(
              amount(row.get("unitPrice"))
                  .multiply(BigDecimal.valueOf((Integer) row.get("quantity"))));
      tax = tax.add(amount(row.get("lineTax")));
    }
    final BigDecimal delivery = amount(((Map<?, ?>) detail.body().get("totals")).get("delivery"));
    return goods.add(tax).add(delivery);
  }

  /**
   * A price view's amount.
   *
   * @param price a {@code {amount, currency}} object
   * @return the amount
   */
  protected static BigDecimal amount(Object price) {
    return new BigDecimal((String) ((Map<?, ?>) price).get("amount"));
  }

  /**
   * The amounts the gateway stub was asked to authorise, read from its request journal.
   *
   * @return the amounts, in request order
   */
  protected static List<BigDecimal> chargedAmounts() {
    return providers().findAll(WireMock.postRequestedFor(WireMock.urlPathEqualTo(GATEWAY))).stream()
        .map(E2eTestSupport::amountOf)
        .toList();
  }

  private static BigDecimal amountOf(LoggedRequest request) {
    try {
      return new BigDecimal(
          (String) JSON.readValue(request.getBodyAsString(), Map.class).get("amount"));
    } catch (tools.jackson.core.JacksonException e) {
      throw new IllegalStateException("unreadable provider request", e);
    }
  }

  /**
   * How many requests the provider stub has received, of any kind.
   *
   * @return the count
   */
  protected static int providerRequests() {
    return providers().getAllServeEvents().size();
  }
}
