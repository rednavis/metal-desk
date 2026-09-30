package com.rednavis.metaldesk.api.cart;

import com.rednavis.metaldesk.api.catalog.ProductViewAssembler;
import com.rednavis.metaldesk.api.catalog.SellablePricing;
import com.rednavis.metaldesk.api.persistence.repository.ProductRepository;
import com.rednavis.metaldesk.persistence.document.ProductDocument;
import com.rednavis.metaldesk.persistence.mapper.ProductMapper;
import com.rednavis.metaldesk.share.domain.catalog.Category;
import com.rednavis.metaldesk.share.domain.catalog.Product;
import com.rednavis.metaldesk.share.domain.order.OrderLine;
import com.rednavis.metaldesk.share.domain.pricing.SellablePrice;
import com.rednavis.metaldesk.share.domain.pricing.TaxRate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Prices cart lines <strong>now</strong>, on every call, through the same derivation the catalog
 * uses ({@link SellablePricing}, which is {@code PriceDerivation} underneath). Nothing is cached on
 * the cart, so a market move shows up on the next read (BRD BR-3); the snapshot that makes a price
 * final happens later, when the order line is created (BR-2).
 *
 * <p>A line whose product has no sellable price right now, or has left the catalog, comes back
 * unpriced rather than being dropped, so the customer sees it and checkout can refuse it.
 */
@Component
@RequiredArgsConstructor
public class CartPricing {

  private final ProductRepository products;
  private final ProductViewAssembler assembler;
  private final ProductMapper productMapper;
  private final SellablePricing pricing;

  /**
   * Prices a list of cart lines.
   *
   * @param lines the lines
   * @return one priced line per cart line, in order
   */
  public Mono<List<PricedLine>> price(List<CartLine> lines) {
    return lines.isEmpty()
        ? Mono.just(List.of())
        : products
            .findAllById(lines.stream().map(line -> line.productId().value()).toList())
            .collectMap(ProductDocument::id)
            .flatMap(
                found ->
                    assembler
                        .context(List.copyOf(found.values()))
                        .map(
                            context ->
                                lines.stream().map(line -> one(line, found, context)).toList()));
  }

  private PricedLine one(
      CartLine line, Map<String, ProductDocument> found, ProductViewAssembler.Context context) {
    final ProductDocument document = found.get(line.productId().value());
    final Category category =
        document == null ? null : context.categories().get(document.categoryId());
    PricedLine result = new PricedLine(line, line.productId().value(), Optional.empty());
    if (document != null && category != null) {
      final Product product = productMapper.toDomain(document, category);
      final Optional<SellablePrice> price = pricing.sellable(product, context.rules());
      result =
          new PricedLine(
              line,
              product.name(),
              price.map(
                  sellable ->
                      OrderLine.of(
                          product.id(),
                          product.name(),
                          line.quantity(),
                          sellable,
                          category.taxCategory(),
                          TaxRate.forCategory(category.taxCategory()))));
    }
    return result;
  }
}
