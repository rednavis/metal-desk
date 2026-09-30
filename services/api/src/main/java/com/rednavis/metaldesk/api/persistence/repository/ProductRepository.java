package com.rednavis.metaldesk.api.persistence.repository;

import com.rednavis.metaldesk.persistence.document.ProductDocument;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.TextCriteria;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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
   * Lists one page of a category's products.
   *
   * @param categoryId the category id
   * @param pageable the page and its sort
   * @return the page's products
   */
  Flux<ProductDocument> findByCategoryId(String categoryId, Pageable pageable);

  /**
   * Counts a category's products.
   *
   * @param categoryId the category id
   * @return the number of products in the category
   */
  Mono<Long> countByCategoryId(String categoryId);

  /**
   * Lists products of a category other than one, backed by the index on the category id.
   *
   * @param categoryId the category id
   * @param excludedId the id of the product to leave out
   * @param pageable the page and its sort, which bounds the result
   * @return the other products in the category
   */
  Flux<ProductDocument> findByCategoryIdAndIdNot(
      String categoryId, String excludedId, Pageable pageable);

  /**
   * Finds products whose name contains text, case-insensitively.
   *
   * @param regex a regular expression, already quoted by the caller so it matches literally
   * @param pageable the page and its sort, which bounds the result
   * @return the matching products
   */
  @Query("{ 'name': { $regex: ?0, $options: 'i' } }")
  Flux<ProductDocument> searchByName(String regex, Pageable pageable);

  /**
   * Full-text searches product names, backed by the text index.
   *
   * @param criteria the text criteria to match
   * @return the matching products
   */
  Flux<ProductDocument> findAllBy(TextCriteria criteria);
}
