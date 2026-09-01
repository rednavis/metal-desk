package com.rednavis.metaldesk.mail;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class MailTemplateTest {

  @Test
  void declaresTheBrdNotificationsAndTheManagerQuoteTerms() {
    final Set<String> names =
        Arrays.stream(MailTemplate.values()).map(Enum::name).collect(Collectors.toSet());
    assertEquals(
        Set.of(
            "EMAIL_VERIFICATION",
            "PASSWORD_RESET",
            "ORDER_CONFIRMATION_CUSTOMER",
            "ORDER_NOTIFICATION_STAFF",
            "INVOICE_CUSTOMER",
            "INVOICE_STAFF",
            "HANDOFF_RECEIPT_CUSTOMER",
            "HANDOFF_NOTIFICATION_STAFF",
            "INQUIRY_RECEIPT_CUSTOMER",
            "INQUIRY_NOTIFICATION_STAFF",
            "MANAGER_QUOTE_CUSTOMER"),
        names);
  }

  @Test
  void resourceNameIsTheLowerCasedConstant() {
    assertEquals("email_verification", MailTemplate.EMAIL_VERIFICATION.resourceName());
    assertEquals("invoice_staff", MailTemplate.INVOICE_STAFF.resourceName());
  }
}
