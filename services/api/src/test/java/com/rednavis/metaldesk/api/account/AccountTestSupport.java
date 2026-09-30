package com.rednavis.metaldesk.api.account;

import com.rednavis.metaldesk.api.auth.ClientAddressResolver;
import com.rednavis.metaldesk.api.auth.SignInThrottle;
import com.rednavis.metaldesk.api.persistence.MongoTestSupport;
import com.rednavis.metaldesk.mail.TransactionalMail;
import com.rednavis.metaldesk.mail.fake.InProcessMailSender;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;

/** The base of the account-flow tests: a client, the recording mail sender, and small helpers. */
@AutoConfigureWebTestClient
public class AccountTestSupport extends MongoTestSupport {

  /** The password every test account is registered with. */
  protected static final String PASSWORD = "a-good-password";

  /** The header the fixed correlation id is sent in. */
  protected static final String CORRELATION = "X-Correlation-Id";

  @Autowired protected WebTestClient client;
  @Autowired protected InProcessMailSender mail;
  @Autowired private SignInThrottle throttle;

  /** Creates the base; subclasses are the tests. */
  protected AccountTestSupport() {
    super();
  }

  /**
   * A fresh email address, so tests sharing one database cannot see each other's accounts.
   *
   * @return the address
   */
  protected String freshEmail() {
    return "acct-" + UUID.randomUUID() + "@example.com";
  }

  /**
   * Registers an account and returns the response.
   *
   * @param email the address
   * @return the exchange result with the body as a map
   */
  protected Registered register(String email) {
    final EntityExchangeResult<Map<String, Object>> result =
        client
            .post()
            .uri("/api/account/register")
            .bodyValue(Map.of("name", "Ann Example", "email", email, "password", PASSWORD))
            .exchange()
            .expectBody(
                new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
            .returnResult();
    return new Registered(
        result.getStatus().value(), Objects.requireNonNull(result.getResponseBody()));
  }

  /**
   * The answer to a registration.
   *
   * @param status the HTTP status
   * @param body the response body
   */
  protected record Registered(int status, Map<String, Object> body) {

    /** Copies the body, so the record cannot be changed through it. */
    protected Registered {
      body = Map.copyOf(body);
    }
  }

  /**
   * The mail sent to an address.
   *
   * @param email the recipient
   * @return the mail, oldest first
   */
  protected List<TransactionalMail> mailTo(String email) {
    return MailInspector.to(mail, email);
  }

  /**
   * Registers an account, confirms its email, and returns the customer id via a sign-in token.
   *
   * @param email the address
   */
  protected void registerAndVerify(String email) {
    final Map<String, Object> accepted = register(email).body();
    client
        .post()
        .uri("/api/account/verify-email")
        .bodyValue(
            Map.of(
                "reference",
                accepted.get("reference"),
                "code",
                MailInspector.typedCode(mailTo(email).get(0))))
        .exchange()
        .expectStatus()
        .isOk();
  }

  /**
   * Signs in and returns the access token.
   *
   * @param identifier the email or phone
   * @param password the password
   * @return the token, or null if the sign-in did not succeed
   */
  protected String signIn(String identifier, String password) {
    throttle.recordSuccess(ClientAddressResolver.UNKNOWN);
    final Map<String, Object> body =
        client
            .post()
            .uri("/api/auth/sign-in")
            .bodyValue(Map.of("identifier", identifier, "password", password))
            .exchange()
            .expectBody(
                new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
            .returnResult()
            .getResponseBody();
    return (String) Objects.requireNonNull(body).get("accessToken");
  }
}
