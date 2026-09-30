package com.rednavis.metaldesk.api.currency;

import com.rednavis.metaldesk.api.cart.dto.CartView;
import com.rednavis.metaldesk.api.catalog.dto.PriceView;
import com.rednavis.metaldesk.api.catalog.dto.ProductDetailView;
import com.rednavis.metaldesk.api.catalog.dto.ProductSummaryView;
import com.rednavis.metaldesk.share.domain.money.Currency;
import com.rednavis.metaldesk.share.domain.money.Money;
import com.rednavis.metaldesk.share.error.ValidationException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Shows catalog and cart views in a display currency (BRD FR-1.8).
 *
 * <p>Every amount is converted with the rate of {@link ExchangeRates} and rounded once, to the
 * minor unit of the display currency, by {@link Money}. A cart's totals are then <em>rebuilt from
 * the converted lines</em>, not converted on their own: net is the sum of the converted line nets,
 * tax the sum of the converted line taxes, total their sum, so what is shown always adds up even
 * though a cent of rounding differs from converting the totals directly. The settlement currency
 * returns the view untouched.
 */
@Component
@RequiredArgsConstructor
public class DisplayCurrencies {

  private final ExchangeRates rates;

  /**
   * Reads the {@code currency} a client asked for.
   *
   * @param code a currency code, or null or blank for the settlement currency
   * @return the currency to show prices in
   * @throws ValidationException {@code currency.unsupported} if it is unknown or has no rate
   */
  public Currency resolve(String code) {
    final Currency requested =
        code == null || code.isBlank() ? rates.settlement() : parse(code.strip());
    if (rates.rateTo(requested).isEmpty()) {
      throw unsupported(code);
    }
    return requested;
  }

  /**
   * The currencies on offer.
   *
   * @return the settlement currency first, then the others in enum order
   */
  public CurrencyOptionsView options() {
    final List<CurrencyOptionsView.CurrencyOption> options =
        Arrays.stream(Currency.values())
            .filter(currency -> rates.rateTo(currency).isPresent())
            .sorted((a, b) -> Boolean.compare(b == rates.settlement(), a == rates.settlement()))
            .map(
                currency ->
                    new CurrencyOptionsView.CurrencyOption(
                        currency.code(), rates.rateTo(currency).orElseThrow().toPlainString()))
            .toList();
    return new CurrencyOptionsView(rates.settlement().code(), rates.source(), options);
  }

  /**
   * Converts one price.
   *
   * @param price a price in the settlement currency, or null
   * @param target the display currency
   * @return the converted price, or null if there was none
   */
  public PriceView price(PriceView price, Currency target) {
    final PriceView converted;
    if (price == null || target == rates.settlement()) {
      converted = price;
    } else {
      final Money money = convert(price, target);
      converted = new PriceView(money.amount().toPlainString(), target.code());
    }
    return converted;
  }

  /**
   * Converts a product summary.
   *
   * @param view the summary
   * @param target the display currency
   * @return the summary with its price converted
   */
  public ProductSummaryView summary(ProductSummaryView view, Currency target) {
    return new ProductSummaryView(
        view.id(),
        view.name(),
        view.categoryId(),
        view.metal(),
        view.stock(),
        view.pricingMode(),
        price(view.price(), target));
  }

  /**
   * Converts a product detail.
   *
   * @param view the detail
   * @param target the display currency
   * @return the detail with its price converted
   */
  public ProductDetailView detail(ProductDetailView view, Currency target) {
    return new ProductDetailView(
        view.id(),
        view.name(),
        view.categoryId(),
        view.categoryName(),
        view.metal(),
        view.purity(),
        view.weight(),
        view.dimensions(),
        view.stock(),
        view.pricingMode(),
        price(view.price(), target),
        view.tax());
  }

  /**
   * Converts a cart: every line, and totals rebuilt from the converted lines.
   *
   * @param view the cart in the settlement currency
   * @param target the display currency
   * @return the cart in the display currency
   */
  public CartView cart(CartView view, Currency target) {
    return target == rates.settlement()
        ? view
        : CartCurrencyView.convert(view, target, price -> price(price, target));
  }

  private Money convert(PriceView price, Currency target) {
    final BigDecimal rate = rates.rateTo(target).orElseThrow();
    return Money.of(new BigDecimal(price.amount()).multiply(rate), target);
  }

  private static Currency parse(String code) {
    try {
      return Currency.valueOf(code.toUpperCase(java.util.Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new ValidationException("currency.unsupported", "Prices cannot be shown in " + code, e);
    }
  }

  private static ValidationException unsupported(String code) {
    return new ValidationException("currency.unsupported", "Prices cannot be shown in " + code);
  }
}
