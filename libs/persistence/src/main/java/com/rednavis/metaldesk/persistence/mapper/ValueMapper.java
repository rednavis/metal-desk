package com.rednavis.metaldesk.persistence.mapper;

import com.rednavis.metaldesk.persistence.document.AddressDocument;
import com.rednavis.metaldesk.persistence.document.MoneyDocument;
import com.rednavis.metaldesk.persistence.document.WeightDocument;
import com.rednavis.metaldesk.share.domain.Region;
import com.rednavis.metaldesk.share.domain.customer.Address;
import com.rednavis.metaldesk.share.domain.measure.Weight;
import com.rednavis.metaldesk.share.domain.money.Currency;
import com.rednavis.metaldesk.share.domain.money.Money;
import java.math.BigDecimal;

/**
 * The mapping of the value types more than one aggregate embeds: money, weight and addresses.
 *
 * <p>Amounts travel as decimal text, so the scale a {@link Money} was built with is exactly the
 * scale it reads back with.
 */
public final class ValueMapper {

  private ValueMapper() {}

  /**
   * Builds the stored form of an amount.
   *
   * @param money the amount
   * @return the document
   */
  public static MoneyDocument moneyToDocument(Money money) {
    return new MoneyDocument(money.amount().toPlainString(), money.currency().code());
  }

  /**
   * Rebuilds an amount.
   *
   * @param document the stored amount
   * @return the amount, at its currency's scale
   */
  public static Money moneyToDomain(MoneyDocument document) {
    return Money.of(document.amount(), Currency.valueOf(document.currency()));
  }

  /**
   * Builds the stored form of a weight.
   *
   * @param weight the weight
   * @return the document
   */
  public static WeightDocument weightToDocument(Weight weight) {
    return new WeightDocument(weight.amount().toPlainString(), weight.unit());
  }

  /**
   * Rebuilds a weight.
   *
   * @param document the stored weight
   * @return the weight
   */
  public static Weight weightToDomain(WeightDocument document) {
    return new Weight(new BigDecimal(document.amount()), document.unit());
  }

  /**
   * Builds the stored form of an address.
   *
   * @param address the address
   * @return the document
   */
  public static AddressDocument addressToDocument(Address address) {
    return new AddressDocument(
        address.kind(),
        address.street(),
        address.city(),
        address.country().code(),
        address.postalCode(),
        address.companyName(),
        address.companyAddress());
  }

  /**
   * Rebuilds an address.
   *
   * @param document the stored address
   * @return the address
   */
  public static Address addressToDomain(AddressDocument document) {
    return new Address(
        document.kind(),
        document.street(),
        document.city(),
        new Region(document.country()),
        document.postalCode(),
        document.companyName(),
        document.companyAddress());
  }
}
