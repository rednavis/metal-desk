package com.rednavis.metaldesk.mail;

import com.rednavis.metaldesk.share.domain.customer.EmailAddress;
import com.rednavis.metaldesk.share.domain.money.Currency;
import com.rednavis.metaldesk.share.domain.money.Money;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Synthetic addresses, models and mails for the mail tests, all on a reserved example domain. */
public final class MailFixtures {

  /** A customer's address. */
  public static final EmailAddress CUSTOMER = new EmailAddress("ann@example.com");

  /** A staff address. */
  public static final EmailAddress STAFF = new EmailAddress("staff@example.com");

  private MailFixtures() {}

  /**
   * Builds a model that supplies every placeholder any template uses. Extra entries are ignored by
   * a template that does not use them.
   *
   * @return the model
   */
  public static Map<String, Object> universalModel() {
    return Map.ofEntries(
        Map.entry("name", "Ann"),
        Map.entry("code", "482913"),
        Map.entry("link", "https://shop.example/reset/abc123"),
        Map.entry("validity", "60 minutes"),
        Map.entry("orderNumber", "080220220004"),
        Map.entry("total", Money.of("1959.32", Currency.EUR)),
        Map.entry("customerName", "Ann Smith"),
        Map.entry("invoiceNumber", "INV-080220220004"),
        Map.entry("reference", "080220220004"),
        Map.entry("ceiling", "value"),
        Map.entry("topic", "Gold bar 1 oz"),
        Map.entry("customerEmail", "ann@example.com"),
        Map.entry("destination", "1 Main Street, 10115 Berlin, DE"),
        Map.entry("exTaxValue", Money.of("25000.00", Currency.EUR)),
        Map.entry("weight", "3110 g"),
        Map.entry("lines", "- 2 x Gold bar 1 oz (1959.32 EUR each)"),
        Map.entry("message", "Do you have this in stock?"),
        Map.entry("deliveryPrice", Money.of("45.00", Currency.EUR)),
        Map.entry("transit", "3-5 days"),
        Map.entry("validUntil", "2026-10-31"),
        Map.entry("terms", "Insured courier, signature on delivery."));
  }

  /**
   * Builds a mail from a template to the customer, with no attachment.
   *
   * @param template the template
   * @return the mail
   */
  public static TransactionalMail mail(MailTemplate template) {
    return new TransactionalMail(
        template, List.of(CUSTOMER), "Subject", "Body", Locale.ENGLISH, List.of());
  }
}
