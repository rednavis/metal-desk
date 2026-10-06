package com.rednavis.metaldesk.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.rednavis.metaldesk.persistence.document.CustomerDocument;
import com.rednavis.metaldesk.persistence.document.OrderDocument;
import com.rednavis.metaldesk.persistence.document.ProductDocument;
import com.rednavis.metaldesk.persistence.fixtures.AccountFixtures;
import com.rednavis.metaldesk.persistence.fixtures.CatalogFixtures;
import com.rednavis.metaldesk.persistence.fixtures.OrderFixtures;
import com.rednavis.metaldesk.persistence.mapper.CategoryMapper;
import com.rednavis.metaldesk.persistence.mapper.CustomerMapper;
import com.rednavis.metaldesk.persistence.mapper.FulfillmentTierMapper;
import com.rednavis.metaldesk.persistence.mapper.OrderMapper;
import com.rednavis.metaldesk.persistence.mapper.ProductMapper;
import com.rednavis.metaldesk.share.domain.catalog.Category;
import com.rednavis.metaldesk.share.domain.catalog.Product;
import com.rednavis.metaldesk.share.domain.customer.Customer;
import com.rednavis.metaldesk.share.domain.fulfillment.FulfillmentTier;
import com.rednavis.metaldesk.share.domain.order.Order;
import org.junit.jupiter.api.Test;

/**
 * Every mapper round-trips a fixture of its aggregate to an equal value, with no database: a field
 * the mapping forgets shows up here as an inequality.
 */
class MapperRoundTripTest {

  private final OrderMapper orders = new OrderMapper();
  private final CustomerMapper customers = new CustomerMapper();
  private final ProductMapper products = new ProductMapper();
  private final CategoryMapper categories = new CategoryMapper();
  private final FulfillmentTierMapper tiers = new FulfillmentTierMapper();

  @Test
  void newOrderWithoutQuoteOrPaymentRoundTrips() {
    final Order order = OrderFixtures.newOrder("o-1", OrderFixtures.number(1));

    assertEquals(order, orders.toDomain(orders.toDocument(order)));
  }

  @Test
  void paidOrderKeepsItsQuotePaymentAndLineSnapshots() {
    final Order order = OrderFixtures.paidOrder("o-2", OrderFixtures.number(2));

    final Order restored = orders.toDomain(orders.toDocument(order));

    assertEquals(order, restored);
    assertEquals(order.lines().get(1).price(), restored.lines().get(1).price());
    assertEquals(order.lines().get(1).tax(), restored.lines().get(1).tax());
    assertEquals(order.totals(), restored.totals());
  }

  @Test
  void moneyKeepsItsScale() {
    final OrderDocument document =
        orders.toDocument(OrderFixtures.paidOrder("o-3", OrderFixtures.number(3)));

    assertEquals("1959.32", document.lines().get(0).price().unitPrice().amount());
    assertEquals("0.00", document.lines().get(0).tax().tax().amount());
    assertEquals("12.50", document.quote().cost().amount());
    assertEquals("280920260003", document.number());
  }

  @Test
  void customersRoundTripWithAndWithoutPhone() {
    final Customer withPhone = AccountFixtures.customer("c-1", "ann@example.com", "+49 30 1234567");
    final Customer withoutPhone = AccountFixtures.customer("c-2", "bob@example.com", null);

    assertEquals(withPhone, customers.toDomain(customers.toDocument(withPhone)));
    assertEquals(withoutPhone, customers.toDomain(customers.toDocument(withoutPhone)));
  }

  @Test
  void customerDocumentWithoutAddressesReadsAsEmpty() {
    final CustomerDocument document =
        new CustomerDocument("c-3", "Cy", "cy@example.com", null, null, null);

    assertEquals(0, document.addresses().size());
  }

  @Test
  void categoriesRoundTripWithAndWithoutParent() {
    for (final Category category :
        new Category[] {CatalogFixtures.rootCategory(), CatalogFixtures.childCategory()}) {
      assertEquals(category, categories.toDomain(categories.toDocument(category)));
    }
  }

  @Test
  void productsRoundTripFixedPricedAndOnRequest() {
    final Category category = CatalogFixtures.rootCategory();
    for (final Product product :
        new Product[] {
          CatalogFixtures.fixedPriceProduct("p-1", category),
          CatalogFixtures.onRequestProduct("p-2", category)
        }) {
      assertEquals(product, products.toDomain(products.toDocument(product), category));
    }
  }

  @Test
  void productStoresItsCategoryByIdOnly() {
    final Product product =
        CatalogFixtures.fixedPriceProduct("p-3", CatalogFixtures.rootCategory());

    assertEquals("cat-bars", products.toDocument(product).categoryId());
  }

  @Test
  void productIsNotRebuiltWithSomeOtherCategory() {
    final ProductDocument document =
        products.toDocument(
            CatalogFixtures.fixedPriceProduct("p-4", CatalogFixtures.rootCategory()));
    final Category other = CatalogFixtures.childCategory();

    assertThrows(IllegalArgumentException.class, () -> products.toDomain(document, other));
  }

  @Test
  void tiersRoundTrip() {
    final FulfillmentTier tier = CatalogFixtures.tier("t-1", "de");

    assertEquals(tier, tiers.toDomain(tiers.toDocument(tier)));
  }
}
