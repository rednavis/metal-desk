package com.rednavis.metaldesk.api.account;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.rednavis.metaldesk.api.persistence.repository.CustomerRepository;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Account switching (BRD FR-2.5): a new token for a permitted target, 403 for anything else. */
class AccountSwitchTest extends AccountTestSupport {

  private static final String SWITCH = "/api/account/switch";
  private static final String ME = "/api/auth/me";

  @Autowired private CustomerRepository customers;
  @Autowired private AccountAccessService access;

  private String idOf(String email) {
    return Objects.requireNonNull(customers.findByEmail(email).block()).id();
  }

  private String switchTo(String token, String target, int status) {
    return client
        .post()
        .uri(SWITCH)
        .headers(headers -> headers.setBearerAuth(token))
        .bodyValue(Map.of("targetCustomerId", target))
        .exchange()
        .expectStatus()
        .isEqualTo(status)
        .expectBody(String.class)
        .returnResult()
        .getResponseBody();
  }

  private void assertActingAs(String token, String customerId) {
    client
        .get()
        .uri(ME)
        .headers(headers -> headers.setBearerAuth(token))
        .exchange()
        .expectBody()
        .jsonPath("$.customerId")
        .isEqualTo(customerId);
  }

  private String tokenFrom(String responseBody) {
    return Objects.requireNonNull(responseBody)
        .replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1");
  }

  @Test
  void switchingToPermittedAccountReturnsNewTokenForTheTarget() {
    final String firstEmail = freshEmail();
    final String secondEmail = freshEmail();
    registerAndVerify(firstEmail);
    registerAndVerify(secondEmail);
    final String first = idOf(firstEmail);
    final String second = idOf(secondEmail);
    access.link(first, second).block();
    final String original = signIn(firstEmail, PASSWORD);

    final String switched = tokenFrom(switchTo(original, second, 200));

    assertNotEquals(original, switched);
    assertActingAs(switched, second);
    assertActingAs(original, first);
  }

  @Test
  void switchedTokenCanSwitchBack() {
    final String firstEmail = freshEmail();
    final String secondEmail = freshEmail();
    registerAndVerify(firstEmail);
    registerAndVerify(secondEmail);
    final String first = idOf(firstEmail);
    final String second = idOf(secondEmail);
    access.link(first, second).block();

    final String asSecond = tokenFrom(switchTo(signIn(firstEmail, PASSWORD), second, 200));
    final String backToFirst = tokenFrom(switchTo(asSecond, first, 200));

    assertActingAs(backToFirst, first);
  }

  @Test
  void switchingToAnAccountOutsideTheGroupIsRefused() {
    final String firstEmail = freshEmail();
    final String strangerEmail = freshEmail();
    registerAndVerify(firstEmail);
    registerAndVerify(strangerEmail);

    final String body = switchTo(signIn(firstEmail, PASSWORD), idOf(strangerEmail), 403);

    org.junit.jupiter.api.Assertions.assertTrue(
        Objects.requireNonNull(body).contains("account.switch-denied"), body);
  }

  @Test
  void targetThatDoesNotExistIsRefusedTheSameWay() {
    final String email = freshEmail();
    registerAndVerify(email);
    final String token = signIn(email, PASSWORD);

    final String unknown = switchTo(token, "no-such-customer", 403);
    final String stranger = switchTo(token, idOf(email) + "-x", 403);

    org.junit.jupiter.api.Assertions.assertTrue(
        Objects.requireNonNull(unknown).contains("account.switch-denied"));
    org.junit.jupiter.api.Assertions.assertTrue(
        Objects.requireNonNull(stranger).contains("account.switch-denied"));
  }

  @Test
  void switchingToYourselfIsAllowed() {
    final String email = freshEmail();
    registerAndVerify(email);

    switchTo(signIn(email, PASSWORD), idOf(email), 200);
  }

  @Test
  void switchingNeedsToken() {
    client
        .post()
        .uri(SWITCH)
        .bodyValue(Map.of("targetCustomerId", "anyone"))
        .exchange()
        .expectStatus()
        .isUnauthorized();
  }

  @Test
  void linkingMergesExistingGroups() {
    final String[] ids = {
      "grp-a-" + freshEmail(),
      "grp-b-" + freshEmail(),
      "grp-c-" + freshEmail(),
      "grp-d-" + freshEmail()
    };
    access.link(ids[0], ids[1]).block();
    access.link(ids[2], ids[3]).block();

    access.link(ids[1], ids[2]).block();

    org.junit.jupiter.api.Assertions.assertEquals(
        java.util.Set.of(ids), access.accessibleTo(ids[3]).block());
  }

  @Test
  void customerInNoGroupCanActOnlyAsThemselves() {
    org.junit.jupiter.api.Assertions.assertEquals(
        java.util.Set.of("loner"), access.accessibleTo("loner").block());
  }
}
