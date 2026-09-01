package com.rednavis.metaldesk.share.domain.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.rednavis.metaldesk.share.domain.id.CategoryId;
import com.rednavis.metaldesk.share.domain.id.ProductId;
import com.rednavis.metaldesk.share.domain.measure.Purity;
import com.rednavis.metaldesk.share.domain.measure.Weight;
import com.rednavis.metaldesk.share.domain.measure.WeightUnit;
import com.rednavis.metaldesk.share.domain.money.Currency;
import com.rednavis.metaldesk.share.domain.money.Money;
import java.util.Arrays;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ProductTest {

  private static final Money PRICE = Money.of("5899.78", Currency.EUR);

  private static Product product(Optional<Money> price) {
    final ProductSpecification specification =
        new ProductSpecification(
            Metal.GOLD, Purity.of("999.9"), Weight.of("100", WeightUnit.GRAM), Optional.empty());
    final Category category =
        new Category(
            new CategoryId("cat-bars"),
            "Gold bars",
            Optional.empty(),
            TaxCategory.INVESTMENT_GRADE);
    return new Product(
        new ProductId("p-1"),
        "  Gold bar 100 g ",
        specification,
        category,
        StockStatus.IN_STOCK,
        price);
  }

  @Test
  void pricedProductIsFixed() {
    // The typed local is the compile-time proof that price() is an Optional<Money>.
    final Optional<Money> price = product(Optional.of(PRICE)).price();
    assertEquals(Optional.of(PRICE), price);
    assertEquals(PricingMode.FIXED, product(Optional.of(PRICE)).pricingMode());
  }

  @Test
  void unpricedProductIsOnRequest() {
    final Product product = product(Optional.empty());
    assertEquals(Optional.empty(), product.price());
    assertEquals(PricingMode.ON_REQUEST, product.pricingMode());
  }

  @Test
  void nameIsTrimmed() {
    assertEquals("Gold bar 100 g", product(Optional.empty()).name());
  }

  @Test
  void productHasNoTaxCategoryOfItsOwn() {
    final boolean hasTaxField =
        Arrays.stream(Product.class.getDeclaredFields())
            .anyMatch(field -> field.getType() == TaxCategory.class);
    final boolean hasTaxAccessor =
        Arrays.stream(Product.class.getDeclaredMethods())
            .anyMatch(method -> method.getReturnType() == TaxCategory.class);
    assertFalse(hasTaxField);
    assertFalse(hasTaxAccessor);
  }

  @Test
  void taxTreatmentIsReadThroughTheCategory() {
    assertSame(TaxCategory.INVESTMENT_GRADE, product(Optional.empty()).category().taxCategory());
  }
}
