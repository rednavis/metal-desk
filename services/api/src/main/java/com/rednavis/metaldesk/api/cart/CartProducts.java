package com.rednavis.metaldesk.api.cart;

import com.rednavis.metaldesk.api.persistence.repository.ProductRepository;
import com.rednavis.metaldesk.api.web.RequestIds;
import com.rednavis.metaldesk.persistence.document.ProductDocument;
import com.rednavis.metaldesk.share.domain.catalog.StockStatus;
import com.rednavis.metaldesk.share.domain.id.ProductId;
import com.rednavis.metaldesk.share.error.NotFoundException;
import com.rednavis.metaldesk.share.error.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/** Checks that a product may be put in a cart or bought now. */
@Component
@RequiredArgsConstructor
public class CartProducts {

  private final ProductRepository products;

  /**
   * Reads a product id from a request.
   *
   * @param productId the id as sent
   * @return the id
   * @throws ValidationException {@code id.malformed} if it is not a well-formed id
   */
  public ProductId idOf(String productId) {
    return new ProductId(RequestIds.require(productId, "product"));
  }

  /**
   * Loads a product that can be bought now: it has a price and is in stock. A product whose stock
   * status is {@code ON_REQUEST} is sold by inquiry, so it is treated like an unpriced one.
   *
   * @param productId the product's id
   * @return the product
   * @throws NotFoundException {@code product.not-found} if there is none
   * @throws ValidationException {@code cart.product-unpriced} if the catalog sells it on request,
   *     {@code cart.product-out-of-stock} if it is not in stock
   */
  public Mono<ProductDocument> loadSellable(String productId) {
    return products
        .findById(productId)
        .switchIfEmpty(
            Mono.error(
                new NotFoundException("product.not-found", "No product with id " + productId)))
        .flatMap(
            product -> {
              if (product.price() == null || product.stock() == StockStatus.ON_REQUEST) {
                return Mono.error(
                    new ValidationException(
                        "cart.product-unpriced",
                        "That product is sold on request and cannot be bought here"));
              }
              return product.stock() == StockStatus.IN_STOCK
                  ? Mono.just(product)
                  : Mono.error(
                      new ValidationException(
                          "cart.product-out-of-stock", "That product is out of stock"));
            });
  }
}
