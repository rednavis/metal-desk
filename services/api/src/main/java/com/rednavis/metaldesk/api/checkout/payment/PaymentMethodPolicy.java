package com.rednavis.metaldesk.api.checkout.payment;

import com.rednavis.metaldesk.share.domain.money.Money;
import com.rednavis.metaldesk.share.domain.payment.PaymentMethod;
import com.rednavis.metaldesk.share.domain.payment.PaymentMethodGroup;
import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The high-value payment restriction (BRD BR-9), bound from {@code metaldesk.checkout.payment}.
 *
 * <p>Above the ceiling, gateway-processed methods (card, bank debit, bank redirect, bank transfer,
 * saved wallet) are not offered, as a fraud and chargeback control. <strong>Invoice is never
 * filtered out</strong>: it is the high-value path, and BR-9 says it becomes the only one. The
 * separate wallet-account provider is off above the ceiling by default, reading BR-9 strictly; a
 * deployment that wants it can switch it on with {@code wallet-high-value}.
 *
 * <p>The comparison is on the order's <em>grand total</em>, the amount that would be charged, and
 * "above" is strict: an order exactly at the ceiling is still under it.
 *
 * @param highValueCeiling the largest order total, in the order's currency, that gateway methods
 *     still serve; illustrative default 2500.00
 * @param walletHighValue whether the wallet-account method stays on above the ceiling
 */
@ConfigurationProperties("metaldesk.checkout.payment")
public record PaymentMethodPolicy(
    @DefaultValue("2500.00") BigDecimal highValueCeiling,
    @DefaultValue("false") boolean walletHighValue) {

  /**
   * Tells whether a method may be offered for an order total.
   *
   * @param method the method
   * @param total the order's grand total
   * @return {@code true} if it may
   */
  public boolean allows(PaymentMethod method, Money total) {
    final boolean highValue = total.amount().compareTo(highValueCeiling) > 0;
    final PaymentMethodGroup group = method.group();
    return !highValue
        || group == PaymentMethodGroup.INVOICE
        || (group == PaymentMethodGroup.WALLET && walletHighValue);
  }

  /**
   * Tells whether an order total is above the ceiling.
   *
   * @param total the order's grand total
   * @return whether gateway methods are restricted for it
   */
  public boolean isHighValue(Money total) {
    return total.amount().compareTo(highValueCeiling) > 0;
  }
}
