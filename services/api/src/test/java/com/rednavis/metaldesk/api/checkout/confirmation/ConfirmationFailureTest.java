package com.rednavis.metaldesk.api.checkout.confirmation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;

import com.rednavis.metaldesk.mail.MailException;
import com.rednavis.metaldesk.mail.MailTemplate;
import com.rednavis.metaldesk.mail.fake.InProcessMailSender;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import reactor.core.publisher.Mono;

/**
 * A confirmation that cannot be completed is a support-actionable error, not a silent failure (BRD
 * FR-8.1): a code, a correlation id that is also in the error log, and the order number.
 */
@ExtendWith(OutputCaptureExtension.class)
class ConfirmationFailureTest extends ConfirmationTestSupport {

  private static final Pattern ORDER_NUMBER = Pattern.compile("[0-9]{12}");

  @MockitoSpyBean private InProcessMailSender spiedSender;

  @BeforeEach
  void seedData() {
    seedPaymentCatalog();
  }

  @Test
  void failedConfirmationCarriesCodeAndCorrelationIdFoundInTheLog(CapturedOutput output) {
    final String id = paidCheckout();
    Mockito.doReturn(
            Mono.error(new MailException("the relay is down", new IllegalStateException("down"))))
        .when(spiedSender)
        .send(any());

    final Called failed = confirm(id);

    assertEquals(503, failed.status());
    assertEquals("checkout.confirmation-failed", failed.body().get("code"));
    final String correlation = (String) failed.body().get("correlationId");
    assertNotNull(correlation);
    final String log = output.getAll();
    assertTrue(log.contains(correlation), "the correlation id is in the log");
    assertTrue(log.contains("Operation failed"), "it is logged as an error");
    final Matcher order = ORDER_NUMBER.matcher((String) failed.body().get("message"));
    assertTrue(order.find(), "the message quotes the order number");
    assertTrue(log.contains(order.group()), "the order reference is in the log");
  }

  @Test
  void confirmingAgainAfterFailedSendDeliversWhatWasMissing() {
    final String id = paidCheckout();
    Mockito.doReturn(
            Mono.error(new MailException("the relay is down", new IllegalStateException("down"))))
        .when(spiedSender)
        .send(any());
    final Called failed = confirm(id);
    assertEquals(503, failed.status());
    Mockito.reset(spiedSender);

    final Called retried = confirm(id);

    assertEquals(200, retried.status());
    final String number = (String) retried.body().get("orderNumber");
    assertEquals(1, mailsOf(MailTemplate.ORDER_CONFIRMATION_CUSTOMER, number).size());
    assertEquals(1, mailsOf(MailTemplate.ORDER_NOTIFICATION_STAFF, number).size());
  }
}
