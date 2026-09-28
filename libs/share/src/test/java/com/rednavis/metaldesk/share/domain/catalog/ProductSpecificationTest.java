package com.rednavis.metaldesk.share.domain.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.rednavis.metaldesk.share.domain.measure.Purity;
import com.rednavis.metaldesk.share.domain.measure.Weight;
import com.rednavis.metaldesk.share.domain.measure.WeightUnit;
import com.rednavis.metaldesk.share.error.ValidationException;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ProductSpecificationTest {

  private static final Purity PURITY = Purity.of("999.9");
  private static final Weight WEIGHT = Weight.of("100", WeightUnit.GRAM);

  @Test
  void holdsTheFr13FieldSet() {
    final ProductSpecification spec =
        new ProductSpecification(Metal.GOLD, PURITY, WEIGHT, Optional.of("50 x 30 x 2 mm"));
    assertEquals(Metal.GOLD, spec.metal());
    assertEquals(PURITY, spec.purity());
    assertEquals(WEIGHT, spec.weight());
    assertEquals(Optional.of("50 x 30 x 2 mm"), spec.dimensions());
  }

  @Test
  void dimensionsAreTrimmed() {
    assertEquals(
        Optional.of("50 mm"),
        new ProductSpecification(Metal.SILVER, PURITY, WEIGHT, Optional.of(" 50 mm "))
            .dimensions());
  }

  @Test
  void nullPurityIsRefused() {
    assertEquals(
        "specification.purity-missing",
        assertThrows(
                ValidationException.class,
                () -> new ProductSpecification(Metal.GOLD, null, WEIGHT, Optional.empty()))
            .code());
  }

  @Test
  void nullWeightIsRefused() {
    assertEquals(
        "specification.weight-missing",
        assertThrows(
                ValidationException.class,
                () -> new ProductSpecification(Metal.GOLD, PURITY, null, Optional.empty()))
            .code());
  }

  @Test
  void nullMetalIsRefused() {
    assertEquals(
        "specification.metal-missing",
        assertThrows(
                ValidationException.class,
                () -> new ProductSpecification(null, PURITY, WEIGHT, Optional.empty()))
            .code());
  }

  @Test
  void nullDimensionsOptionalIsRefused() {
    assertEquals(
        "specification.dimensions-missing",
        assertThrows(
                ValidationException.class,
                () -> new ProductSpecification(Metal.GOLD, PURITY, WEIGHT, null))
            .code());
  }

  @Test
  void blankDimensionsAreRefused() {
    assertEquals(
        "specification.dimensions-blank",
        assertThrows(
                ValidationException.class,
                () -> new ProductSpecification(Metal.GOLD, PURITY, WEIGHT, Optional.of("  ")))
            .code());
  }
}
