package com.rednavis.metaldesk.share.domain.payment;

/**
 * The ways a customer can pay (BRD FR-6.1), each belonging to one {@link PaymentMethodGroup}.
 *
 * <p>The group is what the rules key on. BRD BR-9 restricts <em>gateway-processed</em> methods
 * above a configured order-value ceiling, leaving invoice as the only path, so the checkout asks a
 * method for its {@link #group()} instead of listing methods. The ceiling itself is configuration
 * and is deliberately not here.
 */
public enum PaymentMethod {

  /** Payment by card, through the gateway. */
  CARD(PaymentMethodGroup.GATEWAY),

  /** Bank debit, through the gateway. */
  BANK_DEBIT(PaymentMethodGroup.GATEWAY),

  /** A bank redirect, through the gateway. */
  BANK_REDIRECT(PaymentMethodGroup.GATEWAY),

  /** A bank transfer, through the gateway. */
  BANK_TRANSFER(PaymentMethodGroup.GATEWAY),

  /** A wallet the customer saved with the gateway, paid through the gateway. */
  SAVED_WALLET(PaymentMethodGroup.GATEWAY),

  /** The separate account-based wallet-payment provider, not the gateway's saved wallet. */
  WALLET_ACCOUNT(PaymentMethodGroup.WALLET),

  /** An invoice, generated as a document with no live gateway call. */
  INVOICE(PaymentMethodGroup.INVOICE);

  private final PaymentMethodGroup methodGroup;

  PaymentMethod(PaymentMethodGroup methodGroup) {
    this.methodGroup = methodGroup;
  }

  /**
   * Returns how this method is processed.
   *
   * @return the group, never null
   */
  public PaymentMethodGroup group() {
    return methodGroup;
  }
}
