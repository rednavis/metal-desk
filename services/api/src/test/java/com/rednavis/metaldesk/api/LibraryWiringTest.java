package com.rednavis.metaldesk.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rednavis.metaldesk.mail.MailRenderer;
import com.rednavis.metaldesk.mail.MailTemplate;
import com.rednavis.metaldesk.mail.RenderedMail;
import com.rednavis.metaldesk.payments.gateway.GatewayConfiguration;
import com.rednavis.metaldesk.payments.gateway.GatewayProvider;
import com.rednavis.metaldesk.share.domain.money.Currency;
import com.rednavis.metaldesk.share.domain.money.Money;
import com.rednavis.metaldesk.share.domain.payment.PaymentMethod;
import java.net.URI;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The Phase 2 exit gate for {@code services/api}: it compiles against, and really uses, a type from
 * each library it declares. A dependency that is declared and never used proves nothing, and a type
 * used here fails to compile if the dependency is ever dropped.
 */
class LibraryWiringTest {

  @Test
  void usesTheSharedDomain() {
    assertEquals("12.30", Money.of("12.30", Currency.EUR).amount().toPlainString());
  }

  @Test
  void usesThePaymentsLibrary() {
    final GatewayProvider gateway =
        new GatewayProvider(
            GatewayConfiguration.withDefaults(URI.create("https://gateway.example")));
    assertTrue(gateway.supports(PaymentMethod.CARD));
    assertFalse(gateway.supports(PaymentMethod.INVOICE));
  }

  @Test
  void usesTheMailLibraryAndItsTemplatesResolveFromTheJar() {
    final RenderedMail mail =
        new MailRenderer()
            .render(
                MailTemplate.EMAIL_VERIFICATION,
                Map.of("name", "Ann", "code", "482913"),
                Locale.GERMAN);
    assertTrue(mail.body().contains("482913"));
    assertEquals(Locale.GERMAN, mail.locale());
  }
}
