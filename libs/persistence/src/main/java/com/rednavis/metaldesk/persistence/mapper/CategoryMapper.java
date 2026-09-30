package com.rednavis.metaldesk.persistence.mapper;

import com.rednavis.metaldesk.persistence.document.CategoryDocument;
import com.rednavis.metaldesk.share.domain.catalog.Category;
import com.rednavis.metaldesk.share.domain.id.CategoryId;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Maps a {@link Category} to its {@link CategoryDocument} and back. */
@Component
public class CategoryMapper {

  /**
   * Rebuilds the domain category.
   *
   * @param document the stored category
   * @return the category
   */
  public Category toDomain(CategoryDocument document) {
    return new Category(
        new CategoryId(document.id()),
        document.name(),
        Optional.ofNullable(document.parentId()).map(CategoryId::new),
        document.taxCategory());
  }

  /**
   * Builds the document to store.
   *
   * @param category the category
   * @return the document
   */
  public CategoryDocument toDocument(Category category) {
    return new CategoryDocument(
        category.id().value(),
        category.name(),
        category.parent().map(CategoryId::value).orElse(null),
        category.taxCategory());
  }
}
