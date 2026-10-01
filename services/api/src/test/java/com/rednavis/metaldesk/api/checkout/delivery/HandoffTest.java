package com.rednavis.metaldesk.api.checkout.delivery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rednavis.metaldesk.api.persistence.repository.CustomerRepository;
import com.rednavis.metaldesk.api.persistence.repository.OrderRepository;
import com.rednavis.metaldesk.mail.MailTemplate;
import com.rednavis.metaldesk.mail.TransactionalMail;
import com.rednavis.metaldesk.mail.fake.RecordedMail;
import com.rednavis.metaldesk.persistence.document.CartDocument;
import com.rednavis.metaldesk.persistence.document.CustomerDocument;
import com.rednavis.metaldesk.persistence.document.OrderDocument;
import com.rednavis.metaldesk.share.domain.customer.EmailAddress;
import com.rednavis.metaldesk.share.domain.customer.VerificationState;
import com.rednavis.metaldesk.share.domain.order.OrderStatus;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.http.HttpMethod;

/**
 * The manager handoff (BRD FR-5.3, BR-10): an order for a manager, a reference, two mails, no
 * money.
 */
class HandoffTest extends DeliveryTestSupport {

  private static final String TIGHT = "1000.00";
  private static final String HEAVY = "5000";
  private static final String PRICE = "15.00";
  private static final String CODE = "code";
  private static final String COUNTRY = "country";

  private static final Pattern ORDER_NUMBER = Pattern.compile("\\d{12}");
  private static final String REFERENCE = "reference";
  private static final String STAFF = "staff@metal-desk.example.test";

  @Autowired private OrderRepository orders;
  @Autowired private ReactiveMongoTemplate mongo;
  @Autowired private CustomerRepository customers;

  @BeforeEach
  void seedData() {
    seedCatalog();
  }

  private String overValueCheckout(String region) {
    configureTier(region, TIGHT, HEAVY, PRICE);
    return checkoutTo(region, GOLD_1, 2);
  }

  private OrderDocument orderOf(Called handoff) {
    return Objects.requireNonNull(
        orders.findByNumber((String) handoff.body().get(REFERENCE)).block());
  }

  private List<TransactionalMail> mailsAbout(String reference, MailTemplate template) {
    return mail.sentOf(template).stream()
        .map(RecordedMail::mail)
        .filter(sent -> sent.subject().contains(reference))
        .toList();
  }

  @Test
  void handoffReturnsTheReferenceAndTheBoundCeilingAndTakesNoPayment() {
    final Called called = handoff(overValueCheckout("DK"), null);

    assertEquals(200, called.status());
    assertTrue(ORDER_NUMBER.matcher((String) called.body().get(REFERENCE)).matches());
    assertEquals("VALUE_CEILING_EXCEEDED", called.body().get("reason"));
    assertEquals("VALUE", called.body().get("boundCeiling"));
  }

  @Test
  void theReferenceIsTheOrderNumberOfAnOrderAwaitingManagerQuote() {
    final Called called = handoff(overValueCheckout("FI"), null);

    final OrderDocument order = orderOf(called);

    assertEquals(OrderStatus.AWAITING_MANAGER_QUOTE, order.status());
    assertEquals(called.body().get(REFERENCE), order.number());
    assertEquals(1, order.lines().size());
    assertEquals(2, order.lines().get(0).quantity());
    assertNull(order.quote(), "no delivery quote: a manager sets the price");
    assertNull(order.payment(), "no payment was taken or attempted");
  }

  @Test
  void handoffRemovesTheCartTheOrderWasMadeFrom() {
    configureTier("SE", TIGHT, HEAVY, PRICE);
    final Called cart = add(null, null, GOLD_1);
    changeQuantity(cart.cookie(), GOLD_1, 2);
    final String id = checkoutId(call(HttpMethod.POST, SESSIONS, cart.cookie(), null, null));
    submit(id, null, with(validForm(freshEmail()), COUNTRY, "SE"));
    assertEquals(2, read(cart.cookie(), null).quantityOf(GOLD_1));

    assertEquals(200, handoff(id, null).status());

    assertEquals(0, read(cart.cookie(), null).lines().size());
    assertNull(mongo.findById(cart.cookie(), CartDocument.class).block(), "the document is gone");
  }

