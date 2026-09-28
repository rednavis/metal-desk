package com.rednavis.metaldesk.share.domain.order;

import static com.rednavis.metaldesk.share.domain.order.OrderFixtures.eur;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.rednavis.metaldesk.share.domain.catalog.Category;
import com.rednavis.metaldesk.share.domain.catalog.Metal;
import com.rednavis.metaldesk.share.domain.catalog.Product;
import com.rednavis.metaldesk.share.domain.catalog.ProductSpecification;
import com.rednavis.metaldesk.share.domain.catalog.StockStatus;
import com.rednavis.metaldesk.share.domain.catalog.TaxCategory;
import com.rednavis.metaldesk.share.domain.id.CategoryId;
import com.rednavis.metaldesk.share.domain.id.ProductId;
import com.rednavis.metaldesk.share.domain.measure.Purity;
import com.rednavis.metaldesk.share.domain.measure.Weight;
import com.rednavis.metaldesk.share.domain.measure.WeightUnit;
import com.rednavis.metaldesk.share.domain.money.Money;
import com.rednavis.metaldesk.share.domain.pricing.TaxAmount;
import com.rednavis.metaldesk.share.domain.pricing.TaxRate;
import com.rednavis.metaldesk.share.error.ValidationException;
import java.util.Arrays;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OrderLineTest {

  private static Product product(String price, TaxCategory taxCategory) {
    final ProductSpecification spec =
        new ProductSpecification(
            Metal.GOLD,
            Purity.of("999.9"),
            Weight.of("1", WeightUnit.TROY_OUNCE),
            Optional.empty());
    final Category category =
        new Category(new CategoryId("c-1"), "Bars", Optional.empty(), taxCategory);
    return new Product(
        new ProductId("gold-bar"),
        "Gold bar 1 oz",
        spec,
        category,
        StockStatus.IN_STOCK,
        Optional.of(eur(price)));
  }

  @Test
  void netAndTaxComeFromTheSnapshot() {
    final OrderLine gold = OrderFixtures.goldLine();
    assertEquals(eur("3918.64"), gold.lineNet());
    assertEquals(eur("0.00"), gold.lineTax());
    final OrderLine silver = OrderFixtures.silverLine();
    assertEquals(eur("267.48"), silver.lineNet());
    assertEquals(eur("50.82"), silver.lineTax());
  }

  @Test
  void lineHasNothingOfTypeProduct() {
    final boolean fieldOfProduct =
        Arrays.stream(OrderLine.class.getDeclaredFields())
            .anyMatch(field -> field.getType() == Product.class);
    final boolean methodWithProduct =
        Arrays.stream(OrderLine.class.getDeclaredMethods())
            .anyMatch(
                method ->
                    method.getReturnType() == Product.class
                        || Arrays.asList(method.getParameterTypes()).contains(Product.class));
    assertFalse(fieldOfProduct);
    assertFalse(methodWithProduct);
  }

  @Test
  void laterCatalogChangesDoNotChangeTheLine() {
    final Product before = product("1959.32", TaxCategory.INVESTMENT_GRADE);
    final OrderLine line =
        OrderLine.of(
            before.id(),
            before.name(),
            Quantity.of(2),
            OrderFixtures.sellable(before.price().orElseThrow()),
            before.category().taxCategory(),
            TaxRate.forCategory(before.category().taxCategory()));
    final Money netBefore = line.lineNet();
    final Money taxBefore = line.lineTax();

    // The catalog changes afterwards: a new price, and the category is reclassified as taxable.
    final Product after = product("2500.00", TaxCategory.STANDARD);

    assertEquals(eur("2500.00"), after.price().orElseThrow());
    assertEquals(netBefore, line.lineNet());
    assertEquals(taxBefore, line.lineTax());
    assertEquals(TaxCategory.INVESTMENT_GRADE, line.taxCategory());
  }

  @Test
  void taxComputedOnAnotherNetIsRefused() {
    final OrderLine silver = OrderFixtures.silverLine();
    final TaxAmount wrong = TaxAmount.of(eur("100.00"), TaxRate.of("19"));
    assertEquals(
        "order-line.tax-net-mismatch",
        assertThrows(
                ValidationException.class,
                () ->
                    new OrderLine(
                        silver.productId(),
                        silver.productName(),
                        silver.quantity(),
                        silver.price(),
                        silver.taxCategory(),
                        wrong))
            .code());
  }

  @Test
  void zeroRatedLineWithNonZeroRateIsRefused() {
    final OrderLine gold = OrderFixtures.goldLine();
    assertEquals(
        "order-line.tax-rate-mismatch",
        assertThrows(
                ValidationException.class,
                () ->
                    OrderLine.of(
                        gold.productId(),
                        gold.productName(),
                        gold.quantity(),
                        gold.price(),
                        TaxCategory.INVESTMENT_GRADE,
                        TaxRate.of("19")))
            .code());
  }

  @Test
  void nameIsTrimmedAndBlankNameIsRefused() {
    final OrderLine gold = OrderFixtures.goldLine();
    assertEquals(
        "Gold bar",
        OrderLine.of(
                gold.productId(),
                "  Gold bar ",
                gold.quantity(),
                gold.price(),
                gold.taxCategory(),
                TaxRate.ZERO)
            .productName());
    assertEquals(
        "order-line.name-blank",
        assertThrows(
                ValidationException.class,
                () ->
                    OrderLine.of(
                        gold.productId(),
                        " ",
                        gold.quantity(),
                        gold.price(),
                        gold.taxCategory(),
                        TaxRate.ZERO))
            .code());
  }

  @Test
  void missingFieldsAreRefused() {
    final OrderLine gold = OrderFixtures.goldLine();
    assertEquals(
        "order-line.field-missing",
        assertThrows(
                ValidationException.class,
                () ->
                    OrderLine.of(
                        null,
                        gold.productName(),
                        gold.quantity(),
                        gold.price(),
                        gold.taxCategory(),
                        TaxRate.ZERO))
            .code());
    assertEquals(
        "order-line.field-missing",
        assertThrows(
                ValidationException.class,
                () ->
                    OrderLine.of(
                        gold.productId(),
                        gold.productName(),
                        gold.quantity(),
                        null,
                        gold.taxCategory(),
                        TaxRate.ZERO))
            .code());
  }
}
