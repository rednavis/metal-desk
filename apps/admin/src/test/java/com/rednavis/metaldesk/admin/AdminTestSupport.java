package com.rednavis.metaldesk.admin;

import com.rednavis.metaldesk.admin.persistence.CustomerRepository;
import com.rednavis.metaldesk.admin.persistence.ManagerQuoteRepository;
import com.rednavis.metaldesk.admin.persistence.OrderRepository;
import com.rednavis.metaldesk.admin.persistence.ProductRepository;
import com.rednavis.metaldesk.admin.persistence.ShipmentRepository;
import com.rednavis.metaldesk.admin.persistence.TierRepository;
import com.rednavis.metaldesk.admin.security.StaffPrincipal;
import com.rednavis.metaldesk.admin.security.TokenIssuer;
import com.rednavis.metaldesk.persistence.fixtures.AccountFixtures;
import com.rednavis.metaldesk.persistence.fixtures.OrderFixtures;
import com.rednavis.metaldesk.persistence.mapper.CustomerMapper;
import com.rednavis.metaldesk.persistence.mapper.OrderMapper;
import com.rednavis.metaldesk.persistence.testing.SharedMongo;
import com.rednavis.metaldesk.share.domain.order.Order;
import com.rednavis.metaldesk.share.domain.order.OrderTransitions;
import com.rednavis.metaldesk.share.domain.order.TransitionTrigger;
import com.rednavis.metaldesk.share.domain.user.UserRole;
import java.time.Instant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * The base of every admin test that needs the real stack: the suite's shared MongoDB container, an
 * empty database for each test, and helpers to seed orders.
 */
@SpringBootTest
@AutoConfigureMockMvc
public class AdminTestSupport {

  /** The login the helpers act as. */
  protected static final String STAFF_LOGIN = "staff";

  /** The instant the fixtures treat as now. */
  protected static final Instant NOW = OrderFixtures.NOW;

  @Autowired private MockMvc mvc;
  @Autowired private TokenIssuer tokenIssuer;

  /** A client that calls the application through MockMvc, as a signed-in staff member. */
  protected RestTestClient client;

  @Autowired protected TierRepository tiers;
  @Autowired protected OrderRepository orders;
  @Autowired protected ShipmentRepository shipments;
  @Autowired protected ManagerQuoteRepository quotes;
  @Autowired protected CustomerRepository customers;
  @Autowired protected ProductRepository products;
  @Autowired protected OrderMapper orderMapper;
  @Autowired protected CustomerMapper customerMapper;

  /**
   * Points the application at the shared container.
   *
   * @param registry the registry
   */
  @DynamicPropertySource
  public static void mongoProperties(DynamicPropertyRegistry registry) {
    SharedMongo.registerProperties(registry);
  }

  /** Records which container this test class uses. */
  @BeforeAll
  public static void recordContainer() {
    SharedMongo.recordUse();
  }

  /** Starts every test from an empty database and binds the client. */
  @BeforeEach
  public void emptyDatabase() {
    client = RestTestClient.bindTo(mvc).build();
    tiers.deleteAll();
    orders.deleteAll();
    shipments.deleteAll();
    quotes.deleteAll();
    customers.deleteAll();
    products.deleteAll();
  }

  /**
   * A bearer header value for a valid token of a user with the given role. The user need not exist
   * in the database: a token is checked by its signature, not by a lookup.
   *
   * @param role the role the token carries
   * @return the {@code Authorization} header value
   */
  protected String bearer(UserRole role) {
    return "Bearer "
        + tokenIssuer.issue(new StaffPrincipal("user-" + role, STAFF_LOGIN, role)).value();
  }

  /**
   * GETs as staff.
   *
   * @param uri the path and query
   * @return the response
   */
  protected RestTestClient.ResponseSpec get(String uri) {
    return client
        .get()
        .uri(uri)
        .header(HttpHeaders.AUTHORIZATION, bearer(UserRole.MANAGER))
        .exchange();
  }

  /**
   * POSTs a JSON body as staff.
   *
   * @param uri the path
   * @param json the body
   * @return the response
   */
  protected RestTestClient.ResponseSpec post(String uri, String json) {
    return client
        .post()
        .uri(uri)
        .header(HttpHeaders.AUTHORIZATION, bearer(UserRole.MANAGER))
        .contentType(MediaType.APPLICATION_JSON)
        .body(json)
        .exchange();
  }

  /**
   * POSTs with no body as staff.
   *
   * @param uri the path
   * @return the response
   */
  protected RestTestClient.ResponseSpec post(String uri) {
    return client
        .post()
        .uri(uri)
        .header(HttpHeaders.AUTHORIZATION, bearer(UserRole.MANAGER))
        .exchange();
  }

  /**
   * PUTs a JSON body as staff.
   *
   * @param uri the path
   * @param json the body
   * @return the response
   */
  protected RestTestClient.ResponseSpec put(String uri, String json) {
    return client
        .put()
        .uri(uri)
        .header(HttpHeaders.AUTHORIZATION, bearer(UserRole.MANAGER))
        .contentType(MediaType.APPLICATION_JSON)
        .body(json)
        .exchange();
  }

  /**
   * DELETEs as staff.
   *
   * @param uri the path
   * @return the response
   */
  protected RestTestClient.ResponseSpec delete(String uri) {
    return client
        .delete()
        .uri(uri)
        .header(HttpHeaders.AUTHORIZATION, bearer(UserRole.MANAGER))
        .exchange();
  }

  /**
   * Stores an order.
   *
   * @param order the order
   * @return the same order
   */
  protected Order save(Order order) {
    orders.save(orderMapper.toDocument(order));
    return order;
  }

  /** Stores the customer the fixture orders belong to. */
  protected void saveCustomer() {
    customers.save(
        customerMapper.toDocument(AccountFixtures.customer("cust-1", "ann@example.com", null)));
  }

  /**
   * Builds an order in some status by firing triggers from a new one, as the real flow does.
   *
   * @param id the order id
   * @param sequence the order number sequence
   * @param triggers the triggers to fire, in order
   * @return the order, not yet stored
   */
  protected static Order orderAfter(String id, int sequence, TransitionTrigger... triggers) {
    Order order = OrderFixtures.newOrder(id, OrderFixtures.number(sequence));
    for (final TransitionTrigger trigger : triggers) {
      order = OrderTransitions.advance(order, trigger, NOW);
    }
    return order;
  }
}
