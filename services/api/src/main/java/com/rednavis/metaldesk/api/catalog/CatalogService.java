package com.rednavis.metaldesk.api.catalog;

import com.rednavis.metaldesk.api.catalog.dto.CategoryView;
import com.rednavis.metaldesk.api.catalog.dto.PageView;
import com.rednavis.metaldesk.api.catalog.dto.ProductDetailView;
import com.rednavis.metaldesk.api.catalog.dto.ProductSummaryView;
import com.rednavis.metaldesk.api.persistence.document.ProductDocument;
import com.rednavis.metaldesk.api.persistence.mapper.CategoryMapper;
import com.rednavis.metaldesk.api.persistence.repository.CategoryRepository;
import com.rednavis.metaldesk.api.persistence.repository.ProductRepository;
import com.rednavis.metaldesk.api.web.RequestIds;
import com.rednavis.metaldesk.share.domain.id.CategoryId;
import com.rednavis.metaldesk.share.error.NotFoundException;
import com.rednavis.metaldesk.share.error.ValidationException;
import java.util.List;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * The read side of the catalog: it queries and hands the results to {@link ProductViewAssembler}
 * (BRD FR-1.2 to FR-1.5).
 *
 * <p><strong>Search semantics (FR-1.5).</strong> {@link #search} is a case-insensitive
 * <em>substring</em> match on the product name, so {@code "gold"} and {@code "OLD B"} both find
 * "Gold bar 1 oz". It is a regular-expression scan over names, not an index lookup: the text index
 * declared for {@code products.name} matches whole words only and is not used here. There is no
 * relevance ranking — results are ordered by name — and at most {@link #SEARCH_LIMIT} are returned.
 * That is honest for a catalog of this size; a ranked search would use the text index and is a
 * separate piece of work.
 *
 * <p><strong>Paging.</strong> A category's listing is paged, at most {@link #MAX_PAGE_SIZE} to a
 * page, ordered by name then id so pages are stable.
 */
@Service
@RequiredArgsConstructor
public class CatalogService {

  /**
   * The most related products shown for one product (BRD BR-1, FR-1.4). The BRD's illustrative
   * figure, as recorded in the {@code share.domain.catalog} package Javadoc by T-012.
   */
  public static final int RELATED_CAP = 20;

  /** The most results a search returns. */
  public static final int SEARCH_LIMIT = 50;

  /** The largest page a category listing will serve. */
  public static final int MAX_PAGE_SIZE = 100;

  private static final int MIN_QUERY = 2;
  private static final int MAX_QUERY = 100;
  private static final Sort BY_NAME = Sort.by("name", "id");

  private final CategoryRepository categoryRepo;
  private final ProductRepository productRepo;
  private final CategoryMapper categoryMapper;
  private final ProductViewAssembler assembler;

  /**
   * Lists every category, by name.
   *
   * @return the categories
   */
  public Flux<CategoryView> categories() {
    return categoryRepo
        .findAll(BY_NAME)
        .map(categoryMapper::toDomain)
        .map(
            category ->
                new CategoryView(
                    category.id().value(),
                    category.name(),
                    category.parent().map(CategoryId::value).orElse(null),
                    category.taxCategory()));
  }

  /**
   * Lists one page of a category's products.
   *
   * @param categoryId the category id
   * @param page the zero-based page number
   * @param size the page size, 1 to {@link #MAX_PAGE_SIZE}
   * @return the page
   * @throws ValidationException if the id is malformed or the page or size is out of range
   * @throws NotFoundException if there is no such category
   */
  public Mono<PageView<ProductSummaryView>> productsInCategory(
      String categoryId, int page, int size) {
    RequestIds.require(categoryId, "category");
    if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
      throw new ValidationException(
          "page.invalid", "Page must be 0 or more and size 1 to " + MAX_PAGE_SIZE);
    }
    return categoryRepo
        .findById(categoryId)
        .switchIfEmpty(
            Mono.error(
                () ->
                    new NotFoundException(
                        "category.not-found", "No category with id " + categoryId)))
        .then(
            Mono.zip(
                summaries(
                    productRepo.findByCategoryId(categoryId, PageRequest.of(page, size, BY_NAME))),
                productRepo.countByCategoryId(categoryId),
                (items, total) -> new PageView<>(items, page, size, total)));
  }

  /**
   * Shows one product in full.
   *
   * @param productId the product id
   * @return the detail
   * @throws ValidationException if the id is malformed
   * @throws NotFoundException if there is no such product
   */
  public Mono<ProductDetailView> product(String productId) {
    return load(productId)
        .flatMap(
            document ->
                assembler
                    .context(List.of(document))
                    .map(context -> assembler.detail(document, context)));
  }

  /**
   * Lists other products of the same category, at most {@link #RELATED_CAP} (BRD BR-1).
   *
   * @param productId the subject product's id
   * @return the related products, never including the subject
   * @throws ValidationException if the id is malformed
   * @throws NotFoundException if there is no such product
   */
  public Flux<ProductSummaryView> related(String productId) {
    return load(productId)
        .flatMapMany(
            subject ->
                summaries(
                        productRepo.findByCategoryIdAndIdNot(
                            subject.categoryId(),
                            subject.id(),
                            PageRequest.of(0, RELATED_CAP, BY_NAME)))
                    .flatMapMany(Flux::fromIterable));
  }

  /**
   * Searches product names for text, case-insensitively, as a substring.
   *
   * @param query the text to look for, {@value #MIN_QUERY} to {@value #MAX_QUERY} characters
   * @return up to {@link #SEARCH_LIMIT} matches by name; empty when nothing matches
   * @throws ValidationException if the query is too short or too long
   */
  public Flux<ProductSummaryView> search(String query) {
    final String text = query == null ? "" : query.strip();
    if (text.length() < MIN_QUERY || text.length() > MAX_QUERY) {
      throw new ValidationException(
          "search.query-invalid",
          "Search text must be " + MIN_QUERY + " to " + MAX_QUERY + " characters");
    }
    return summaries(
            productRepo.searchByName(Pattern.quote(text), PageRequest.of(0, SEARCH_LIMIT, BY_NAME)))
        .flatMapMany(Flux::fromIterable);
  }

  private Mono<ProductDocument> load(String productId) {
    RequestIds.require(productId, "product");
    return productRepo
        .findById(productId)
        .switchIfEmpty(
            Mono.error(
                () ->
                    new NotFoundException("product.not-found", "No product with id " + productId)));
  }

  private Mono<List<ProductSummaryView>> summaries(Flux<ProductDocument> documents) {
    return documents
        .collectList()
        .flatMap(
            list ->
                list.isEmpty()
                    ? Mono.just(List.<ProductSummaryView>of())
                    : assembler
                        .context(list)
                        .map(
                            context ->
                                list.stream()
                                    .map(document -> assembler.summary(document, context))
                                    .toList()));
  }
}
