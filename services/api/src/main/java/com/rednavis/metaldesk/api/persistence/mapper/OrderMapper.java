package com.rednavis.metaldesk.api.persistence.mapper;

import com.rednavis.metaldesk.api.persistence.document.OrderDocument;
import com.rednavis.metaldesk.api.persistence.document.OrderLineDocument;
import com.rednavis.metaldesk.api.persistence.document.PaymentDocument;
import com.rednavis.metaldesk.api.persistence.document.PriceDocument;
import com.rednavis.metaldesk.api.persistence.document.QuoteDocument;
import com.rednavis.metaldesk.api.persistence.document.TaxDocument;
import com.rednavis.metaldesk.share.domain.fulfillment.DeliveryQuote;
import com.rednavis.metaldesk.share.domain.fulfillment.TransitTime;
import com.rednavis.metaldesk.share.domain.id.CategoryId;
import com.rednavis.metaldesk.share.domain.id.CustomerId;
import com.rednavis.metaldesk.share.domain.id.FulfillmentTierId;
import com.rednavis.metaldesk.share.domain.id.OrderId;
import com.rednavis.metaldesk.share.domain.id.ProductId;
import com.rednavis.metaldesk.share.domain.order.Order;
import com.rednavis.metaldesk.share.domain.order.OrderLine;
import com.rednavis.metaldesk.share.domain.order.OrderNumber;
import com.rednavis.metaldesk.share.domain.order.Quantity;
import com.rednavis.metaldesk.share.domain.payment.PaymentRecord;
import com.rednavis.metaldesk.share.domain.payment.ProviderReference;
import com.rednavis.metaldesk.share.domain.pricing.Margin;
import com.rednavis.metaldesk.share.domain.pricing.PriceRule;
import com.rednavis.metaldesk.share.domain.pricing.ReferencePrice;
import com.rednavis.metaldesk.share.domain.pricing.SellablePrice;
import com.rednavis.metaldesk.share.domain.pricing.TaxAmount;
import com.rednavis.metaldesk.share.domain.pricing.TaxRate;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Maps an {@link Order} to its {@link OrderDocument} and back.
 *
 * <p>This is where BRD BR-2's price finality survives storage: every field of an order line's price
 * and tax snapshot is written out and read back, and the domain constructors re-check that they are
 * still consistent.
 */
@Component
public class OrderMapper {

  /**
   * Rebuilds the domain order.
   *
   * @param document the stored order
   * @return the order
   */
  public Order toDomain(OrderDocument document) {
    return new Order(
        new OrderId(document.id()),
        OrderNumber.parse(document.number()),
        new CustomerId(document.customerId()),
        ValueMapper.addressToDomain(document.deliveryAddress()),
        document.lines().stream().map(OrderMapper::orderLineToDomain).toList(),
        Optional.ofNullable(document.quote()).map(OrderMapper::quoteToDomain),
        Optional.ofNullable(document.payment()).map(OrderMapper::paymentToDomain),
        document.status(),
        document.createdAt(),
        document.updatedAt());
  }

  /**
   * Builds the document to store.
   *
   * @param order the order
   * @return the document
   */
  public OrderDocument toDocument(Order order) {
    return new OrderDocument(
        order.id().value(),
        order.number().format(),
        order.customerId().value(),
        ValueMapper.addressToDocument(order.deliveryAddress()),
        order.lines().stream().map(OrderMapper::orderLineToDocument).toList(),
        order.quote().map(OrderMapper::quoteToDocument).orElse(null),
        order.payment().map(OrderMapper::paymentToDocument).orElse(null),
        order.status(),
        order.createdAt(),
        order.updatedAt());
  }

  private static OrderLine orderLineToDomain(OrderLineDocument document) {
    final TaxDocument tax = document.tax();
    return new OrderLine(
        new ProductId(document.productId()),
        document.productName(),
        new Quantity(document.quantity()),
        priceToDomain(document.price()),
        document.taxCategory(),
        new TaxAmount(
            ValueMapper.moneyToDomain(tax.net()),
            new TaxRate(new BigDecimal(tax.ratePercent())),
            ValueMapper.moneyToDomain(tax.tax())));
  }

  private static OrderLineDocument orderLineToDocument(OrderLine line) {
    final TaxAmount tax = line.tax();
    return new OrderLineDocument(
        line.productId().value(),
        line.productName(),
        line.quantity().value(),
        priceToDocument(line.price()),
        line.taxCategory(),
        new TaxDocument(
            ValueMapper.moneyToDocument(tax.net()),
            tax.rate().percent().toPlainString(),
            ValueMapper.moneyToDocument(tax.tax())));
  }

  private static SellablePrice priceToDomain(PriceDocument document) {
    final PriceRule.Scope scope =
        switch (document.scopeKind()) {
          case PRODUCT -> new PriceRule.Scope.ForProduct(new ProductId(document.scopeId()));
          case CATEGORY -> new PriceRule.Scope.ForCategory(new CategoryId(document.scopeId()));
        };
    return new SellablePrice(
        ValueMapper.moneyToDomain(document.unitPrice()),
        new PriceRule(new Margin(new BigDecimal(document.marginPercent())), scope),
        new ReferencePrice(
            document.reference().metal(),
            ValueMapper.moneyToDomain(document.reference().pricePerGram()),
            document.reference().observedAt()));
  }

  private static PriceDocument priceToDocument(SellablePrice price) {
    final PriceRule rule = price.rule();
    final ReferencePrice reference = price.reference();
    final PriceDocument.ScopeKind kind;
    final String scopeId;
    switch (rule.scope()) {
      case PriceRule.Scope.ForProduct product -> {
        kind = PriceDocument.ScopeKind.PRODUCT;
        scopeId = product.productId().value();
      }
      case PriceRule.Scope.ForCategory category -> {
        kind = PriceDocument.ScopeKind.CATEGORY;
        scopeId = category.categoryId().value();
      }
    }
    return new PriceDocument(
        ValueMapper.moneyToDocument(price.unitPrice()),
        rule.margin().percent().toPlainString(),
        kind,
        scopeId,
        new PriceDocument.ReferenceDocument(
            reference.metal(),
            ValueMapper.moneyToDocument(reference.pricePerGram()),
            reference.observedAt()));
  }

  private static DeliveryQuote quoteToDomain(QuoteDocument document) {
    return new DeliveryQuote(
        new FulfillmentTierId(document.tierId()),
        ValueMapper.moneyToDomain(document.cost()),
        new TransitTime(document.minDays(), document.maxDays()),
        document.quotedAt());
  }

  private static QuoteDocument quoteToDocument(DeliveryQuote quote) {
    return new QuoteDocument(
        quote.tierId().value(),
        ValueMapper.moneyToDocument(quote.cost()),
        quote.transit().minDays(),
        quote.transit().maxDays(),
        quote.quotedAt());
  }

  private static PaymentRecord paymentToDomain(PaymentDocument document) {
    return new PaymentRecord(
        document.providerId(),
        document.method(),
        document.status(),
        new ProviderReference(document.reference()),
        ValueMapper.moneyToDomain(document.amount()));
  }

  private static PaymentDocument paymentToDocument(PaymentRecord payment) {
    return new PaymentDocument(
        payment.providerId(),
        payment.method(),
        payment.status(),
        payment.reference().value(),
        ValueMapper.moneyToDocument(payment.amount()));
  }
}
