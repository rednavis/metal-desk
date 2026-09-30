package com.rednavis.metaldesk.admin.quote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rednavis.metaldesk.admin.AdminTestSupport;
import com.rednavis.metaldesk.mail.MailTemplate;
import com.rednavis.metaldesk.mail.fake.InProcessMailSender;
import com.rednavis.metaldesk.share.domain.order.Order;
import com.rednavis.metaldesk.share.domain.order.OrderStatus;
import com.rednavis.metaldesk.share.domain.order.TransitionTrigger;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.client.RestTestClient.ResponseSpec;

/** Both outcomes of a handed-off order, through the state machine, with the price written. */
class QuoteApiTest extends AdminTestSupport {

  private static final String QUOTES = "/api/admin/quotes/";
  private static final String ID = "o-1";
  private static final String STATUS = "$.status";
  private static final String PRICE = "250.00";
  private static final String FUTURE = Instant.now().plusSeconds(86_400L * 30).toString();

  @Autowired private InProcessMailSender mail;

  @BeforeEach
  void clearMail() {
    mail.clear();
  }

  private Order handedOff() {
    saveCustomer();
    return save(
        orderAfter(ID, 1, TransitionTrigger.CHECKOUT_SUBMITTED, TransitionTrigger.TIER_EXCEEDED));
  }

  private static String terms(String price, String validUntil) {
    return terms(price, "Insured courier, signature required.", validUntil);
  }

  private static String terms(String price, String text, String validUntil) {
    return ("{\"deliveryPrice\":\"%s\",\"terms\":\"%s\",\"transitMinDays\":3,"
            + "\"transitMaxDays\":5,\"validUntil\":\"%s\"}")
        .formatted(price, text, validUntil);
  }

  private ResponseSpec putTerms(String id, String json) {
    return post(QUOTES + id + "/terms", json);
  }

  private Order stored() {
    return orderMapper.toDomain(orders.findById(ID).orElseThrow());
  }

  @Test
  void queueListsOnlyOrdersAwaitingQuote() {
    handedOff();
    save(orderAfter("o-2", 2, TransitionTrigger.CHECKOUT_SUBMITTED));

    get("/api/admin/quotes")
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.total")
        .isEqualTo(1)
        .jsonPath("$.items[0].id")
        .isEqualTo(ID);
    get(QUOTES + ID)
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.actions[0]")
        .isEqualTo("QUOTE_SET")
        .jsonPath("$.actions[1]")
        .isEqualTo("QUOTE_DECLINED");
  }

  @Test
  void settingTermsReturnsOrderToAwaitingPaymentWithTheHumanPrice() {
    final Order before = handedOff();

    putTerms(ID, terms(PRICE, FUTURE))
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath(STATUS)
        .isEqualTo("AWAITING_PAYMENT")
        .jsonPath("$.deliveryPrice")
        .isEqualTo(PRICE);

    final Order stored = stored();
    assertEquals(OrderStatus.AWAITING_PAYMENT, stored.status());
    assertEquals(PRICE, stored.quote().orElseThrow().cost().amount().toPlainString());
    assertEquals(QuoteService.MANAGER_TIER, stored.quote().orElseThrow().tierId().value());
    // The total the customer is asked to pay is the goods plus the human-set delivery price.
    assertEquals(
        before.totals().net().plus(before.totals().tax()).plus(stored.quote().orElseThrow().cost()),
        stored.totals().grandTotal());
    assertNotEquals(before.totals().grandTotal(), stored.totals().grandTotal());
    assertEquals("staff@example.com", quotes.findById(ID).orElseThrow().staff());
  }

  @Test
  void customerIsToldTheTerms() {
    handedOff();
    putTerms(ID, terms(PRICE, FUTURE)).expectStatus().isOk();

    assertEquals(1, mail.sentOf(MailTemplate.MANAGER_QUOTE_CUSTOMER).size());
    assertTrue(
        mail.sentOf(MailTemplate.MANAGER_QUOTE_CUSTOMER)
            .get(0)
            .mail()
            .body()
            .contains("Insured courier"));
  }

  @Test
  void decliningCancelsTheOrder() {
    handedOff();

    post(QUOTES + ID + "/decline")
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath(STATUS)
        .isEqualTo("CANCELLED");

    assertEquals(OrderStatus.CANCELLED, stored().status());
  }

  @Test
  void orderNotAwaitingQuoteIsConflict() {
    save(orderAfter(ID, 1, TransitionTrigger.CHECKOUT_SUBMITTED));
    saveCustomer();

    putTerms(ID, terms(PRICE, FUTURE)).expectStatus().isEqualTo(409);
    post(QUOTES + ID + "/decline").expectStatus().isEqualTo(409);
    assertEquals(OrderStatus.AWAITING_PAYMENT, stored().status());
  }

  @Test
  void termsCannotBeSetTwice() {
    handedOff();
    putTerms(ID, terms(PRICE, FUTURE)).expectStatus().isOk();

    putTerms(ID, terms("10.00", FUTURE)).expectStatus().isEqualTo(409);
    assertEquals(PRICE, stored().quote().orElseThrow().cost().amount().toPlainString());
  }

  @Test
  void refusesPriceTermsOrValidityThatMakeNoSense() {
    handedOff();

    putTerms(ID, terms("0", FUTURE)).expectStatus().isBadRequest();
    putTerms(ID, terms("-5.00", FUTURE)).expectStatus().isBadRequest();
    putTerms(ID, terms(PRICE, "2020-01-01T00:00:00Z"))
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("quote.validity-invalid");
    putTerms(ID, terms(PRICE, "  ", FUTURE)).expectStatus().isBadRequest();
    putTerms(ID, "{}").expectStatus().isBadRequest();
    assertEquals(OrderStatus.AWAITING_MANAGER_QUOTE, stored().status());
  }

  @Test
  void unknownOrderIsNotFound() {
    putTerms("nope", terms(PRICE, FUTURE)).expectStatus().isNotFound();
  }
}
