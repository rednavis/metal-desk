package com.rednavis.metaldesk.share.domain.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.rednavis.metaldesk.share.domain.id.CategoryId;
import com.rednavis.metaldesk.share.error.ValidationException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class CategoryTest {

  private static final CategoryId ID = new CategoryId("cat-1");
  private static final String NAME = "Bars";

  @Test
  void parentIsOptional() {
    final Category root =
        new Category(ID, "Precious metals", Optional.empty(), TaxCategory.STANDARD);
    assertEquals(Optional.empty(), root.parent());
  }

  @Test
  void parentMayBeAnotherCategory() {
    final CategoryId parent = new CategoryId("cat-0");
    final Category child =
        new Category(ID, NAME, Optional.of(parent), TaxCategory.INVESTMENT_GRADE);
    assertEquals(Optional.of(parent), child.parent());
  }

  @Test
  void parentEqualToOwnIdIsRefused() {
    assertEquals(
        "category.parent-self",
        assertThrows(
                ValidationException.class,
                () -> new Category(ID, NAME, Optional.of(ID), TaxCategory.STANDARD))
            .code());
  }

  @Test
  void nameIsTrimmed() {
    assertEquals(
        NAME, new Category(ID, "  " + NAME + " ", Optional.empty(), TaxCategory.STANDARD).name());
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\t"})
  void blankNameIsRefused(String name) {
    assertEquals(
        "category.name-blank",
        assertThrows(
                ValidationException.class,
                () -> new Category(ID, name, Optional.empty(), TaxCategory.STANDARD))
            .code());
  }

  @Test
  void nullIdIsRefused() {
    assertEquals(
        "category.id-missing",
        assertThrows(
                ValidationException.class,
                () -> new Category(null, NAME, Optional.empty(), TaxCategory.STANDARD))
            .code());
  }

  @Test
  void nullParentOptionalIsRefused() {
    assertEquals(
        "category.parent-missing",
        assertThrows(
                ValidationException.class, () -> new Category(ID, NAME, null, TaxCategory.STANDARD))
            .code());
  }

  @Test
  void nullTaxCategoryIsRefused() {
    assertEquals(
        "category.tax-category-missing",
        assertThrows(
                ValidationException.class, () -> new Category(ID, NAME, Optional.empty(), null))
            .code());
  }
}
