package com.rednavis.metaldesk.api.inquiry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rednavis.metaldesk.api.checkout.confirmation.ConfirmationTestSupport;
import com.rednavis.metaldesk.mail.MailTemplate;
import com.rednavis.metaldesk.mail.TransactionalMail;
import com.rednavis.metaldesk.mail.fake.RecordedMail;
import com.rednavis.metaldesk.share.domain.customer.EmailAddress;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

/**
 * Price inquiries and messages to staff (BRD FR-9.1, FR-9.2), from every source, without sign-in.
 */
class InquiryTest extends ConfirmationTestSupport {

  private static final String INQUIRIES = "/api/inquiries";
  private static final String REFERENCE = "reference";
  private static final String SOURCE = "source";
  private static final String HANDOFF = "HANDOFF";

  @BeforeEach
  void seedData() {
    seedPaymentCatalog();
  }

  private Map<String, Object> inquiry(String source, String email) {
    final Map<String, Object> body = new ConcurrentHashMap<>();
    body.put(SOURCE, source);
    body.put("name", "Vera Visitor");
    body.put("email", email);
    body.put("topic", "Price of a 1 oz bar");
    body.put("message", "Do you hold 10 of these?");
    return body;
  }

  private Called send(Map<String, Object> body) {
    return call(HttpMethod.POST, INQUIRIES, null, null, body);
  }

  private void assertBothNotified(String email, String reference) {
    final List<TransactionalMail> receipts =
        mailsOf(MailTemplate.INQUIRY_RECEIPT_CUSTOMER, reference);
    assertEquals(1, receipts.size());
    assertEquals(List.of(new EmailAddress(email)), receipts.get(0).to());
    assertTrue(receipts.get(0).body().contains(reference), "the customer is given the reference");
    final List<TransactionalMail> staff =
        mail.sentOf(MailTemplate.INQUIRY_NOTIFICATION_STAFF).stream()
            .map(RecordedMail::mail)
            .filter(sent -> sent.body().contains(reference))
            .toList();
    assertEquals(1, staff.size());
    assertTrue(staff.get(0).body().contains(email), "staff see who to answer");
    assertEquals(List.of(new EmailAddress(properties.staffEmail())), staff.get(0).to());
  }

  @Test
  void catalogInquiryFromVisitorNotifiesBothPartiesAndReturnsReference() {
    final String email = freshEmail();

    final Called sent = send(inquiry("CATALOG", email));

    assertEquals(201, sent.status());
    final String reference = (String) sent.body().get(REFERENCE);
    assertTrue(reference.startsWith("INQ-"));
    assertBothNotified(email, reference);
  }

  @Test
  void productInquiryNamesTheProduct() {
    final String email = freshEmail();
    final Map<String, Object> body = inquiry("PRODUCT", email);
    body.put("productId", GOLD_1);

    final Called sent = send(body);

    assertEquals(201, sent.status());
    assertBothNotified(email, (String) sent.body().get(REFERENCE));
  }

  @Test
  void handoffInquiryNamesTheHandedOffOrderAndItsEmail() {
    final String reference = numberOf(handedOffCheckout());
    final String email = emailOf(reference);
    final Map<String, Object> body = inquiry(HANDOFF, email);
    body.put("handoffReference", reference);

    final Called sent = send(body);

    assertEquals(201, sent.status());
    assertBothNotified(email, (String) sent.body().get(REFERENCE));
  }

  @Test
  void messageInEachLanguageIsAnsweredInThatLanguage() {
    final String email = freshEmail();
    final Map<String, Object> body = inquiry("CATALOG", email);
    body.put("locale", "de");

    send(body);

    assertEquals(Locale.GERMAN, mail.sentTo(new EmailAddress(email)).get(0).mail().locale());
  }

  @Test
  void handoffInquiryWithAnotherEmailFailsLikeAnUnknownReference() {
    final String reference = numberOf(handedOffCheckout());
    final Map<String, Object> wrongEmail = inquiry(HANDOFF, freshEmail());
    wrongEmail.put("handoffReference", reference);
    final Map<String, Object> unknown = inquiry(HANDOFF, freshEmail());
    unknown.put("handoffReference", "000000000000");

    final Called first = send(wrongEmail);
    final Called second = send(unknown);

    assertEquals(400, first.status());
    assertEquals(400, second.status());
    assertEquals(first.body().get("violations"), second.body().get("violations"));
    assertTrue(mailsOf(MailTemplate.INQUIRY_NOTIFICATION_STAFF, reference).isEmpty());
  }

  @Test
  void unknownProductIsRefused() {
    final Map<String, Object> body = inquiry("PRODUCT", freshEmail());
    body.put("productId", "no-such-product");

    assertEquals(400, send(body).status());
  }

  @Test
  void everyInvalidFieldIsReportedAtOnce() {
    final Called sent = send(Map.of(SOURCE, "PRODUCT", "email", "not-an-email"));

    assertEquals(400, sent.status());
    assertEquals("validation.failed", sent.body().get("code"));
    final List<?> violations = (List<?>) sent.body().get("violations");
    final List<?> fields =
        violations.stream().map(violation -> ((Map<?, ?>) violation).get("field")).toList();
    assertTrue(fields.containsAll(List.of("name", "topic", "message", "email", "productId")));
  }

  @Test
  void missingBodyIsRefusedNotCrashed() {
    final Called sent = call(HttpMethod.POST, INQUIRIES, null, null, null);

    assertEquals(400, sent.status());
    assertNotNull(sent.body().get("correlationId"));
    assertFalse(sent.body().isEmpty());
  }
}
