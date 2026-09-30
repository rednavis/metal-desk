package com.rednavis.metaldesk.api.checkout.confirmation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rednavis.metaldesk.api.persistence.document.OrderDocument;
import com.rednavis.metaldesk.api.persistence.mapper.OrderMapper;
import com.rednavis.metaldesk.mail.MailTemplate;
import com.rednavis.metaldesk.mail.TransactionalMail;
import com.rednavis.metaldesk.share.domain.customer.EmailAddress;
import com.rednavis.metaldesk.share.domain.order.Order;
import com.rednavis.metaldesk.share.domain.payment.PaymentMethod;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

/**
 * The confirmation after a captured payment and after a manager handoff (BRD FR-8.1), and that it
 * mails each party once however often it is asked for.
 */
class ConfirmationTest extends ConfirmationTestSupport {

  private static final String KIND = "kind";

  @Autowired private ConfirmationSettlement settlement;
  @Autowired private OrderMapper mapper;

  @BeforeEach
  void seedData() {
    seedPaymentCatalog();
  }

  @Test
  void capturedPaymentSendsOneConfirmationToTheCustomerAndOneNotificationToStaff() {
    final String id = paidCheckout();

    final Called confirmed = confirm(id);

    assertEquals(200, confirmed.status());
    assertEquals("PAID", confirmed.body().get(KIND));
    final String number = (String) confirmed.body().get("orderNumber");
    final List<TransactionalMail> toCustomer =
        mailsOf(MailTemplate.ORDER_CONFIRMATION_CUSTOMER, number);
    assertEquals(1, toCustomer.size());
    assertEquals(List.of(new EmailAddress(emailOf(number))), toCustomer.get(0).to());
    final List<TransactionalMail> toStaff = mailsOf(MailTemplate.ORDER_NOTIFICATION_STAFF, number);
    assertEquals(1, toStaff.size());
    assertEquals(List.of(new EmailAddress(properties.staffEmail())), toStaff.get(0).to());
  }

  @Test
  void confirmingAgainSendsNothingMore() {
    final String id = paidCheckout();
    final String number = numberOf(id);

    confirm(id);
    confirm(id);

    assertEquals(1, mailsOf(MailTemplate.ORDER_CONFIRMATION_CUSTOMER, number).size());
    assertEquals(1, mailsOf(MailTemplate.ORDER_NOTIFICATION_STAFF, number).size());
  }

  @Test
  void duplicateSettlementFromTheProviderDoesNotMailTwice() {
    final String id = paidCheckout();
    final String number = numberOf(id);
    final Order order =
        mapper.toDomain(Objects.requireNonNull(orderRepo.findByNumber(number).block()));

    settlement.paid(order, Locale.ENGLISH).block();
    settlement.paid(order, Locale.ENGLISH).block();
    confirm(id);

    assertEquals(1, mailsOf(MailTemplate.ORDER_CONFIRMATION_CUSTOMER, number).size());
  }

  @Test
  void confirmationIsWrittenInTheLanguageTheCustomerPaidIn() {
    stubAuthorise(GATEWAY, CAPTURED_BODY);
    final String id = readyCheckout(SMALL, 1, PaymentMethod.CARD);
    call(
        HttpMethod.POST,
        SESSIONS + "/" + id + "/payment/execute",
        null,
        null,
        Map.of("confirmedTotal", totalOf(id), "locale", "de"));

    final String number = numberOf(id);

    assertEquals(
        Locale.GERMAN, mailsOf(MailTemplate.ORDER_CONFIRMATION_CUSTOMER, number).get(0).locale());
  }

  @Test
  void handoffGetsConfirmationWithTheOneOrderNumberItAlreadyHas() {
    final String id = handedOffCheckout();
    final String reference = numberOf(id);

    final Called confirmed = confirm(id);

    assertEquals(200, confirmed.status());
    assertEquals("MANAGER_QUOTE", confirmed.body().get(KIND));
    assertEquals(reference, confirmed.body().get("orderNumber"));
    assertNull(confirmed.body().get("total"));
    final OrderDocument order = Objects.requireNonNull(orderRepo.findByNumber(reference).block());
    assertEquals(
        1,
        orderRepo.findByCustomerId(order.customerId()).toStream().count(),
        "no second order, so no second number");
    assertEquals(1, mailsOf(MailTemplate.HANDOFF_RECEIPT_CUSTOMER, reference).size());
    assertTrue(mailsOf(MailTemplate.ORDER_CONFIRMATION_CUSTOMER, reference).isEmpty());
  }

  @Test
  void confirmationOfCheckoutThatPlacedNothingIsRefused() {
    final String id = readyCheckout(SMALL, 1, PaymentMethod.CARD);

    final Called confirmed = confirm(id);

    assertEquals(409, confirmed.status());
    assertEquals("checkout.not-confirmable", confirmed.body().get("code"));
  }
}
