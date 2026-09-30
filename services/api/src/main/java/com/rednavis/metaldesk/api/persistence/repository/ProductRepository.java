package com.rednavis.metaldesk.api.persistence.repository;

import com.rednavis.metaldesk.api.persistence.document.ProductDocument;
import org.springframework.data.mongodb.core.query.TextCriteria;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;

/** Reactive access to stored products. */
public interface ProductRepository extends ReactiveMongoRepository<ProductDocument, String> {

  /**
   * Lists the products of one category, backed by the index on the category id.
   *
   * @param categoryId the category id
   * @return the category's products
   */
  Flux<ProductDocument> findByCategoryId(String categoryId);

  /**
   * Full-text searches product names, backed by the text index.
   *
   * @param criteria the text criteria to match
   * @return the matching products
   */
  Flux<ProductDocument> findAllBy(TextCriteria criteria);
}
