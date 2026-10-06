package com.rednavis.metaldesk.mail;

import java.util.Locale;

/**
 * The transactional notifications the platform sends: a closed set, one constant for each
 * notification the BRD requires, so adding a notification is a deliberate change here and a
 * template file with it.
 *
 * <p>Customer and staff variants are separate templates, not one template with a flag: BRD FR-8.1
 * and FR-6.2 require notifying both parties, and the two messages carry different information and
 * need different subjects.
 *
 * <p>Each constant has a template file per supported language under {@code mail/} on the classpath,
 * named {@link #resourceName()} and the language code, for example {@code
 * email_verification_en.txt}.
 */
public enum MailTemplate {

  /** The code that verifies an email address on registration (BRD FR-2.3, FR-2.6). */
  EMAIL_VERIFICATION,

  /** The time-limited link a customer uses to regain access to their account (BRD FR-2.4). */
  PASSWORD_RESET,

  /** Tells the customer their order is confirmed (BRD FR-8.1). */
  ORDER_CONFIRMATION_CUSTOMER,

  /** Tells staff a new order has been placed (BRD FR-8.1). */
  ORDER_NOTIFICATION_STAFF,

  /** Sends the customer their invoice as an attachment (BRD FR-6.2). */
  INVOICE_CUSTOMER,

  /** Sends staff a copy of the invoice that went to the customer (BRD FR-6.2). */
  INVOICE_STAFF,

  /**
   * Confirms to the customer that a manager handoff was received, with a reference (BRD FR-5.3).
   */
  HANDOFF_RECEIPT_CUSTOMER,

  /** Tells staff an order went to manager handoff, with its context (BRD FR-5.3). */
  HANDOFF_NOTIFICATION_STAFF,

  /**
   * Tells the customer that staff have set the final delivery price and terms of a handed-off
   * order, so they can now pay (BRD FR-5.3).
   */
  MANAGER_QUOTE_CUSTOMER,

  /** Confirms to the customer that their inquiry or message was received (BRD FR-9.1, FR-9.2). */
  INQUIRY_RECEIPT_CUSTOMER,

  /** Tells staff a customer sent an inquiry or a message (BRD FR-9.1, FR-9.2). */
  INQUIRY_NOTIFICATION_STAFF;

  /**
   * Returns the base name of this template's files: the constant's name in lower case.
   *
   * @return for example {@code email_verification}
   */
  public String resourceName() {
    return name().toLowerCase(Locale.ROOT);
  }
}