  @Test
  void orderThatFitsTierCannotBeHandedOff() {
    configureTier("IE", "10000.00", HEAVY, PRICE);

    final Called called = handoff(checkoutTo("IE", GOLD_1, 1), null);

    assertEquals(409, called.status());
    assertEquals("checkout.handoff-not-required", called.body().get(CODE));
  }

  @Test
  void regionWithNoTierCanBeHandedOffWithItsOwnReason() {
    removeTiers("PT");

    final Called called = handoff(checkoutTo("PT", GOLD_1, 1), null);

    assertEquals(200, called.status());
    assertEquals("NO_TIER_FOR_REGION", called.body().get("reason"));
    assertNull(called.body().get("boundCeiling"));
  }

  @Test
  void exactlyOneReceiptAndOneStaffNotificationAreSent() {
    final Called called = handoff(overValueCheckout("GR"), null);
    final String reference = (String) called.body().get(REFERENCE);

    assertEquals(1, mailsAbout(reference, MailTemplate.HANDOFF_RECEIPT_CUSTOMER).size());
    assertEquals(1, mailsAbout(reference, MailTemplate.HANDOFF_NOTIFICATION_STAFF).size());
    assertEquals(
        List.of(new EmailAddress(STAFF)),
        mailsAbout(reference, MailTemplate.HANDOFF_NOTIFICATION_STAFF).get(0).to());
  }

  @Test
  void theStaffMailCarriesTheFullContext() {
    final Called called = handoff(overValueCheckout("LU"), null);

    final String body =
        mailsAbout((String) called.body().get(REFERENCE), MailTemplate.HANDOFF_NOTIFICATION_STAFF)
            .get(0)
            .body();

    assertTrue(body.contains("Cart gold one"), body);
    assertTrue(body.contains("2 x"), body);
    assertTrue(body.contains("1 Main Street, 10115 Berlin, LU"), body);
    assertTrue(body.contains("12,598.74"), "the value before tax: " + body);
    assertTrue(body.contains("200 g"), body);
    assertTrue(body.contains("Ceiling exceeded: value"), body);
    assertTrue(body.contains("Ann Example"), body);
  }

  @Test
  void theCustomerReceiptIsInTheirLanguageAndCarriesTheReference() {
    final Called called = handoff(overValueCheckout("MT"), Map.of("locale", "de"));

    final TransactionalMail receipt =
        mailsAbout((String) called.body().get(REFERENCE), MailTemplate.HANDOFF_RECEIPT_CUSTOMER)
            .get(0);

    assertTrue(receipt.subject().contains("Bestellanfrage"), receipt.subject());
    assertTrue(receipt.body().contains((String) called.body().get(REFERENCE)));
  }

  @Test
  void handingOffTwiceReturnsTheSameHandoffAndSendsNothingMore() {
    final String id = overValueCheckout("EE");
    final Called first = handoff(id, null);
    final int receipts = mail.sentOf(MailTemplate.HANDOFF_RECEIPT_CUSTOMER).size();
    final int staff = mail.sentOf(MailTemplate.HANDOFF_NOTIFICATION_STAFF).size();

    final Called second = handoff(id, null);

    assertEquals(first.body().get(REFERENCE), second.body().get(REFERENCE));
    assertEquals(receipts, mail.sentOf(MailTemplate.HANDOFF_RECEIPT_CUSTOMER).size());
    assertEquals(staff, mail.sentOf(MailTemplate.HANDOFF_NOTIFICATION_STAFF).size());
  }

  @Test
  void simultaneousHandoffsCreateOneOrder() {
    final String id = overValueCheckout("LV");

    final List<Called> calls =
        java.util.stream.IntStream.range(0, 5).parallel().mapToObj(i -> handoff(id, null)).toList();

    assertEquals(1, calls.stream().map(call -> call.body().get(REFERENCE)).distinct().count());
    assertEquals(
        1,
        mail.sentOf(MailTemplate.HANDOFF_NOTIFICATION_STAFF).stream()
            .map(RecordedMail::mail)
            .filter(sent -> sent.subject().contains((String) calls.get(0).body().get(REFERENCE)))
            .count());
  }

  @Test
  void stepOneCannotBeEditedOnceTheOrderIsWithStaff() {
    final String id = overValueCheckout("LT");
    handoff(id, null);

    final Called edit = submit(id, null, validForm(freshEmail()));

    assertEquals(409, edit.status());
    assertEquals("checkout.already-handed-off", edit.body().get(CODE));
  }

