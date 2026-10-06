package com.rednavis.metaldesk.share.domain.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class PaymentMethodTest {

  @ParameterizedTest
  @EnumSource(
      value = PaymentMethod.class,
      names = {"CARD", "BANK_DEBIT", "BANK_REDIRECT", "BANK_TRANSFER", "SAVED_WALLET"})
  void theFiveGatewayMethodsAreGatewayProcessed(PaymentMethod method) {
    assertEquals(PaymentMethodGroup.GATEWAY, method.group());
  }

  @Test
  void theWalletProviderIsItsOwnGroup() {
    assertEquals(PaymentMethodGroup.WALLET, PaymentMethod.WALLET_ACCOUNT.group());
  }

  @Test
  void invoiceIsItsOwnGroup() {
    assertEquals(PaymentMethodGroup.INVOICE, PaymentMethod.INVOICE.group());
  }

  @Test
  void everyMethodHasGroupAndThereAreSeven() {
    assertEquals(7, PaymentMethod.values().length);
    for (final PaymentMethod method : PaymentMethod.values()) {
      assertEquals(method.group(), PaymentMethodGroup.valueOf(method.group().name()));
    }
  }
}
