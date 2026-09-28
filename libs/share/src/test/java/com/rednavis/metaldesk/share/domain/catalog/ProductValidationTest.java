package com.rednavis.metaldesk.share.domain.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.rednavis.metaldesk.share.domain.id.CategoryId;
import com.rednavis.metaldesk.share.domain.id.ProductId;
import com.rednavis.metaldesk.share.domain.measure.Purity;
import com.rednavis.metaldesk.share.domain.measure.Weight;
import com.rednavis.metaldesk.share.domain.measure.WeightUnit;
import com.rednavis.metaldesk.share.error.ValidationException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ProductValidationTest {

  private static final ProductId ID = new ProductId("p-1");
  private static final String NAME = "Gold bar";
  private static final ProductSpecification SPECIFICATION =
      new ProductSpecification(
          Metal.GOLD, Purity.of("999.9"), Weight.of("100", WeightUnit.GRAM), Optional.empty());
  private static final Category CATEGORY =
      new Category(
          new CategoryId("cat-bars"), "Gold bars", Optional.empty(), TaxCategory.INVESTMENT_GRADE);

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\t"})
  void blankNameIsRefused(String name) {
    assertEquals(
        "product.name-blank",
        assertThrows(
                ValidationException.class,
                () ->
                    new Product(
                        ID, name, SPECIFICATION, CATEGORY, StockStatus.IN_STOCK, Optional.empty()))
            .code());
  }

  @Test
  void nullCategoryIsRefused() {
    assertEquals(
        "product.category-missing",
        assertThrows(
                ValidationException.class,
                () ->
                    new Product(
                        ID, NAME, SPECIFICATION, null, StockStatus.IN_STOCK, Optional.empty()))
            .code());
  }

  @Test
  void nullIdIsRefused() {
    assertEquals(
        "product.id-missing",
        assertThrows(
                ValidationException.class,
                () ->
                    new Product(
                        null,
                        NAME,
                        SPECIFICATION,
                        CATEGORY,
                        StockStatus.IN_STOCK,
                        Optional.empty()))
            .code());
  }

  @Test
  void nullSpecificationIsRefused() {
    assertEquals(
        "product.specification-missing",
        assertThrows(
                ValidationException.class,
                () -> new Product(ID, NAME, null, CATEGORY, StockStatus.IN_STOCK, Optional.empty()))
            .code());
  }

  @Test
  void nullStockIsRefused() {
    assertEquals(
        "product.stock-missing",
        assertThrows(
                ValidationException.class,
                () -> new Product(ID, NAME, SPECIFICATION, CATEGORY, null, Optional.empty()))
            .code());
  }

  @Test
  void nullPriceOptionalIsRefused() {
    assertEquals(
        "product.price-missing",
        assertThrows(
                ValidationException.class,
                () -> new Product(ID, NAME, SPECIFICATION, CATEGORY, StockStatus.IN_STOCK, null))
            .code());
  }
}
