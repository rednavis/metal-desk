package com.rednavis.metaldesk.api.checkout.confirmation;

import com.rednavis.metaldesk.api.checkout.CheckoutProperties;
import com.rednavis.metaldesk.api.checkout.payment.PaymentTestSupport;
import com.rednavis.metaldesk.api.persistence.repository.CustomerRepository;
import com.rednavis.metaldesk.mail.MailTemplate;
import com.rednavis.metaldesk.mail.TransactionalMail;
import com.rednavis.metaldesk.mail.fake.RecordedMail;
import com.rednavis.metaldesk.persistence.document.CustomerDocument;
import com.rednavis.metaldesk.share.domain.payment.PaymentMethod;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

/**
 * The base of the confirmation tests: drives a checkout to each way an order can be placed and
 * reads what was mailed, always from the in-process sender.
 */
public class ConfirmationTestSupport extends PaymentTestSupport {

  /** What the gateway stub answers to a payment it captures. */
  protected static final String CAPTURED_BODY =
      "{\"status\":\"captured\",\"reference\":\"gw_cap_1\"}";

  @Autowired private CustomerRepository customerRepo;
  @Autowired protected CheckoutProperties properties;

  /** Creates the base; subclasses are the tests. */
  protected ConfirmationTestSupport() {
    super();
  }

  /**
   * Pays a one-item checkout by card, which the stub captures.
   *
   * @return the checkout id
   */
  protected String paidCheckout() {
    stubAuthorise(GATEWAY, CAPTURED_BODY);
    final String id = readyCheckout(SMALL, 1, PaymentMethod.CARD);
    pay(id);
    return id;
  }

  /**
   * Places an invoice order.
   *
   * @param mixedTax whether the cart mixes investment-grade and standard-rated items
   * @return the checkout id
   */
  protected String invoicedCheckout(boolean mixedTax) {
    final String region = nextRegion();
    configureWideTier(region);
    final Called first = add(null, null, GOLD_1);
    if (mixedTax) {
      add(first.cookie(), null, TAXED);
    }
    final String id = checkoutId(call(HttpMethod.POST, SESSIONS, first.cookie(), null, null));
    submit(id, null, with(validForm(freshEmail()), "country", region));
    evaluate(id);
    select(id, PaymentMethod.INVOICE);
    pay(id);
    return id;
  }

  /**
   * Hands a checkout to a manager, because its region has no delivery tier.
   *
   * @return the checkout id
   */
  protected String handedOffCheckout() {
    final String id = checkoutTo(nextRegion(), SMALL, 1);
    evaluate(id);
    handoff(id, Map.of());
    return id;
  }

  /**
   * Asks for the confirmation of a checkout.
   *
   * @param id the checkout id
   * @return the response
   */
  protected Called confirm(String id) {
    return call(HttpMethod.GET, SESSIONS + "/" + id + "/confirmation", null, null, null);
  }

  /**
   * The order number of a checkout's confirmation.
   *
   * @param id the checkout id
   * @return the order number
   */
  protected String numberOf(String id) {
    return (String) Objects.requireNonNull(confirm(id).body().get("orderNumber"));
  }

  /**
   * The mails of a template that are about one order.
   *
   * @param template the template
   * @param orderNumber the order number the mail mentions
   * @return the mails
   */
  protected List<TransactionalMail> mailsOf(MailTemplate template, String orderNumber) {
    return mail.sentOf(template).stream()
        .map(RecordedMail::mail)
        .filter(sent -> (sent.subject() + sent.body()).contains(orderNumber))
        .toList();
  }

  /**
   * The email address of the customer who owns an order.
   *
   * @param orderNumber the order number
   * @return the address
   */
  protected String emailOf(String orderNumber) {
    final String customerId =
        Objects.requireNonNull(orderRepo.findByNumber(orderNumber).block()).customerId();
    final CustomerDocument customer =
        Objects.requireNonNull(customerRepo.findById(customerId).block());
    return customer.email();
  }
}
