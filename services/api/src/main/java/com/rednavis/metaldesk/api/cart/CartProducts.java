package com.rednavis.metaldesk.api.cart;

import com.rednavis.metaldesk.api.persistence.document.ProductDocument;
import com.rednavis.metaldesk.api.persistence.repository.ProductRepository;
import com.rednavis.metaldesk.api.web.RequestIds;
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
   * Loads a product the catalog sells at a price.
   *
   * @param productId the product's id
   * @return the product
   * @throws NotFoundException {@code product.not-found} if there is none
   * @throws ValidationException {@code cart.product-unpriced} if the catalog sells it on request
   */
  public Mono<ProductDocument> loadSellable(String productId) {
    return products
        .findById(productId)
        .switchIfEmpty(
            Mono.error(
                new NotFoundException("product.not-found", "No product with id " + productId)))
        .flatMap(
            product ->
                product.price() == null
                    ? Mono.error(
                        new ValidationException(
                            "cart.product-unpriced",
                            "That product is sold on request and cannot be bought here"))
                    : Mono.just(product));
  }
}
