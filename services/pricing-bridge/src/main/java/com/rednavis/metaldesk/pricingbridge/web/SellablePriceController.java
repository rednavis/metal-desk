package com.rednavis.metaldesk.pricingbridge.web;

import com.rednavis.metaldesk.pricingbridge.pricing.SellablePriceService;
import com.rednavis.metaldesk.share.domain.catalog.ProductSpecification;
import com.rednavis.metaldesk.share.domain.id.CategoryId;
import com.rednavis.metaldesk.share.domain.id.ProductId;
import com.rednavis.metaldesk.share.domain.measure.Purity;
import com.rednavis.metaldesk.share.domain.measure.Weight;
import com.rednavis.metaldesk.share.domain.pricing.Margin;
import com.rednavis.metaldesk.share.domain.pricing.PriceRule;
import com.rednavis.metaldesk.share.domain.pricing.SellablePrice;
import com.rednavis.metaldesk.share.error.NotFoundException;
import com.rednavis.metaldesk.share.error.ValidationException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Derived prices (BRD BR-3) for a product or a category, from the latest reference price. */
@RestController
@RequestMapping("/api/sellable-prices")
@RequiredArgsConstructor
public class SellablePriceController {

  private final SellablePriceService service;

  /**
   * Prices a product described in the request.
   *
   * @param request the product and its margin rule
   * @return the sellable price and the reference it came from
   * @throws ValidationException if the request is incomplete or a value is out of range (400)
   * @throws NotFoundException if no price of the metal has been observed yet (404)
   */
  @PostMapping("/quotes")
  public SellableQuoteView quote(@RequestBody SellableQuoteRequest request) {
    final SellableQuoteRequest checked =
        Optional.ofNullable(request)
            .filter(body -> body.scope() != null && body.scope().type() != null)
            .filter(body -> body.metal() != null && body.weightUnit() != null)
            .orElseThrow(
                () ->
                    new ValidationException(
                        "quote.incomplete", "A quote needs a scope, a metal and a weight unit"));
    final ProductSpecification spec =
        new ProductSpecification(
            checked.metal(),
            Purity.of(checked.purity()),
            Weight.of(checked.weight(), checked.weightUnit()),
            Optional.empty());
    final PriceRule rule =
        new PriceRule(Margin.of(checked.marginPercent()), ruleScope(checked.scope()));
    final SellablePrice price =
        service
            .quote(spec, rule)
            .orElseThrow(
                () ->
                    new NotFoundException(
                        "reference-price.unavailable",
                        "No price of " + checked.metal() + " has been observed yet"));
    return new SellableQuoteView(
        checked.scope(),
        checked.metal(),
        price.unitPrice().amount().toPlainString(),
        price.unitPrice().currency().code(),
        rule.margin().percent().toPlainString(),
        price.reference().pricePerGram().amount().toPlainString(),
        price.reference().observedAt());
  }

  private static PriceRule.Scope ruleScope(SellableQuoteRequest.Scope scope) {
    return switch (scope.type()) {
      case PRODUCT -> new PriceRule.Scope.ForProduct(new ProductId(scope.id()));
      case CATEGORY -> new PriceRule.Scope.ForCategory(new CategoryId(scope.id()));
    };
  }
}
