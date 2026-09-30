package com.rednavis.metaldesk.api.catalog;

import com.rednavis.metaldesk.api.catalog.dto.CategoryView;
import com.rednavis.metaldesk.api.catalog.dto.PageView;
import com.rednavis.metaldesk.api.catalog.dto.ProductDetailView;
import com.rednavis.metaldesk.api.catalog.dto.ProductSummaryView;
import com.rednavis.metaldesk.api.currency.DisplayCurrencies;
import com.rednavis.metaldesk.share.domain.money.Currency;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * The public catalog endpoints (BRD FR-1.2 to FR-1.5). Reads need no sign-in; making that explicit
 * in the security configuration is the auth task's (T-032).
 */
@RestController
@RequestMapping("/api/catalog")
@RequiredArgsConstructor
public class CatalogController {

  private final CatalogService service;
  private final DisplayCurrencies currencies;

  /**
   * Lists the categories (FR-1.2).
   *
   * @return every category, by name
   */
  @GetMapping("/categories")
  public Flux<CategoryView> categories() {
    return service.categories();
  }

  /**
   * Lists one page of a category's products (FR-1.2).
   *
   * @param id the category id
   * @param page the zero-based page number
   * @param size the page size, 1 to 100
   * @return the page; each entry says whether it is priced or on request
   */
  @GetMapping("/categories/{id}/products")
  public Mono<PageView<ProductSummaryView>> products(
      @PathVariable String id,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String currency) {
    final Currency shown = currencies.resolve(currency);
    return service
        .productsInCategory(id, page, size)
        .map(
            result ->
                new PageView<>(
                    result.items().stream().map(item -> currencies.summary(item, shown)).toList(),
                    result.page(),
                    result.size(),
                    result.total()));
  }

  /**
   * Shows a product in full (FR-1.3).
   *
   * @param id the product id
   * @return the detail
   */
  @GetMapping("/products/{id}")
  public Mono<ProductDetailView> product(
      @PathVariable String id, @RequestParam(required = false) String currency) {
    final Currency shown = currencies.resolve(currency);
    return service.product(id).map(view -> currencies.detail(view, shown));
  }

  /**
   * Lists products related to one, from the same category and capped (FR-1.4, BR-1).
   *
   * @param id the product id
   * @return the related products
   */
  @GetMapping("/products/{id}/related")
  public Flux<ProductSummaryView> related(
      @PathVariable String id, @RequestParam(required = false) String currency) {
    final Currency shown = currencies.resolve(currency);
    return service.related(id).map(view -> currencies.summary(view, shown));
  }

  /**
   * Searches products by name (FR-1.5). No match is an empty list, not an error.
   *
   * @param query the text to look for, a case-insensitive substring of the name
   * @return the matches
   */
  @GetMapping("/search")
  public Flux<ProductSummaryView> search(
      @RequestParam("q") String query, @RequestParam(required = false) String currency) {
    final Currency shown = currencies.resolve(currency);
    return service.search(query).map(view -> currencies.summary(view, shown));
  }
}
