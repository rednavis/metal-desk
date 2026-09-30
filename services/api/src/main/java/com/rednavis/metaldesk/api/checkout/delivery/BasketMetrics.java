package com.rednavis.metaldesk.api.checkout.delivery;

import com.rednavis.metaldesk.api.persistence.document.ProductDocument;
import com.rednavis.metaldesk.api.persistence.mapper.ValueMapper;
import com.rednavis.metaldesk.api.persistence.repository.ProductRepository;
import com.rednavis.metaldesk.share.domain.measure.Weight;
import com.rednavis.metaldesk.share.domain.measure.WeightUnit;
import com.rednavis.metaldesk.share.domain.money.Money;
import com.rednavis.metaldesk.share.domain.order.OrderLine;
import com.rednavis.metaldesk.share.domain.order.OrderTotalsCalculator;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Derives the two numbers the tiers are evaluated on (BRD FR-5.1) from a basket: the order value
 * <strong>before tax</strong>, and the total weight.
 *
 * <p>The value is the net of BRD BR-5, {@code Σ(unit price × quantity)} without tax; using the
 * gross total would move every ceiling by the tax rate. The weight is each product's specified
 * weight times its quantity, read from the catalog now and summed in grams.
 */
@Component
@RequiredArgsConstructor
public class BasketMetrics {

  private final ProductRepository products;

  /**
   * The value of the lines before tax.
   *
   * @param lines the priced lines, at least one
   * @return their net total
   */
  public Money exTaxValue(List<OrderLine> lines) {
    return OrderTotalsCalculator.compute(lines, Money.zero(lines.get(0).lineNet().currency()))
        .net();
  }

  /**
   * The total weight of the lines.
   *
   * @param lines the lines
   * @return their weight in grams; an error signal if a product is no longer in the catalog
   */
  public Mono<Weight> weight(List<OrderLine> lines) {
    return products
        .findAllById(lines.stream().map(line -> line.productId().value()).toList())
        .collectMap(ProductDocument::id)
        .map(found -> total(lines, found));
  }

  private static Weight total(List<OrderLine> lines, Map<String, ProductDocument> found) {
    BigDecimal grams = BigDecimal.ZERO;
    for (final OrderLine line : lines) {
      final Weight each =
          ValueMapper.weightToDomain(found.get(line.productId().value()).weight()).toCanonical();
      grams = grams.add(each.amount().multiply(line.quantity().asFactor()));
    }
    return new Weight(grams, WeightUnit.GRAM);
  }
}
