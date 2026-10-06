package com.rednavis.metaldesk.persistence.mapper;

import com.rednavis.metaldesk.persistence.document.ProductDocument;
import com.rednavis.metaldesk.share.domain.catalog.Category;
import com.rednavis.metaldesk.share.domain.catalog.Product;
import com.rednavis.metaldesk.share.domain.catalog.ProductSpecification;
import com.rednavis.metaldesk.share.domain.id.ProductId;
import com.rednavis.metaldesk.share.domain.measure.Purity;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Maps a {@link Product} to its {@link ProductDocument} and back.
 *
 * <p>A stored product holds its category's id only, so rebuilding the domain product needs the
 * category from the caller — that join is the service's job, and it keeps a renamed or reclassified
 * category from leaving stale copies inside every product.
 */
@Component
public class ProductMapper {

  /**
   * Rebuilds the domain product.
   *
   * @param document the stored product
   * @param category the category the document's {@code categoryId} refers to
   * @return the product
   * @throws IllegalArgumentException if the category is not the one the document refers to
   */
  public Product toDomain(ProductDocument document, Category category) {
    if (!category.id().value().equals(document.categoryId())) {
      throw new IllegalArgumentException(
          "Category " + category.id().value() + " is not product's " + document.categoryId());
    }
    return new Product(
        new ProductId(document.id()),
        document.name(),
        new ProductSpecification(
            document.metal(),
            new Purity(new BigDecimal(document.purity())),
            ValueMapper.weightToDomain(document.weight()),
            Optional.ofNullable(document.dimensions())),
        category,
        document.stock(),
        Optional.ofNullable(document.price()).map(ValueMapper::moneyToDomain));
  }

  /**
   * Builds the document to store.
   *
   * @param product the product
   * @return the document, holding only the category's id
   */
  public ProductDocument toDocument(Product product) {
    final ProductSpecification specification = product.specification();
    return new ProductDocument(
        product.id().value(),
        product.name(),
        product.category().id().value(),
        specification.metal(),
        specification.purity().partsPerThousand().toPlainString(),
        ValueMapper.weightToDocument(specification.weight()),
        specification.dimensions().orElse(null),
        product.stock(),
        product.price().map(ValueMapper::moneyToDocument).orElse(null));
  }
}