  @Test
  void guestGetsAnUnverifiedContactRecordTheOrderBelongsTo() {
    configureTier("SK", TIGHT, HEAVY, PRICE);
    final String email = freshEmail();
    final Called cart = add(null, null, GOLD_1);
    final String id = checkoutId(call(HttpMethod.POST, SESSIONS, cart.cookie(), null, null));
    submit(id, null, with(validForm(email), COUNTRY, "SK"));

    final Called called = handoff(id, null);

    final CustomerDocument customer = Objects.requireNonNull(customers.findByEmail(email).block());
    assertEquals(VerificationState.UNVERIFIED, customer.verification());
    assertEquals(customer.id(), orderOf(called).customerId());
  }

  @Test
  void signedInCustomersOrderBelongsToThem() {
    configureTier("SI", TIGHT, HEAVY, PRICE);
    final String token = signedInToken();
    final Called cart = add(null, token, GOLD_1);
    final String id = checkoutId(call(HttpMethod.POST, SESSIONS, cart.cookie(), token, null));
    submit(id, token, with(validForm(freshEmail()), COUNTRY, "SI"));

    final Called called =
        call(HttpMethod.POST, SESSIONS + "/" + id + HANDOFF_PATH, null, token, null);

    assertEquals(200, called.status());
    assertEquals(
        call(HttpMethod.GET, "/api/auth/me", null, token, null).body().get("customerId"),
        orderOf(called).customerId());
  }

  @Test
  void orderForAnAddressThatAlreadyHasAnAccountGoesToThatAccountWithoutCreatingAnother() {
    configureTier("HR", TIGHT, HEAVY, PRICE);
    final String email = freshEmail();
    registerAndVerify(email);
    final String existing = Objects.requireNonNull(customers.findByEmail(email).block()).id();
    final Called cart = add(null, null, GOLD_1);
    final String id = checkoutId(call(HttpMethod.POST, SESSIONS, cart.cookie(), null, null));
    submit(id, null, with(validForm(email), COUNTRY, "HR"));

    final Called called = handoff(id, null);

    assertEquals(existing, orderOf(called).customerId());
  }

  @Test
  void handoffBeforeStepOneIsComplete409s() {
    final Called called = handoff(checkoutId(startFromCart(null)), null);

    assertEquals(409, called.status());
    assertEquals("checkout.step1-incomplete", called.body().get(CODE));
  }

  @Test
  void theGuestContactRecordCanBeClaimedThroughPasswordReset() {
    configureTier("BG", TIGHT, HEAVY, PRICE);
    final String email = freshEmail();
    final Called cart = add(null, null, GOLD_1);
    final String id = checkoutId(call(HttpMethod.POST, SESSIONS, cart.cookie(), null, null));
    submit(id, null, with(validForm(email), COUNTRY, "BG"));
    handoff(id, null);

    // The registration path issues a code to the unverified record; verifying it and then
    // resetting the password creates the credential once the mailbox is proven.
    final Object reference = register(email).body().get(REFERENCE);
    call(
        HttpMethod.POST,
        "/api/account/verify-email",
        null,
        null,
        Map.of(
            REFERENCE,
            reference,
            CODE,
            com.rednavis.metaldesk.api.account.MailInspector.typedCode(
                lastMailTo(email, MailTemplate.EMAIL_VERIFICATION))));
    call(
        HttpMethod.POST, "/api/account/password-reset/request", null, null, Map.of("email", email));
    final TransactionalMail link = lastMailTo(email, MailTemplate.PASSWORD_RESET);
    call(
        HttpMethod.POST,
        "/api/account/password-reset/confirm",
        null,
        null,
        Map.of(
            REFERENCE,
            com.rednavis.metaldesk.api.account.MailInspector.linkReference(link),
            CODE,
            com.rednavis.metaldesk.api.account.MailInspector.linkCode(link),
            "newPassword",
            "a-brand-new-password"));

    assertNotNull(signIn(email, "a-brand-new-password"));
  }

  private TransactionalMail lastMailTo(String email, MailTemplate template) {
    final List<TransactionalMail> sent =
        mailTo(email).stream().filter(mail -> mail.template() == template).toList();
    return sent.get(sent.size() - 1);
  }
}
