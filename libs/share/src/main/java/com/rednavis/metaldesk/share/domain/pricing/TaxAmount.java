package com.rednavis.metaldesk.share.domain.pricing;

import com.rednavis.metaldesk.share.domain.money.Money;
import com.rednavis.metaldesk.share.error.ValidationException;

/**
 * The tax on a net amount at a rate (BRD BR-4, BR-5), keeping the inputs beside the result so an
 * invoice line can show how the tax arose.
 *
 * <p>The tax is rounded once, to the currency's minor unit, by {@link Money}. The canonical
 * constructor refuses a tax that is not what {@link #of} would compute, so an instance cannot claim
 * an amount its net and rate do not produce.
 *
 * <p>This prices one amount. Which amounts are taxed together, and whether tax is summed per line
 * or on the order, is order totalling (T-014, BRD BR-5).
 *
 * @param net the amount before tax, never null
 * @param rate the rate applied, never null
 * @param tax the tax on {@code net} at {@code rate}, never null
 */
public record TaxAmount(Money net, TaxRate rate, Money tax) {

  /**
   * Validates the fields and that the tax follows from the net and rate.
   *
   * @throws ValidationException if a field is null, or the tax is not the tax on the net at the
   *     rate
   */
  public TaxAmount {
    if (net == null || rate == null || tax == null) {
      throw new ValidationException(
          "tax-amount.input-missing", "Tax amount requires a net amount, a rate and a tax");
    }
    if (!tax.equals(net.multiply(rate.asFraction()))) {
      throw new ValidationException(
          "tax-amount.inconsistent", "Tax does not equal the net amount at the given rate");
    }
  }

  /**
   * Computes the tax on a net amount.
   *
   * @param net the amount before tax
   * @param rate the rate to apply
   * @return the tax, in the net amount's currency, rounded once to the minor unit
   * @throws ValidationException if the net amount or rate is null
   */
  public static TaxAmount of(Money net, TaxRate rate) {
    if (net == null || rate == null) {
      throw new ValidationException(
          "tax-amount.input-missing", "Tax amount requires a net amount and a rate");
    }
    return new TaxAmount(net, rate, net.multiply(rate.asFraction()));
  }
}
