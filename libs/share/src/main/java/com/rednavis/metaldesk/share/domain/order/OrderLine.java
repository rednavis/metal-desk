package com.rednavis.metaldesk.share.domain.order;

import com.rednavis.metaldesk.share.domain.catalog.TaxCategory;
import com.rednavis.metaldesk.share.domain.id.ProductId;
import com.rednavis.metaldesk.share.domain.money.Money;
import com.rednavis.metaldesk.share.domain.pricing.SellablePrice;
import com.rednavis.metaldesk.share.domain.pricing.TaxAmount;
import com.rednavis.metaldesk.share.domain.pricing.TaxRate;
import com.rednavis.metaldesk.share.error.ValidationException;

/**
 * One line of an order, <em>as it was when the order was placed</em> (BRD BR-2).
 *
 * <p><strong>Nothing here is resolved on read.</strong> The line holds the product's id and a copy
 * of its name for display, and a snapshot of everything that priced it: the whole {@link
 * SellablePrice} (which itself carries the margin rule and the reference price behind it), the
 * resolved tax classification, and the computed tax. It has no reference to the catalog entry and
 * no method that takes one, because a line that looked the price or tax up live would make order
 * history (FR-10.1) change under the customer when a margin or a tax rule changed (Architecture
 * section 3: "OrderLine snapshots price and tax category at order time").
 *
 * <p>The canonical constructor refuses a snapshot that contradicts itself: a tax computed on a
 * different net than {@link #lineNet()}, or a zero-rated classification carrying a non-zero rate.
 * {@link #of} builds a consistent line from a resolved rate.
 *
 * @param productId the product ordered, never null
 * @param productName the product's name when ordered, trimmed and never blank
 * @param quantity how many units, never null
 * @param price the unit price with its provenance, never null
 * @param taxCategory the tax classification resolved when ordered, never null
 * @param tax the tax on this line's net, never null
 */
public record OrderLine(
    ProductId productId,
    String productName,
    Quantity quantity,
    SellablePrice price,
    TaxCategory taxCategory,
    TaxAmount tax) {

  /**
   * Validates the fields and that the snapshot is consistent.
   *
   * @throws ValidationException if a field is null, the name is blank, the tax was not computed on
   *     the line's net, or a zero-rated classification carries a non-zero rate
   */
  public OrderLine {
    requireFields(productId, quantity, price, taxCategory);
    if (productName == null || productName.isBlank()) {
      throw new ValidationException(
          "order-line.name-blank", "Order line product name must not be null or blank");
    }
    productName = productName.strip();
    requireConsistentTax(quantity, price, taxCategory, tax);
  }

  private static void requireFields(
      ProductId productId, Quantity quantity, SellablePrice price, TaxCategory taxCategory) {
    if (productId == null || quantity == null || price == null || taxCategory == null) {
      throw new ValidationException(
          "order-line.field-missing",
          "Order line requires a product id, quantity, price and tax category");
    }
  }

  private static void requireConsistentTax(
      Quantity quantity, SellablePrice price, TaxCategory taxCategory, TaxAmount tax) {
    if (tax == null) {
      throw new ValidationException("order-line.tax-missing", "Order line tax must not be null");
    }
    if (!tax.net().equals(netOf(price, quantity))) {
      throw new ValidationException(
          "order-line.tax-net-mismatch", "Order line tax was not computed on the line's net");
    }
    if (taxCategory.isZeroRated() && !tax.rate().isZero()) {
      throw new ValidationException(
          "order-line.tax-rate-mismatch", "A zero-rated line cannot carry a non-zero tax rate");
    }
  }

  /**
   * Builds a line, computing its tax from a resolved rate.
   *
   * @param productId the product ordered
   * @param productName the product's name when ordered
   * @param quantity how many units
   * @param price the unit price with its provenance
   * @param taxCategory the tax classification resolved when ordered
   * @param rate the rate the classification resolved to, see {@link TaxRate#forCategory}
   * @return the line, with its tax computed once on its net
   * @throws ValidationException if an argument is null or the line would be inconsistent
   */
  public static OrderLine of(
      ProductId productId,
      String productName,
      Quantity quantity,
      SellablePrice price,
      TaxCategory taxCategory,
      TaxRate rate) {
    if (quantity == null || price == null || rate == null) {
      throw new ValidationException(
          "order-line.field-missing", "Order line requires a quantity, price and tax rate");
    }
    return new OrderLine(
        productId,
        productName,
        quantity,
        price,
        taxCategory,
        TaxAmount.of(netOf(price, quantity), rate));
  }

  /**
   * Returns the line's amount before tax.
   *
   * @return the unit price times the quantity
   */
  public Money lineNet() {
    return netOf(price, quantity);
  }

  /**
   * Returns the tax on the line.
   *
   * @return the tax computed when the order was placed
   */
  public Money lineTax() {
    return tax.tax();
  }

  private static Money netOf(SellablePrice price, Quantity quantity) {
    return price.unitPrice().multiply(quantity.asFactor());
  }
}
