package com.rednavis.metaldesk.api.cart;

import com.rednavis.metaldesk.api.account.AccountTestSupport;
import com.rednavis.metaldesk.api.catalog.CatalogSeed;
import com.rednavis.metaldesk.api.persistence.AccountFixtures;
import com.rednavis.metaldesk.api.persistence.document.CustomerDocument;
import com.rednavis.metaldesk.api.persistence.mapper.ValueMapper;
import com.rednavis.metaldesk.api.persistence.repository.CustomerRepository;
import com.rednavis.metaldesk.share.domain.catalog.Category;
import com.rednavis.metaldesk.share.domain.catalog.TaxCategory;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * The base of the cart tests: calls the cart API with an optional cart cookie and bearer token, and
 * seeds a small catalog of priced and unpriced products.
 */
public class CartTestSupport extends AccountTestSupport {

  /** A zero-rated category, with a margin rule. */
  protected static final String BARS = "ct-bars";

  /** A standard-rated category, with a margin rule. */
  protected static final String STANDARD = "ct-std";

  /** A priced product in the zero-rated category. */
  protected static final String GOLD_1 = "ct-gold-1";

  /** Another priced product in the zero-rated category. */
  protected static final String GOLD_2 = "ct-gold-2";

  /** A priced product in the standard-rated category. */
  protected static final String TAXED = "ct-taxed";

  /** A product the catalog sells on request. */
  protected static final String ON_REQUEST = "ct-onreq";

  private static final Pattern CART_COOKIE = Pattern.compile(CartController.COOKIE + "=([^;]+)");

  private static final ParameterizedTypeReference<Map<String, Object>> MAP =
      new ParameterizedTypeReference<>() {};

  @Autowired protected CatalogSeed seed;
  @Autowired private CustomerRepository customerRepo;

  /** Creates the base; subclasses are the tests. */
  protected CartTestSupport() {
    super();
  }

  /** Seeds the catalog and a reference price, idempotently. */
  protected void seedCatalog() {
    final Category bars = seed.category(BARS, TaxCategory.INVESTMENT_GRADE);
    seed.marginForCategory(bars, "5");
    seed.pricedProduct(GOLD_1, "Cart gold one", bars);
    seed.pricedProduct(GOLD_2, "Cart gold two", bars);
    seed.unpricedProduct(ON_REQUEST, "Cart on request", bars);
    final Category standard = seed.category(STANDARD, TaxCategory.STANDARD);
    seed.marginForCategory(standard, "10");
    seed.pricedProduct(TAXED, "Cart taxed", standard);
    seed.observeGold("60.00");
  }

  /**
   * The answer to a cart call.
   *
   * @param status the HTTP status
   * @param body the response body, empty for a 204
   * @param cookie the cart cookie the response set, or null
   */
  protected record Called(int status, Map<String, Object> body, String cookie) {

    /** Copies the body, so the record cannot be changed through it. */
    protected Called {
      body = Map.copyOf(body);
    }

    /**
     * The lines of a cart view.
     *
     * @return the lines
     */
    @SuppressWarnings("unchecked")
    protected List<Map<String, Object>> lines() {
      return (List<Map<String, Object>>) body.get("lines");
    }

    /**
     * The quantity of a product's line.
     *
     * @param productId the product
     * @return the quantity, or 0 if there is no such line
     */
    protected int quantityOf(String productId) {
      return lines().stream()
          .filter(line -> productId.equals(line.get("productId")))
          .mapToInt(line -> (Integer) line.get("quantity"))
          .findFirst()
          .orElse(0);
    }
  }

  /**
   * Calls the cart API.
   *
   * @param method the HTTP method
   * @param uri the path
   * @param cookie the cart cookie to send, or null
   * @param token the bearer token to send, or null
   * @param body the request body, or null
   * @return the response
   */
  protected Called call(HttpMethod method, String uri, String cookie, String token, Object body) {
    final WebTestClient.RequestBodySpec spec =
        client
            .method(method)
            .uri(uri)
            .headers(
                headers -> {
                  if (token != null) {
                    headers.setBearerAuth(token);
                  }
                })
            .cookies(
                cookies -> {
                  if (cookie != null) {
                    cookies.add(CartController.COOKIE, cookie);
                  }
                });
    final WebTestClient.RequestHeadersSpec<?> request = body == null ? spec : spec.bodyValue(body);
    final EntityExchangeResult<Map<String, Object>> result =
        request.exchange().expectBody(MAP).returnResult();
    final String set = cookieIn(result.getResponseHeaders().get(HttpHeaders.SET_COOKIE));
    final Map<String, Object> answer =
        result.getResponseBody() == null ? Map.of() : result.getResponseBody();
    return new Called(result.getStatus().value(), answer, set);
  }

  private static String cookieIn(List<String> headers) {
    return headers == null
        ? null
        : headers.stream()
            .map(CART_COOKIE::matcher)
            .filter(Matcher::find)
            .map(matcher -> matcher.group(1))
            .findFirst()
            .orElse(null);
  }

  /**
   * Adds a product to a cart.
   *
   * @param cookie the cart cookie, or null
   * @param token the bearer token, or null
   * @param productId the product
   * @return the response
   */
  protected Called add(String cookie, String token, String productId) {
    return call(HttpMethod.POST, "/api/cart/lines", cookie, token, Map.of("productId", productId));
  }

  /**
   * Reads a cart.
   *
   * @param cookie the cart cookie, or null
   * @param token the bearer token, or null
   * @return the response
   */
  protected Called read(String cookie, String token) {
    return call(HttpMethod.GET, "/api/cart", cookie, token, null);
  }

  /**
   * Sets a line's quantity.
   *
   * @param cookie the cart cookie, or null
   * @param productId the product
   * @param quantity the quantity
   * @return the response
   */
  protected Called changeQuantity(String cookie, String productId, int quantity) {
    return call(
        HttpMethod.PUT, "/api/cart/lines/" + productId, cookie, null, Map.of("quantity", quantity));
  }

  /**
   * Finds a customer's id by email.
   *
   * @param email the address
   * @return the id
   */
  protected String customerId(String email) {
    return Objects.requireNonNull(customerRepo.findByEmail(email).block()).id();
  }

  /**
   * Saves the fixture delivery address on a customer.
   *
   * @param customerId the customer's id
   */
  protected void saveDeliveryAddress(String customerId) {
    final CustomerDocument found =
        Objects.requireNonNull(customerRepo.findById(customerId).block());
    final CustomerDocument withAddress =
        new CustomerDocument(
            found.id(),
            found.name(),
            found.email(),
            found.phone(),
            List.of(ValueMapper.addressToDocument(AccountFixtures.deliveryAddress())),
            found.verification());
    customerRepo.save(withAddress).block();
  }

  /**
   * Registers a customer, verifies them and signs them in.
   *
   * @return the customer's token
   */
  protected String signedInToken() {
    final String email = freshEmail();
    registerAndVerify(email);
    return Objects.requireNonNull(signIn(email, PASSWORD));
  }
}
