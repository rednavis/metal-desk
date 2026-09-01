package com.rednavis.metaldesk.api.checkout;

import com.rednavis.metaldesk.api.cart.CartTestSupport;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpMethod;

/**
 * The base of the checkout tests: starts checkouts, submits step 1, and builds a valid step-1 form
 * that a test then spoils in exactly one way.
 */
public class CheckoutTestSupport extends CartTestSupport {

  /** The privacy policy version the test application requires. */
  protected static final String POLICY = "2026-10";

  /** The step-1 path suffix. */
  protected static final String STEP1 = "/step1";

  /** The base path of checkout sessions. */
  protected static final String SESSIONS = "/api/checkout/sessions";

  /** The name of the violations array in an error body. */
  protected static final String VIOLATIONS_KEY = "violations";

  /** Creates the base; subclasses are the tests. */
  protected CheckoutTestSupport() {
    super();
  }

  /**
   * A complete, valid step-1 form for a guest.
   *
   * @param email the email address to use
   * @return a mutable form
   */
  protected Map<String, Object> validForm(String email) {
    final Map<String, Object> form = new ConcurrentHashMap<>();
    form.put("name", "Ann Example");
    form.put("contact", new ConcurrentHashMap<>(Map.of("email", email, "phone", "+49 30 1234567")));
    form.put("street", "1 Main Street");
    form.put("city", "Berlin");
    form.put("country", "de");
    form.put("postalCode", "10115");
    form.put("privacyPolicyAccepted", true);
    form.put("policyVersion", POLICY);
    return form;
  }

  /**
   * Removes a field, or a nested one given as {@code parent.child}.
   *
   * @param form the form
   * @param path the field
   * @return the same form, for chaining
   */
  @SuppressWarnings("unchecked")
  protected Map<String, Object> without(Map<String, Object> form, String path) {
    final int dot = path.indexOf('.');
    if (dot < 0) {
      form.remove(path);
    } else {
      ((Map<String, Object>) form.get(path.substring(0, dot))).remove(path.substring(dot + 1));
    }
    return form;
  }

  /**
   * Sets a field, or a nested one given as {@code parent.child}.
   *
   * @param form the form
   * @param path the field
   * @param value the value
   * @return the same form, for chaining
   */
  @SuppressWarnings("unchecked")
  protected Map<String, Object> with(Map<String, Object> form, String path, Object value) {
    final int dot = path.indexOf('.');
    if (dot < 0) {
      form.put(path, value);
    } else {
      final String parent = path.substring(0, dot);
      final Map<String, Object> nested =
          form.get(parent) == null
              ? new ConcurrentHashMap<>()
              : new ConcurrentHashMap<>((Map<String, Object>) form.get(parent));
      nested.put(path.substring(dot + 1), value);
      form.put(parent, nested);
    }
    return form;
  }

  /**
   * Puts a priced product in a fresh cart and starts a checkout from it.
   *
   * @param token the bearer token, or null for a guest
   * @return the response of starting the checkout
   */
  protected Called startFromCart(String token) {
    final Called cart = add(null, token, GOLD_1);
    return call(HttpMethod.POST, SESSIONS, cart.cookie(), token, null);
  }

  /**
   * The id of the session in a start response.
   *
   * @param started the response
   * @return the checkout id
   */
  protected String checkoutId(Called started) {
    return (String) Objects.requireNonNull(started.body().get("checkoutId"));
  }

  /**
   * Submits step 1.
   *
   * @param checkoutId the session's id
   * @param token the bearer token, or null
   * @param form the form
   * @return the response
   */
  protected Called submit(String checkoutId, String token, Map<String, Object> form) {
    return call(HttpMethod.PUT, SESSIONS + "/" + checkoutId + STEP1, null, token, form);
  }

  /**
   * Reads a session.
   *
   * @param checkoutId the session's id
   * @param token the bearer token, or null
   * @return the response
   */
  protected Called session(String checkoutId, String token) {
    return call(HttpMethod.GET, SESSIONS + "/" + checkoutId, null, token, null);
  }

  /**
   * The violations of an error response.
   *
   * @param called the response
   * @return the violations, each with {@code field}, {@code code} and {@code message}
   */
  @SuppressWarnings("unchecked")
  protected List<Map<String, Object>> violations(Called called) {
    return (List<Map<String, Object>>) called.body().get(VIOLATIONS_KEY);
  }

  /**
   * Finds the violation of a field.
   *
   * @param called the response
   * @param field the field
   * @return its code, or null if the field has no violation
   */
  protected String violationCode(Called called, String field) {
    return violations(called).stream()
        .filter(violation -> field.equals(violation.get("field")))
        .map(violation -> (String) violation.get("code"))
        .findFirst()
        .orElse(null);
  }
}
