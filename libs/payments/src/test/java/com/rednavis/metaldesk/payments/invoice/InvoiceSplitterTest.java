package com.rednavis.metaldesk.payments.invoice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import com.rednavis.metaldesk.share.domain.order.OrderLine;
import com.rednavis.metaldesk.share.error.ValidationException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class InvoiceSplitterTest {

  private static void assertNeverMoreThanTwo(List<OrderLine> lines) {
    assertTrue(InvoiceSplitter.split(lines).size() <= 2);
  }

  @Test
  void singleTaxCategoryGivesOneDocument() {
    final List<DocumentScope> zeroRated =
        InvoiceSplitter.split(List.of(InvoiceFixtures.goldLine()));
    final List<DocumentScope> standard =
        InvoiceSplitter.split(List.of(InvoiceFixtures.silverLine()));
    assertEquals(1, zeroRated.size());
    assertEquals(TaxCategory.INVESTMENT_GRADE, zeroRated.get(0).taxCategory());
    assertEquals(1, standard.size());
    assertEquals(TaxCategory.STANDARD, standard.get(0).taxCategory());
  }

  @Test
  void mixedOrderGivesTwoDocumentsZeroRatedFirst() {
    final List<DocumentScope> scopes =
        InvoiceSplitter.split(List.of(InvoiceFixtures.silverLine(), InvoiceFixtures.goldLine()));
    assertEquals(2, scopes.size());
    assertEquals(TaxCategory.INVESTMENT_GRADE, scopes.get(0).taxCategory());
    assertEquals(TaxCategory.STANDARD, scopes.get(1).taxCategory());
    assertEquals(List.of(InvoiceFixtures.goldLine()), scopes.get(0).lines());
    assertEquals(List.of(InvoiceFixtures.silverLine()), scopes.get(1).lines());
  }

  @Test
  void manyLinesOfBothCategoriesStillGiveTwo() {
    final List<OrderLine> many =
        List.of(
            InvoiceFixtures.goldLine(),
            InvoiceFixtures.silverLine(),
            InvoiceFixtures.line("g2", "Gold coin", 1, "500.00", TaxCategory.INVESTMENT_GRADE, "0"),
            InvoiceFixtures.line("s2", "Silver coin", 1, "20.00", TaxCategory.STANDARD, "19"));
    final List<DocumentScope> scopes = InvoiceSplitter.split(many);
    assertEquals(2, scopes.size());
    assertEquals(2, scopes.get(0).lines().size());
    assertEquals(2, scopes.get(1).lines().size());
  }

  @Test
  void neverMoreThanTwoForAnyFixtureIncludingEmptyAndSingle() {
    assertNeverMoreThanTwo(List.of());
    assertNeverMoreThanTwo(List.of(InvoiceFixtures.goldLine()));
    assertNeverMoreThanTwo(List.of(InvoiceFixtures.goldLine(), InvoiceFixtures.goldLine()));
    assertNeverMoreThanTwo(List.of(InvoiceFixtures.goldLine(), InvoiceFixtures.silverLine()));
    assertEquals(0, InvoiceSplitter.split(List.of()).size());
  }

  @Test
  void theCapIsTiedToTaxCategoryHavingExactlyTwoMembers() {
    // If this fails, TaxCategory gained a member: revisit InvoiceSplitter and FR-6.2 together.
    assertEquals(InvoiceSplitter.MAX_DOCUMENTS, TaxCategory.values().length);
    assertEquals(2, InvoiceSplitter.MAX_DOCUMENTS);
  }

  @Test
  void snapshottedCategoryDecidesNotLaterCatalogChange() {
    final ProductSpecification spec =
        new ProductSpecification(
            Metal.GOLD,
            Purity.of("999.9"),
            Weight.of("1", WeightUnit.TROY_OUNCE),
            Optional.empty());
    final Product before =
        new Product(
            new ProductId("gold-bar"),
            "Gold bar 1 oz",
            spec,
            new Category(
                new CategoryId("c-1"), "Bars", Optional.empty(), TaxCategory.INVESTMENT_GRADE),
            StockStatus.IN_STOCK,
            Optional.of(InvoiceFixtures.eur("1959.32")));
    final OrderLine line =
        InvoiceFixtures.line(
            before.id().value(), before.name(), 2, "1959.32", TaxCategory.INVESTMENT_GRADE, "0");
    final List<DocumentScope> expected = InvoiceSplitter.split(List.of(line));

    // The category is reclassified afterwards; the settled order's split must not move.
    final Product after =
        new Product(
            before.id(),
            before.name(),
            spec,
            new Category(new CategoryId("c-1"), "Bars", Optional.empty(), TaxCategory.STANDARD),
            StockStatus.IN_STOCK,
            before.price());

    assertEquals(TaxCategory.STANDARD, after.category().taxCategory());
    assertEquals(expected, InvoiceSplitter.split(List.of(line)));
    assertEquals(TaxCategory.INVESTMENT_GRADE, expected.get(0).taxCategory());
  }

  @Test
  void splitterAndScopeNeverMentionTheCatalogEntryType() {
    for (final Class<?> type : List.of(InvoiceSplitter.class, DocumentScope.class)) {
      assertFalse(
          Arrays.stream(type.getDeclaredMethods())
              .anyMatch(
                  method ->
                      method.getReturnType() == Product.class
                          || Arrays.asList(method.getParameterTypes()).contains(Product.class)),
          type.getSimpleName());
      assertFalse(
          Arrays.stream(type.getDeclaredFields())
              .anyMatch(field -> field.getType() == Product.class),
          type.getSimpleName());
    }
  }

  @Test
  void invalidInputAndMixedScopesAreRefused() {
    final List<OrderLine> withNull = Arrays.asList(InvoiceFixtures.goldLine(), null);
    assertEquals(
        "invoice-splitter.lines-invalid",
        assertThrows(ValidationException.class, () -> InvoiceSplitter.split(null)).code());
    assertEquals(
        "invoice-splitter.lines-invalid",
        assertThrows(ValidationException.class, () -> InvoiceSplitter.split(withNull)).code());
    final List<OrderLine> gold = List.of(InvoiceFixtures.goldLine());
    assertEquals(
        "document-scope.mixed",
        assertThrows(ValidationException.class, () -> new DocumentScope(TaxCategory.STANDARD, gold))
            .code());
  }

  @Test
  void splitterOffersOnlyTheSplitOperation() {
    assertEquals(
        List.of("split"),
        Arrays.stream(InvoiceSplitter.class.getDeclaredMethods())
            .filter(method -> !method.isSynthetic())
            .map(Method::getName)
            .toList());
  }
}
