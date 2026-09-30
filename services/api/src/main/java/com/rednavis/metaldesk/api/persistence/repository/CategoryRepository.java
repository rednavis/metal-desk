package com.rednavis.metaldesk.api.persistence.repository;

import com.rednavis.metaldesk.api.persistence.document.CategoryDocument;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;

/** Reactive access to stored categories. */
public interface CategoryRepository extends ReactiveMongoRepository<CategoryDocument, String> {

  /**
   * Lists the direct children of a category.
   *
   * @param parentId the parent category id
   * @return the child categories
   */
  Flux<CategoryDocument> findByParentId(String parentId);
}
