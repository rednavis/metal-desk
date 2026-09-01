package com.rednavis.metaldesk.share.domain.pricing;

import com.rednavis.metaldesk.share.domain.catalog.TaxCategory;
import com.rednavis.metaldesk.share.error.ValidationException;
import java.math.BigDecimal;

/**
 * A resolved tax rate, as a percentage (BRD BR-4).
 *
 * <p>{@link TaxCategory} carries a rate <em>source</em>, not a rate. This type is what the source
 * resolves to: {@link #forCategory(TaxCategory, Resolver)} answers zero for a zero-rated category
 * because that is a legal classification, and asks a {@link Resolver} for everything else because
 * the standard rate is configuration, not a compiled-in constant.
 *
 * <p>The valid range is 0 to 100 percent inclusive. The value is an exact decimal, stripped of
 * trailing zeros at construction so {@code 19.0} equals {@code 19}.
 *
 * @param percent the rate in percent, in the range [0, 100]
 */
public record TaxRate(BigDecimal percent) {

  /** The zero rate: what investment-grade metal products carry (BRD BR-4). */
  public static final TaxRate ZERO = new TaxRate(BigDecimal.ZERO);

  /**
   * Validates the range and normalises the scale.
   *
   * @throws ValidationException if the value is null, negative or above 100
   */
  public TaxRate {
    if (percent == null) {
      throw new ValidationException("tax-rate.required", "Tax rate requires a percentage");
    }
    // Not a static constant: ZERO above is built during class initialisation, and would run this
    // constructor before any constant declared below it had a value.
    if (percent.signum() < 0 || percent.compareTo(BigDecimal.valueOf(100)) > 0) {
      throw new ValidationException(
          "tax-rate.out-of-range", "Tax rate must be between 0 and 100%, was " + percent + "%");
    }
    final BigDecimal stripped = percent.stripTrailingZeros();
    percent = stripped.scale() < 0 ? stripped.setScale(0) : stripped;
  }

  /**
   * Creates a rate from its decimal text form.
   *
   * @param percent the decimal text, for example {@code "19"}
   * @return the rate
   * @throws ValidationException if the text is not a decimal number or is out of range
   */
  public static TaxRate of(String percent) {
    if (percent == null) {
      throw new ValidationException("tax-rate.required", "Tax rate requires a percentage");
    }
    try {
      return new TaxRate(new BigDecimal(percent));
    } catch (NumberFormatException e) {
      throw new ValidationException(
          "tax-rate.malformed", "Not a decimal percentage: " + percent, e);
    }
  }

  /**
   * Resolves the rate of a tax category using the illustrative {@linkplain Resolver#defaults()
   * default rates}. Intended for tests and local runs; a deployment supplies its own resolver.
   *
   * @param category the category's tax classification
   * @return the rate
   * @throws ValidationException if the category is null
   */
  public static TaxRate forCategory(TaxCategory category) {
    return forCategory(category, Resolver.defaults());
  }

  /**
   * Resolves the rate of a tax category. A zero-rated category is {@link #ZERO} whatever the
   * resolver says; any other category's configuration reference is handed to the resolver.
   *
   * @param category the category's tax classification
   * @param resolver looks up a configured rate by reference
   * @return the rate
   * @throws ValidationException if an argument is null, or the resolver gives no rate for the
   *     category's reference
   */
  public static TaxRate forCategory(TaxCategory category, Resolver resolver) {
    if (category == null || resolver == null) {
      throw new ValidationException(
          "tax-rate.input-missing", "Tax rate resolution requires a category and a resolver");
    }
    return switch (category.rateSource()) {
      case TaxCategory.RateSource.Zero() -> ZERO;
      case TaxCategory.RateSource.Configured(String reference) -> {
        final TaxRate resolved = resolver.resolve(reference);
        if (resolved == null) {
          throw new ValidationException(
              "tax-rate.unresolved", "No tax rate is configured for reference " + reference);
        }
        yield resolved;
      }
    };
  }

  /**
   * Tells whether the rate is zero.
   *
   * @return {@code true} if the rate is zero
   */
  public boolean isZero() {
    return percent.signum() == 0;
  }

  /**
   * Returns the rate as a fraction, exactly: 19 percent is {@code 0.19}.
   *
   * @return the percentage divided by 100, without rounding
   */
  public BigDecimal asFraction() {
    return percent.movePointLeft(2);
  }

  /** Looks up a configured tax rate by the reference a {@link TaxCategory} carries. */
  @FunctionalInterface
  public interface Resolver {

    /**
     * Returns the rate configured for a reference.
     *
     * @param reference the configuration key, for example {@code "standard"}
     * @return the configured rate, or {@code null} when none is configured
     */
    TaxRate resolve(String reference);

    /**
     * Returns a resolver holding an illustrative standard rate of 19 percent, the German VAT rate
     * for the home market the legacy platform served. It is a stand-in for configuration: tests and
     * local runs use it, and a deployment supplies its own resolver because the rate must be
     * reviewed against current tax law wherever the platform operates (BRD BR-4).
     *
     * @return a resolver that knows only the {@code "standard"} reference
     */
    static Resolver defaults() {
      final TaxRate standard = new TaxRate(BigDecimal.valueOf(19));
      return reference -> "standard".equals(reference) ? standard : null;
    }
  }
}
