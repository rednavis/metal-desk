package com.rednavis.metaldesk.api.checkout.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rednavis.metaldesk.share.domain.money.Currency;
import com.rednavis.metaldesk.share.domain.money.Money;
import com.rednavis.metaldesk.share.domain.payment.PaymentMethod;
import com.rednavis.metaldesk.share.domain.payment.PaymentMethodGroup;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The high-value restriction (BRD BR-9): gateway methods go, invoice never does. */
class PaymentMethodPolicyTest {

  private static final BigDecimal CEILING = new BigDecimal("2500.00");

  private static Money eur(String amount) {
    return Money.of(amount, Currency.EUR);
  }

  private static List<PaymentMethod> allowed(PaymentMethodPolicy policy, String total) {
    return Arrays.stream(PaymentMethod.values())
        .filter(method -> policy.allows(method, eur(total)))
        .toList();
  }

  @Test
  void belowTheCeilingEveryMethodIsAllowed() {
    assertEquals(
        List.of(PaymentMethod.values()),
        allowed(new PaymentMethodPolicy(CEILING, false), "100.00"));
  }

  @Test
  void orderExactlyAtTheCeilingIsStillBelowIt() {
    assertEquals(
        List.of(PaymentMethod.values()),
        allowed(new PaymentMethodPolicy(CEILING, false), "2500.00"));
  }

  @Test
  void aboveTheCeilingOnlyInvoiceRemains() {
    assertEquals(
        List.of(PaymentMethod.INVOICE),
        allowed(new PaymentMethodPolicy(CEILING, false), "2500.01"));
  }

  @Test
  void theWalletAccountCanBeSwitchedOnAboveTheCeiling() {
    assertEquals(
        List.of(PaymentMethod.WALLET_ACCOUNT, PaymentMethod.INVOICE),
        allowed(new PaymentMethodPolicy(CEILING, true), "9999.00"));
  }

  @Test
  void invoiceIsNeverFilteredOutWhateverTheTotal() {
    final PaymentMethodPolicy policy = new PaymentMethodPolicy(CEILING, false);
    for (final Money total :
        List.of(eur("0.01"), eur("2500.00"), eur("2500.01"), eur("1000000.00"))) {
      assertTrue(policy.allows(PaymentMethod.INVOICE, total));
    }
  }

  @Test
  void everyGatewayMethodIsRestrictedAboveTheCeiling() {
    final PaymentMethodPolicy policy = new PaymentMethodPolicy(CEILING, true);
    final Money high = eur("2500.01");
    for (final PaymentMethod method : PaymentMethod.values()) {
      if (method.group() == PaymentMethodGroup.GATEWAY) {
        assertFalse(policy.allows(method, high));
      }
    }
  }
}
