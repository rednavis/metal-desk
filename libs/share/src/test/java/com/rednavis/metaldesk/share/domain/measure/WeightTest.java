package com.rednavis.metaldesk.share.domain.measure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rednavis.metaldesk.share.error.ValidationException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class WeightTest {

  @Test
  void oneTroyOunceIsExactlyItsGramDefinition() {
    assertEquals(
        Weight.of("31.1034768", WeightUnit.GRAM),
        Weight.of("1", WeightUnit.TROY_OUNCE).toCanonical());
  }

  @Test
  void kilogramConvertsToGrams() {
    assertEquals(
        Weight.of("2500", WeightUnit.GRAM), Weight.of("2.5", WeightUnit.KILOGRAM).toCanonical());
  }

  @Test
  void compareToIgnoresTheUnit() {
    final Weight oneKilo = Weight.of("1", WeightUnit.KILOGRAM);

    assertTrue(oneKilo.compareTo(Weight.of("33", WeightUnit.TROY_OUNCE)) < 0);
    assertTrue(oneKilo.compareTo(Weight.of("999", WeightUnit.GRAM)) > 0);
    assertEquals(0, oneKilo.compareTo(Weight.of("1000", WeightUnit.GRAM)));
  }

  @Test
  void trailingZerosDoNotAffectEquality() {
    assertEquals(Weight.of("1.0", WeightUnit.GRAM), Weight.of("1", WeightUnit.GRAM));
    assertEquals(Weight.of("1000", WeightUnit.GRAM), Weight.of("1E+3", WeightUnit.GRAM));
  }

  @Test
  void differentUnitsAreNotEqualEvenWhenTheyWeighTheSame() {
    assertNotEquals(Weight.of("1000", WeightUnit.GRAM), Weight.of("1", WeightUnit.KILOGRAM));
  }

  @Test
  void zeroIsAllowedAndNegativeIsRefused() {
    assertEquals(BigDecimal.ZERO, Weight.of("0.00", WeightUnit.GRAM).amount());
    final ValidationException failure =
        assertThrows(ValidationException.class, () -> Weight.of("-1", WeightUnit.GRAM));
    assertEquals("weight.negative", failure.code());
  }

  @Test
  void nullsAndMalformedTextAreRefused() {
    assertThrows(ValidationException.class, () -> new Weight(null, WeightUnit.GRAM));
    assertThrows(ValidationException.class, () -> new Weight(BigDecimal.ONE, null));
    assertThrows(ValidationException.class, () -> Weight.of(null, WeightUnit.GRAM));
    final ValidationException failure =
        assertThrows(ValidationException.class, () -> Weight.of("heavy", WeightUnit.GRAM));
    assertEquals("weight.malformed-amount", failure.code());
  }
}
